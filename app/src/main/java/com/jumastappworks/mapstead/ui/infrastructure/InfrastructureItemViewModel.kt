package com.jumastappworks.mapstead.ui.infrastructure

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.attachments.*
import com.jumastappworks.mapstead.data.backup.TemporaryCameraCapture
import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import com.jumastappworks.mapstead.data.relationships.ItemRelationshipUiModel
import com.jumastappworks.mapstead.data.relationships.RelationshipWriteResult
import com.jumastappworks.mapstead.data.repository.*
import com.jumastappworks.mapstead.ui.attachments.AttachmentListItemUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

sealed interface InfrastructureItemUiState {
    data object Loading : InfrastructureItemUiState
    data class Ready(
        val item: InfrastructureItemEntity? = null,
        val isSaving: Boolean = false,
        val isDeleting: Boolean = false,
        val saveErrorRes: Int? = null,
        val deleteErrorRes: Int? = null,
        val initialSnapshot: InfrastructureItemSnapshot? = null
    ) : InfrastructureItemUiState
    data object NotFound : InfrastructureItemUiState
}

data class InfrastructureItemSnapshot(
    val name: String,
    val category: String,
    val subtype: String,
    val status: String,
    val manufacturer: String,
    val model: String,
    val serialNumber: String,
    val serviceProvider: String,
    val phoneNumber: String,
    val website: String,
    val instructions: String,
    val emergencyInstructions: String,
    val notes: String,
    val isEmergencyItem: Boolean
)

enum class InfrastructureStatus(val databaseValue: String, val labelRes: Int) {
    ACTIVE("Active", R.string.item_status_active),
    INACTIVE("Inactive", R.string.item_status_inactive),
    DAMAGED("Damaged", R.string.item_status_damaged),
    RETIRED("Retired", R.string.item_status_retired);

    companion object {
        fun fromDatabaseValue(value: String): InfrastructureStatus {
            return entries.find { it.databaseValue.equals(value, ignoreCase = true) } ?: ACTIVE
        }
    }
}

