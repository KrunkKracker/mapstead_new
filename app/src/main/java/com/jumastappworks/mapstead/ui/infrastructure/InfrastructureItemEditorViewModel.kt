package com.jumastappworks.mapstead.ui.infrastructure

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import com.jumastappworks.mapstead.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

sealed interface InfrastructureItemEditorUiState {
    data object Loading : InfrastructureItemEditorUiState
    data class Ready(
        val item: InfrastructureItemEntity? = null,
        val isSaving: Boolean = false,
        val saveErrorRes: Int? = null,
        val initialSnapshot: InfrastructureItemEditorSnapshot? = null
    ) : InfrastructureItemEditorUiState
    data object NotFound : InfrastructureItemEditorUiState
}

data class InfrastructureItemEditorSnapshot(
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
class InfrastructureItemEditorViewModel @Inject constructor(
    private val repository: InfrastructureRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<InfrastructureItemEditorUiState>(InfrastructureItemEditorUiState.Loading)
    val uiState = _uiState.asStateFlow()

    var propertyId: UUID? = null
    var itemId: UUID? = null
    private var loadedItem: InfrastructureItemEntity? = null

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
        if (this.itemId == id && this.propertyId == propId && uiState.value is InfrastructureItemEditorUiState.Ready) return
        this.propertyId = propId
        this.itemId = id
        if (id != null) {
            _uiState.value = InfrastructureItemEditorUiState.Loading
            viewModelScope.launch {
                val item = repository.getActiveItemForProperty(propId, id)
                if (item == null) {
                    _uiState.value = InfrastructureItemEditorUiState.NotFound
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
                
                val snapshot = InfrastructureItemEditorSnapshot(
                    name = name, category = category, subtype = subtype, status = status,
                    manufacturer = manufacturer, model = model, serialNumber = serialNumber,
                    serviceProvider = serviceProvider, phoneNumber = phoneNumber, website = website,
                    instructions = instructions, emergencyInstructions = emergencyInstructions,
                    notes = notes, isEmergencyItem = isEmergencyItem
                )
                
                _uiState.value = InfrastructureItemEditorUiState.Ready(item, initialSnapshot = snapshot)
            }
        } else {
            // Default reset
            name = ""; category = ""; subtype = ""; status = "Active"
            manufacturer = ""; model = ""; serialNumber = ""; serviceProvider = ""
            phoneNumber = ""; website = ""; instructions = ""; emergencyInstructions = ""
            notes = ""; isEmergencyItem = false
            
            val snapshot = InfrastructureItemEditorSnapshot(
                name = name, category = category, subtype = subtype, status = status,
                manufacturer = manufacturer, model = model, serialNumber = serialNumber,
                serviceProvider = serviceProvider, phoneNumber = phoneNumber, website = website,
                instructions = instructions, emergencyInstructions = emergencyInstructions,
                notes = notes, isEmergencyItem = isEmergencyItem
            )
            
            _uiState.value = InfrastructureItemEditorUiState.Ready(initialSnapshot = snapshot)
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
        val current = _uiState.value as? InfrastructureItemEditorUiState.Ready ?: return false
        val s = current.initialSnapshot ?: return name.isNotBlank() || category.isNotBlank()
        return name != s.name || category != s.category || subtype != s.subtype || 
               status != s.status || manufacturer != s.manufacturer || model != s.model || 
               serialNumber != s.serialNumber || serviceProvider != s.serviceProvider || 
               phoneNumber != s.phoneNumber || website != s.website || 
               instructions != s.instructions || emergencyInstructions != s.emergencyInstructions || 
               notes != s.notes || isEmergencyItem != s.isEmergencyItem
    }

    fun saveItem(onSuccess: (UUID) -> Unit) {
        val current = _uiState.value as? InfrastructureItemEditorUiState.Ready ?: return
        if (current.isSaving) return
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
}
