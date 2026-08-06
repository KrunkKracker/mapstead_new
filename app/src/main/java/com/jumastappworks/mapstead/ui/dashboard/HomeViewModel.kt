package com.jumastappworks.mapstead.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import com.jumastappworks.mapstead.data.db.entities.MaintenanceRecordEntity
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.repository.InfrastructureRepository
import com.jumastappworks.mapstead.data.repository.MaintenanceRepository
import com.jumastappworks.mapstead.data.repository.PropertyRepository
import com.jumastappworks.mapstead.data.repository.MapRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Ready(
        val property: PropertyEntity,
        val needsAttentionTasks: List<MaintenanceRecordEntity> = emptyList(),
        val upcomingTasks: List<MaintenanceRecordEntity> = emptyList(),
        val recentlyAddedItems: List<InfrastructureItemEntity> = emptyList(),
        val hasMapFeaturesOnly: Boolean = false
    ) : HomeUiState
    data object NoProperties : HomeUiState
    data object NotFound : HomeUiState
    data class Error(val messageRes: Int) : HomeUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val propertyRepository: PropertyRepository,
    private val infrastructureRepository: InfrastructureRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val mapRepository: MapRepository
) : ViewModel() {

    private val _propertyId = MutableStateFlow<UUID?>(null)
    private val _retryTrigger = MutableStateFlow(0L)

    val uiState: StateFlow<HomeUiState> = combine(_propertyId, _retryTrigger) { pid, _ -> pid }
        .flatMapLatest { pid ->
            if (pid == null) return@flatMapLatest flowOf(HomeUiState.Loading)
            
            // Start each property/retry cycle with Loading to clear stale state
            flow {
                emit(HomeUiState.Loading)
                
                val propertyFlow = propertyRepository.getAllProperties().map { list -> list.find { it.id == pid } }
                val itemsFlow = infrastructureRepository.getItemsForProperty(pid)
                val tasksFlow = maintenanceRepository.getRecordsForProperty(pid)
                val featuresFlow = mapRepository.getFeaturesForProperty(pid)

                combine(propertyFlow, itemsFlow, tasksFlow, featuresFlow) { property, items, tasks, features ->
                    if (property == null) return@combine HomeUiState.NotFound

                    val now = LocalDate.now()
                    val activeTasks = tasks.filter { isTaskActive(it) }
                    
                    val needsAttention = activeTasks
                        .filter { isOverdueOrDueToday(it, now) }
                        .sortedWith(compareByDescending<MaintenanceRecordEntity> { getEffectiveDueDate(it) }.thenBy { it.title })

                    val upcoming = activeTasks
                        .filter { isUpcoming(it, now) }
                        .sortedBy { getEffectiveDueDate(it) }
                        .take(3)
                    
                    val recentItems = items.filter { it.deletedAt == null }
                        .sortedByDescending { it.createdAt }
                        .take(5)

                    val hasMapFeaturesOnly = items.isEmpty() && features.any { it.deletedAt == null }

                    HomeUiState.Ready(
                        property = property,
                        needsAttentionTasks = needsAttention,
                        upcomingTasks = upcoming,
                        recentlyAddedItems = recentItems,
                        hasMapFeaturesOnly = hasMapFeaturesOnly
                    )
                }.collect { emit(it) }
            }.catch { e ->
                if (e is CancellationException) throw e
                emit(HomeUiState.Error(com.jumastappworks.mapstead.R.string.home_error_loading))
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState.Loading)

    fun setPropertyId(id: UUID) {
        if (_propertyId.value != id) {
            _propertyId.value = id
        }
    }

    fun retry() {
        _retryTrigger.update { it + 1 }
    }

    companion object {
        fun isTaskActive(task: MaintenanceRecordEntity): Boolean {
            if (task.deletedAt != null) return false
            val status = task.status.trim().lowercase()
            if (status == "completed" || status == "cancelled") return false
            return true
        }

        fun isOverdueOrDueToday(task: MaintenanceRecordEntity, now: LocalDate): Boolean {
            val due = task.nextDueDate ?: return false
            return due.isBefore(now) || due == now
        }

        fun isUpcoming(task: MaintenanceRecordEntity, now: LocalDate): Boolean {
            val due = task.nextDueDate ?: return false
            return due.isAfter(now)
        }

        fun getEffectiveDueDate(task: MaintenanceRecordEntity): LocalDate? {
            return task.nextDueDate
        }
    }
}
