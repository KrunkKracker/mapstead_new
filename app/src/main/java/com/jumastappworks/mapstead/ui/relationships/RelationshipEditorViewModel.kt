package com.jumastappworks.mapstead.ui.relationships

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import com.jumastappworks.mapstead.data.relationships.*
import com.jumastappworks.mapstead.data.repository.InfrastructureRelationshipRepository
import com.jumastappworks.mapstead.data.repository.InfrastructureRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface RelationshipEditorUiState {
    data object Loading : RelationshipEditorUiState
    data class Ready(
        val propertyId: UUID,
        val currentItemId: UUID,
        val currentItemName: String,
        val relationshipId: UUID? = null,
        val relatedItemId: UUID? = null,
        val selectedType: ItemRelationshipType = ItemRelationshipType.CONNECTED_TO,
        val isOutgoing: Boolean = true,
        val description: String = "",
        val availableItems: List<InfrastructureItemEntity> = emptyList(),
        val isSaving: Boolean = false,
        val errorRes: Int? = null,
        val saved: Boolean = false
    ) : RelationshipEditorUiState
    data object NotFound : RelationshipEditorUiState
    data class Error(val message: String) : RelationshipEditorUiState
}

@HiltViewModel
class RelationshipEditorViewModel @Inject constructor(
    private val relationshipRepository: InfrastructureRelationshipRepository,
    private val infrastructureRepository: InfrastructureRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<RelationshipEditorUiState>(RelationshipEditorUiState.Loading)
    val uiState: StateFlow<RelationshipEditorUiState> = _uiState.asStateFlow()

    fun init(propertyId: UUID, currentItemId: UUID, relationshipId: UUID?) {
        viewModelScope.launch {
            val currentItem = infrastructureRepository.getActiveItemForProperty(propertyId, currentItemId)
            if (currentItem == null) {
                _uiState.value = RelationshipEditorUiState.NotFound
                return@launch
            }

            val allItems = infrastructureRepository.getItemsForProperty(propertyId).first()
            val available = allItems.filter { it.id != currentItemId && it.deletedAt == null }
            
            if (relationshipId != null) {
                val rel = relationshipRepository.getRelationshipForProperty(propertyId, relationshipId)
                if (rel == null || rel.deletedAt != null) {
                    _uiState.value = RelationshipEditorUiState.NotFound
                    return@launch
                }

                // Context validation: currentItem must be one of the endpoints
                if (rel.sourceItemId != currentItemId && rel.targetItemId != currentItemId) {
                    _uiState.value = RelationshipEditorUiState.NotFound
                    return@launch
                }

                val isOutgoing = rel.sourceItemId == currentItemId
                val relatedId = if (isOutgoing) rel.targetItemId else rel.sourceItemId

                _uiState.value = RelationshipEditorUiState.Ready(
                    propertyId = propertyId,
                    currentItemId = currentItemId,
                    currentItemName = currentItem.name,
                    relationshipId = relationshipId,
                    relatedItemId = relatedId,
                    selectedType = ItemRelationshipType.fromString(rel.relationshipType),
                    isOutgoing = isOutgoing,
                    description = rel.description ?: "",
                    availableItems = available
                )
            } else {
                _uiState.value = RelationshipEditorUiState.Ready(
                    propertyId = propertyId,
                    currentItemId = currentItemId,
                    currentItemName = currentItem.name,
                    availableItems = available
                )
            }
        }
    }

    fun onRelatedItemChange(itemId: UUID) {
        val current = _uiState.value as? RelationshipEditorUiState.Ready ?: return
        _uiState.value = current.copy(relatedItemId = itemId)
    }

    fun onTypeChange(type: ItemRelationshipType) {
        val current = _uiState.value as? RelationshipEditorUiState.Ready ?: return
        _uiState.value = current.copy(selectedType = type)
    }

    fun onDirectionChange(isOutgoing: Boolean) {
        val current = _uiState.value as? RelationshipEditorUiState.Ready ?: return
        _uiState.value = current.copy(isOutgoing = isOutgoing)
    }

    fun onDescriptionChange(desc: String) {
        val current = _uiState.value as? RelationshipEditorUiState.Ready ?: return
        _uiState.value = current.copy(description = desc)
    }

    fun save() {
        val current = _uiState.value as? RelationshipEditorUiState.Ready ?: return
        if (current.isSaving) return
        val targetId = current.relatedItemId ?: return
        
        _uiState.value = current.copy(isSaving = true, errorRes = null)
        
        viewModelScope.launch {
            val sourceId = if (current.isOutgoing) current.currentItemId else targetId
            val destId = if (current.isOutgoing) targetId else current.currentItemId
            
            try {
                val result = if (current.relationshipId != null) {
                    relationshipRepository.updateRelationship(
                        propertyId = current.propertyId,
                        relationshipId = current.relationshipId,
                        sourceId = sourceId,
                        targetId = destId,
                        type = current.selectedType,
                        description = current.description.takeIf { it.isNotBlank() }
                    )
                } else {
                    relationshipRepository.createRelationship(
                        propertyId = current.propertyId,
                        sourceId = sourceId,
                        targetId = destId,
                        type = current.selectedType,
                        description = current.description.takeIf { it.isNotBlank() }
                    )
                }
                
                when (result) {
                    is RelationshipWriteResult.Success -> {
                        _uiState.value = (_uiState.value as RelationshipEditorUiState.Ready).copy(isSaving = false, saved = true)
                    }
                    is RelationshipWriteResult.Duplicate -> {
                        _uiState.value = (_uiState.value as RelationshipEditorUiState.Ready).copy(isSaving = false, errorRes = com.jumastappworks.mapstead.R.string.error_relationship_exists)
                    }
                    is RelationshipWriteResult.DependencyCycle -> {
                        _uiState.value = (_uiState.value as RelationshipEditorUiState.Ready).copy(isSaving = false, errorRes = com.jumastappworks.mapstead.R.string.error_circular_dependency)
                    }
                    else -> {
                        _uiState.value = (_uiState.value as RelationshipEditorUiState.Ready).copy(isSaving = false, errorRes = com.jumastappworks.mapstead.R.string.error_relationship_save_failed)
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                _uiState.value = (_uiState.value as RelationshipEditorUiState.Ready).copy(isSaving = false, errorRes = com.jumastappworks.mapstead.R.string.error_save_failed)
            }
        }
    }

    fun deleteRelationship() {
        val current = _uiState.value as? RelationshipEditorUiState.Ready ?: return
        if (current.isSaving) return
        val relId = current.relationshipId ?: return
        
        _uiState.value = current.copy(isSaving = true, errorRes = null)
        
        viewModelScope.launch {
            try {
                val result = relationshipRepository.softDeleteRelationship(current.propertyId, relId)
                if (result is RelationshipWriteResult.Success) {
                    _uiState.value = current.copy(isSaving = false, saved = true)
                } else {
                    _uiState.value = current.copy(isSaving = false, errorRes = com.jumastappworks.mapstead.R.string.error_relationship_delete_failed)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                _uiState.value = current.copy(isSaving = false, errorRes = com.jumastappworks.mapstead.R.string.error_delete_failed)
            }
        }
    }
}
