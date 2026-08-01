package com.jumastappworks.mapstead.ui.relationships

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import com.jumastappworks.mapstead.data.relationships.RelationshipWriteResult
import com.jumastappworks.mapstead.data.repository.InfrastructureRelationshipRepository
import com.jumastappworks.mapstead.data.repository.InfrastructureRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface ParentEditorUiState {
    data object Loading : ParentEditorUiState
    data class Ready(
        val propertyId: UUID,
        val itemId: UUID,
        val itemName: String,
        val currentParentId: UUID? = null,
        val availableParents: List<InfrastructureItemEntity> = emptyList(),
        val isSaving: Boolean = false,
        val errorRes: Int? = null,
        val saved: Boolean = false
    ) : ParentEditorUiState
    data object NotFound : ParentEditorUiState
    data class Error(val message: String) : ParentEditorUiState
}

@HiltViewModel
class ParentEditorViewModel @Inject constructor(
    private val relationshipRepository: InfrastructureRelationshipRepository,
    private val infrastructureRepository: InfrastructureRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ParentEditorUiState>(ParentEditorUiState.Loading)
    val uiState: StateFlow<ParentEditorUiState> = _uiState.asStateFlow()

    fun init(propertyId: UUID, itemId: UUID) {
        viewModelScope.launch {
            val item = infrastructureRepository.getActiveItemForProperty(propertyId, itemId)
            if (item == null) {
                _uiState.value = ParentEditorUiState.NotFound
                return@launch
            }

            val allItems = infrastructureRepository.getItemsForProperty(propertyId).first()
            
            // Exclude self and descendants to prevent cycles
            val descendants = getDescendants(propertyId, itemId)
            val available = allItems.filter { 
                it.id != itemId && !descendants.contains(it.id) && it.deletedAt == null 
            }
            
            _uiState.value = ParentEditorUiState.Ready(
                propertyId = propertyId,
                itemId = itemId,
                itemName = item.name,
                currentParentId = item.parentItemId,
                availableParents = available
            )
        }
    }

    private suspend fun getDescendants(propertyId: UUID, itemId: UUID): Set<UUID> {
        val result = mutableSetOf<UUID>()
        val allItems = infrastructureRepository.getItemsForProperty(propertyId).first()
        val childrenMap = allItems.groupBy { it.parentItemId }
        
        val queue = mutableListOf(itemId)
        while (queue.isNotEmpty()) {
            val current = queue.removeAt(0)
            childrenMap[current]?.forEach { child ->
                if (result.add(child.id)) {
                    queue.add(child.id)
                }
            }
        }
        return result
    }

    fun setParent(parentId: UUID?) {
        val current = _uiState.value as? ParentEditorUiState.Ready ?: return
        if (current.isSaving) return

        _uiState.value = current.copy(isSaving = true, errorRes = null)
        viewModelScope.launch {
            try {
                val result = relationshipRepository.setParent(
                    propertyId = current.propertyId,
                    itemId = current.itemId,
                    parentId = parentId
                )
                
                when (result) {
                    is RelationshipWriteResult.Success -> {
                        _uiState.value = current.copy(isSaving = false, saved = true)
                    }
                    RelationshipWriteResult.HierarchyCycle -> {
                        _uiState.value = current.copy(isSaving = false, errorRes = com.jumastappworks.mapstead.R.string.error_circular_hierarchy)
                    }
                    else -> {
                        _uiState.value = current.copy(isSaving = false, errorRes = com.jumastappworks.mapstead.R.string.error_parent_save_failed)
                    }
                }
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (e: Exception) {
                _uiState.value = current.copy(isSaving = false, errorRes = com.jumastappworks.mapstead.R.string.error_occurred)
            }
        }
    }
}
