package com.jumastappworks.mapstead.ui.infrastructure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jumastappworks.mapstead.data.attachments.AttachmentFileState
import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.data.relationships.ItemRelationshipUiModel
import com.jumastappworks.mapstead.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

sealed interface InfrastructureItemDetailUiState {
    data object Loading : InfrastructureItemDetailUiState
    data class Ready(
        val item: InfrastructureItemEntity,
        val propertyName: String?,
        val mapLocations: List<MapFeatureEntity>,
        val attachmentCount: Int,
        val coverThumbnailUri: android.net.Uri?,
        val maintenanceCount: Int,
        val nextDueDate: LocalDate?,
        val parentItem: InfrastructureItemEntity?,
        val childrenItems: List<InfrastructureItemEntity>,
        val relationshipSummary: List<ItemRelationshipUiModel>
    ) : InfrastructureItemDetailUiState
    data object NotFound : InfrastructureItemDetailUiState
    data class Error(val message: String) : InfrastructureItemDetailUiState
    data object Deleting : InfrastructureItemDetailUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InfrastructureItemDetailViewModel @Inject constructor(
    private val infrastructureRepository: InfrastructureRepository,
    private val propertyRepository: PropertyRepository,
    private val mapRepository: MapRepository,
    private val attachmentRepository: AttachmentRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val relationshipRepository: InfrastructureRelationshipRepository
) : ViewModel() {

    private val _itemId = MutableStateFlow<UUID?>(null)
    private val _propertyId = MutableStateFlow<UUID?>(null)
    private val _deleteState = MutableStateFlow(false)

    val uiState: StateFlow<InfrastructureItemDetailUiState> = combine(_propertyId, _itemId, _deleteState) { pid, iid, deleting ->
        if (deleting) return@combine flowOf(InfrastructureItemDetailUiState.Deleting)
        if (pid == null || iid == null) return@combine flowOf(InfrastructureItemDetailUiState.Loading)
        
        val itemFlow = infrastructureRepository.observeActiveItem(pid, iid)
        val propertyFlow = propertyRepository.getAllProperties().map { list -> list.find { it.id == pid }?.name }
        val featuresFlow = mapRepository.getFeaturesForItem(iid)
            .map { features -> features.filter { it.deletedAt == null }.sortedBy { it.label ?: "" } }
        
        val attachmentsFlow = attachmentRepository.getAttachmentsForInfrastructureItem(pid, iid)
            .map { list ->
                val cover = list.find { it.isCover } ?: list.firstOrNull { it.attachmentType == "Photo" }
                val coverUri = cover?.let {
                    val state = attachmentRepository.resolveAttachmentFile(pid, it.id, verifyHash = false)
                    (state as? AttachmentFileState.Available)?.uri
                }
                list.size to coverUri
            }
        
        val maintenanceFlow = maintenanceRepository.getRecordsForItem(iid)
            .map { records ->
                val count = records.size
                val nextDue = records.filter { it.nextDueDate != null }
                    .minByOrNull { it.nextDueDate!! }?.nextDueDate
                count to nextDue
            }
        
        val relationshipsFlow = relationshipRepository.observeRelationshipsForItem(pid, iid)
        val childrenFlow = relationshipRepository.getChildrenForItem(pid, iid)
        
        combine(
            itemFlow, propertyFlow, featuresFlow, attachmentsFlow,
            maintenanceFlow, relationshipsFlow, childrenFlow
        ) { array ->
            val item = array[0] as? InfrastructureItemEntity
            val propName = array[1] as? String
            @Suppress("UNCHECKED_CAST")
            val features = array[2] as List<MapFeatureEntity>
            @Suppress("UNCHECKED_CAST")
            val attachments = array[3] as Pair<Int, android.net.Uri?>
            @Suppress("UNCHECKED_CAST")
            val maint = array[4] as Pair<Int, LocalDate?>
            @Suppress("UNCHECKED_CAST")
            val rels = array[5] as List<ItemRelationshipUiModel>
            @Suppress("UNCHECKED_CAST")
            val children = array[6] as List<InfrastructureItemEntity>
            
            if (item == null || item.propertyId != pid) {
                InfrastructureItemDetailUiState.NotFound
            } else {
                val parentItem = item.parentItemId?.let { infrastructureRepository.getItemById(it) }
                
                InfrastructureItemDetailUiState.Ready(
                    item = item,
                    propertyName = propName,
                    mapLocations = features,
                    attachmentCount = attachments.first,
                    coverThumbnailUri = attachments.second,
                    maintenanceCount = maint.first,
                    nextDueDate = maint.second,
                    parentItem = parentItem,
                    childrenItems = children,
                    relationshipSummary = rels
                )
            }
        }
    }.flatMapLatest { it }
    .catch { emit(InfrastructureItemDetailUiState.Error(it.message ?: "Unknown error")) }
    .stateIn(viewModelScope, SharingStarted.Eagerly, InfrastructureItemDetailUiState.Loading)

    fun init(propertyId: UUID, itemId: UUID) {
        _propertyId.value = propertyId
        _itemId.value = itemId
    }

    fun deleteItem(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val pid = _propertyId.value ?: return
        val iid = _itemId.value ?: return
        
        viewModelScope.launch {
            try {
                val result = infrastructureRepository.softDeleteItemForProperty(pid, iid)
                if (result is InfrastructureWriteResult.Success) {
                    _deleteState.value = true
                    onSuccess()
                } else {
                    onError("Failed to delete item")
                }
            } catch (e: Exception) {
                onError(e.message ?: "Deletion failed")
            }
        }
    }
}
