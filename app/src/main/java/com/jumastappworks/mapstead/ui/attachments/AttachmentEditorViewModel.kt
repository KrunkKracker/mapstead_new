package com.jumastappworks.mapstead.ui.attachments

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.attachments.*
import com.jumastappworks.mapstead.data.mapping.MapFeatureContextResolver
import com.jumastappworks.mapstead.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AttachmentEditorUiState(
    val propertyId: UUID,
    val owner: AttachmentOwner,
    val ownerDisplayName: String = "",
    val attachmentId: UUID? = null,
    val displayName: String = "",
    val attachmentType: AttachmentType = AttachmentType.Other,
    val caption: String = "",
    val stagedFileUri: String? = null,
    val cameraCaptureToken: String? = null,
    val isImage: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorRes: Int? = null,
    val saveResult: AttachmentSaveResult? = null,
    val isCameraCapture: Boolean = false,
    val isCover: Boolean = false,
    val initialSnapshot: AttachmentSnapshot? = null
) {
    fun isDirty(): Boolean {
        val s = initialSnapshot ?: return displayName.isNotBlank() || caption.isNotBlank()
        return displayName != s.displayName || attachmentType != s.attachmentType || 
               caption != s.caption || isCover != s.isCover
    }
}

data class AttachmentSnapshot(
    val displayName: String,
    val attachmentType: AttachmentType,
    val caption: String,
    val isCover: Boolean
)

