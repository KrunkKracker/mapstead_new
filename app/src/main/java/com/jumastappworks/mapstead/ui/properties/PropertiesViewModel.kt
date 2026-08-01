package com.jumastappworks.mapstead.ui.properties

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.repository.PropertyRepository
import com.jumastappworks.mapstead.data.mapping.ExamplePropertySeeder
import com.jumastappworks.mapstead.data.prefs.UserPreferences
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

sealed interface PropertiesUiState {
    data object Loading : PropertiesUiState
    data class Loaded(
        val properties: List<PropertyEntity>,
        val archivedProperties: List<PropertyEntity>,
        val isDemoInstalled: Boolean,
        val showWelcome: Boolean,
        val exampleOperation: ExampleOperation = ExampleOperation.Idle,
        val isArchiving: Boolean = false,
        val errorRes: Int? = null
    ) : PropertiesUiState
}

enum class ExampleOperation {
    Idle, Installing, Removing
}

// Internal batch states for type-safe aggregation (Phase 2.2g)
private data class PropertiesRepoBatch(
    val properties: List<PropertyEntity>,
    val archived: List<PropertyEntity>,
    val prefs: UserPreferences
)

private data class PropertiesOperationBatch(
    val exampleOp: ExampleOperation,
    val archiving: Boolean,
    val error: Int?
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class PropertiesViewModel @Inject constructor(
    private val propertyRepository: PropertyRepository,
    private val examplePropertySeeder: ExamplePropertySeeder,
    private val userPrefs: UserPreferencesRepository
) : ViewModel() {

    private val _properties = propertyRepository.getAllProperties()
    private val _archivedProperties = propertyRepository.getArchivedProperties()
    
    private val _exampleOperation = MutableStateFlow(ExampleOperation.Idle)
    private val _isArchiving = MutableStateFlow(false)
    private val _errorRes = MutableStateFlow<Int?>(null)
    private val _prefs = userPrefs.userPreferencesFlow

    private val _repoBatch = combine(_properties, _archivedProperties, _prefs) { props, archived, prefs ->
        PropertiesRepoBatch(props, archived, prefs)
    }

    private val _opBatch = combine(_exampleOperation, _isArchiving, _errorRes) { op, archiving, err ->
        PropertiesOperationBatch(op, archiving, err)
    }

    val uiState: StateFlow<PropertiesUiState> = combine(_repoBatch, _opBatch) { repo, op ->
        val isDemo = repo.properties.any { it.id == ExamplePropertySeeder.EXAMPLE_PROPERTY_ID }
        PropertiesUiState.Loaded(
            properties = repo.properties,
            archivedProperties = repo.archived,
            isDemoInstalled = isDemo,
            showWelcome = !repo.prefs.welcomeDismissed && repo.properties.isEmpty(),
            exampleOperation = op.exampleOp,
            isArchiving = op.archiving,
            errorRes = op.error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PropertiesUiState.Loading)

    fun dismissWelcome() {
        viewModelScope.launch {
            userPrefs.updateWelcomeDismissed(true)
        }
    }

    fun installDemoData() {
        if (_exampleOperation.value != ExampleOperation.Idle) return
        _exampleOperation.value = ExampleOperation.Installing
        _errorRes.value = null
        viewModelScope.launch {
            try {
                examplePropertySeeder.seedExample()
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                _errorRes.value = R.string.error_save_failed
            } finally {
                _exampleOperation.value = ExampleOperation.Idle
            }
        }
    }

    fun removeDemoData() {
        if (_exampleOperation.value != ExampleOperation.Idle) return
        _exampleOperation.value = ExampleOperation.Removing
        _errorRes.value = null
        viewModelScope.launch {
            try {
                // Clear selection if it's the demo
                val prefs = userPrefs.userPreferencesFlow.first()
                if (prefs.selectedPropertyId == ExamplePropertySeeder.EXAMPLE_PROPERTY_ID.toString()) {
                    userPrefs.updateSelectedProperty(null)
                }
                examplePropertySeeder.removeExample()
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                _errorRes.value = R.string.error_delete_failed
            } finally {
                _exampleOperation.value = ExampleOperation.Idle
            }
        }
    }

    fun archiveProperty(id: UUID) {
        if (_isArchiving.value) return
        _isArchiving.value = true
        viewModelScope.launch {
            try {
                propertyRepository.archiveProperty(id)
                // Clear selection only after successful archive
                val prefs = userPrefs.userPreferencesFlow.first()
                if (prefs.selectedPropertyId == id.toString()) {
                    userPrefs.updateSelectedProperty(null)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                _errorRes.value = R.string.error_save_failed
            } finally {
                _isArchiving.value = false
            }
        }
    }

    fun restoreProperty(id: UUID) {
        viewModelScope.launch {
            propertyRepository.restoreProperty(id)
        }
    }

    fun softDeleteProperty(id: UUID) {
        viewModelScope.launch {
            try {
                propertyRepository.softDeleteProperty(id)
                // Clear selection only after successful deletion
                val prefs = userPrefs.userPreferencesFlow.first()
                if (prefs.selectedPropertyId == id.toString()) {
                    userPrefs.updateSelectedProperty(null)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                _errorRes.value = R.string.error_delete_failed
            }
        }
    }

    fun clearError() {
        _errorRes.value = null
    }
}
