package com.jumastappworks.mapstead.data.backup

import android.content.Context
import android.os.Build
import com.jumastappworks.mapstead.BuildConfig
import com.jumastappworks.mapstead.data.db.DatabaseTransactionRunner
import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.db.entities.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.*
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class BackupArchiveService @Inject constructor(
    private val db: MapsteadDatabase,
    private val transactionRunner: DatabaseTransactionRunner,
    private val attachmentStorage: AttachmentStorageService,
    private val json: Json,
    private val validator: BackupArchiveValidator,
    @ApplicationContext private val context: Context
) {
    
    open suspend fun createBackupArchive(
        isSafetyBackup: Boolean = false,
        onProgress: (Int) -> Unit = {}
    ): Result<CreatedBackupArchive> = withContext(Dispatchers.IO) {
        val backupId = UUID.randomUUID().toString()
        val tempDir = File(context.cacheDir, "backups/$backupId").apply { mkdirs() }
        val zipFile = File(context.cacheDir, "backups/Mapstead_Backup_${getTimestampString()}_${backupId.take(8)}.mapsteadbackup")
        
        try {
            onProgress(10)
            val snapshot = transactionRunner.run {
                DataSnapshot(
                    db.propertyDao().getAllPropertiesOnce(),
                    db.planDao().getAllPlansOnce(),
                    db.layerDao().getAllLayersOnce(),
                    db.mapFeatureDao().getAllFeaturesOnce(),
                    db.infrastructureDao().getAllItemsOnce(),
                    db.maintenanceDao().getAllRecordsOnce(),
                    db.maintenanceDao().getAllRemindersOnce(),
                    db.attachmentDao().getAllAttachmentsOnce(),
                    db.itemRelationshipDao().getAllRelationshipsOnce()
                )
            }
            
            onProgress(30)
            val dataDir = File(tempDir, "data").apply { mkdir() }
            val attachmentsDir = File(tempDir, "attachments").apply { mkdir() }
            
            writeFile(File(dataDir, "properties.json"), json.encodeToString(ListSerializer(PropertyEntity.serializer()), snapshot.properties))
            writeFile(File(dataDir, "plans.json"), json.encodeToString(ListSerializer(PlanEntity.serializer()), snapshot.plans))
            writeFile(File(dataDir, "layers.json"), json.encodeToString(ListSerializer(LayerEntity.serializer()), snapshot.layers))
            writeFile(File(dataDir, "map_features.json"), json.encodeToString(ListSerializer(MapFeatureEntity.serializer()), snapshot.features))
            writeFile(File(dataDir, "infrastructure_items.json"), json.encodeToString(ListSerializer(InfrastructureItemEntity.serializer()), snapshot.items))
            writeFile(File(dataDir, "maintenance_records.json"), json.encodeToString(ListSerializer(MaintenanceRecordEntity.serializer()), snapshot.maintenance))
            writeFile(File(dataDir, "reminders.json"), json.encodeToString(ListSerializer(ReminderEntity.serializer()), snapshot.reminders))
            writeFile(File(dataDir, "attachments.json"), json.encodeToString(ListSerializer(AttachmentEntity.serializer()), snapshot.attachments))
            writeFile(File(dataDir, "item_relationships.json"), json.encodeToString(ListSerializer(ItemRelationshipEntity.serializer()), snapshot.relationships))
            
            onProgress(50)
            val warnings = mutableListOf<String>()
            var includedBytes = 0L
            snapshot.attachments.forEach { attachment ->
                val sourceFile = attachment.appManagedCopyPath?.let { path ->
                    attachmentStorage.resolveFromEntityPath(path).getOrNull()
                }
                if (sourceFile != null && sourceFile.exists()) {
                    attachmentStorage.validateFile(sourceFile, attachment.fileSizeBytes, attachment.sha256).onSuccess {
                        val destFile = File(attachmentsDir, attachment.id.toString())
                        sourceFile.copyTo(destFile, overwrite = true)
                        includedBytes += sourceFile.length()
                    }.onFailure { e ->
                        if (isSafetyBackup) throw IOException("Safety backup failed: attachment ${attachment.displayName} is invalid (${e.message})")
                        warnings.add("Attachment file invalid: ${attachment.displayName} (${e.message})")
                    }
                } else {
                    if (isSafetyBackup) throw IOException("Safety backup failed: attachment ${attachment.displayName} is missing")
                    warnings.add("Missing attachment: ${attachment.displayName}")
                }
            }
            
            val manifest = BackupManifest(
                formatVersion = 1,
                backupId = backupId,
                createdAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                appVersionName = BuildConfig.VERSION_NAME,
                appVersionCode = BuildConfig.VERSION_CODE,
                databaseSchemaVersion = db.openHelper.readableDatabase.version,
                deviceManufacturer = Build.MANUFACTURER,
                deviceModel = Build.MODEL,
                androidVersion = Build.VERSION.RELEASE,
                propertyCount = snapshot.properties.size,
                planCount = snapshot.plans.size,
                layerCount = snapshot.layers.size,
                mapFeatureCount = snapshot.features.size,
                infrastructureCount = snapshot.items.size,
                maintenanceCount = snapshot.maintenance.size,
                reminderCount = snapshot.reminders.size,
                attachmentCount = snapshot.attachments.size,
                relationshipCount = snapshot.relationships.size,
                includedAttachmentBytes = includedBytes,
                warnings = warnings
            )
            writeFile(File(tempDir, "manifest.json"), json.encodeToString(BackupManifest.serializer(), manifest))
            
            onProgress(70)
            val checksums = mutableListOf<BackupFileChecksum>()
            tempDir.walkTopDown().sortedBy { it.absolutePath }.forEach { file ->
                if (file.isFile && file.name != "checksums.json") {
                    val relPath = file.relativeTo(tempDir).path.replace('\\', '/')
                    val category = when {
                        relPath == "manifest.json" -> "MANIFEST"
                        relPath.startsWith("data/") -> "DATA"
                        relPath.startsWith("attachments/") -> "ATTACHMENT"
                        relPath.startsWith("plans/") -> "PLAN"
                        else -> "DATA"
                    }
                    checksums.add(BackupFileChecksum(relPath, calculateSha256(file), file.length(), category))
                }
            }
            writeFile(File(tempDir, "checksums.json"), json.encodeToString(BackupChecksums.serializer(), BackupChecksums(checksums)))
            
            onProgress(90)
            zipDirectory(tempDir, zipFile)
            onProgress(100)
            Result.success(CreatedBackupArchive(zipFile, manifest))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    suspend fun getRestorePreview(zipFile: File): Result<BackupValidationReport> = withContext(Dispatchers.IO) {
        validator.validate(zipFile)
    }

    private fun writeFile(file: File, content: String) {
        file.writeText(content)
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { isStream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (isStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun zipDirectory(sourceDir: File, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            sourceDir.walkTopDown().sortedBy { it.absolutePath }.forEach { file ->
                if (file.isFile) {
                    val entryName = file.relativeTo(sourceDir).path.replace('\\', '/')
                    val entry = ZipEntry(entryName)
                    zos.putNextEntry(entry)
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }

    private fun getTimestampString(): String {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss").format(java.time.LocalDateTime.now())
    }

    private data class DataSnapshot(
        val properties: List<PropertyEntity>,
        val plans: List<PlanEntity>,
        val layers: List<LayerEntity>,
        val features: List<MapFeatureEntity>,
        val items: List<InfrastructureItemEntity>,
        val maintenance: List<MaintenanceRecordEntity>,
        val reminders: List<ReminderEntity>,
        val attachments: List<AttachmentEntity>,
        val relationships: List<ItemRelationshipEntity>
    )
}

