package com.jumastappworks.mapstead.data.backup

import android.content.Context
import com.jumastappworks.mapstead.data.db.DatabaseTransactionRunner
import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import com.jumastappworks.mapstead.data.db.entities.ReminderEntity
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import com.jumastappworks.mapstead.data.work.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestoreCoordinator @Inject constructor(
    private val db: MapsteadDatabase,
    private val transactionRunner: DatabaseTransactionRunner,
    private val archiveService: BackupArchiveService,
    private val attachmentStorage: AttachmentStorageService,
    private val prefs: UserPreferencesRepository,
    private val journalManager: RestoreJournalManager,
    private val validator: BackupArchiveValidator,
    private val coordinator: BackupOperationCoordinator,
    private val reminderScheduler: ReminderScheduler,
    @ApplicationContext private val context: Context
) {

    suspend fun restore(
        zipFile: File,
        manifestBackupId: String? = null,
        driveFileId: String? = null,
        createSafetyBackup: Boolean = true,
        onProgress: (BackupOperationPhase, Int) -> Unit
    ): Result<SafetyBackupReference?> = withContext(Dispatchers.IO) {
        // 1. Validate archive
        onProgress(BackupOperationPhase.VALIDATING, 5)
        val validationResult = validator.validate(zipFile)
        if (validationResult.isFailure) return@withContext Result.failure(validationResult.exceptionOrNull()!!)
        val report = validationResult.getOrThrow()
        
        val oldReminders = try { db.maintenanceDao().getAllRemindersOnce() } catch (_: Exception) { emptyList() }

        // 2. Perform storage preflight
        onProgress(BackupOperationPhase.PREPARING, 10)
        val dbFile = context.getDatabasePath(MapsteadDatabase.DATABASE_NAME)
        val walFile = File(dbFile.path + "-wal")
        val shmFile = File(dbFile.path + "-shm")
        val dbSize = (if (dbFile.exists()) dbFile.length() else 0L) +
                (if (walFile.exists()) walFile.length() else 0L) +
                (if (shmFile.exists()) shmFile.length() else 0L)
        val activeAttachmentsDir = File(context.filesDir, "mapstead_attachments")
        val currentAttachmentsSize = activeAttachmentsDir.listFiles()?.sumOf { it.length() } ?: 0L
        val currentAttachmentsCount = activeAttachmentsDir.listFiles()?.size ?: 0

        coordinator.checkStorageCapacity(
            estimatedArchiveBytes = 0, // already downloaded
            estimatedStagingBytes = report.manifest.includedAttachmentBytes,
            estimatedRollbackBytes = currentAttachmentsSize,
            estimatedSafetyBytes = dbSize + currentAttachmentsSize,
            estimatedDbBytes = dbSize
        ).getOrThrow()

        val journal = RestoreJournalData(
            manifestBackupId = manifestBackupId,
            driveFileId = driveFileId,
            downloadedArchivePath = zipFile.absolutePath,
            stage = RestoreJournalStage.INITIALIZED
        )
        journalManager.saveJournal(journal)

        val stagingDirResult = attachmentStorage.prepareStagingDir()
        if (stagingDirResult.isFailure) return@withContext Result.failure(stagingDirResult.exceptionOrNull()!!)
        val stagingDir = stagingDirResult.getOrThrow()
        journal.stagingAttachmentPath = stagingDir.absolutePath
        journalManager.saveJournal(journal)

        val rollbackDirResult = attachmentStorage.prepareRollbackDir()
        if (rollbackDirResult.isFailure) return@withContext Result.failure(rollbackDirResult.exceptionOrNull()!!)
        val rollbackDir = rollbackDirResult.getOrThrow()
        journal.rollbackAttachmentPath = rollbackDir.absolutePath
        journalManager.saveJournal(journal)

        var safetyBackupFile: File? = null
        var safetyRef: SafetyBackupReference? = null
        try {
            // 3. Stage and verify replacement attachments
            onProgress(BackupOperationPhase.STAGING_ATTACHMENTS, 20)
            journal.stage = RestoreJournalStage.STAGING_ATTACHMENTS
            journalManager.saveJournal(journal)
            if (report.extractionDir != null && File(report.extractionDir, "attachments").exists()) {
                val attachmentsSource = File(report.extractionDir, "attachments")
                attachmentsSource.copyRecursively(stagingDir, overwrite = true)
            } else {
                unzipAttachments(zipFile, stagingDir)
            }
            verifyStagedAttachments(stagingDir, report)

            if (createSafetyBackup) {
                // 4. Create and verify a COMPLETE safety backup
                onProgress(BackupOperationPhase.CREATING_SAFETY_BACKUP, 40)
                journal.stage = RestoreJournalStage.CREATING_SAFETY_BACKUP
                journalManager.saveJournal(journal)
                val safetyResult = archiveService.createBackupArchive(isSafetyBackup = true) { /* sub-progress */ }
                if (safetyResult.isFailure) throw Exception("Safety backup failed: ${safetyResult.exceptionOrNull()?.message}")
                val createdSafety = safetyResult.getOrThrow()
                safetyBackupFile = moveSafetyBackup(createdSafety.file)
                journal.safetyBackupPath = safetyBackupFile.absolutePath
                journal.stage = RestoreJournalStage.VALIDATING_SAFETY_BACKUP
                journalManager.saveJournal(journal)
                validateSafetyBackup(safetyBackupFile)

                safetyRef = SafetyBackupReference(
                    backupId = createdSafety.manifest.backupId,
                    file = safetyBackupFile,
                    createdAt = Instant.parse(createdSafety.manifest.createdAt),
                    sizeBytes = safetyBackupFile.length(),
                    validationStatus = SafetyBackupValidationStatus.VALID
                )

                // 5. Write/Update RestoreJournal
                journalManager.saveJournal(journal)
            } else {
                onProgress(BackupOperationPhase.CREATING_SAFETY_BACKUP, 45)
            }

            // CRITICAL SECTION START (Steps 6-11) - protected from cancellation!
            withContext(NonCancellable) {
                // 6. Move current attachment root to rollback storage
                onProgress(BackupOperationPhase.ACTIVATING_ATTACHMENTS, 50)
                journal.stage = RestoreJournalStage.MOVING_ACTIVE_TO_ROLLBACK
                journal.activeAttachmentPath = File(context.filesDir, "mapstead_attachments").absolutePath
                journalManager.saveJournal(journal)
                attachmentStorage.moveActiveToRollback(rollbackDir).getOrThrow()

                // 7. Verify rollback storage
                attachmentStorage.verifyRollbackStorage(rollbackDir, currentAttachmentsCount, currentAttachmentsSize).getOrThrow()

                // 8. Activate staged attachment root
                journal.stage = RestoreJournalStage.ACTIVATING_STAGED_ATTACHMENTS
                journalManager.saveJournal(journal)
                attachmentStorage.activateStagedAttachments(stagingDir).getOrThrow()

                // 9. Verify active attachment root
                attachmentStorage.verifyActiveAttachmentRoot(report.attachments.size, report.manifest.includedAttachmentBytes).getOrThrow()

                // 10. Execute Room replacement transaction with COMMIT MARKER
                onProgress(BackupOperationPhase.REPLACING_DATABASE, 70)
                journal.stage = RestoreJournalStage.DATABASE_TRANSACTION_START
                journalManager.saveJournal(journal)
                transactionRunner.run {
                    val commitMarker = com.jumastappworks.mapstead.data.db.entities.BackupRecordEntity(
                        id = UUID.fromString(journal.operationId),
                        operationType = "RESTORE",
                        status = "COMMIT_MARKER",
                        backupId = report.manifest.backupId
                    )
                    db.backupDao().insertBackupRecord(commitMarker)
                    
                    clearCurrentData()
                    insertRestoredData(report)
                }
                
                journal.stage = RestoreJournalStage.DATABASE_TRANSACTION_COMMIT
                journal.wasDbCommitted = true
                journalManager.saveJournal(journal)

                // 11. Perform post-restore repair
                onProgress(BackupOperationPhase.VERIFYING, 85)
                journal.stage = RestoreJournalStage.POST_RESTORE_VERIFICATION
                journalManager.saveJournal(journal)
                performPostRestoreRepair(report, oldReminders)
                
                // 12. Verify restored attachments
                verifyRestoredFiles(report)

                // 13. Verify database counts against manifest
                verifyDatabaseCounts(report.manifest)

                // Clear COMMIT MARKER after post-restore verification
                db.backupDao().deleteBackupRecordById(UUID.fromString(journal.operationId))

                // 14. Mark journal verified then completed
                journal.wasPostRestoreVerified = true
                journal.stage = RestoreJournalStage.CLEANING_UP
                journalManager.saveJournal(journal)
            }
            
            onProgress(BackupOperationPhase.SUCCESS, 100)
            journal.stage = RestoreJournalStage.SUCCESS
            journalManager.saveJournal(journal)
            
            // 15. Only then remove rollback storage
            rollbackDir.deleteRecursively()
            journalManager.deleteJournal()

            applyRetentionPolicy()

            Result.success(safetyRef)
        } catch (e: Exception) {
            performCompensation(journal, rollbackDir, safetyBackupFile)
            Result.failure(e)
        } finally {
            stagingDir.deleteRecursively()
            report.extractionDir?.deleteRecursively()
        }
    }

    private suspend fun performCompensation(journal: RestoreJournalData, rollbackDir: File, safetyBackupFile: File?) {
        try {
            // Rollback attachments if we moved them to rollback storage
            if (journal.stage.ordinal >= RestoreJournalStage.ACTIVATING_STAGED_ATTACHMENTS.ordinal) {
                attachmentStorage.rollback(rollbackDir).getOrThrow()
            }

            // Restore database if transaction committed but verification failed
            if (journal.wasDbCommitted && !journal.wasPostRestoreVerified && safetyBackupFile != null) {
                restoreDatabaseFromSafety(safetyBackupFile)
            }
        } catch (compensationError: Exception) {
            journal.stage = RestoreJournalStage.RECOVERY_FAILED
            journal.lastRecoveryError = "Rollback compensation failed: ${compensationError.message}"
            journalManager.saveJournal(journal)
            throw compensationError
        }
    }

    private suspend fun restoreDatabaseFromSafety(safetyBackupFile: File) {
        val report = archiveService.getRestorePreview(safetyBackupFile).getOrThrow()
        transactionRunner.run {
            clearCurrentData()
            insertRestoredData(report)
        }
    }

    private suspend fun performPostRestoreRepair(report: BackupValidationReport, oldReminders: List<ReminderEntity>) {
        // 1. Verify selected property
        val currentSelected = prefs.userPreferencesFlow.first().selectedPropertyId
        val exists = currentSelected != null && report.properties.any { it.id.toString() == currentSelected }
        if (!exists) {
            val firstProperty = report.properties.firstOrNull { !it.isArchived && it.deletedAt == null }
                ?: report.properties.firstOrNull { it.deletedAt == null }
            prefs.updateSelectedProperty(firstProperty?.id?.toString())
        }

        // 2. Cancel old/obsolete reminders, reschedule restored ones
        oldReminders.forEach { reminderScheduler.cancelReminder(it.id) }
        report.reminders.forEach { reminderScheduler.scheduleReminder(it) }

        // 3. Recreate application root directory for attachments
        val attachmentsDir = File(context.filesDir, "mapstead_attachments")
        if (!attachmentsDir.exists()) {
            if (!attachmentsDir.mkdirs()) {
                throw IOException("Failed to recreate application root: ${attachmentsDir.absolutePath}")
            }
        }
    }

    private fun verifyRestoredFiles(report: BackupValidationReport) {
        report.attachments.forEach { attachment ->
            val file = attachmentStorage.getAttachmentFile(attachment.id)
            attachmentStorage.validateFile(file, attachment.fileSizeBytes, attachment.sha256).getOrThrow()
        }
    }

    private suspend fun verifyDatabaseCounts(manifest: BackupManifest) {
        if (db.propertyDao().getCount() != manifest.propertyCount) throw IOException("Database count mismatch: Properties")
        if (db.planDao().getCount() != manifest.planCount) throw IOException("Database count mismatch: Plans")
        if (db.attachmentDao().getCount() != manifest.attachmentCount) throw IOException("Database count mismatch: Attachments")
    }

    private fun moveSafetyBackup(tempFile: File): File {
        val safetyDir = File(context.filesDir, "safety_backups").apply { mkdirs() }
        val permanentFile = File(safetyDir, "Mapstead_Safety_${System.currentTimeMillis()}.mapsteadbackup")
        if (!tempFile.renameTo(permanentFile)) {
            tempFile.copyTo(permanentFile, overwrite = true)
            tempFile.delete()
        }
        return permanentFile
    }

    private fun validateSafetyBackup(file: File) {
        validator.validate(file).getOrThrow()
    }

    private fun verifyStagedAttachments(stagingDir: File, report: BackupValidationReport) {
        report.attachments.forEach { attachment ->
            val file = File(stagingDir, attachment.id.toString())
            attachmentStorage.validateFile(file, attachment.fileSizeBytes, attachment.sha256).getOrThrow()
        }
    }

    private fun unzipAttachments(zipFile: File, destDir: File) {
        java.util.zip.ZipInputStream(java.io.FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.startsWith("attachments/")) {
                    val file = File(destDir, entry.name.removePrefix("attachments/"))
                    if (!file.canonicalPath.startsWith(destDir.canonicalPath + File.separator)) {
                        throw IOException("ZIP slip in attachments")
                    }
                    file.parentFile?.mkdirs()
                    file.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private suspend fun clearCurrentData() {
        db.itemRelationshipDao().clearAll()
        db.attachmentDao().clearAll()
        db.maintenanceDao().clearAllReminders()
        db.maintenanceDao().clearAllRecords()
        db.mapFeatureDao().clearAll()
        db.infrastructureDao().clearAll()
        db.layerDao().clearAll()
        db.planDao().clearAll()
        db.propertyDao().clearAll()
    }

    private suspend fun insertRestoredData(report: BackupValidationReport) {
        report.properties.forEach { db.propertyDao().upsertProperty(it) }
        report.plans.forEach { db.planDao().upsertPlan(it) }
        report.layers.forEach { db.layerDao().upsertLayer(it) }
        
        val sortedItems = topologicalSortItems(report.items)
        sortedItems.forEach { db.infrastructureDao().upsertItem(it) }
        
        report.features.forEach { db.mapFeatureDao().upsertFeature(it) }
        report.maintenance.forEach { db.maintenanceDao().upsertRecord(it) }
        report.reminders.forEach { db.maintenanceDao().upsertReminder(it) }
        
        report.attachments.forEach { 
            val updated = it.copy(appManagedCopyPath = attachmentStorage.getEntityPath(it.id))
            db.attachmentDao().upsertAttachment(updated) 
        }
        report.relationships.forEach { db.itemRelationshipDao().upsertRelationship(it) }
    }

    suspend fun getSafetyBackups(): List<SafetyBackupReference> = withContext(Dispatchers.IO) {
        val safetyDir = File(context.filesDir, "safety_backups")
        if (!safetyDir.exists()) return@withContext emptyList()

        safetyDir.listFiles()?.mapNotNull { file ->
            try {
                val previewResult = archiveService.getRestorePreview(file)
                val status = if (previewResult.isSuccess) SafetyBackupValidationStatus.VALID else SafetyBackupValidationStatus.CORRUPT_ARCHIVE
                val manifest = previewResult.getOrNull()?.manifest
                SafetyBackupReference(
                    backupId = manifest?.backupId ?: file.nameWithoutExtension,
                    file = file,
                    createdAt = manifest?.createdAt?.let { Instant.parse(it) } ?: Instant.ofEpochMilli(file.lastModified()),
                    sizeBytes = file.length(),
                    validationStatus = status
                )
            } catch (e: Exception) {
                null
            }
        }?.sortedByDescending { it.createdAt } ?: emptyList()
    }

    suspend fun deleteSafetyBackup(backupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val safetyBackups = getSafetyBackups()
        val toDelete = safetyBackups.find { it.backupId == backupId }
            ?: return@withContext Result.failure(Exception("Safety backup not found"))
        
        if (toDelete.file.delete()) {
            Result.success(Unit)
        } else {
            Result.failure(IOException("Failed to delete safety backup file"))
        }
    }

    suspend fun restoreSafetyBackup(backupId: String, onProgress: (BackupOperationPhase, Int) -> Unit): Result<SafetyBackupReference?> = withContext(Dispatchers.IO) {
        val safetyBackups = getSafetyBackups()
        val reference = safetyBackups.find { it.backupId == backupId }
            ?: return@withContext Result.failure(Exception("Safety backup not found"))
        
        // Allow only one level of automatic safety generation during a recovery
        // If the backup we are restoring is a safety backup, we still create ONE safety backup of current state.
        // But if we were ALREADY in a restore process, the coordinator handles it.
        // The instruction says "Allow only one level of automatic safety generation during a recovery".
        // This likely means if this call to restore() is part of a recovery, we shouldn't chain.
        // For manual "Restore Previous Local State", we DO want a safety backup of what we're replacing.
        
        restore(
            zipFile = reference.file,
            manifestBackupId = reference.backupId,
            createSafetyBackup = true, // We still want to backup current state before restoring previous
            onProgress = onProgress
        )
    }

    private suspend fun applyRetentionPolicy() = withContext(Dispatchers.IO) {
        val safetyBackups = getSafetyBackups() // Sorted newest first
        
        val activeJournal = journalManager.loadJournal()
        val referencedPath = activeJournal?.safetyBackupPath

        var validCount = 0
        safetyBackups.forEach { ref ->
            val isReferenced = ref.file.absolutePath == referencedPath
            if (isReferenced) {
                return@forEach
            }
            
            if (ref.validationStatus == SafetyBackupValidationStatus.VALID) {
                validCount++
                if (validCount > 3) {
                    ref.file.delete()
                }
            } else {
                ref.file.delete()
            }
        }
    }

    private fun topologicalSortItems(items: List<InfrastructureItemEntity>): List<InfrastructureItemEntity> {
        val result = mutableListOf<InfrastructureItemEntity>()
        val remaining = items.toMutableList()
        val insertedIds = mutableSetOf<UUID>()
        
        while (remaining.isNotEmpty()) {
            val countBefore = remaining.size
            val iterator = remaining.iterator()
            while (iterator.hasNext()) {
                val item = iterator.next()
                if (item.parentItemId == null || insertedIds.contains(item.parentItemId)) {
                    result.add(item)
                    insertedIds.add(item.id)
                    iterator.remove()
                }
            }
            if (remaining.size == countBefore) throw IOException("Circular or broken infrastructure hierarchy")
        }
        return result
    }
}
