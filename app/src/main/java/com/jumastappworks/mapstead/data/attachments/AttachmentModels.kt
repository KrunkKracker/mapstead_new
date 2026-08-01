package com.jumastappworks.mapstead.data.attachments

import android.net.Uri
import java.util.UUID

sealed interface AttachmentOwner {
    val propertyId: UUID

    data class Property(override val propertyId: UUID) : AttachmentOwner
    data class InfrastructureItem(
        override val propertyId: UUID,
        val itemId: UUID
    ) : AttachmentOwner
    data class MaintenanceRecord(
        override val propertyId: UUID,
        val recordId: UUID
    ) : AttachmentOwner
    data class MapFeature(
        override val propertyId: UUID,
        val featureId: UUID
    ) : AttachmentOwner
}

enum class AttachmentType(val canonicalName: String) {
    Photo("Photo"),
    Document("Document"),
    Receipt("Receipt"),
    Manual("Manual"),
    Warranty("Warranty"),
    Inspection("Inspection"),
    Diagram("Diagram"),
    Other("Other");

    companion object {
        fun fromString(value: String?): AttachmentType {
            val normalized = value?.trim()?.lowercase() ?: return Other
            return entries.find { it.canonicalName.lowercase() == normalized } ?: Other
        }
    }
}

enum class AttachmentNavigationOrigin {
    PROPERTY_FILES,
    INFRASTRUCTURE,
    MAINTENANCE,
    MAP_FEATURE
}

sealed interface AttachmentWriteResult {
    data class Success(val attachmentId: UUID) : AttachmentWriteResult
    data object NotFound : AttachmentWriteResult
    data object OwnershipMismatch : AttachmentWriteResult
    data object InvalidOwner : AttachmentWriteResult
    data object UnsupportedType : AttachmentWriteResult
    data object TooLarge : AttachmentWriteResult
    data object CopyFailed : AttachmentWriteResult
    data object ValidationFailed : AttachmentWriteResult
    data object StorageFailure : AttachmentWriteResult
    data class Error(val message: String?) : AttachmentWriteResult
}

sealed interface AttachmentSaveResult {
    data object Saved : AttachmentSaveResult
    data class SavedWithCoverWarning(val messageRes: Int) : AttachmentSaveResult
    data class Failed(val message: String) : AttachmentSaveResult
}

sealed interface StoredAttachmentOwnerResult {
    data class Valid(val owner: AttachmentOwner) : StoredAttachmentOwnerResult
    data object MultipleOwners : StoredAttachmentOwnerResult
    data object InvalidOwner : StoredAttachmentOwnerResult
}

sealed interface AttachmentFileState {
    data class Available(
        val uri: Uri,
        val fileSizeBytes: Long,
        val sha256: String?
    ) : AttachmentFileState

    data object Missing : AttachmentFileState
    data object Damaged : AttachmentFileState
    data object InvalidPath : AttachmentFileState
}

sealed interface AttachmentDeleteState {
    data object Idle : AttachmentDeleteState
    data object Deleting : AttachmentDeleteState
    data object Deleted : AttachmentDeleteState
    data class DeletedWithCleanupWarning(val messageRes: Int) : AttachmentDeleteState
    data class Error(val messageRes: Int) : AttachmentDeleteState
}

sealed interface AttachmentOwnerParseResult {
    data class Valid(val owner: AttachmentOwner) : AttachmentOwnerParseResult
    data object InvalidType : AttachmentOwnerParseResult
    data object MissingOwnerId : AttachmentOwnerParseResult
}

sealed interface ActiveAttachmentOwnerResult {
    data class Valid(
        val attachment: com.jumastappworks.mapstead.data.db.entities.AttachmentEntity,
        val owner: AttachmentOwner
    ) : ActiveAttachmentOwnerResult

    data object AttachmentNotFound : ActiveAttachmentOwnerResult
    data object OwnershipMismatch : ActiveAttachmentOwnerResult
    data object MultipleOwners : ActiveAttachmentOwnerResult
    data object OwnerNotFound : ActiveAttachmentOwnerResult
}

sealed interface TempCameraCaptureInspectionResult {
    data object Ready : TempCameraCaptureInspectionResult
    data object Missing : TempCameraCaptureInspectionResult
    data object Empty : TempCameraCaptureInspectionResult
    data object Unreadable : TempCameraCaptureInspectionResult
    data object InvalidImage : TempCameraCaptureInspectionResult
}

sealed interface StagedCreationPhotoState {
    data object None : StagedCreationPhotoState
    data object Loading : StagedCreationPhotoState
    data class Ready(
        val uri: String,
        val cameraCaptureToken: String?
    ) : StagedCreationPhotoState
    data class Failed(val messageRes: Int) : StagedCreationPhotoState
}
