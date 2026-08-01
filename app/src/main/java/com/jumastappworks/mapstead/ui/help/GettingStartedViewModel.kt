package com.jumastappworks.mapstead.ui.help

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
import java.util.UUID
import javax.inject.Inject

sealed interface GettingStartedUiState {
    data object LoadingProperties : GettingStartedUiState
    data class NeedsProperty(
        val context: GettingStartedPropertyContext,
        val steps: List<GettingStartedStep>
    ) : GettingStartedUiState
    data class LoadingProgress(
        val context: GettingStartedPropertyContext.Selected
    ) : GettingStartedUiState
    data class Ready(
        val context: GettingStartedPropertyContext.Selected,
        val steps: List<GettingStartedStep>,
        val plans: List<PlanEntity> = emptyList(),
        val adoptionError: Int? = null
    ) : GettingStartedUiState
}

// Internal batch states for type-safe aggregation (Phase 2.2g)
private data class GettingStartedRepoBatch(
    val plans: List<PlanEntity>,
    val items: List<InfrastructureItemEntity>,
    val maintenance: List<MaintenanceRecordEntity>,
    val attachments: List<AttachmentEntity>,
    val features: List<MapFeatureEntity>
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GettingStartedViewModel @Inject constructor(
    private val mapRepository: MapRepository,
    private val infrastructureRepository: InfrastructureRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val attachmentRepository: AttachmentRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val selectionManager: PropertySelectionManager
) : ViewModel() {

    private val _adoptionError = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<GettingStartedUiState> = selectionManager.selectionState
        .flatMapLatest { selection ->
            when (selection) {
                is PropertySelectionState.Loading -> flowOf(GettingStartedUiState.LoadingProperties)
                is PropertySelectionState.NoProperties -> {
                    val progress = GettingStartedProgress(hasProperty = false, hasMap = false, hasMappedItem = false, hasInfrastructure = false, hasMaintenance = false, hasAttachment = false, emergencyReviewed = false, dismissed = false)
                    flowOf(GettingStartedUiState.NeedsProperty(GettingStartedPropertyContext.NoProperties, GettingStartedStepBuilder.buildSteps(progress, propertySelected = false)))
                }
                is PropertySelectionState.NeedsSelection -> {
                    val progress = GettingStartedProgress(hasProperty = true, hasMap = false, hasMappedItem = false, hasInfrastructure = false, hasMaintenance = false, hasAttachment = false, emergencyReviewed = false, dismissed = false)
                    flowOf(GettingStartedUiState.NeedsProperty(GettingStartedPropertyContext.NeedsSelection(selection.activeProperties), GettingStartedStepBuilder.buildSteps(progress, propertySelected = false)))
                }
                is PropertySelectionState.Selected -> {
                    val context = GettingStartedPropertyContext.Selected(selection.selectedProperty, selection.allActiveProperties)
                    val selectedId = selection.selectedProperty.id
                    
                    val repoBatchFlow = combine(
                        mapRepository.getPlansForProperty(selectedId),
                        infrastructureRepository.getItemsForProperty(selectedId),
                        maintenanceRepository.getRecordsForProperty(selectedId),
                        attachmentRepository.getAttachmentsForProperty(selectedId),
                        mapRepository.getFeaturesForProperty(selectedId)
                    ) { plans, items, maint, atts, feats ->
                        GettingStartedRepoBatch(plans, items, maint, atts, feats)
                    }

                    combine(
                        repoBatchFlow,
                        userPreferencesRepository.userPreferencesFlow,
                        _adoptionError
                    ) { repo, prefs, err ->
                        val progress = GettingStartedProgress(
                            hasProperty = true,
                            hasMap = repo.plans.isNotEmpty(),
                            hasMappedItem = repo.features.isNotEmpty(),
                            hasInfrastructure = repo.items.isNotEmpty(),
                            hasMaintenance = repo.maintenance.isNotEmpty(),
                            hasAttachment = repo.attachments.isNotEmpty(),
                            emergencyReviewed = prefs.emergencyReviewedPropertyIds.contains(selectedId.toString()),
                            dismissed = prefs.gettingStartedDismissedPropertyIds.contains(selectedId.toString())
                        )

                        GettingStartedUiState.Ready(
                            context = context,
                            steps = GettingStartedStepBuilder.buildSteps(progress, propertySelected = true),
                            plans = repo.plans,
                            adoptionError = err
                        ) as GettingStartedUiState
                    }.onStart { emit(GettingStartedUiState.LoadingProgress(context)) }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GettingStartedUiState.LoadingProperties)

    fun markEmergencyReviewed(propertyId: UUID) {
        viewModelScope.launch {
            userPreferencesRepository.markEmergencyReviewed(propertyId.toString())
        }
    }

    fun selectProperty(propertyId: UUID) {
        viewModelScope.launch {
            try {
                selectionManager.selectProperty(propertyId)
                _adoptionError.value = null
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _adoptionError.value = R.string.error_save_failed
            }
        }
    }

    fun clearAdoptionError() {
        _adoptionError.value = null
    }
}
