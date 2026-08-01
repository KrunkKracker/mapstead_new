package com.jumastappworks.mapstead.ui.attachments

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.attachments.*
import com.jumastappworks.mapstead.data.db.entities.AttachmentEntity
import com.jumastappworks.mapstead.data.mapping.MapFeatureContextResolver
import com.jumastappworks.mapstead.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface AttachmentDetailsUiState {
    data object Loading : AttachmentDetailsUiState
    data class Ready(
        val attachment: AttachmentEntity,
        val ownerDisplayName: String,
        val fileUri: Uri,
        val isImage: Boolean,
        val isCover: Boolean = false,
        val ownerDestination: AttachmentOwnerDestination,
        val coverActionLoading: Boolean = false,
        val coverActionErrorRes: Int? = null
    ) : AttachmentDetailsUiState
    data class MissingFile(val attachment: AttachmentEntity, val ownerDisplayName: String) : AttachmentDetailsUiState
    data class DamagedFile(val attachment: AttachmentEntity, val ownerDisplayName: String) : AttachmentDetailsUiState
    data object NotFound : AttachmentDetailsUiState
    data class Error(val messageRes: Int) : AttachmentDetailsUiState
}

sealed interface AttachmentOwnerDestination {
    data class Property(val propertyId: UUID) : AttachmentOwnerDestination
    data class InfrastructureItem(val propertyId: UUID, val itemId: UUID) : AttachmentOwnerDestination
    data class MaintenanceRecord(val propertyId: UUID, val recordId: UUID) : AttachmentOwnerDestination
    data class MapFeature(val propertyId: UUID, val planId: UUID, val featureId: UUID) : AttachmentOwnerDestination
}

