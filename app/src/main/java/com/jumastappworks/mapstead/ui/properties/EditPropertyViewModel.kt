package com.jumastappworks.mapstead.ui.properties

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.data.repository.PropertyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class PropertyDetailsSnapshot(
    val name: String,
    val type: String,
    val addressLine1: String,
    val addressLine2: String,
    val city: String,
    val state: String,
    val postalCode: String,
    val countryCode: String,
    val parcelNumber: String,
    val acreage: String,
    val description: String,
    val latitude: String,
    val longitude: String
)

sealed interface AddressLookupStateLegacy {
    data object Idle : AddressLookupStateLegacy
    data object Searching : AddressLookupStateLegacy
    data class Results(val matches: List<AddressLocationMatch>) : AddressLookupStateLegacy
    data class ConfirmingSelection(val match: AddressLocationMatch) : AddressLookupStateLegacy
    data class Error(val messageRes: Int) : AddressLookupStateLegacy
}

@HiltViewModel
class EditPropertyViewModel @Inject constructor(
    private val propertyRepository: PropertyRepository,
    private val locationProvider: CurrentLocationProvider,
    private val addressResolver: AddressLocationResolver
) : ViewModel() {

    private val _locationState = MutableStateFlow<LocationResult?>(null)
    val locationState: StateFlow<LocationResult?> = _locationState.asStateFlow()

    private val _addressLookupState = MutableStateFlow<AddressLookupStateLegacy>(AddressLookupStateLegacy.Idle)
    val addressLookupState = _addressLookupState.asStateFlow()

    var propertyId: UUID? = null
    private var loadedProperty: PropertyEntity? = null
    private var initialSnapshot: PropertyDetailsSnapshot? = null

    var name by mutableStateOf("")
    var type by mutableStateOf("Home")
    var addressLine1 by mutableStateOf("")
    var addressLine2 by mutableStateOf("")
    var city by mutableStateOf("")
    var state by mutableStateOf("")
    var postalCode by mutableStateOf("")
    var countryCode by mutableStateOf("")
    var parcelNumber by mutableStateOf("")
    var acreage by mutableStateOf("")
    var description by mutableStateOf("")
    var latitude by mutableStateOf("")
    var longitude by mutableStateOf("")

    var isSaving by mutableStateOf(false)
    var nameError by mutableStateOf<Int?>(null)
    var latitudeError by mutableStateOf<Int?>(null)
    var longitudeError by mutableStateOf<Int?>(null)
    var acreageError by mutableStateOf<Int?>(null)
    var saveErrorRes by mutableStateOf<Int?>(null)

    fun isDirty(): Boolean {
        val s = initialSnapshot ?: return false
        return name != s.name || type != s.type || addressLine1 != s.addressLine1 || addressLine2 != s.addressLine2 ||
               city != s.city || state != s.state || postalCode != s.postalCode || countryCode != s.countryCode ||
               parcelNumber != s.parcelNumber || acreage != s.acreage || description != s.description ||
               latitude != s.latitude || longitude != s.longitude
    }

    fun loadProperty(id: UUID) {
        if (this.propertyId == id && initialSnapshot != null) return
        this.propertyId = id
        viewModelScope.launch {
            propertyRepository.getPropertyById(id)?.let {
                loadedProperty = it
                name = it.name
                type = it.propertyType
                addressLine1 = it.addressLine1 ?: ""
                addressLine2 = it.addressLine2 ?: ""
                city = it.city ?: ""
                state = it.stateOrRegion ?: ""
                postalCode = it.postalCode ?: ""
                countryCode = it.countryCode ?: ""
                parcelNumber = it.parcelNumber ?: ""
                acreage = it.acreage?.toString() ?: ""
                description = it.description ?: ""
                latitude = it.latitude?.toString() ?: ""
                longitude = it.longitude?.toString() ?: ""
                
                initialSnapshot = createSnapshot()
            }
        }
    }

    private fun createSnapshot() = PropertyDetailsSnapshot(
        name = name, type = type, addressLine1 = addressLine1, addressLine2 = addressLine2,
        city = city, state = state, postalCode = postalCode, countryCode = countryCode,
        parcelNumber = parcelNumber, acreage = acreage, description = description,
        latitude = latitude, longitude = longitude
    )

    private fun validate(): Boolean {
        var isValid = true
        if (name.isBlank()) { nameError = R.string.error_name_required; isValid = false } else nameError = null
        
        val lat = latitude.toDoubleOrNull()
        if (latitude.isNotBlank() && (lat == null || lat < -90.0 || lat > 90.0)) { latitudeError = R.string.error_invalid_latitude; isValid = false } else latitudeError = null
        
        val lng = longitude.toDoubleOrNull()
        if (longitude.isNotBlank() && (lng == null || lng < -180.0 || lng > 180.0)) { longitudeError = R.string.error_invalid_longitude; isValid = false } else longitudeError = null

        val acr = acreage.toDoubleOrNull()
        if (acreage.isNotBlank() && (acr == null || acr < 0)) { acreageError = R.string.error_invalid_number; isValid = false } else acreageError = null
        
        return isValid
    }

    fun saveProperty(onSuccess: () -> Unit) {
        if (isSaving || !validate()) return
        val pid = propertyId ?: return
        isSaving = true
        saveErrorRes = null
        
        val p = loadedProperty?.copy(
            name = name.trim(), propertyType = type, 
            addressLine1 = addressLine1.trim().takeIf { it.isNotBlank() },
            addressLine2 = addressLine2.trim().takeIf { it.isNotBlank() },
            city = city.trim().takeIf { it.isNotBlank() },
            stateOrRegion = state.trim().takeIf { it.isNotBlank() },
            postalCode = postalCode.trim().takeIf { it.isNotBlank() },
            countryCode = countryCode.trim().takeIf { it.isNotBlank() },
            parcelNumber = parcelNumber.trim().takeIf { it.isNotBlank() },
            acreage = acreage.toDoubleOrNull(),
            description = description.trim().takeIf { it.isNotBlank() },
            latitude = latitude.toDoubleOrNull(),
            longitude = longitude.toDoubleOrNull(),
            updatedAt = java.time.Instant.now()
        ) ?: return

        viewModelScope.launch {
            try {
                propertyRepository.updateProperty(p)
                onSuccess()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                saveErrorRes = R.string.error_save_failed
                isSaving = false
            }
        }
    }

    fun deleteProperty(onSuccess: () -> Unit) {
        val id = propertyId ?: return
        isSaving = true
        viewModelScope.launch {
            try {
                propertyRepository.softDeleteProperty(id)
                onSuccess()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                saveErrorRes = R.string.error_save_failed
                isSaving = false
            }
        }
    }

    private val _isLocating = MutableStateFlow(false)
    val isLocating = _isLocating.asStateFlow()
    var permissionRequested by mutableStateOf(false)

    fun onPermissionResult(granted: Boolean) { permissionRequested = true; if (granted) requestLocation() }

    fun requestLocation() {
        if (_isLocating.value) return
        _isLocating.value = true
        viewModelScope.launch {
            try {
                val result = locationProvider.getCurrentLocation()
                _locationState.value = result
                if (result is LocationResult.Success) {
                    latitude = String.format(java.util.Locale.US, "%.6f", result.latitude)
                    longitude = String.format(java.util.Locale.US, "%.6f", result.longitude)
                }
            } finally { _isLocating.value = false }
        }
    }

    private var searchJob: Job? = null
    private var searchGeneration = 0L

    fun searchAddress(query: String) {
        if (query.isBlank()) {
            _addressLookupState.value = AddressLookupStateLegacy.Idle
            return
        }
        
        searchJob?.cancel()
        val generation = ++searchGeneration
        _addressLookupState.value = AddressLookupStateLegacy.Searching
        
        searchJob = viewModelScope.launch {
            delay(500L) // Debounce
            try {
                val result = addressResolver.search(query)
                if (generation != searchGeneration) return@launch
                
                if (result is AddressSearchResult.Success && result.matches.isNotEmpty()) {
                    _addressLookupState.value = AddressLookupStateLegacy.Results(result.matches)
                } else {
                    _addressLookupState.value = AddressLookupStateLegacy.Error(R.string.error_no_address_matches)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (generation == searchGeneration) _addressLookupState.value = AddressLookupStateLegacy.Error(R.string.error_generic_location)
            }
        }
    }

    fun findCoordinatesFromAddress() {
        val query = listOf(addressLine1, city, state, postalCode).filter { it.isNotBlank() }.joinToString(", ")
        searchAddress(query)
    }

    fun selectAddressMatch(match: AddressLocationMatch) { _addressLookupState.value = AddressLookupStateLegacy.ConfirmingSelection(match) }
    fun confirmAddressSelection() {
        val s = _addressLookupState.value as? AddressLookupStateLegacy.ConfirmingSelection ?: return
        latitude = String.format(java.util.Locale.US, "%.6f", s.match.latitude)
        longitude = String.format(java.util.Locale.US, "%.6f", s.match.longitude)
        _addressLookupState.value = AddressLookupStateLegacy.Idle
    }
    fun cancelAddressLookup() { _addressLookupState.value = AddressLookupStateLegacy.Idle }
    fun clearLocation() { latitude = ""; longitude = ""; _locationState.value = null }
    fun setManualError(resId: Int) { saveErrorRes = resId }
}