@HiltViewModel
class InfrastructureItemViewModel @Inject constructor(
    private val repository: InfrastructureRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val attachmentRepository: AttachmentRepository,
    private val relationshipRepository: InfrastructureRelationshipRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<InfrastructureItemUiState>(InfrastructureItemUiState.Loading)
    val uiState = _uiState.asStateFlow()

    var propertyId: UUID? = null
    var itemId: UUID? = null
    private var loadedItem: InfrastructureItemEntity? = null
    
    private val _maintenanceCount = MutableStateFlow(0)
    val maintenanceCount = _maintenanceCount.asStateFlow()
    
    private val _nextDueDate = MutableStateFlow<LocalDate?>(null)
    val nextDueDate = _nextDueDate.asStateFlow()

    private val _attachments = MutableStateFlow<List<AttachmentListItemUiModel>>(emptyList())
    val attachments = _attachments.asStateFlow()

    private val _parentItem = MutableStateFlow<InfrastructureItemEntity?>(null)
    val parentItem = _parentItem.asStateFlow()

    private val _childrenItems = MutableStateFlow<List<InfrastructureItemEntity>>(emptyList())
    val childrenItems = _childrenItems.asStateFlow()

    private val _relationships = MutableStateFlow<List<ItemRelationshipUiModel>>(emptyList())
    val relationships = _relationships.asStateFlow()

    private val _actionState = MutableStateFlow<RelationshipWriteResult?>(null)
    val actionState = _actionState.asStateFlow()

    var name by mutableStateOf("")
    var category by mutableStateOf("")
    var subtype by mutableStateOf("")
    var status by mutableStateOf("Active")
    var manufacturer by mutableStateOf("")
    var model by mutableStateOf("")
    var serialNumber by mutableStateOf("")
    var serviceProvider by mutableStateOf("")
    var phoneNumber by mutableStateOf("")
    var website by mutableStateOf("")
    var instructions by mutableStateOf("")
    var emergencyInstructions by mutableStateOf("")
    var notes by mutableStateOf("")
    var isEmergencyItem by mutableStateOf(false)

    var nameError by mutableStateOf<Int?>(null)
    var categoryError by mutableStateOf<Int?>(null)

    fun loadItem(propId: UUID, id: UUID?) {
        if (this.itemId == id && this.propertyId == propId && uiState.value is InfrastructureItemUiState.Ready) return
        this.propertyId = propId
        this.itemId = id
        if (id != null) {
            _uiState.value = InfrastructureItemUiState.Loading
            viewModelScope.launch {
                val item = repository.getActiveItemForProperty(propId, id)
                if (item == null) {
                    _uiState.value = InfrastructureItemUiState.NotFound
                    return@launch
                }
                
                loadedItem = item
                name = item.name
                category = item.category
                subtype = item.subtype ?: ""
                status = item.status
                manufacturer = item.manufacturer ?: ""
                model = item.model ?: ""
                serialNumber = item.serialNumber ?: ""
                serviceProvider = item.serviceProvider ?: ""
                phoneNumber = item.phoneNumber ?: ""
                website = item.website ?: ""
                instructions = item.instructions ?: ""
                emergencyInstructions = item.emergencyInstructions ?: ""
                notes = item.notes ?: ""
                isEmergencyItem = item.isEmergencyItem
                
                val snapshot = InfrastructureItemSnapshot(
                    name = name, category = category, subtype = subtype, status = status,
                    manufacturer = manufacturer, model = model, serialNumber = serialNumber,
                    serviceProvider = serviceProvider, phoneNumber = phoneNumber, website = website,
                    instructions = instructions, emergencyInstructions = emergencyInstructions,
                    notes = notes, isEmergencyItem = isEmergencyItem
                )
                
                // Load parent
                item.parentItemId?.let { pid ->
                    _parentItem.value = repository.getActiveItemForProperty(propId, pid)
                }
                
                _uiState.value = InfrastructureItemUiState.Ready(item, initialSnapshot = snapshot)
            }
            
            maintenanceRepository.getRecordsForItem(id)
                .onEach { records ->
                    _maintenanceCount.value = records.size
                    _nextDueDate.value = records
                        .filter { it.nextDueDate != null }
                        .minByOrNull { it.nextDueDate!! }
                        ?.nextDueDate
                }
                .launchIn(viewModelScope)

            attachmentRepository.getAttachmentsForInfrastructureItem(propId, id)
                .onEach { list ->
                    _attachments.value = list.map { entity ->
                        val fileState = attachmentRepository.resolveAttachmentFile(propId, entity.id, verifyHash = false)
                        AttachmentListItemUiModel(
                            attachment = entity,
                            previewUri = (fileState as? AttachmentFileState.Available)?.uri,
                            isMissing = fileState is AttachmentFileState.Missing,
                            isDamaged = fileState is AttachmentFileState.Damaged
                        )
                    }
                }
                .launchIn(viewModelScope)

            relationshipRepository.observeRelationshipsForItem(propId, id)
                .onEach { _relationships.value = it }
                .launchIn(viewModelScope)

            relationshipRepository.getChildrenForItem(propId, id)
                .onEach { _childrenItems.value = it }
                .launchIn(viewModelScope)
        } else {
            // Default reset
            name = ""; category = ""; subtype = ""; status = "Active"
            manufacturer = ""; model = ""; serialNumber = ""; serviceProvider = ""
            phoneNumber = ""; website = ""; instructions = ""; emergencyInstructions = ""
            notes = ""; isEmergencyItem = false
            
            val snapshot = InfrastructureItemSnapshot(
                name = name, category = category, subtype = subtype, status = status,
                manufacturer = manufacturer, model = model, serialNumber = serialNumber,
                serviceProvider = serviceProvider, phoneNumber = phoneNumber, website = website,
                instructions = instructions, emergencyInstructions = emergencyInstructions,
                notes = notes, isEmergencyItem = isEmergencyItem
            )
            
            _uiState.value = InfrastructureItemUiState.Ready(initialSnapshot = snapshot)
            _maintenanceCount.value = 0
            _nextDueDate.value = null
            _attachments.value = emptyList()
            _parentItem.value = null
            _childrenItems.value = emptyList()
            _relationships.value = emptyList()
        }
    }

    private fun validate(): Boolean {
        var isValid = true
        if (name.isBlank()) {
            nameError = R.string.error_name_required
            isValid = false
        } else nameError = null
        
        if (category.isBlank()) {
            categoryError = R.string.error_category_required
            isValid = false
        } else categoryError = null
        
        return isValid
    }

    fun isDirty(): Boolean {
        val current = _uiState.value as? InfrastructureItemUiState.Ready ?: return false
        val s = current.initialSnapshot ?: return name.isNotBlank() || category.isNotBlank()
        return name != s.name || category != s.category || subtype != s.subtype || 
               status != s.status || manufacturer != s.manufacturer || model != s.model || 
               serialNumber != s.serialNumber || serviceProvider != s.serviceProvider || 
               phoneNumber != s.phoneNumber || website != s.website || 
               instructions != s.instructions || emergencyInstructions != s.emergencyInstructions || 
               notes != s.notes || isEmergencyItem != s.isEmergencyItem
    }

    fun saveItem(onSuccess: (UUID) -> Unit) {
        val current = _uiState.value as? InfrastructureItemUiState.Ready ?: return
        if (current.isSaving || current.isDeleting) return
        if (!validate()) return

        val pid = propertyId ?: return
        val id = itemId ?: UUID.randomUUID()
        
        val item = if (itemId != null && loadedItem != null) {
            loadedItem!!.copy(
                name = name.trim(),
                category = category.trim(),
                subtype = subtype.takeIf { it.isNotBlank() }?.trim(),
                status = status,
                manufacturer = manufacturer.takeIf { it.isNotBlank() }?.trim(),
                model = model.takeIf { it.isNotBlank() }?.trim(),
                serialNumber = serialNumber.takeIf { it.isNotBlank() }?.trim(),
                serviceProvider = serviceProvider.takeIf { it.isNotBlank() }?.trim(),
                phoneNumber = phoneNumber.takeIf { it.isNotBlank() }?.trim(),
                website = website.takeIf { it.isNotBlank() }?.trim(),
                instructions = instructions.takeIf { it.isNotBlank() }?.trim(),
                emergencyInstructions = emergencyInstructions.takeIf { it.isNotBlank() }?.trim(),
                notes = notes.takeIf { it.isNotBlank() }?.trim(),
                isEmergencyItem = isEmergencyItem,
                updatedAt = Instant.now()
            )
        } else {
            InfrastructureItemEntity(
                id = id,
                propertyId = pid,
                name = name.trim(),
                category = category.trim(),
                subtype = subtype.takeIf { it.isNotBlank() }?.trim(),
                status = status,
                manufacturer = manufacturer.takeIf { it.isNotBlank() }?.trim(),
                model = model.takeIf { it.isNotBlank() }?.trim(),
                serialNumber = serialNumber.takeIf { it.isNotBlank() }?.trim(),
                serviceProvider = serviceProvider.takeIf { it.isNotBlank() }?.trim(),
                phoneNumber = phoneNumber.takeIf { it.isNotBlank() }?.trim(),
                website = website.takeIf { it.isNotBlank() }?.trim(),
                instructions = instructions.takeIf { it.isNotBlank() }?.trim(),
                emergencyInstructions = emergencyInstructions.takeIf { it.isNotBlank() }?.trim(),
                notes = notes.takeIf { it.isNotBlank() }?.trim(),
                isEmergencyItem = isEmergencyItem
            )
        }

        _uiState.value = current.copy(isSaving = true, saveErrorRes = null)
        
        viewModelScope.launch {
            try {
                if (itemId != null) {
                    val result = repository.updateItemForProperty(pid, item)
                    if (result is InfrastructureWriteResult.Success) {
                        onSuccess(id)
                    } else {
                        _uiState.value = current.copy(isSaving = false, saveErrorRes = R.string.error_save_failed)
                    }
                } else {
                    repository.insertItem(item)
                    onSuccess(id)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                _uiState.value = current.copy(isSaving = false, saveErrorRes = R.string.error_save_failed)
            }
        }
    }

    fun deleteItem(onSuccess: () -> Unit) {
        val current = _uiState.value as? InfrastructureItemUiState.Ready ?: return
        if (current.isSaving || current.isDeleting) return
        val pid = propertyId ?: return
        val id = itemId ?: return
        
        _uiState.value = current.copy(isDeleting = true, deleteErrorRes = null)
        
        viewModelScope.launch {
            try {
                val result = repository.softDeleteItemForProperty(pid, id)
                if (result is InfrastructureWriteResult.Success) {
                    onSuccess()
                } else {
                    _uiState.value = current.copy(isDeleting = false, deleteErrorRes = R.string.error_delete_failed_generic)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                _uiState.value = current.copy(isDeleting = false, deleteErrorRes = R.string.error_delete_failed_generic)
            }
        }
    }

    fun createCameraCapture(): TemporaryCameraCapture? {
        return attachmentRepository.createTempCameraUri().getOrNull()
    }

    fun deleteCameraCapture(token: String) {
        attachmentRepository.deleteTempCameraCapture(token)
    }

    fun removeRelationship(relationshipId: UUID) {
        val pid = propertyId ?: return
        val current = _uiState.value as? InfrastructureItemUiState.Ready ?: return
        if (current.isSaving || current.isDeleting) return
        
        viewModelScope.launch {
            _actionState.value = null
            val result = relationshipRepository.softDeleteRelationship(pid, relationshipId)
            _actionState.value = result
        }
    }

    fun removeParent() {
        val pid = propertyId ?: return
        val id = itemId ?: return
        val current = _uiState.value as? InfrastructureItemUiState.Ready ?: return
        if (current.isSaving || current.isDeleting) return

        viewModelScope.launch {
            _actionState.value = null
            val result = relationshipRepository.setParent(pid, id, null)
            if (result is RelationshipWriteResult.Success) {
                _parentItem.value = null
            }
            _actionState.value = result
        }
    }

    fun clearActionState() {
        _actionState.value = null
    }
}