@HiltViewModel
class AttachmentDetailsViewModel @Inject constructor(
    private val attachmentRepository: AttachmentRepository,
    private val propertyRepository: PropertyRepository,
    private val infrastructureRepository: InfrastructureRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val mapFeatureContextResolver: MapFeatureContextResolver
) : ViewModel() {

    private val _uiState = MutableStateFlow<AttachmentDetailsUiState>(AttachmentDetailsUiState.Loading)
    val uiState: StateFlow<AttachmentDetailsUiState> = _uiState.asStateFlow()

    private val _deleteState = MutableStateFlow<AttachmentDeleteState>(AttachmentDeleteState.Idle)
    val deleteState: StateFlow<AttachmentDeleteState> = _deleteState.asStateFlow()

    fun init(propertyId: UUID, attachmentId: UUID) {
        viewModelScope.launch {
            val result = attachmentRepository.resolveActiveAttachmentOwner(propertyId, attachmentId)
            if (result !is ActiveAttachmentOwnerResult.Valid) {
                _uiState.value = when (result) {
                    ActiveAttachmentOwnerResult.AttachmentNotFound -> AttachmentDetailsUiState.NotFound
                    ActiveAttachmentOwnerResult.OwnershipMismatch -> AttachmentDetailsUiState.Error(R.string.error_invalid_owner)
                    ActiveAttachmentOwnerResult.MultipleOwners -> AttachmentDetailsUiState.Error(R.string.error_invalid_owner)
                    ActiveAttachmentOwnerResult.OwnerNotFound -> AttachmentDetailsUiState.Error(R.string.error_invalid_owner)
                    else -> AttachmentDetailsUiState.Error(R.string.error_occurred)
                }
                return@launch
            }

            val attachment = result.attachment
            val owner = result.owner
            var ownerName: String
            var destination: AttachmentOwnerDestination

            when (owner) {
                is AttachmentOwner.InfrastructureItem -> {
                    ownerName = infrastructureRepository.getItemById(owner.itemId)?.name ?: "System Item"
                    destination = AttachmentOwnerDestination.InfrastructureItem(propertyId, owner.itemId)
                }
                is AttachmentOwner.MaintenanceRecord -> {
                    ownerName = maintenanceRepository.getRecordByIdOnce(owner.recordId)?.title ?: "Maintenance Record"
                    destination = AttachmentOwnerDestination.MaintenanceRecord(propertyId, owner.recordId)
                }
                is AttachmentOwner.MapFeature -> {
                    val mapContext = mapFeatureContextResolver.resolveFromFeature(propertyId, owner.featureId)
                    if (mapContext == null) {
                        _uiState.value = AttachmentDetailsUiState.Error(R.string.error_invalid_owner)
                        return@launch
                    }
                    ownerName = "${mapContext.feature.label ?: mapContext.feature.geometryType} (${mapContext.plan.name} / ${mapContext.layer.name})"
                    destination = AttachmentOwnerDestination.MapFeature(propertyId, mapContext.plan.id, owner.featureId)
                }
                is AttachmentOwner.Property -> {
                    ownerName = propertyRepository.getPropertyById(propertyId)?.name ?: "Property"
                    destination = AttachmentOwnerDestination.Property(propertyId)
                }
            }

            val fileState = attachmentRepository.resolveAttachmentFile(propertyId, attachmentId, verifyHash = true)
            
            _uiState.value = when (fileState) {
                is AttachmentFileState.Available -> {
                    AttachmentDetailsUiState.Ready(
                        attachment = attachment,
                        ownerDisplayName = ownerName,
                        fileUri = fileState.uri,
                        isImage = attachment.mimeType?.startsWith("image/") == true,
                        isCover = attachment.isCover,
                        ownerDestination = destination
                    )
                }
                AttachmentFileState.Missing -> {
                    AttachmentDetailsUiState.MissingFile(attachment, ownerName)
                }
                AttachmentFileState.Damaged -> {
                    AttachmentDetailsUiState.DamagedFile(attachment, ownerName)
                }
                AttachmentFileState.InvalidPath -> {
                    AttachmentDetailsUiState.Error(R.string.error_invalid_owner)
                }
            }
        }
    }

    fun deleteAttachment() {
        val current = _uiState.value
        val attachment = when (current) {
            is AttachmentDetailsUiState.Ready -> current.attachment
            is AttachmentDetailsUiState.MissingFile -> current.attachment
            is AttachmentDetailsUiState.DamagedFile -> current.attachment
            else -> return
        }

        viewModelScope.launch {
            _deleteState.value = AttachmentDeleteState.Deleting
            val result = attachmentRepository.softDeleteAttachment(
                attachment.propertyId,
                attachment.id
            )
            _deleteState.value = result
        }
    }

    fun clearDeleteState() {
        _deleteState.value = AttachmentDeleteState.Idle
    }

    fun setAsCover() {
        val ready = _uiState.value as? AttachmentDetailsUiState.Ready ?: return
        if (ready.coverActionLoading) return
        val featureId = ready.attachment.mapFeatureId ?: return
        
        viewModelScope.launch {
            _uiState.value = ready.copy(coverActionLoading = true, coverActionErrorRes = null)
            val res = attachmentRepository.setFeatureCoverAttachment(ready.attachment.propertyId, featureId, ready.attachment.id)
            if (res is CoverResult.Set) {
                init(ready.attachment.propertyId, ready.attachment.id)
            } else {
                val errorRes = when (res) {
                    CoverResult.UnsupportedType -> R.string.error_unsupported_type
                    CoverResult.MissingFile -> R.string.missing_file_title
                    CoverResult.DamagedFile -> R.string.damaged_file_title
                    else -> R.string.error_cover_update_failed
                }
                _uiState.value = ready.copy(coverActionLoading = false, coverActionErrorRes = errorRes)
            }
        }
    }

    fun removeCover() {
        val ready = _uiState.value as? AttachmentDetailsUiState.Ready ?: return
        if (ready.coverActionLoading) return
        val featureId = ready.attachment.mapFeatureId ?: return
        
        viewModelScope.launch {
            _uiState.value = ready.copy(coverActionLoading = true, coverActionErrorRes = null)
            val res = attachmentRepository.clearFeatureCoverAttachment(ready.attachment.propertyId, featureId)
            if (res is CoverResult.Cleared || res is CoverResult.AlreadyClear) {
                init(ready.attachment.propertyId, ready.attachment.id)
            } else {
                _uiState.value = ready.copy(coverActionLoading = false, coverActionErrorRes = R.string.error_cover_update_failed)
            }
        }
    }

    fun clearCoverActionError() {
        val ready = _uiState.value as? AttachmentDetailsUiState.Ready ?: return
        _uiState.value = ready.copy(coverActionErrorRes = null)
    }
}
