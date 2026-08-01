package com.jumastappworks.mapstead.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jumastappworks.mapstead.data.db.entities.PlanEntity
import com.jumastappworks.mapstead.data.repository.MapRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlansViewModel @Inject constructor(
    private val mapRepository: MapRepository
) : ViewModel() {

    private val _propertyId = MutableStateFlow<UUID?>(null)

    val plans: StateFlow<List<PlanEntity>> = _propertyId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else mapRepository.getPlansForProperty(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setPropertyId(id: UUID) {
        _propertyId.value = id
    }
}
