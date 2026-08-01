package com.jumastappworks.mapstead.ui.emergency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import com.jumastappworks.mapstead.data.repository.InfrastructureRepository
import com.jumastappworks.mapstead.data.repository.MapRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject

data class EmergencyItemWithLocation(
    val item: InfrastructureItemEntity,
    val planId: UUID? = null,
    val featureId: UUID? = null,
    val planName: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EmergencyViewModel @Inject constructor(
    private val infrastructureRepository: InfrastructureRepository,
    private val mapRepository: MapRepository
) : ViewModel() {

    private val _propertyId = MutableStateFlow<UUID?>(null)

    val emergencyItems: StateFlow<List<EmergencyItemWithLocation>> = _propertyId
        .flatMapLatest { pid ->
            if (pid == null) flowOf(emptyList())
            else {
                combine(
                    infrastructureRepository.getEmergencyItems(pid),
                    mapRepository.getFeaturesForProperty(pid),
                    mapRepository.getPlansForProperty(pid)
                ) { items, features, plans ->
                    items.map { item ->
                        val matchedFeature = features.find { it.infrastructureItemId == item.id }
                        val matchedPlan = plans.find { it.id == matchedFeature?.planId }
                        EmergencyItemWithLocation(
                            item = item,
                            planId = matchedFeature?.planId,
                            featureId = matchedFeature?.id,
                            planName = matchedPlan?.name
                        )
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setPropertyId(id: UUID) {
        _propertyId.value = id
    }
}
