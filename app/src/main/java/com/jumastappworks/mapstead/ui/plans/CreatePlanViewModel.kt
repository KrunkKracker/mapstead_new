package com.jumastappworks.mapstead.ui.plans

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.db.entities.PlanEntity
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.data.repository.MapRepository
import com.jumastappworks.mapstead.data.repository.PropertyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class PlanLocationMethod {
    PROPERTY_LOCATION,
    ADDRESS_SEARCH,
    PHONE_LOCATION,
    MANUAL_COORDINATES
}

sealed interface CreatePlanLocationState {
    data object Idle : CreatePlanLocationState
    data object Loading : CreatePlanLocationState
    data class Success(
        val latitude: Double,
        val longitude: Double,
        val method: PlanLocationMethod,
        val addressLabel: String? = null,
        val accuracyMeters: Float? = null,
        val zoom: Float = 17f
    ) : CreatePlanLocationState
    data class Error(val messageRes: Int) : CreatePlanLocationState
}

@HiltViewModel
class CreatePlanViewModel @Inject constructor(
    private val mapRepository: MapRepository,
    private val propertyRepository: PropertyRepository,
    private val locationProvider: CurrentLocationProvider,
    private val addressResolver: AddressLocationResolver
) : ViewModel() {

    var propertyId: UUID? = null
    var name by mutableStateOf("")
    
    private val _property = MutableStateFlow<PropertyEntity?>(null)
    val property = _property.asStateFlow()

    private val _locationState = MutableStateFlow<CreatePlanLocationState>(CreatePlanLocationState.Idle)
    val locationState = _locationState.asStateFlow()

    private val _addressSearchResults = MutableStateFlow<List<AddressLocationMatch>>(emptyList())
    val addressSearchResults = _addressSearchResults.asStateFlow()

    var isSaving by mutableStateOf(false)
    var nameError by mutableStateOf<Int?>(null)
    var saveErrorRes by mutableStateOf<Int?>(null)

    var manualLatitude by mutableStateOf("")
    var manualLongitude by mutableStateOf("")
    
    var latitudeError by mutableStateOf<Int?>(null)
    var longitudeError by mutableStateOf<Int?>(null)

    private val _isLocating = MutableStateFlow(false)
    val isLocating = _isLocating.asStateFlow()

    var permissionRequested by mutableStateOf(false)

    fun onPermissionResult(granted: Boolean) {
        permissionRequested = true
        if (granted) {
            useMyLocation()
        }
    }

    fun setPid(id: UUID) {
        if (propertyId == id) return
        propertyId = id
        viewModelScope.launch {
            _property.value = propertyRepository.getPropertyById(id)
        }
    }

    fun isDirty(): Boolean = name.isNotBlank() || _locationState.value !is CreatePlanLocationState.Idle || manualLatitude.isNotBlank() || manualLongitude.isNotBlank()

    fun usePropertyLocation() {
        val prop = _property.value ?: return
        if (prop.latitude != null && prop.longitude != null) {
            _locationState.value = CreatePlanLocationState.Success(prop.latitude, prop.longitude, PlanLocationMethod.PROPERTY_LOCATION)
        }
    }

    fun useMyLocation() {
        if (_isLocating.value) return
        _isLocating.value = true
        _locationState.value = CreatePlanLocationState.Loading
        viewModelScope.launch {
            try {
                val result = locationProvider.getCurrentLocation()
                if (result is LocationResult.Success) {
                    _locationState.value = CreatePlanLocationState.Success(result.latitude, result.longitude, PlanLocationMethod.PHONE_LOCATION, accuracyMeters = result.accuracyMeters)
                } else {
                    val msgRes = when (result) {
                        LocationResult.PermissionDenied -> R.string.location_issue_permission_denied
                        LocationResult.PermanentlyDenied -> R.string.location_issue_permission_permanently_denied
                        LocationResult.ProviderDisabled -> R.string.location_issue_provider_disabled
                        LocationResult.Timeout -> R.string.location_issue_timeout
                        else -> R.string.location_issue_unavailable
                    }
                    _locationState.value = CreatePlanLocationState.Error(msgRes)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                _locationState.value = CreatePlanLocationState.Error(R.string.error_occurred)
            } finally {
                _isLocating.value = false
            }
        }
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    fun searchAddress(query: String) {
        val normalized = query.trim()
        if (normalized.isBlank()) {
            _locationState.value = CreatePlanLocationState.Error(R.string.error_invalid_query)
            return
        }
        
        _locationState.value = CreatePlanLocationState.Loading
        _addressSearchResults.value = emptyList()
        
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            try {
                val result = addressResolver.search(normalized)
                if (result is AddressSearchResult.Success) {
                    _addressSearchResults.value = result.matches
                    _locationState.value = CreatePlanLocationState.Idle
                } else {
                    val msgRes = when (result) {
                        AddressSearchResult.NoMatches -> R.string.error_no_address_matches
                        AddressSearchResult.Unavailable -> R.string.error_geocoder_unavailable
                        AddressSearchResult.NetworkFailure -> R.string.error_network_failure
                        else -> R.string.error_generic_location
                    }
                    _locationState.value = CreatePlanLocationState.Error(msgRes)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                _locationState.value = CreatePlanLocationState.Error(R.string.error_generic_location)
            }
        }
    }

    fun selectAddressMatch(match: AddressLocationMatch) {
        _locationState.value = CreatePlanLocationState.Success(match.latitude, match.longitude, PlanLocationMethod.ADDRESS_SEARCH, addressLabel = match.displayAddress)
        _addressSearchResults.value = emptyList()
    }

    fun applyManualCoordinates() {
        val lat = manualLatitude.trim().toDoubleOrNull()
        val lng = manualLongitude.trim().toDoubleOrNull()
        
        var valid = true
        if (lat == null || !lat.isFinite() || lat < -90.0 || lat > 90.0) {
            latitudeError = R.string.error_invalid_latitude
            valid = false
        } else {
            latitudeError = null
            manualLatitude = String.format(java.util.Locale.US, "%.6f", lat)
        }
        
        if (lng == null || !lng.isFinite() || lng < -180.0 || lng > 180.0) {
            longitudeError = R.string.error_invalid_longitude
            valid = false
        } else {
            longitudeError = null
            manualLongitude = String.format(java.util.Locale.US, "%.6f", lng)
        }
        
        if (valid && lat != null && lng != null) {
            _locationState.value = CreatePlanLocationState.Success(lat, lng, PlanLocationMethod.MANUAL_COORDINATES)
        }
    }

    fun changeLocation() {
        _locationState.value = CreatePlanLocationState.Idle
        _addressSearchResults.value = emptyList()
    }

    fun savePlan(onSuccess: (UUID) -> Unit) {
        if (isSaving) return
        if (name.isBlank()) {
            nameError = R.string.error_name_required
            return
        }
        nameError = null
        saveErrorRes = null
        
        val loc = _locationState.value as? CreatePlanLocationState.Success
        if (loc == null) {
            saveErrorRes = R.string.error_select_location
            return
        }
        
        val pid = propertyId ?: return
        val planId = UUID.randomUUID()
        isSaving = true
        
        viewModelScope.launch {
            try {
                mapRepository.createPlanWithDefaultLayer(
                    PlanEntity(
                        id = planId,
                        propertyId = pid,
                        name = name.trim(),
                        planType = "EXTERIOR_MAP",
                        backgroundType = "MAP",
                        centerLatitude = loc.latitude,
                        centerLongitude = loc.longitude,
                        zoom = loc.zoom.toDouble()
                    )
                )
                onSuccess(planId)
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                saveErrorRes = R.string.error_save_failed
            } finally {
                isSaving = false
            }
        }
    }

    fun setManualError(resId: Int) {
        saveErrorRes = resId
    }
}
