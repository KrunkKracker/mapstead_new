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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

    private var currentLoadingJob: Job? = null
    private var lastPropertyId: UUID? = null
    private var lastItemId: UUID? = null

    fun loadAttachments(propertyId: UUID, itemId: UUID) {
        lastPropertyId = propertyId
        lastItemId = itemId
        
        currentLoadingJob?.let { it.cancel() }
        currentLoadingJob = viewModelScope.launch {
            try {
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = InfrastructureAttachmentsUiState.Error(com.jumastappworks.mapstead.R.string.error_loading_attachments)
            }
        }
    }

    fun retryAttachments() {
        val pid = lastPropertyId ?: return
        val iid = lastItemId ?: return
        loadAttachments(pid, iid)
    }

    fun createCameraCapture(): TemporaryCameraCapture? {
        return attachmentRepository.createTempCameraUri().getOrNull()
    }

    fun deleteCameraCapture(token: String) {
        attachmentRepository.deleteTempCameraCapture(token)
    }
}
