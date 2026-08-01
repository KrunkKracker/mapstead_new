package com.jumastappworks.mapstead.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.db.entities.*
import com.jumastappworks.mapstead.data.help.*
import com.jumastappworks.mapstead.data.repository.*
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.jumastappworks.mapstead.util.MaintenanceStatus
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

sealed interface PropertyDashboardUiState {
    data object Loading : PropertyDashboardUiState
    data object NotFound : PropertyDashboardUiState
    data class Error(val messageRes: Int) : PropertyDashboardUiState
    data class Ready(
        val property: PropertyEntity,
        val formattedAddress: String,
        val planCount: Int,
        val itemCount: Int,
        val emergencyCount: Int,
        val dueMaintenanceCount: Int,
        val attachmentCount: Int,
        val checklist: List<GettingStartedStep> = emptyList(),
        val showChecklist: Boolean = false
    ) : PropertyDashboardUiState
}

// Internal batch states for type-safe aggregation (Phase 2.2g)
private data class DashboardRepoBatch(
    val property: PropertyEntity?,
    val plans: List<PlanEntity>,
    val items: List<InfrastructureItemEntity>,
    val emergency: List<InfrastructureItemEntity>,
    val maintenance: List<MaintenanceRecordEntity>,
    val attachments: List<AttachmentEntity>,
    val features: List<MapFeatureEntity>,
    val allProperties: List<PropertyEntity>
)

private data class DashboardRepoBatchPart1(
    val property: PropertyEntity?,
    val plans: List<PlanEntity>,
    val items: List<InfrastructureItemEntity>,
    val emergency: List<InfrastructureItemEntity>,
    val maintenance: List<MaintenanceRecordEntity>
)

private data class DashboardRepoBatchPart2(
    val attachments: List<AttachmentEntity>,
    val features: List<MapFeatureEntity>,
    val allProperties: List<PropertyEntity>
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PropertyDashboardViewModel @Inject constructor(
    private val propertyRepository: PropertyRepository,
    private val mapRepository: MapRepository,
    private val infrastructureRepository: InfrastructureRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val attachmentRepository: AttachmentRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _propertyId = MutableStateFlow<UUID?>(null)
    private val _retryTrigger = MutableStateFlow(0)

    val uiState: StateFlow<PropertyDashboardUiState> = combine(
        _propertyId, 
        _retryTrigger,
        userPreferencesRepository.userPreferencesFlow
    ) { id, retry, prefs -> Triple(id, retry, prefs) }
        .flatMapLatest { (id, retry, prefs) ->
            if (id == null) flowOf(PropertyDashboardUiState.Loading)
            else {
                val batchFlow = combine(
                    combine(
                        flow { emit(propertyRepository.getPropertyById(id)) },
                        mapRepository.getPlansForProperty(id),
                        infrastructureRepository.getItemsForProperty(id),
                        infrastructureRepository.getEmergencyItems(id),
                        maintenanceRepository.getRecordsForProperty(id)
                    ) { prop, plans, items, emergency, maint ->
                        DashboardRepoBatchPart1(prop, plans, items, emergency, maint)
                    },
                    combine(
                        attachmentRepository.getAttachmentsForProperty(id),
                        mapRepository.getFeaturesForProperty(id),
                        propertyRepository.getAllProperties()
                    ) { atts, feats, allProps ->
                        DashboardRepoBatchPart2(atts, feats, allProps)
                    }
                ) { b1, b2 ->
                    DashboardRepoBatch(
                        property = b1.property,
                        plans = b1.plans,
                        items = b1.items,
                        emergency = b1.emergency,
                        maintenance = b1.maintenance,
                        attachments = b2.attachments,
                        features = b2.features,
                        allProperties = b2.allProperties
                    )
                }

                batchFlow.map { batch ->
                    val prop = batch.property
                    if (prop == null) {
                        PropertyDashboardUiState.NotFound
                    } else {
                        val today = LocalDate.now()
                        val dueCount = batch.maintenance.count { record ->
                            record.deletedAt == null &&
                            record.nextDueDate != null &&
                            (record.nextDueDate.isBefore(today) || record.nextDueDate.isEqual(today)) &&
                            !MaintenanceStatus.isCompleted(record.status)
                        }
                        
                        val progress = GettingStartedProgress(
                            hasProperty = batch.allProperties.isNotEmpty(),
                            hasMap = batch.plans.isNotEmpty(),
                            hasMappedItem = batch.features.isNotEmpty(),
                            hasInfrastructure = batch.items.isNotEmpty(),
                            hasMaintenance = batch.maintenance.isNotEmpty(),
                            hasAttachment = batch.attachments.isNotEmpty(),
                            emergencyReviewed = prefs.emergencyReviewedPropertyIds.contains(id.toString()),
                            dismissed = prefs.gettingStartedDismissedPropertyIds.contains(id.toString())
                        )
                        
                        val checklist = GettingStartedStepBuilder.buildSteps(progress, propertySelected = true)
                        val allDone = checklist.all { it.isCompleted }
                        
                        PropertyDashboardUiState.Ready(
                            property = prop,
                            formattedAddress = formatAddress(prop),
                            planCount = batch.plans.size,
                            itemCount = batch.items.size,
                            emergencyCount = batch.emergency.size,
                            dueMaintenanceCount = dueCount,
                            attachmentCount = batch.attachments.size,
                            checklist = checklist,
                            showChecklist = !progress.dismissed && !allDone
                        )
                    }
                }.catch { e ->
                    emit(PropertyDashboardUiState.Error(R.string.property_load_failed))
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PropertyDashboardUiState.Loading)

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
}
