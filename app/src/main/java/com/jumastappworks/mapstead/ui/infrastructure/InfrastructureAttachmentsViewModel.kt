package com.jumastappworks.mapstead.ui.infrastructure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jumastappworks.mapstead.data.attachments.AttachmentFileState
import com.jumastappworks.mapstead.data.backup.TemporaryCameraCapture
import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import com.jumastappworks.mapstead.data.repository.AttachmentRepository
import com.jumastappworks.mapstead.data.repository.InfrastructureRepository
import com.jumastappworks.mapstead.ui.attachments.AttachmentListItemUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface InfrastructureAttachmentsUiState {
    data object Loading : InfrastructureAttachmentsUiState
    data class Ready(
        val item: InfrastructureItemEntity,
        val attachments: List<AttachmentListItemUiModel>
    ) : InfrastructureAttachmentsUiState
    data object NotFound : InfrastructureAttachmentsUiState
    data class Error(val messageRes: Int) : InfrastructureAttachmentsUiState
}

@HiltViewModel
class InfrastructureAttachmentsViewModel @Inject constructor(
    private val infrastructureRepository: InfrastructureRepository,
    private val attachmentRepository: AttachmentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<InfrastructureAttachmentsUiState>(InfrastructureAttachmentsUiState.Loading)
    val uiState: StateFlow<InfrastructureAttachmentsUiState> = _uiState.asStateFlow()

    fun loadAttachments(propertyId: UUID, itemId: UUID) {
        viewModelScope.launch {
            _uiState.value = InfrastructureAttachmentsUiState.Loading
            val item = infrastructureRepository.getActiveItemForProperty(propertyId, itemId)
            if (item == null) {
                _uiState.value = InfrastructureAttachmentsUiState.NotFound
                return@launch
            }

            attachmentRepository.getAttachmentsForInfrastructureItem(propertyId, itemId).collect { list ->
                val models = list.map { entity ->
                    val fileState = attachmentRepository.resolveAttachmentFile(propertyId, entity.id, verifyHash = false)
                    AttachmentListItemUiModel(
                        attachment = entity,
                        previewUri = (fileState as? AttachmentFileState.Available)?.uri,
                        isMissing = fileState is AttachmentFileState.Missing,
                        isDamaged = fileState is AttachmentFileState.Damaged
                    )
                }
                _uiState.value = InfrastructureAttachmentsUiState.Ready(item, models)
            }
        }
    }

    fun createCameraCapture(): TemporaryCameraCapture? {
        return attachmentRepository.createTempCameraUri().getOrNull()
    }

    fun deleteCameraCapture(token: String) {
        attachmentRepository.deleteTempCameraCapture(token)
    }
}
