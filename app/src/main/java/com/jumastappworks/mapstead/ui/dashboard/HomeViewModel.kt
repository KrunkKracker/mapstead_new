package com.jumastappworks.mapstead.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import com.jumastappworks.mapstead.data.db.entities.MaintenanceRecordEntity
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.repository.InfrastructureRepository
import com.jumastappworks.mapstead.data.repository.MaintenanceRepository
import com.jumastappworks.mapstead.data.repository.PropertyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class HomeUiState(
    val property: PropertyEntity? = null,
    val needsAttentionTasks: List<MaintenanceRecordEntity> = emptyList(),
    val upcomingTasks: List<MaintenanceRecordEntity> = emptyList(),
    val recentlyAddedItems: List<InfrastructureItemEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val propertyRepository: PropertyRepository,
    private val infrastructureRepository: InfrastructureRepository,
    private val maintenanceRepository: MaintenanceRepository
) : ViewModel() {

    private val _propertyId = MutableStateFlow<UUID?>(null)

    val uiState: StateFlow<HomeUiState> = _propertyId.flatMapLatest { pid ->
        if (pid == null) return@flatMapLatest flowOf(HomeUiState())
        
        val propertyFlow = propertyRepository.getAllProperties().map { list -> list.find { it.id == pid } }
        val itemsFlow = infrastructureRepository.getItemsForProperty(pid)
        val tasksFlow = maintenanceRepository.getRecordsForProperty(pid)

        combine(propertyFlow, itemsFlow, tasksFlow) { property, items, tasks ->
            val now = LocalDate.now()
            
            val activeTasks = tasks.filter { it.deletedAt == null && it.status != "Completed" }
            val needsAttention = activeTasks.filter { it.serviceDate.isBefore(now) || it.serviceDate == now }
            val upcoming = activeTasks.filter { it.serviceDate.isAfter(now) }.sortedBy { it.serviceDate }.take(3)
            
            val recentItems = items.filter { it.deletedAt == null }
                .sortedByDescending { it.createdAt }
                .take(5)

            HomeUiState(
                property = property,
                needsAttentionTasks = needsAttention,
                upcomingTasks = upcoming,
                recentlyAddedItems = recentItems,
                isLoading = false
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState(isLoading = true))

    fun setPropertyId(id: UUID) {
        _propertyId.value = id
    }
}
