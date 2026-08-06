package com.jumastappworks.mapstead.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.db.entities.*
import com.jumastappworks.mapstead.data.help.*
import com.jumastappworks.mapstead.data.repository.*
import com.jumastappworks.mapstead.data.prefs.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.jumastappworks.mapstead.util.MaintenanceStatus
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object NotFound : HomeUiState
    data class Error(val messageRes: Int) : HomeUiState
    data class Ready(
        val property: PropertyEntity,
        val formattedAddress: String,
        val needsAttentionTasks: List<HomeTaskSummary>,
        val upcomingTasks: List<HomeTaskSummary>,
        val recentlyAddedItems: List<HomePropertyItemSummary>,
        val hasAnyPropertyContent: Boolean,
        val checklist: List<GettingStartedStep> = emptyList(),
        val showChecklist: Boolean = false
    ) : HomeUiState
}

data class HomeTaskSummary(
    val recordId: UUID,
    val title: String,
    val dueDate: LocalDate?,
    val dueState: HomeTaskDueState,
    val relatedItemId: UUID?
)

enum class HomeTaskDueState {
    OVERDUE, TODAY, UPCOMING
}

data class HomePropertyItemSummary(
    val itemId: UUID,
    val name: String,
    val category: String,
    val isEmergency: Boolean,
    val status: String,
    val createdAt: java.time.Instant
)

private data class HomeGroupA(
    val property: PropertyEntity?,
    val items: List<InfrastructureItemEntity>,
    val records: List<MaintenanceRecordEntity>,
    val features: List<MapFeatureEntity>
)

private data class HomeGroupB(
    val plans: List<PlanEntity>,
    val attachments: List<AttachmentEntity>,
    val allProperties: List<PropertyEntity>,
    val prefs: UserPreferences
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val propertyRepository: PropertyRepository,
    private val mapRepository: MapRepository,
    private val infrastructureRepository: InfrastructureRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val attachmentRepository: AttachmentRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _propertyId = MutableStateFlow<UUID?>(null)
    private val _retryTrigger = MutableStateFlow(0)

    val uiState: StateFlow<HomeUiState> = _propertyId
        .flatMapLatest { id ->
            if (id == null) flowOf(HomeUiState.Loading)
            else {
                _retryTrigger.flatMapLatest {
                    val flowA = combine(
                        flow { emit(propertyRepository.getPropertyById(id)) },
                        infrastructureRepository.getItemsForProperty(id),
                        maintenanceRepository.getRecordsForProperty(id),
                        mapRepository.getFeaturesForProperty(id)
                    ) { p, i, r, f -> HomeGroupA(p, i, r, f) }

                    val flowB = combine(
                        mapRepository.getPlansForProperty(id),
                        attachmentRepository.getAttachmentsForProperty(id),
                        propertyRepository.getAllProperties(),
                        userPreferencesRepository.userPreferencesFlow
                    ) { pl, a, ap, pr -> HomeGroupB(pl, a, ap, pr) }

                    combine(flowA, flowB) { a, b ->
                        val prop = a.property
                        if (prop == null) {
                            HomeUiState.NotFound
                        } else {
                            val today = LocalDate.now()
                            
                            val activeRecords = a.records.filter { record ->
                                record.deletedAt == null &&
                                !MaintenanceStatus.isCompleted(record.status) &&
                                !record.status.equals("Cancelled", ignoreCase = true)
                            }

                            val needsAttention = activeRecords.filter { it.nextDueDate != null && (it.nextDueDate.isBefore(today) || it.nextDueDate.isEqual(today)) }
                                .sortedBy { it.nextDueDate }
                                .map { it.toSummary(today) }

                            val upcoming = activeRecords.filter { it.nextDueDate != null && it.nextDueDate.isAfter(today) }
                                .sortedBy { it.nextDueDate }
                                .take(3)
                                .map { it.toSummary(today) }

                            val recentItems = a.items.filter { it.deletedAt == null }
                                .sortedByDescending { it.createdAt }
                                .take(5)
                                .map { it.toSummary() }

                            val hasAnyContent = a.items.any { it.deletedAt == null } || a.features.isNotEmpty()

                            val progress = GettingStartedProgress(
                                hasProperty = b.allProperties.isNotEmpty(),
                                hasMap = b.plans.isNotEmpty(),
                                hasMappedItem = a.features.isNotEmpty(),
                                hasInfrastructure = a.items.isNotEmpty(),
                                hasMaintenance = a.records.isNotEmpty(),
                                hasAttachment = b.attachments.isNotEmpty(),
                                emergencyReviewed = b.prefs.emergencyReviewedPropertyIds.contains(id.toString()),
                                dismissed = b.prefs.gettingStartedDismissedPropertyIds.contains(id.toString())
                            )
                            
                            val checklist = GettingStartedStepBuilder.buildSteps(progress, propertySelected = true)
                            val allDone = checklist.all { it.isCompleted }

                            HomeUiState.Ready(
                                property = prop,
                                formattedAddress = formatAddress(prop),
                                needsAttentionTasks = needsAttention,
                                upcomingTasks = upcoming,
                                recentlyAddedItems = recentItems,
                                hasAnyPropertyContent = hasAnyContent,
                                checklist = checklist,
                                showChecklist = !progress.dismissed && !allDone
                            )
                        }
                    }.onStart { emit(HomeUiState.Loading) }
                }
            }
        }.catch { e ->
            if (e is CancellationException) throw e
            emit(HomeUiState.Error(R.string.property_load_failed))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState.Loading)

    fun setPropertyId(id: UUID) {
        _propertyId.value = id
    }

    fun retry() {
        _retryTrigger.value += 1
    }

    fun dismissChecklist() {
        val id = _propertyId.value ?: return
        viewModelScope.launch {
            userPreferencesRepository.updateGettingStartedDismissed(id.toString(), true)
        }
    }

    fun markEmergencyReviewed() {
        val id = _propertyId.value ?: return
        viewModelScope.launch {
            userPreferencesRepository.markEmergencyReviewed(id.toString())
        }
    }

    private fun formatAddress(p: PropertyEntity): String {
        val parts = mutableListOf<String>()
        p.city?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        p.stateOrRegion?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        return parts.joinToString(", ")
    }

    private fun MaintenanceRecordEntity.toSummary(today: LocalDate): HomeTaskSummary {
        val state = when {
            nextDueDate == null -> HomeTaskDueState.UPCOMING
            nextDueDate.isBefore(today) -> HomeTaskDueState.OVERDUE
            nextDueDate.isEqual(today) -> HomeTaskDueState.TODAY
            else -> HomeTaskDueState.UPCOMING
        }
        return HomeTaskSummary(
            recordId = id,
            title = title,
            dueDate = nextDueDate,
            dueState = state,
            relatedItemId = infrastructureItemId
        )
    }

    private fun InfrastructureItemEntity.toSummary(): HomePropertyItemSummary {
        return HomePropertyItemSummary(
            itemId = id,
            name = name,
            category = category,
            isEmergency = isEmergencyItem,
            status = status,
            createdAt = createdAt
        )
    }
}
