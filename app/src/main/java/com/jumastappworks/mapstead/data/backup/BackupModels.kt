package com.jumastappworks.mapstead.data.backup

import com.jumastappworks.mapstead.data.db.entities.*
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import java.io.File
import java.time.Instant

@Serializable
data class BackupManifest(
    val formatVersion: Int,
    val backupId: String,
    val createdAt: String, // ISO-8601
    val appVersionName: String,
    val appVersionCode: Int,
    val databaseSchemaVersion: Int,
    val deviceManufacturer: String,
    val deviceModel: String,
    val androidVersion: String,
    val propertyCount: Int,
    val planCount: Int,
    val layerCount: Int,
    val mapFeatureCount: Int,
    val infrastructureCount: Int,
    val maintenanceCount: Int,
    val reminderCount: Int,
    val attachmentCount: Int,
    val relationshipCount: Int,
    val includedAttachmentBytes: Long,
    val warnings: List<String> = emptyList()
)

@Serializable
data class BackupFileChecksum(
    val path: String,
    val hash: String, // SHA-256
    val size: Long,
    val payloadCategory: String = "DATA"
)

@Serializable
data class BackupChecksums(
    val files: List<BackupFileChecksum>
)

enum class BackupOperationType {
    BACKUP, RESTORE
}

enum class BackupOperationPhase {
    IDLE,
    AUTHORIZING,
    PREPARING,
    CREATING_ARCHIVE,
    UPLOADING,
    DOWNLOADING,
    VALIDATING,
    PREVIEWING,
    CREATING_SAFETY_BACKUP,
    STAGING_ATTACHMENTS,
    ACTIVATING_ATTACHMENTS,
    REPLACING_DATABASE,
    VERIFYING,
    COMPENSATING,
    SUCCESS,
    FAILED,
    CANCELLED
}

enum class SafetyBackupValidationStatus {
    VALID,
    CORRUPT_ARCHIVE,
    MISSING_CRITICAL_FILES,
    DATABASE_INTEGRITY_FAILED,
    INSUFFICIENT_SPACE
}

data class DriveBackupMetadata(
    val backupId: String,
    val createdAt: String,
    val appVersion: String,
    val formatVersion: Int
)

data class DriveBackupFile(
    val driveFileId: String,
    val name: String,
    val size: Long,
    val createdAt: String,
    val appVersion: String,
    val formatVersion: Int,
    val backupId: String
)

data class DriveFolder(
    val driveFolderId: String,
    val name: String
)

@Serializable
sealed interface PendingDriveAction : Parcelable {
    val retryCount: Int
    val operationId: String

    @Serializable
    @Parcelize
    data class Connect(
        override val retryCount: Int = 0,
        override val operationId: String = ""
    ) : PendingDriveAction

    @Serializable
    @Parcelize
    data class ListBackups(
        override val retryCount: Int = 0,
        override val operationId: String = ""
    ) : PendingDriveAction

    @Serializable
    @Parcelize
    data class CreateBackup(
        override val retryCount: Int = 0,
        override val operationId: String = ""
    ) : PendingDriveAction

    @Serializable
    @Parcelize
    data class Restore(
        val fileId: String,
        override val retryCount: Int = 0,
        override val operationId: String = ""
    ) : PendingDriveAction

    @Serializable
    @Parcelize
    data class PreviewRestore(
        val fileId: String,
        override val retryCount: Int = 0,
        override val operationId: String = ""
    ) : PendingDriveAction

    @Serializable
    @Parcelize
    data class Delete(
        val fileId: String,
        override val retryCount: Int = 0,
        override val operationId: String = ""
    ) : PendingDriveAction
}

sealed class DriveAuthorizationResult {
    data class ResolutionRequired(val pendingIntent: android.app.PendingIntent) : DriveAuthorizationResult()
    data class Authorized(val accessToken: String, val email: String?) : DriveAuthorizationResult()
    data class Failure(val error: DriveError) : DriveAuthorizationResult()
}

data class BackupValidationReport(
    val manifest: BackupManifest,
    val properties: List<PropertyEntity>,
    val plans: List<PlanEntity>,
    val layers: List<LayerEntity>,
    val features: List<MapFeatureEntity>,
    val items: List<InfrastructureItemEntity>,
    val maintenance: List<MaintenanceRecordEntity>,
    val reminders: List<ReminderEntity>,
    val attachments: List<AttachmentEntity>,
    val relationships: List<ItemRelationshipEntity>,
    val warnings: List<String>,
    val extractionDir: File? = null
)

data class PendingRestore(
    val driveFile: DriveBackupFile,
    val localArchive: File,
    val validationReport: BackupValidationReport
)

data class SafetyBackupReference(
    val backupId: String,
    val file: File,
    val createdAt: Instant,
    val sizeBytes: Long,
    val validationStatus: SafetyBackupValidationStatus
)

data class CreatedBackupArchive(
    val file: File,
    val manifest: BackupManifest
)

data class BackupArchiveLimits(
    val maxArchiveBytes: Long = 500 * 1024 * 1024L, // 500MB
    val maxEntryCount: Int = 5000,
    val maxEntryUncompressedBytes: Long = 200 * 1024 * 1024L, // 200MB
    val maxTotalExtractedBytes: Long = 1024 * 1024 * 1024L, // 1GB
    val maxJsonBytes: Long = 50 * 1024 * 1024L, // 50MB
    val maxAttachmentCount: Int = 1000,
    val maxPlanFileCount: Int = 100,
    val maxCompressionRatio: Double = 100.0,
    val maxFilenameLength: Int = 255,
    val maxPathDepth: Int = 10
)

data class PendingIncompleteUpload(
    val archiveFile: File,
    val manifest: BackupManifest,
    val recordId: java.util.UUID,
    val accessToken: String
)
