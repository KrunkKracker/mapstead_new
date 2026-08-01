package com.jumastappworks.mapstead.data.backup

import androidx.room.withTransaction
import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.db.entities.BackupRecordEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestoreRecoveryManager @Inject constructor(
    private val journalManager: RestoreJournalManager,
    private val attachmentStorage: AttachmentStorageService,
    private val db: MapsteadDatabase,
    private val archiveService: BackupArchiveService,
    private val featureGate: BackupFeatureGate,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val _recoveryStatus = MutableStateFlow<RecoveryStatus>(RecoveryStatus.Idle)
    val recoveryStatus = _recoveryStatus.asStateFlow()

    sealed class RecoveryStatus {
        object Idle : RecoveryStatus()
        object Recovering : RecoveryStatus()
        object RecoveryRequired : RecoveryStatus()
        object Success : RecoveryStatus()
        data class Failed(val error: String) : RecoveryStatus()
    }

    fun checkAndRecover(scope: CoroutineScope) {
        if (!featureGate.isEnabled) return
        val journal = journalManager.loadJournal() ?: return
        if (journal.stage == RestoreJournalStage.SUCCESS) {
            journalManager.deleteJournal()
            return
        }
        if (journal.stage == RestoreJournalStage.RECOVERY_FAILED) {
            _recoveryStatus.value = RecoveryStatus.RecoveryRequired
            return
        }

        scope.launch(ioDispatcher) {
            _recoveryStatus.value = RecoveryStatus.Recovering
            try {
                performRecovery(journal)
                if (_recoveryStatus.value == RecoveryStatus.RecoveryRequired) {
                    journal.stage = RestoreJournalStage.RECOVERY_FAILED
                    journal.lastRecoveryError = "Manual recovery required"
                    journalManager.saveJournal(journal)
                } else {
                    _recoveryStatus.value = RecoveryStatus.Success
                    journalManager.deleteJournal()
                }
            } catch (e: Exception) {
                journal.stage = RestoreJournalStage.RECOVERY_FAILED
                journal.lastRecoveryError = e.message
                journalManager.saveJournal(journal)
                _recoveryStatus.value = RecoveryStatus.Failed(e.message ?: "Recovery failed")
            }
        }
    }

    private suspend fun performRecovery(journal: RestoreJournalData) {
        val rollbackDir = journal.rollbackAttachmentPath?.let { File(it) }
        val safetyFile = journal.safetyBackupPath?.let { File(it) }

        when (journal.stage) {
            RestoreJournalStage.IDLE,
            RestoreJournalStage.SUCCESS -> {
                // No compensation needed
            }
            
            RestoreJournalStage.INITIALIZED,
            RestoreJournalStage.VALIDATING_ARCHIVE,
            RestoreJournalStage.PREPARING_STAGING,
            RestoreJournalStage.EXTRACTING_ARCHIVE,
            RestoreJournalStage.STAGING_ATTACHMENTS -> {
                // Stage 1-5: Just clean up temp and staging files
                cleanup(journal)
            }
            
            RestoreJournalStage.CREATING_SAFETY_BACKUP,
            RestoreJournalStage.VALIDATING_SAFETY_BACKUP -> {
                // Stage 6-7: Clean up temp, staging and safety file if partially created
                cleanup(journal)
                safetyFile?.delete()
            }
            
            RestoreJournalStage.PREPARING_ROLLBACK -> {
                // Stage 8: Clean up rollback dir if created
                cleanup(journal)
                rollbackDir?.deleteRecursively()
                safetyFile?.delete()
            }
            
            RestoreJournalStage.ACTIVATING_ATTACHMENTS,
            RestoreJournalStage.MOVING_ACTIVE_TO_ROLLBACK,
            RestoreJournalStage.ACTIVATING_STAGED_ATTACHMENTS -> {
                // Stage 9-10: Roll back attachments from rollback directory
                if (rollbackDir != null && rollbackDir.exists()) {
                    attachmentStorage.rollback(rollbackDir).getOrThrow()
                }
                cleanup(journal)
                safetyFile?.delete()
            }
            
            RestoreJournalStage.DATABASE_TRANSACTION,
            RestoreJournalStage.DATABASE_TRANSACTION_START,
            RestoreJournalStage.DATABASE_TRANSACTION_COMMIT,
            RestoreJournalStage.POST_RESTORE_VERIFICATION,
            RestoreJournalStage.CLEANING_UP,
            RestoreJournalStage.RECOVERY_REQUIRED -> {
                // Stage 11-14: Destructive database transaction phase has run or is unknown.
                // We MUST compensate from validated safety backup!
                if (safetyFile != null && safetyFile.exists()) {
                    // 1. Rollback attachments first if possible
                    if (rollbackDir != null && rollbackDir.exists()) {
                        attachmentStorage.rollback(rollbackDir).getOrNull()
                    }
                    
                    // 2. Restore database and attachments from safety backup
                    restoreFromSafetyBackup(safetyFile)
                    cleanup(journal)
                } else {
                    // No safety backup available to restore from, manual recovery required
                    _recoveryStatus.value = RecoveryStatus.RecoveryRequired
                }
            }
            
            RestoreJournalStage.RECOVERY_FAILED -> {
                _recoveryStatus.value = RecoveryStatus.RecoveryRequired
            }
        }
    }

    private suspend fun restoreFromSafetyBackup(safetyFile: File) {
        val reportResult = archiveService.getRestorePreview(safetyFile)
        if (reportResult.isFailure) {
            throw IOException("Failed to validate safety backup for recovery: ${reportResult.exceptionOrNull()?.message}")
        }
        val report = reportResult.getOrThrow()

        // 1. Restore Database inside a transaction
        db.withTransaction {
            db.itemRelationshipDao().clearAll()
            db.attachmentDao().clearAll()
            db.maintenanceDao().clearAllReminders()
            db.maintenanceDao().clearAllRecords()
            db.infrastructureDao().clearAll()
            db.mapFeatureDao().clearAll()
            db.layerDao().clearAll()
            db.planDao().clearAll()
            db.propertyDao().clearAll()

            report.properties.forEach { db.propertyDao().insertProperty(it) }
            report.plans.forEach { db.planDao().insertPlan(it) }
            report.layers.forEach { db.layerDao().insertLayer(it) }
            report.features.forEach { db.mapFeatureDao().insertFeature(it) }
            report.items.forEach { db.infrastructureDao().insertItem(it) }
            report.maintenance.forEach { db.maintenanceDao().insertRecord(it) }
            report.reminders.forEach { db.maintenanceDao().insertReminder(it) }
            report.attachments.forEach { db.attachmentDao().insertAttachment(it) }
            report.relationships.forEach { db.itemRelationshipDao().insertRelationship(it) }
        }

        // 2. Restore attachments
        val activeAttachmentsDir = File(attachmentStorage.getAttachmentFile(UUID.randomUUID()).parentFile ?: File(""), "")
        activeAttachmentsDir.mkdirs()
        
        java.util.zip.ZipInputStream(java.io.FileInputStream(safetyFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.startsWith("attachments/")) {
                    val fileIdStr = entry.name.removePrefix("attachments/")
                    val destFile = attachmentStorage.getAttachmentFile(UUID.fromString(fileIdStr))
                    destFile.parentFile?.mkdirs()
                    destFile.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun cleanup(journal: RestoreJournalData) {
        journal.stagingAttachmentPath?.let { File(it).deleteRecursively() }
        journal.downloadedArchivePath?.let { File(it).delete() }
    }
}