@HiltViewModel
class AttachmentEditorViewModel @Inject constructor(
    private val attachmentRepository: AttachmentRepository,
    private val propertyRepository: PropertyRepository,
    private val infrastructureRepository: InfrastructureRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val mapFeatureContextResolver: MapFeatureContextResolver
) : ViewModel() {

    private val _uiState = MutableStateFlow<AttachmentEditorUiState?>(null)
    val uiState: StateFlow<AttachmentEditorUiState?> = _uiState.asStateFlow()

    fun init(
        propertyId: UUID,
        ownerType: String,
        ownerId: UUID?,
        attachmentId: UUID?,
        stagedFileUri: String?,
        cameraCaptureToken: String?
    ) {
        if (_uiState.value != null) return

        viewModelScope.launch {
            if (attachmentId != null) {
                val result = attachmentRepository.resolveActiveAttachmentOwner(propertyId, attachmentId)
                if (result is ActiveAttachmentOwnerResult.Valid) {
                    val existing = result.attachment
                    val owner = result.owner
                    val ownerName = resolveOwnerName(owner) ?: "Unknown Owner"
                    
                    val snapshot = AttachmentSnapshot(
                        displayName = existing.displayName,
                        attachmentType = AttachmentType.fromString(existing.attachmentType),
                        caption = existing.caption ?: "",
                        isCover = existing.isCover
                    )

                    _uiState.value = AttachmentEditorUiState(
                        propertyId = propertyId,
                        owner = owner,
                        ownerDisplayName = ownerName,
                        attachmentId = attachmentId,
                        displayName = snapshot.displayName,
                        attachmentType = snapshot.attachmentType,
                        caption = snapshot.caption,
                        stagedFileUri = attachmentRepository.getAttachmentUri(attachmentId).toString(),
                        isImage = existing.mimeType?.startsWith("image/") == true,
                        isLoading = false,
                        isCover = snapshot.isCover,
                        initialSnapshot = snapshot
                    )
                } else {
                    val errorRes = when (result) {
                        ActiveAttachmentOwnerResult.AttachmentNotFound -> R.string.error_attachment_not_found
                        ActiveAttachmentOwnerResult.OwnershipMismatch -> R.string.error_invalid_owner
                        ActiveAttachmentOwnerResult.MultipleOwners -> R.string.error_invalid_owner
                        ActiveAttachmentOwnerResult.OwnerNotFound -> R.string.error_invalid_owner
                        else -> R.string.error_occurred
                    }
                    _uiState.value = createErrorState(propertyId, errorRes)
                }
            } else {
                val parseResult = parseOwner(propertyId, ownerType, ownerId)
                if (parseResult is AttachmentOwnerParseResult.Valid) {
                    val owner = parseResult.owner
                    val ownerName = resolveOwnerName(owner)
                    
                    if (ownerName == null) {
                        _uiState.value = createErrorState(propertyId, R.string.error_invalid_owner)
                        return@launch
                    }

                    val metadata = stagedFileUri?.let { attachmentRepository.getSourceMetadata(Uri.parse(it)) }

                    _uiState.value = AttachmentEditorUiState(
                        propertyId = propertyId,
                        owner = owner,
                        ownerDisplayName = ownerName,
                        displayName = metadata?.displayName ?: "",
                        stagedFileUri = stagedFileUri,
                        cameraCaptureToken = cameraCaptureToken,
                        isImage = metadata?.isImage ?: false,
                        isLoading = false,
                        isCameraCapture = cameraCaptureToken != null,
                        initialSnapshot = null
                    )
                } else {
                    _uiState.value = createErrorState(propertyId, R.string.error_invalid_owner)
                }
            }
        }
    }

    private suspend fun resolveOwnerName(owner: AttachmentOwner): String? {
        return when (owner) {
            is AttachmentOwner.Property -> propertyRepository.getPropertyById(owner.propertyId)?.name ?: "Property"
            is AttachmentOwner.InfrastructureItem -> infrastructureRepository.getItemById(owner.itemId)?.name ?: "Item"
            is AttachmentOwner.MaintenanceRecord -> maintenanceRepository.getRecordByIdOnce(owner.recordId)?.title ?: "Record"
            is AttachmentOwner.MapFeature -> {
                val context = mapFeatureContextResolver.resolveFromFeature(owner.propertyId, owner.featureId)
                context?.let { "${it.feature.label ?: it.feature.geometryType} (${it.plan.name} / ${it.layer.name})" }
            }
        }
    }

    private fun parseOwner(propertyId: UUID, ownerType: String, ownerId: UUID?): AttachmentOwnerParseResult {
        return when (ownerType.uppercase()) {
            "PROPERTY" -> AttachmentOwnerParseResult.Valid(AttachmentOwner.Property(propertyId))
            "INFRASTRUCTURE" -> {
                if (ownerId == null) AttachmentOwnerParseResult.MissingOwnerId
                else AttachmentOwnerParseResult.Valid(AttachmentOwner.InfrastructureItem(propertyId, ownerId))
            }
            "MAINTENANCE" -> {
                if (ownerId == null) AttachmentOwnerParseResult.MissingOwnerId
                else AttachmentOwnerParseResult.Valid(AttachmentOwner.MaintenanceRecord(propertyId, ownerId))
            }
            "MAP_FEATURE" -> {
                if (ownerId == null) AttachmentOwnerParseResult.MissingOwnerId
                else AttachmentOwnerParseResult.Valid(AttachmentOwner.MapFeature(propertyId, ownerId))
            }
            else -> AttachmentOwnerParseResult.InvalidType
        }
    }

    private fun createErrorState(propertyId: UUID, errorRes: Int): AttachmentEditorUiState {
        return AttachmentEditorUiState(
            propertyId = propertyId,
            owner = AttachmentOwner.Property(propertyId),
            isLoading = false,
            errorRes = errorRes
        )
    }

    fun onDisplayNameChange(name: String) {
        _uiState.value = _uiState.value?.copy(displayName = name)
    }

    fun onTypeChange(type: AttachmentType) {
        _uiState.value = _uiState.value?.copy(attachmentType = type)
    }

    fun onCaptionChange(caption: String) {
        _uiState.value = _uiState.value?.copy(caption = caption)
    }

    fun onCoverChange(isCover: Boolean) {
        _uiState.value = _uiState.value?.copy(isCover = isCover)
    }

    fun save() {
        val currentState = _uiState.value ?: return
        if (currentState.isSaving) return
        if (currentState.displayName.isBlank()) {
            _uiState.value = currentState.copy(errorRes = R.string.attachment_display_name_required)
            return
        }

        _uiState.value = currentState.copy(isSaving = true, errorRes = null)
        
        viewModelScope.launch {
            try {
                val result = if (currentState.attachmentId != null) {
                    attachmentRepository.updateMetadata(
                        currentState.propertyId,
                        currentState.attachmentId,
                        currentState.attachmentType,
                        currentState.displayName,
                        currentState.caption.takeIf { it.isNotBlank() }
                    )
                } else if (currentState.stagedFileUri != null) {
                    val uri = Uri.parse(currentState.stagedFileUri)
                    attachmentRepository.importAttachment(
                        currentState.owner,
                        uri,
                        currentState.attachmentType,
                        currentState.displayName,
                        currentState.caption.takeIf { it.isNotBlank() },
                        currentState.cameraCaptureToken
                    )
                } else {
                    AttachmentWriteResult.Error("Nothing to save")
                }

                when (result) {
                    is AttachmentWriteResult.Success -> {
                        var finalSaveResult: AttachmentSaveResult = AttachmentSaveResult.Saved
                        
                        if (currentState.owner is AttachmentOwner.MapFeature) {
                            val currentAttachmentId = result.attachmentId
                            val existing = attachmentRepository.getAttachmentById(currentAttachmentId)
                            val wasCover = existing?.isCover == true
                            
                            if (currentState.isCover != wasCover) {
                                val coverResult = if (currentState.isCover) {
                                    attachmentRepository.setFeatureCoverAttachment(
                                        currentState.propertyId,
                                        currentState.owner.featureId,
                                        currentAttachmentId
                                    )
                                } else {
                                    attachmentRepository.clearFeatureCoverAttachment(
                                        currentState.propertyId,
                                        currentState.owner.featureId
                                    )
                                }
                                
                                if (coverResult !is CoverResult.Set && coverResult !is CoverResult.Cleared && coverResult !is CoverResult.AlreadyClear) {
                                    finalSaveResult = AttachmentSaveResult.SavedWithCoverWarning(R.string.warning_metadata_saved_cover_failed)
                                }
                            }
                        }
                        
                        _uiState.value = _uiState.value?.copy(isSaving = false, saveResult = finalSaveResult)
                    }
                    AttachmentWriteResult.TooLarge -> {
                        _uiState.value = _uiState.value?.copy(isSaving = false, errorRes = R.string.error_file_too_large)
                    }
                    AttachmentWriteResult.UnsupportedType -> {
                        _uiState.value = _uiState.value?.copy(isSaving = false, errorRes = R.string.error_unsupported_type)
                    }
                    else -> {
                        _uiState.value = _uiState.value?.copy(isSaving = false, errorRes = R.string.error_file_copy_failed)
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                _uiState.value = _uiState.value?.copy(isSaving = false, errorRes = R.string.error_occurred)
            }
        }
    }

    fun onCancel() {
        val currentState = _uiState.value ?: return
        if (currentState.isCameraCapture && currentState.cameraCaptureToken != null) {
            attachmentRepository.deleteTempCameraCapture(currentState.cameraCaptureToken)
        }
    }
}
