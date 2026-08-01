package com.jumastappworks.mapstead.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.jumastappworks.mapstead.data.attachments.*
import com.jumastappworks.mapstead.data.backup.AttachmentStorageService
import com.jumastappworks.mapstead.data.backup.TemporaryCameraCapture
import com.jumastappworks.mapstead.data.db.DatabaseTransactionRunner
import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.db.entities.AttachmentEntity
import com.jumastappworks.mapstead.data.mapping.MapFeatureContextResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AttachmentExportFileResult {
    data class Available(
        val attachment: AttachmentEntity,
        val owner: AttachmentOwner,
        val file: File
    ) : AttachmentExportFileResult

    data object AttachmentNotFound : AttachmentExportFileResult
    data object InvalidOwner : AttachmentExportFileResult
    data object Missing : AttachmentExportFileResult
    data object Damaged : AttachmentExportFileResult
    data object InvalidPath : AttachmentExportFileResult
    data object Unreadable : AttachmentExportFileResult
}

@Singleton
class AttachmentRepository @Inject constructor(
    private val database: MapsteadDatabase,
    private val storageService: AttachmentStorageService,
    private val mapFeatureContextResolver: MapFeatureContextResolver,
    private val transactionRunner: DatabaseTransactionRunner,
    @ApplicationContext private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val attachmentDao = database.attachmentDao()

    private class TransactionRollbackException(val result: CoverResult) : Exception()

    fun getAttachmentsForProperty(propertyId: UUID): Flow<List<AttachmentEntity>> =
        attachmentDao.getAttachmentsForProperty(propertyId).map { list ->
            list.filter { parseStoredAttachmentOwner(it) is StoredAttachmentOwnerResult.Valid }
        }

    fun getPropertyLevelAttachments(propertyId: UUID): Flow<List<AttachmentEntity>> =
        attachmentDao.getPropertyLevelAttachments(propertyId)

    fun getAttachmentsForInfrastructureItem(propertyId: UUID, itemId: UUID): Flow<List<AttachmentEntity>> =
        attachmentDao.getAttachmentsForInfrastructureItem(propertyId, itemId).map { list ->
            list.filter { parseStoredAttachmentOwner(it) is StoredAttachmentOwnerResult.Valid }
        }

    fun getAttachmentsForMaintenanceRecord(propertyId: UUID, recordId: UUID): Flow<List<AttachmentEntity>> =
        attachmentDao.getAttachmentsForMaintenanceRecord(propertyId, recordId).map { list ->
            list.filter { parseStoredAttachmentOwner(it) is StoredAttachmentOwnerResult.Valid }
        }

    fun getAttachmentsForMapFeature(propertyId: UUID, featureId: UUID): Flow<List<AttachmentEntity>> =
        attachmentDao.getAttachmentsForMapFeature(propertyId, featureId).map { list ->
            list.filter { parseStoredAttachmentOwner(it) is StoredAttachmentOwnerResult.Valid }
        }

    suspend fun getAttachmentById(id: UUID): AttachmentEntity? =
        attachmentDao.getAttachmentById(id)

    suspend fun getAttachmentForProperty(propertyId: UUID, attachmentId: UUID): AttachmentEntity? {
        val attachment = attachmentDao.getAttachmentById(attachmentId) ?: return null
        if (attachment.propertyId != propertyId || attachment.deletedAt != null) return null
        
        // Ensure owner exclusivity
        if (parseStoredAttachmentOwner(attachment) !is StoredAttachmentOwnerResult.Valid) return null
        
        return attachment
    }

    suspend fun getFeatureAttachmentCount(propertyId: UUID, featureId: UUID): Int {
        return attachmentDao.getCountForMapFeature(propertyId, featureId)
    }

    fun parseStoredAttachmentOwner(entity: AttachmentEntity): StoredAttachmentOwnerResult {
        val populatedOwners = listOfNotNull(
            entity.infrastructureItemId?.let { AttachmentOwner.InfrastructureItem(entity.propertyId, it) },
            entity.maintenanceRecordId?.let { AttachmentOwner.MaintenanceRecord(entity.propertyId, it) },
            entity.mapFeatureId?.let { AttachmentOwner.MapFeature(entity.propertyId, it) }
        )
        
        return when (populatedOwners.size) {
            0 -> StoredAttachmentOwnerResult.Valid(AttachmentOwner.Property(entity.propertyId))
            1 -> StoredAttachmentOwnerResult.Valid(populatedOwners.single())
            else -> StoredAttachmentOwnerResult.MultipleOwners
        }
    }

    suspend fun resolveActiveAttachmentOwner(
        propertyId: UUID,
        attachmentId: UUID
    ): ActiveAttachmentOwnerResult {
        val attachment = attachmentDao.getAttachmentById(attachmentId)
            ?: return ActiveAttachmentOwnerResult.AttachmentNotFound

        if (attachment.propertyId != propertyId) {
            return ActiveAttachmentOwnerResult.OwnershipMismatch
        }

        if (attachment.deletedAt != null) {
            return ActiveAttachmentOwnerResult.AttachmentNotFound
        }

        val ownerResult = parseStoredAttachmentOwner(attachment)
        if (ownerResult !is StoredAttachmentOwnerResult.Valid) {
            return ActiveAttachmentOwnerResult.MultipleOwners
        }

        val owner = ownerResult.owner
        validateOwnerIdentity(owner) ?: return ActiveAttachmentOwnerResult.OwnerNotFound

        return ActiveAttachmentOwnerResult.Valid(attachment, owner)
    }

    suspend fun setFeatureCoverAttachment(
        propertyId: UUID,
        featureId: UUID,
        attachmentId: UUID
    ): CoverResult {
        // 1. Resolve authoritative context
        val mapContext = mapFeatureContextResolver.resolveFromFeature(propertyId, featureId)
            ?: return CoverResult.FeatureNotFound

        val attachment = attachmentDao.getAttachmentById(attachmentId)
            ?: return CoverResult.AttachmentNotFound
        
        // 2. Validate owner exclusivity and identity
        val ownerResult = parseStoredAttachmentOwner(attachment)
        if (ownerResult !is StoredAttachmentOwnerResult.Valid || 
            (ownerResult.owner as? AttachmentOwner.MapFeature)?.featureId != featureId ||
            attachment.propertyId != propertyId || attachment.deletedAt != null) {
            return CoverResult.InvalidOwner
        }
        
        if (attachment.mimeType?.startsWith("image/") != true) {
            return CoverResult.UnsupportedType
        }

        // 3. Verify file exists and is healthy
        val fileState = resolveAttachmentFile(propertyId, attachmentId, verifyHash = true)
        if (fileState !is AttachmentFileState.Available) {
            return when (fileState) {
                AttachmentFileState.Missing -> CoverResult.MissingFile
                AttachmentFileState.Damaged -> CoverResult.DamagedFile
                else -> CoverResult.Error("File unavailable")
            }
        }

        // 4. Atomic transaction
        return withContext(ioDispatcher) {
            try {
                transactionRunner.run {
                    // 5. Clear previous cover
                    attachmentDao.clearFeatureCover(propertyId, featureId)
                    // 6. Set selected as cover
                    val affected = attachmentDao.setFeatureCover(propertyId, featureId, attachmentId)
                    // 7. Verify exactly one row updated
                    if (affected != 1) {
                        throw TransactionRollbackException(CoverResult.Error("Failed to update cover flag"))
                    }
                }
                CoverResult.Set
            } catch (e: TransactionRollbackException) {
                e.result
            } catch (e: Exception) {
                CoverResult.Error(e.message)
            }
        }
    }

    suspend fun clearFeatureCoverAttachment(
        propertyId: UUID,
        featureId: UUID
    ): CoverResult {
        val mapContext = mapFeatureContextResolver.resolveFromFeature(propertyId, featureId)
            ?: return CoverResult.FeatureNotFound

        return withContext(ioDispatcher) {
            try {
                val affected = attachmentDao.clearFeatureCover(propertyId, featureId)
                if (affected > 0) CoverResult.Cleared
                else CoverResult.AlreadyClear
            } catch (e: Exception) {
                CoverResult.Error(e.message)
            }
        }
    }

    fun getAttachmentUri(attachmentId: UUID): Uri {
        return storageService.getUriForAttachment(attachmentId)
    }

    fun createTempCameraUri(): Result<TemporaryCameraCapture> {
        return storageService.createTempCameraCapture()
    }

    fun deleteTempCameraCapture(token: String) {
        storageService.deleteTempCameraCapture(token)
    }

    fun inspectTempCameraCapture(token: String, uri: Uri): TempCameraCaptureInspectionResult {
        return storageService.inspectTempCameraCapture(token, uri)
    }

    suspend fun resolveAttachmentFile(
        propertyId: UUID,
        attachmentId: UUID,
        verifyHash: Boolean = false
    ): AttachmentFileState {
        val attachment = attachmentDao.getAttachmentById(attachmentId)
            ?: return AttachmentFileState.Missing
        
        if (attachment.propertyId != propertyId) return AttachmentFileState.InvalidPath

        if (parseStoredAttachmentOwner(attachment) !is StoredAttachmentOwnerResult.Valid) {
            return AttachmentFileState.InvalidPath
        }
        
        val path = attachment.appManagedCopyPath ?: return AttachmentFileState.Missing

        return withContext(ioDispatcher) {
            val fileResult = storageService.resolveFromEntityPath(path)
            if (fileResult.isFailure) return@withContext AttachmentFileState.InvalidPath
            
            val file = fileResult.getOrThrow()
            if (!file.exists() || !file.isFile) return@withContext AttachmentFileState.Missing
            
            if (attachment.fileSizeBytes != null && file.length() != attachment.fileSizeBytes) {
                return@withContext AttachmentFileState.Damaged
            }
            
            if (verifyHash && attachment.sha256 != null) {
                val actualHash = storageService.calculateSha256(file)
                if (actualHash != attachment.sha256) {
                    return@withContext AttachmentFileState.Damaged
                }
            }
            
            AttachmentFileState.Available(
                uri = storageService.getUriForFile(file),
                fileSizeBytes = file.length(),
                sha256 = attachment.sha256
            )
        }
    }

    suspend fun resolveVerifiedAttachmentFileForExport(
        propertyId: UUID,
        attachmentId: UUID
    ): AttachmentExportFileResult = withContext(ioDispatcher) {
        val attachment = attachmentDao.getAttachmentById(attachmentId)
            ?: return@withContext AttachmentExportFileResult.AttachmentNotFound

        if (attachment.propertyId != propertyId || attachment.deletedAt != null) {
            return@withContext AttachmentExportFileResult.AttachmentNotFound
        }

        val ownerResult = parseStoredAttachmentOwner(attachment)
        if (ownerResult !is StoredAttachmentOwnerResult.Valid) {
            return@withContext AttachmentExportFileResult.InvalidOwner
        }

        val owner = ownerResult.owner
        if (validateOwnerIdentity(owner) == null) {
            return@withContext AttachmentExportFileResult.InvalidOwner
        }

        val path = attachment.appManagedCopyPath ?: return@withContext AttachmentExportFileResult.Missing
        val fileResult = storageService.resolveFromEntityPath(path)
        if (fileResult.isFailure) return@withContext AttachmentExportFileResult.InvalidPath

        val file = fileResult.getOrThrow()
        if (!file.exists() || !file.isFile) return@withContext AttachmentExportFileResult.Missing
        if (!file.canRead()) return@withContext AttachmentExportFileResult.Unreadable

        if (attachment.fileSizeBytes != null && file.length() != attachment.fileSizeBytes) {
            return@withContext AttachmentExportFileResult.Damaged
        }

        if (attachment.sha256 != null) {
            val actualHash = storageService.calculateSha256(file)
            if (actualHash != attachment.sha256) {
                return@withContext AttachmentExportFileResult.Damaged
            }
        }

        AttachmentExportFileResult.Available(attachment, owner, file)
    }

    suspend fun resolveAttachmentFileForExport(
        propertyId: UUID,
        attachmentId: UUID
    ): java.io.File? = withContext(ioDispatcher) {
        val attachment = attachmentDao.getAttachmentById(attachmentId) ?: return@withContext null
        if (attachment.propertyId != propertyId || attachment.deletedAt != null) return@withContext null
        
        val path = attachment.appManagedCopyPath ?: return@withContext null
        val fileResult = storageService.resolveFromEntityPath(path)
        if (fileResult.isFailure) return@withContext null
        
        val file = fileResult.getOrThrow()
        if (file.exists() && file.isFile) file else null
    }

    suspend fun importAttachment(
        owner: AttachmentOwner,
        uri: Uri,
        type: AttachmentType,
        customDisplayName: String?,
        caption: String?,
        cameraCaptureToken: String? = null
    ): AttachmentWriteResult {
        validateOwnerIdentity(owner) ?: return AttachmentWriteResult.InvalidOwner

        return withContext(ioDispatcher) {
            try {
                val contentResolver = context.contentResolver
                val metadata = getUriMetadata(uri)
                val displayName = customDisplayName?.takeIf { it.isNotBlank() } ?: metadata.displayName ?: "Untitled"
                val mimeType = contentResolver.getType(uri) ?: metadata.mimeType

                if (!isMimeTypeSupported(mimeType)) return@withContext AttachmentWriteResult.UnsupportedType

                val inputStream = contentResolver.openInputStream(uri)
                    ?: return@withContext AttachmentWriteResult.StorageFailure

                val stagedResult = inputStream.use { input ->
                    storageService.stageInputStream(input, 50 * 1024 * 1024L) // 50MB
                }.getOrElse { 
                    return@withContext if (it is java.io.IOException && it.message?.contains("limit") == true) {
                        AttachmentWriteResult.TooLarge
                    } else {
                        AttachmentWriteResult.StorageFailure
                    }
                }

                val attachmentId = UUID.randomUUID()
                val commitResult = storageService.commitStagedFile(stagedResult.file, attachmentId)
                if (commitResult.isFailure) {
                    if (stagedResult.file.exists()) stagedResult.file.delete()
                    return@withContext AttachmentWriteResult.StorageFailure
                }

                val now = Instant.now()
                val entity = AttachmentEntity(
                    id = attachmentId,
                    propertyId = owner.propertyId,
                    infrastructureItemId = (owner as? AttachmentOwner.InfrastructureItem)?.itemId,
                    maintenanceRecordId = (owner as? AttachmentOwner.MaintenanceRecord)?.recordId,
                    mapFeatureId = (owner as? AttachmentOwner.MapFeature)?.featureId,
                    attachmentType = type.canonicalName,
                    localUri = uri.toString(),
                    appManagedCopyPath = storageService.getEntityPath(attachmentId),
                    displayName = displayName,
                    mimeType = mimeType,
                    fileSizeBytes = stagedResult.size,
                    sha256 = stagedResult.sha256,
                    caption = caption,
                    createdAt = now,
                    updatedAt = now,
                    revision = 1L
                )

                try {
                    attachmentDao.insertAttachment(entity)
                    cameraCaptureToken?.let { storageService.deleteTempCameraCapture(it) }
                    AttachmentWriteResult.Success(attachmentId)
                } catch (e: Exception) {
                    storageService.deleteManagedFile(attachmentId)
                    AttachmentWriteResult.Error("Database persistence failed")
                }
            } catch (e: Exception) {
                AttachmentWriteResult.Error("Import failed")
            }
        }
    }

    suspend fun createPhotoAttachment(
        owner: AttachmentOwner,
        stagedPhoto: java.io.File,
        type: AttachmentType,
        displayName: String,
        caption: String?
    ): AttachmentWriteResult {
        validateOwnerIdentity(owner) ?: return AttachmentWriteResult.InvalidOwner

        if (!stagedPhoto.exists()) return AttachmentWriteResult.NotFound

        return withContext(ioDispatcher) {
            try {
                val attachmentId = UUID.randomUUID()
                val size = stagedPhoto.length()
                val sha256 = storageService.calculateSha256(stagedPhoto)

                val commitResult = storageService.commitStagedFile(stagedPhoto, attachmentId)
                if (commitResult.isFailure) {
                    if (stagedPhoto.exists()) stagedPhoto.delete()
                    return@withContext AttachmentWriteResult.StorageFailure
                }

                val now = Instant.now()
                val entity = AttachmentEntity(
                    id = attachmentId,
                    propertyId = owner.propertyId,
                    infrastructureItemId = (owner as? AttachmentOwner.InfrastructureItem)?.itemId,
                    maintenanceRecordId = (owner as? AttachmentOwner.MaintenanceRecord)?.recordId,
                    mapFeatureId = (owner as? AttachmentOwner.MapFeature)?.featureId,
                    attachmentType = type.canonicalName,
                    localUri = "camera://capture",
                    appManagedCopyPath = storageService.getEntityPath(attachmentId),
                    displayName = displayName,
                    mimeType = "image/jpeg",
                    fileSizeBytes = size,
                    sha256 = sha256,
                    caption = caption,
                    createdAt = now,
                    updatedAt = now,
                    revision = 1L
                )

                try {
                    attachmentDao.insertAttachment(entity)
                    AttachmentWriteResult.Success(attachmentId)
                } catch (e: Exception) {
                    storageService.deleteManagedFile(attachmentId)
                    AttachmentWriteResult.Error("Database persistence failed")
                }
            } catch (e: Exception) {
                AttachmentWriteResult.Error("Photo creation failed")
            }
        }
    }

    suspend fun updateMetadata(
        propertyId: UUID,
        attachmentId: UUID,
        type: AttachmentType,
        displayName: String,
        caption: String?
    ): AttachmentWriteResult {
        val existing = attachmentDao.getAttachmentById(attachmentId)
            ?: return AttachmentWriteResult.NotFound
        
        if (existing.propertyId != propertyId || existing.deletedAt != null) return AttachmentWriteResult.OwnershipMismatch
        
        val ownerResult = parseStoredAttachmentOwner(existing)
        if (ownerResult !is StoredAttachmentOwnerResult.Valid) return AttachmentWriteResult.InvalidOwner
        
        if (validateOwnerIdentity(ownerResult.owner) == null) return AttachmentWriteResult.InvalidOwner
        
        if (displayName.isBlank()) return AttachmentWriteResult.Error("Display name cannot be empty")

        return withContext(ioDispatcher) {
            try {
                val updated = existing.copy(
                    attachmentType = type.canonicalName,
                    displayName = displayName,
                    caption = caption,
                    updatedAt = Instant.now(),
                    revision = existing.revision + 1
                )
                val affected = attachmentDao.updateAttachment(updated)
                if (affected == 1) {
                    AttachmentWriteResult.Success(attachmentId)
                } else {
                    AttachmentWriteResult.Error("Update failed")
                }
            } catch (e: Exception) {
                AttachmentWriteResult.Error("Update failed")
            }
        }
    }

    suspend fun softDeleteAttachment(propertyId: UUID, attachmentId: UUID): AttachmentDeleteState {
        val existing = attachmentDao.getAttachmentById(attachmentId)
            ?: return AttachmentDeleteState.Error(com.jumastappworks.mapstead.R.string.error_attachment_not_found)

        if (existing.propertyId != propertyId) {
            return AttachmentDeleteState.Error(com.jumastappworks.mapstead.R.string.error_invalid_owner)
        }

        if (existing.deletedAt != null) {
            return AttachmentDeleteState.Error(com.jumastappworks.mapstead.R.string.error_attachment_not_found)
        }

        if (parseStoredAttachmentOwner(existing) !is StoredAttachmentOwnerResult.Valid) {
            return AttachmentDeleteState.Error(com.jumastappworks.mapstead.R.string.error_invalid_owner)
        }

        return withContext(ioDispatcher) {
            try {
                val affected = attachmentDao.softDeletePropertyAttachment(propertyId, attachmentId, Instant.now(), Instant.now())
                if (affected == 0) {
                    return@withContext AttachmentDeleteState.Error(com.jumastappworks.mapstead.R.string.error_attachment_not_found)
                }

                val fileCleanup = storageService.deleteManagedFile(attachmentId)
                
                if (fileCleanup.isSuccess) {
                    AttachmentDeleteState.Deleted
                } else {
                    AttachmentDeleteState.DeletedWithCleanupWarning(com.jumastappworks.mapstead.R.string.error_delete_cleanup_warning)
                }
            } catch (e: Exception) {
                AttachmentDeleteState.Error(com.jumastappworks.mapstead.R.string.error_delete_failed)
            }
        }
    }

    private suspend fun validateOwnerIdentity(owner: AttachmentOwner): UUID? {
        val property = database.propertyDao().getPropertyById(owner.propertyId)
            ?: return null
        if (property.deletedAt != null) return null
        
        when (owner) {
            is AttachmentOwner.InfrastructureItem -> {
                val item = database.infrastructureDao().getItemById(owner.itemId)
                    ?: return null
                if (item.propertyId != owner.propertyId || item.deletedAt != null) return null
            }
            is AttachmentOwner.MaintenanceRecord -> {
                val record = database.maintenanceDao().getRecordByIdOnce(owner.recordId)
                    ?: return null
                if (record.propertyId != owner.propertyId || record.deletedAt != null) return null
            }
            is AttachmentOwner.MapFeature -> {
                val context = mapFeatureContextResolver.resolveFromFeature(owner.propertyId, owner.featureId)
                if (context == null) return null
            }
            is AttachmentOwner.Property -> {}
        }
        return owner.propertyId
    }

    fun getSourceMetadata(uri: Uri): com.jumastappworks.mapstead.ui.attachments.AttachmentSourceMetadata {
        val metadata = getUriMetadata(uri)
        return com.jumastappworks.mapstead.ui.attachments.AttachmentSourceMetadata(
            mimeType = metadata.mimeType,
            displayName = metadata.displayName,
            reportedSize = metadata.size,
            isImage = metadata.mimeType?.startsWith("image/") == true
        )
    }

    private fun getUriMetadata(uri: Uri): UriMetadata {
        var displayName: String? = null
        var size: Long? = null
        var mimeType: String? = null
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) displayName = it.getString(nameIndex)
                    
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) size = it.getLong(sizeIndex)
                }
            }
            mimeType = context.contentResolver.getType(uri)
        } catch (e: Exception) {}
        return UriMetadata(displayName, mimeType, size)
    }

    private fun isMimeTypeSupported(mimeType: String?): Boolean {
        if (mimeType == null) return false
        val supported = setOf(
            "image/jpeg", "image/png", "image/webp", "image/heic", "image/heif",
            "application/pdf", "text/plain", "text/csv"
        )
        return supported.contains(mimeType.lowercase())
    }

    private data class UriMetadata(val displayName: String?, val mimeType: String?, val size: Long?)
}
