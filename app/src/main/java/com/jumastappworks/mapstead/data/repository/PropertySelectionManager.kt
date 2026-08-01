package com.jumastappworks.mapstead.data.repository

import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import com.jumastappworks.mapstead.util.UuidHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

sealed interface PropertySelectionState {
    data object Loading : PropertySelectionState
    data object NoProperties : PropertySelectionState
    data class NeedsSelection(val activeProperties: List<PropertyEntity>) : PropertySelectionState
    data class Selected(
        val selectedProperty: PropertyEntity,
        val allActiveProperties: List<PropertyEntity>
    ) : PropertySelectionState
}

sealed interface PropertySelectionWriteResult {
    data object Success : PropertySelectionWriteResult
    data object PersistenceFailed : PropertySelectionWriteResult
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class PropertySelectionManager @Inject constructor(
    private val propertyRepository: PropertyRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val externalScope: CoroutineScope
) {
    private val _activeProperties = propertyRepository.getAllProperties().map { list -> 
        list.filter { it.deletedAt == null && !it.isArchived } 
    }

    // internal flag to avoid infinite loops if persistence fails repeatedly
    private var lastAttemptedAdoptionId: UUID? = null

    val selectionState: StateFlow<PropertySelectionState> = combine(
        _activeProperties,
        userPreferencesRepository.userPreferencesFlow
    ) { activeProps, prefs ->
        val selectedId = prefs.selectedPropertyId?.let { UuidHelper.safeParse(it) }
        val selectedMatch = activeProps.find { it.id == selectedId }

        when {
            activeProps.isEmpty() -> PropertySelectionState.NoProperties
            selectedMatch != null -> PropertySelectionState.Selected(selectedMatch, activeProps)
            activeProps.size == 1 -> {
                // If it's the only property, but not yet persisted, we stay in NeedsSelection 
                // until the background persistence update completes and reflects in userPreferencesFlow.
                PropertySelectionState.NeedsSelection(activeProps)
            }
            else -> PropertySelectionState.NeedsSelection(activeProps)
        }
    }.stateIn(externalScope, SharingStarted.Eagerly, PropertySelectionState.Loading)

    init {
        externalScope.launch {
            combine(_activeProperties, userPreferencesRepository.userPreferencesFlow) { activeProps, prefs ->
                activeProps to prefs
            }.collect { (activeProps, prefs) ->
                try {
                    val storedIdStr = prefs.selectedPropertyId
                    val selectedId = storedIdStr?.let { UuidHelper.safeParse(it) }
                    
                    if (activeProps.isEmpty()) {
                        if (!storedIdStr.isNullOrBlank()) {
                            userPreferencesRepository.updateSelectedProperty(null)
                        }
                    } else if (activeProps.size == 1) {
                        val single = activeProps.first()
                        if (selectedId != single.id && lastAttemptedAdoptionId != single.id) {
                            lastAttemptedAdoptionId = single.id
                            userPreferencesRepository.updateSelectedProperty(single.id.toString())
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // Monitoring continues. Reset attempt flag to allow retry on next emission
                    lastAttemptedAdoptionId = null
                }
            }
        }
    }

    suspend fun selectProperty(id: UUID): PropertySelectionWriteResult {
        return try {
            userPreferencesRepository.updateSelectedProperty(id.toString())
            PropertySelectionWriteResult.Success
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            PropertySelectionWriteResult.PersistenceFailed
        }
    }

    suspend fun clearSelection(): PropertySelectionWriteResult {
        return try {
            userPreferencesRepository.updateSelectedProperty(null)
            PropertySelectionWriteResult.Success
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            PropertySelectionWriteResult.PersistenceFailed
        }
    }
}
