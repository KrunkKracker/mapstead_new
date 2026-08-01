package com.jumastappworks.mapstead.ui.attachments

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jumastappworks.mapstead.data.attachments.*
import com.jumastappworks.mapstead.data.backup.TemporaryCameraCapture
import com.jumastappworks.mapstead.data.db.entities.AttachmentEntity
import com.jumastappworks.mapstead.data.repository.AttachmentRepository
import com.jumastappworks.mapstead.data.repository.PropertyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class FileFilter {
    All, Photos, Documents, Property, Infrastructure, Maintenance, Feature
}

data class AttachmentListItemUiModel(
    val attachment: AttachmentEntity,
    val previewUri: Uri?,
    val isMissing: Boolean = false,
    val isDamaged: Boolean = false
)

data class PropertyFilesUiState(
    val propertyId: UUID,
    val propertyName: String = "",
    val attachments: List<AttachmentListItemUiModel> = emptyList(),
    val filteredAttachments: List<AttachmentListItemUiModel> = emptyList(),
    val currentFilter: FileFilter = FileFilter.All,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class PropertyFilesViewModel @Inject constructor(
    private val attachmentRepository: AttachmentRepository,
    private val propertyRepository: PropertyRepository
) : ViewModel() {

    private val _propertyId = MutableStateFlow<UUID?>(null)
    private val _currentFilter = MutableStateFlow(FileFilter.All)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _attachments = _propertyId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else attachmentRepository.getAttachmentsForProperty(id)
            .map { list ->
                list.map { entity ->
                    val fileState = attachmentRepository.resolveAttachmentFile(id, entity.id, verifyHash = false)
                    AttachmentListItemUiModel(
                        attachment = entity,
                        previewUri = (fileState as? AttachmentFileState.Available)?.uri,
                        isMissing = fileState is AttachmentFileState.Missing,
                        isDamaged = fileState is AttachmentFileState.Damaged
                    )
                }
            }
    }

    val uiState: StateFlow<PropertyFilesUiState?> = combine(
        _propertyId,
        _attachments,
        _currentFilter
    ) { id, attachments, filter ->
        if (id == null) return@combine null

        val propertyName = propertyRepository.getPropertyById(id)?.name ?: ""
        
        val filtered = when (filter) {
            FileFilter.All -> attachments
            FileFilter.Photos -> attachments.filter { it.attachment.mimeType?.startsWith("image/") == true }
            FileFilter.Documents -> attachments.filter { it.attachment.mimeType?.startsWith("image/") != true }
            FileFilter.Property -> attachments.filter { 
                it.attachment.infrastructureItemId == null && 
                it.attachment.maintenanceRecordId == null &&
                it.attachment.mapFeatureId == null
            }
            FileFilter.Infrastructure -> attachments.filter { it.attachment.infrastructureItemId != null }
            FileFilter.Maintenance -> attachments.filter { it.attachment.maintenanceRecordId != null }
            FileFilter.Feature -> attachments.filter { it.attachment.mapFeatureId != null }
        }

        PropertyFilesUiState(
            propertyId = id,
            propertyName = propertyName,
            attachments = attachments,
            filteredAttachments = filtered,
            currentFilter = filter,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun init(propertyId: UUID) {
        _propertyId.value = propertyId
    }

    fun setFilter(filter: FileFilter) {
        _currentFilter.value = filter
    }

    fun createCameraCapture(): TemporaryCameraCapture? {
        return attachmentRepository.createTempCameraUri().getOrNull()
    }

    fun deleteCameraCapture(token: String) {
        attachmentRepository.deleteTempCameraCapture(token)
    }
}
