package com.jumastappworks.mapstead.ui.mapping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jumastappworks.mapstead.data.attachments.AttachmentFileState
import com.jumastappworks.mapstead.data.db.entities.LayerEntity
import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.data.db.entities.PlanEntity
import com.jumastappworks.mapstead.data.repository.AttachmentRepository
import com.jumastappworks.mapstead.data.repository.MapRepository
import com.jumastappworks.mapstead.ui.attachments.AttachmentListItemUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface MapFeatureAttachmentsUiState {
    data object Idle : MapFeatureAttachmentsUiState
    data object Loading : MapFeatureAttachmentsUiState
    data class Ready(
        val feature: MapFeatureEntity,
        val plan: PlanEntity,
        val layer: LayerEntity,
        val attachments: List<AttachmentListItemUiModel>,
        val coverAttachment: AttachmentListItemUiModel?,
        val photoCount: Int,
        val documentCount: Int
    ) : MapFeatureAttachmentsUiState
    data object NotFound : MapFeatureAttachmentsUiState
    data class Error(val messageRes: Int) : MapFeatureAttachmentsUiState
}

@HiltViewModel
class MapFeatureAttachmentsViewModel @Inject constructor(
    private val attachmentRepository: AttachmentRepository,
    private val mapRepository: MapRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<MapFeatureAttachmentsUiState>(MapFeatureAttachmentsUiState.Idle)
    val uiState: StateFlow<MapFeatureAttachmentsUiState> = _uiState.asStateFlow()

    private var currentJob: kotlinx.coroutines.Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    fun loadAttachments(propertyId: UUID, planId: UUID, featureId: UUID) {
        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            _uiState.value = MapFeatureAttachmentsUiState.Loading
            
            val context = mapRepository.getActiveFeatureContext(propertyId, planId, featureId)
            if (context == null) {
                _uiState.value = MapFeatureAttachmentsUiState.NotFound
                return@launch
            }

            attachmentRepository.getAttachmentsForMapFeature(propertyId, featureId)
                .map { entities ->
                    entities.map { entity ->
                        val fileState = attachmentRepository.resolveAttachmentFile(propertyId, entity.id, verifyHash = false)
                        AttachmentListItemUiModel(
                            attachment = entity,
                            previewUri = (fileState as? AttachmentFileState.Available)?.uri,
                            isMissing = fileState is AttachmentFileState.Missing,
                            isDamaged = fileState is AttachmentFileState.Damaged
                        )
                    }
                }
                .map { uiModels ->
                    val cover = uiModels.find { it.attachment.isCover }
                    val photos = uiModels.count { it.attachment.mimeType?.startsWith("image/") == true }
                    val docs = uiModels.size - photos

                    MapFeatureAttachmentsUiState.Ready(
                        feature = context.feature,
                        plan = context.plan,
                        layer = context.layer,
                        attachments = uiModels,
                        coverAttachment = cover,
                        photoCount = photos,
                        documentCount = docs
                    )
                }
                .catch { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _uiState.value = MapFeatureAttachmentsUiState.Error(com.jumastappworks.mapstead.R.string.error_occurred)
                }
                .collect { _uiState.value = it }
        }
    }

    fun clear() {
        currentJob?.cancel()
        _uiState.value = MapFeatureAttachmentsUiState.Idle
    }

    fun createCameraCapture(): com.jumastappworks.mapstead.data.backup.TemporaryCameraCapture? {
        return attachmentRepository.createTempCameraUri().getOrNull()
    }

    fun deleteCameraCapture(token: String) {
        attachmentRepository.deleteTempCameraCapture(token)
    }
}
