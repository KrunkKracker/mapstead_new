package com.jumastappworks.mapstead.ui.properties

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.attachments.*
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.data.prefs.MeasurementSystem
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import com.jumastappworks.mapstead.data.repository.AttachmentRepository
import com.jumastappworks.mapstead.data.repository.PropertyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface PropertySetupOutcome {
    data class PropertyCreated(val propertyId: UUID) : PropertySetupOutcome
    data class PropertyCreatedWithPhotoWarning(val propertyId: UUID) : PropertySetupOutcome
    data object PropertyCreationFailed : PropertySetupOutcome
}

enum class SetupStep {
    NAME_AND_TYPE,
    LOCATE,
    REVIEW
}

enum class PropertyLocationMethod {
    NONE,
    ADDRESS,
    GPS,
    MAP,
    MANUAL
}

data class PropertyLocationCandidate(
    val method: PropertyLocationMethod,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double?,
    val displayLabel: String
)

sealed interface PropertySetupTarget {
    val id: UUID
    data class New(override val id: UUID) : PropertySetupTarget
    data class Existing(override val id: UUID) : PropertySetupTarget
}

data class PropertySetupState(
    val currentStep: SetupStep = SetupStep.NAME_AND_TYPE,
    val target: PropertySetupTarget,
    val propertyName: String = "",
    val propertyType: String? = null,
    val locationMethodScreen: PropertyLocationMethod = PropertyLocationMethod.NONE,
    val confirmedLocation: PropertyLocationCandidate? = null,
    val candidateLocation: PropertyLocationCandidate? = null,
    val isLocationDeferred: Boolean = false,
    val addressQuery: String = "",
    val addressResults: List<AddressLocationMatch> = emptyList(),
    val isSearchingAddress: Boolean = false,
    val isLocatingGps: Boolean = false,
    val locationIssue: LocationIssue? = null,
    val isSaving: Boolean = false,
    val errorRes: Int? = null,
    val nameErrorRes: Int? = null,
    val manualLatInput: String = "",
    val manualLngInput: String = "",
    val pickerLat: Double? = null,
    val pickerLng: Double? = null,
    val pickerZoom: Double? = null,
    val permissionRequested: Boolean = false,
    val locationPermissionLaunchInProgress: Boolean = false,
    val pendingLocationPurpose: LocationRequestPurpose? = null,
    val existingPropertyLoaded: Boolean = false,
    val measurementSystem: MeasurementSystem = MeasurementSystem.IMPERIAL,
    val stagedPhoto: StagedCreationPhotoState = StagedCreationPhotoState.None,
    val outcome: PropertySetupOutcome? = null
)

// Internal batch states for type-safe aggregation (Phase 2.2g)
private data class Triple3<A, B, C>(val a: A, val b: B, val c: C)
private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

private data class SetupIdentityBatch(
    val step: SetupStep,
    val target: PropertySetupTarget,
    val name: String,
    val type: String?,
    val existingLoaded: Boolean
)

private data class SetupLocationBatch(
    val screen: PropertyLocationMethod,
    val deferred: Boolean,
    val confirmed: PropertyLocationCandidate?,
    val candidate: PropertyLocationCandidate?
)

private data class SetupInputBatch(
    val query: String,
    val results: List<AddressLocationMatch>,
    val manualLat: String,
    val manualLng: String,
    val pLat: Double?
)

private data class SetupSystemBatch(
    val pLng: Double?,
    val pZoom: Double?,
    val req: Boolean,
    val launch: Boolean,
    val purpose: LocationRequestPurpose?
)

private data class SetupOperationBatch(
    val isSaving: Boolean,
    val isSearching: Boolean,
    val isLocating: Boolean,
    val errorRes: Int?,
    val nameErrorRes: Int?,
    val outcome: PropertySetupOutcome?
)

private data class SetupUiStatusBatch(
    val issue: LocationIssue?,
    val measurement: MeasurementSystem,
    val photo: StagedCreationPhotoState
)

private data class SetupGroupA(
    val identity: SetupIdentityBatch,
    val loc: SetupLocationBatch,
    val inputs: SetupInputBatch
)

private data class SetupGroupB(
    val system: SetupSystemBatch,
    val operation: SetupOperationBatch,
    val status: SetupUiStatusBatch
)

@HiltViewModel
class AddPropertyViewModel @Inject constructor(
    private val propertyRepository: PropertyRepository,
    private val attachmentRepository: AttachmentRepository,
    private val locationProvider: CurrentLocationProvider,
    private val addressResolver: AddressLocationResolver,
    private val userPreferencesRepository: UserPreferencesRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private companion object {
        const val KEY_TARGET_TYPE = "setup_target_type"
        const val KEY_TARGET_ID = "setup_target_id"
        const val KEY_STEP = "setup_step"
        const val KEY_NAME = "setup_name"
        const val KEY_TYPE = "setup_type"
        const val KEY_METHOD_SCREEN = "setup_method_screen"
        
        const val KEY_CONF_METHOD = "setup_conf_method"
        const val KEY_CONF_LAT = "setup_conf_lat"
        const val KEY_CONF_LNG = "setup_conf_lng"
        const val KEY_CONF_ACC = "setup_conf_acc"
        const val KEY_CONF_LABEL = "setup_conf_label"

        const val KEY_CAND_METHOD = "setup_cand_method"
        const val KEY_CAND_LAT = "setup_cand_lat"
        const val KEY_CAND_LNG = "setup_cand_lng"
        const val KEY_CAND_ACC = "setup_cand_acc"
        const val KEY_CAND_LABEL = "setup_cand_label"

        const val KEY_DEFERRED = "setup_deferred"
        const val KEY_ADDR_QUERY = "setup_addr_query"
        const val KEY_MANUAL_LAT = "setup_manual_lat"
        const val KEY_MANUAL_LNG = "setup_manual_lng"
        
        const val KEY_PICKER_LAT = "setup_picker_lat"
        const val KEY_PICKER_LNG = "setup_picker_lng"
        const val KEY_PICKER_ZOOM = "setup_picker_zoom"
        const val KEY_PERM_REQ = "setup_perm_req"
        const val KEY_EXISTING_LOADED = "setup_existing_loaded"
        const val KEY_PHOTO_URI = "setup_staged_photo_uri"
        const val KEY_PHOTO_TOKEN = "setup_staged_photo_token"
        const val KEY_IN_FLIGHT_URI = "setup_in_flight_photo_uri"
        const val KEY_IN_FLIGHT_TOKEN = "setup_in_flight_photo_token"
        const val KEY_PENDING_PURPOSE = "setup_pending_location_purpose"
        const val KEY_PERM_LAUNCH_IN_PROGRESS = "setup_perm_launch_in_progress"
    }

    private val _draftId: String = savedStateHandle.get<String>(KEY_TARGET_ID) ?: UUID.randomUUID().toString().also { savedStateHandle[KEY_TARGET_ID] = it }

    private val _isSaving = MutableStateFlow(false)
    private val _isSearching = MutableStateFlow(false)
    private val _isLocating = MutableStateFlow(false)
    private val _locationIssue = MutableStateFlow<LocationIssue?>(null)
    private val _addressResults = MutableStateFlow<List<AddressLocationMatch>>(emptyList())
    private val _errorRes = MutableStateFlow<Int?>(null)
    private val _nameErrorRes = MutableStateFlow<Int?>(null)
    private val _outcome = MutableStateFlow<PropertySetupOutcome?>(null)
    private val _pendingLocationPurpose = savedStateHandle.getStateFlow<LocationRequestPurpose?>(KEY_PENDING_PURPOSE, null)
    private val _locationPermissionLaunchInProgress = savedStateHandle.getStateFlow(KEY_PERM_LAUNCH_IN_PROGRESS, false)
    
    private val _stagedPhoto = MutableStateFlow<StagedCreationPhotoState>(StagedCreationPhotoState.None)

    private val _measurementSystem = userPreferencesRepository.userPreferencesFlow
        .map { it.measurementSystem }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MeasurementSystem.IMPERIAL)

    private val _identityBatch = combine(
        combine(
            savedStateHandle.getStateFlow(KEY_STEP, SetupStep.NAME_AND_TYPE.name),
            savedStateHandle.getStateFlow(KEY_TARGET_TYPE, "NEW"),
            savedStateHandle.getStateFlow(KEY_TARGET_ID, _draftId),
            savedStateHandle.getStateFlow(KEY_NAME, "")
        ) { step, type, id, name -> Quad(step, type, id, name) },
        savedStateHandle.getStateFlow<String?>(KEY_TYPE, null),
        savedStateHandle.getStateFlow(KEY_EXISTING_LOADED, false)
    ) { p1, pType, loaded ->
        val target = if (p1.b == "EXISTING") PropertySetupTarget.Existing(UUID.fromString(p1.c)) else PropertySetupTarget.New(UUID.fromString(p1.c))
        SetupIdentityBatch(SetupStep.valueOf(p1.a), target, p1.d, pType, loaded)
    }

    private val _locationBatch = combine(
        savedStateHandle.getStateFlow(KEY_METHOD_SCREEN, PropertyLocationMethod.NONE.name),
        savedStateHandle.getStateFlow(KEY_DEFERRED, false),
        combine(
            savedStateHandle.getStateFlow<String?>(KEY_CONF_METHOD, null),
            savedStateHandle.getStateFlow<Double?>(KEY_CONF_LAT, null),
            savedStateHandle.getStateFlow<Double?>(KEY_CONF_LNG, null),
            savedStateHandle.getStateFlow<Double?>(KEY_CONF_ACC, null),
            savedStateHandle.getStateFlow<String?>(KEY_CONF_LABEL, null)
        ) { method, lat, lng, acc, label ->
            if (method != null && lat != null && lng != null && label != null) {
                PropertyLocationCandidate(PropertyLocationMethod.valueOf(method), lat, lng, acc, label)
            } else null
        },
        combine(
            savedStateHandle.getStateFlow<String?>(KEY_CAND_METHOD, null),
            savedStateHandle.getStateFlow<Double?>(KEY_CAND_LAT, null),
            savedStateHandle.getStateFlow<Double?>(KEY_CAND_LNG, null),
            savedStateHandle.getStateFlow<Double?>(KEY_CAND_ACC, null),
            savedStateHandle.getStateFlow<String?>(KEY_CAND_LABEL, null)
        ) { method, lat, lng, acc, label ->
            if (method != null && lat != null && lng != null && label != null) {
                PropertyLocationCandidate(PropertyLocationMethod.valueOf(method), lat, lng, acc, label)
            } else null
        }
    ) { screen, deferred, confirmed, candidate ->
        SetupLocationBatch(PropertyLocationMethod.valueOf(screen), deferred, confirmed, candidate)
    }

    private val _inputBatch = combine(
        savedStateHandle.getStateFlow(KEY_ADDR_QUERY, ""),
        _addressResults,
        savedStateHandle.getStateFlow(KEY_MANUAL_LAT, ""),
        savedStateHandle.getStateFlow(KEY_MANUAL_LNG, ""),
        savedStateHandle.getStateFlow<Double?>(KEY_PICKER_LAT, null)
    ) { query, results, lat, lng, pLat ->
        SetupInputBatch(query, results, lat, lng, pLat)
    }

    private val _systemBatch = combine(
        savedStateHandle.getStateFlow<Double?>(KEY_PICKER_LNG, null),
        savedStateHandle.getStateFlow<Double?>(KEY_PICKER_ZOOM, null),
        savedStateHandle.getStateFlow(KEY_PERM_REQ, false),
        _locationPermissionLaunchInProgress,
        _pendingLocationPurpose
    ) { pLng, pZoom, req, launch, purpose ->
        SetupSystemBatch(pLng, pZoom, req, launch, purpose)
    }

    private val _operationBatch = combine(
        _isSaving, _isSearching, _isLocating, _errorRes, _nameErrorRes
    ) { s, sr, l, e, ne ->
        Triple(s, sr, l) to (e to ne)
    }.combine(_outcome) { p, outcome ->
        SetupOperationBatch(p.first.first, p.first.second, p.first.third, p.second.first, p.second.second, outcome)
    }

    private val _statusBatch = combine(
        _locationIssue, _measurementSystem, _stagedPhoto
    ) { issue, measurement, photo ->
        SetupUiStatusBatch(issue, measurement, photo)
    }

    private val _aggGroupA = combine(_identityBatch, _locationBatch, _inputBatch) { identity, loc, inputs ->
        SetupGroupA(identity, loc, inputs)
    }

    private val _aggGroupB = combine(_systemBatch, _operationBatch, _statusBatch) { system, operation, status ->
        SetupGroupB(system, operation, status)
    }

    val uiState: StateFlow<PropertySetupState> = combine(_aggGroupA, _aggGroupB) { groupA: SetupGroupA, groupB: SetupGroupB ->
        val identity = groupA.identity
        val loc = groupA.loc
        val inputs = groupA.inputs
        val system = groupB.system
        val operation = groupB.operation
        val status = groupB.status

        PropertySetupState(
            currentStep = identity.step,
            target = identity.target,
            propertyName = identity.name,
            propertyType = identity.type,
            locationMethodScreen = loc.screen,
            confirmedLocation = loc.confirmed,
            candidateLocation = loc.candidate,
            isLocationDeferred = loc.deferred,
            addressQuery = inputs.query,
            addressResults = inputs.results,
            manualLatInput = inputs.manualLat,
            manualLngInput = inputs.manualLng,
            pickerLat = inputs.pLat,
            pickerLng = system.pLng,
            pickerZoom = system.pZoom,
            permissionRequested = system.req,
            locationPermissionLaunchInProgress = system.launch,
            pendingLocationPurpose = system.purpose,
            locationIssue = status.issue,
            existingPropertyLoaded = identity.existingLoaded,
            measurementSystem = status.measurement,
            stagedPhoto = status.photo,
            isSaving = operation.isSaving,
            isSearchingAddress = operation.isSearching,
            isLocatingGps = operation.isLocating,
            errorRes = operation.errorRes,
            nameErrorRes = operation.nameErrorRes,
            outcome = operation.outcome
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PropertySetupState(target = PropertySetupTarget.New(UUID.fromString(_draftId))))

    var nameInput by mutableStateOf(savedStateHandle[KEY_NAME] ?: "")

    init {
        val targetType = savedStateHandle.get<String>(KEY_TARGET_TYPE)
        val targetIdStr = savedStateHandle.get<String>(KEY_TARGET_ID)
        val loaded = savedStateHandle.get<Boolean>(KEY_EXISTING_LOADED) ?: false
        if (targetType == "EXISTING" && targetIdStr != null && !loaded) {
            loadExistingProperty(UUID.fromString(targetIdStr))
        }
        
        // Restore staged photo if it was Ready in SavedStateHandle
        val photoUri = savedStateHandle.get<String>(KEY_PHOTO_URI)
        val photoToken = savedStateHandle.get<String>(KEY_PHOTO_TOKEN)
        if (photoUri != null) {
            _stagedPhoto.value = StagedCreationPhotoState.Ready(photoUri, photoToken)
        }
    }

    override fun onCleared() {
        // ViewModel destruction cleanup
        if (_outcome.value == null || _outcome.value is PropertySetupOutcome.PropertyCreationFailed) {
            clearStagedPhoto()
        }
    }

    fun loadExistingProperty(id: UUID) {
        savedStateHandle[KEY_TARGET_TYPE] = "EXISTING"
        savedStateHandle[KEY_TARGET_ID] = id.toString()
        savedStateHandle[KEY_EXISTING_LOADED] = false
        
        viewModelScope.launch {
            val p = propertyRepository.getPropertyById(id)
            if (p != null) {
                // Phase 2.2g: Protect user edits from late repository results
                if (savedStateHandle.get<String>(KEY_NAME).isNullOrBlank()) {
                    savedStateHandle[KEY_NAME] = p.name
                    nameInput = p.name
                }
                if (savedStateHandle.get<String>(KEY_TYPE).isNullOrBlank()) {
                    savedStateHandle[KEY_TYPE] = p.propertyType
                }
                if (p.latitude != null && p.longitude != null) {
                    if (savedStateHandle.get<String>(KEY_CONF_METHOD) == null) {
                        savedStateHandle[KEY_CONF_METHOD] = PropertyLocationMethod.MAP.name
                        savedStateHandle[KEY_CONF_LAT] = p.latitude
                        savedStateHandle[KEY_CONF_LNG] = p.longitude
                        savedStateHandle[KEY_CONF_LABEL] = "Saved Location"
                        savedStateHandle[KEY_DEFERRED] = false
                    }
                }
                savedStateHandle[KEY_STEP] = SetupStep.LOCATE.name
                savedStateHandle[KEY_METHOD_SCREEN] = PropertyLocationMethod.NONE.name
                savedStateHandle[KEY_EXISTING_LOADED] = true
            } else {
                _errorRes.value = R.string.property_not_found
            }
        }
    }

    fun setName(name: String) {
        savedStateHandle[KEY_NAME] = name
        nameInput = name
        _nameErrorRes.value = null
    }

    fun setType(type: String?) {
        savedStateHandle[KEY_TYPE] = type
    }

    fun proceedToLocate() {
        if (nameInput.isBlank()) {
            _nameErrorRes.value = R.string.error_name_required
            return
        }
        savedStateHandle[KEY_STEP] = SetupStep.LOCATE.name
    }

    fun goBackToName() {
        savedStateHandle[KEY_STEP] = SetupStep.NAME_AND_TYPE.name
    }

    fun goToReviewStep() {
        savedStateHandle[KEY_STEP] = SetupStep.REVIEW.name
    }

    fun setInFlightCapture(uri: String, token: String?) {
        savedStateHandle[KEY_IN_FLIGHT_URI] = uri
        savedStateHandle[KEY_IN_FLIGHT_TOKEN] = token
    }

    fun getInFlightUri(): String? = savedStateHandle[KEY_IN_FLIGHT_URI]
    fun getInFlightToken(): String? = savedStateHandle[KEY_IN_FLIGHT_TOKEN]

    fun clearInFlightCapture() {
        savedStateHandle.remove<String>(KEY_IN_FLIGHT_URI)
        savedStateHandle.remove<String>(KEY_IN_FLIGHT_TOKEN)
    }

    fun setStagedPhoto(uri: String, token: String?) {
        savedStateHandle[KEY_PHOTO_URI] = uri
        savedStateHandle[KEY_PHOTO_TOKEN] = token
        _stagedPhoto.value = StagedCreationPhotoState.Ready(uri, token)
    }

    fun clearStagedPhoto() {
        val token = savedStateHandle.get<String>(KEY_PHOTO_TOKEN)
        if (token != null) {
            attachmentRepository.deleteTempCameraCapture(token)
        }
        val inFlightToken = savedStateHandle.get<String>(KEY_IN_FLIGHT_TOKEN)
        if (inFlightToken != null) {
            attachmentRepository.deleteTempCameraCapture(inFlightToken)
        }
        savedStateHandle[KEY_PHOTO_URI] = null
        savedStateHandle[KEY_PHOTO_TOKEN] = null
        _stagedPhoto.value = StagedCreationPhotoState.None
        clearInFlightCapture()
    }

    private fun consumeStagedPhotoState() {
        savedStateHandle[KEY_PHOTO_URI] = null
        savedStateHandle[KEY_PHOTO_TOKEN] = null
        _stagedPhoto.value = StagedCreationPhotoState.None
    }

    fun createCameraCapture() = attachmentRepository.createTempCameraUri()

    fun openLocationMenu() {
        savedStateHandle[KEY_METHOD_SCREEN] = PropertyLocationMethod.NONE.name
        clearCandidate()
    }

    fun openAddressSearch() {
        savedStateHandle[KEY_METHOD_SCREEN] = PropertyLocationMethod.ADDRESS.name
    }

    fun openMapPicker() {
        savedStateHandle[KEY_METHOD_SCREEN] = PropertyLocationMethod.MAP.name
    }

    fun openManualEntry() {
        savedStateHandle[KEY_METHOD_SCREEN] = PropertyLocationMethod.MANUAL.name
    }

    fun setManualInputs(lat: String, lng: String) {
        savedStateHandle[KEY_MANUAL_LAT] = lat
        savedStateHandle[KEY_MANUAL_LNG] = lng
    }

    fun setPickerCamera(lat: Double, lng: Double, zoom: Double) {
        savedStateHandle[KEY_PICKER_LAT] = lat
        savedStateHandle[KEY_PICKER_LNG] = lng
        savedStateHandle[KEY_PICKER_ZOOM] = zoom
    }

    private var searchJob: Job? = null
    private var searchGeneration = 0L
    fun searchAddress(query: String) {
        savedStateHandle[KEY_ADDR_QUERY] = query
        if (query.isBlank()) {
            _addressResults.value = emptyList()
            _errorRes.value = null
            return
        }
        
        searchJob?.cancel()
        val generation = ++searchGeneration
        _isSearching.value = true
        _errorRes.value = null
        
        searchJob = viewModelScope.launch {
            try {
                val result = addressResolver.search(query)
                if (generation != searchGeneration) return@launch
                
                if (result is AddressSearchResult.Success) {
                    _addressResults.value = result.matches
                } else {
                    _errorRes.value = when(result) {
                        AddressSearchResult.NoMatches -> R.string.error_no_address_matches
                        AddressSearchResult.Unavailable -> R.string.error_geocoder_unavailable
                        AddressSearchResult.NetworkFailure -> R.string.error_network_failure
                        else -> R.string.error_generic_location
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (generation == searchGeneration) _errorRes.value = R.string.error_generic_location
            } finally {
                if (generation == searchGeneration) _isSearching.value = false
            }
        }
    }

    fun selectAddressCandidate(match: AddressLocationMatch) {
        setCandidate(PropertyLocationCandidate(PropertyLocationMethod.ADDRESS, match.latitude, match.longitude, null, match.displayAddress))
    }

    fun setPendingLocationPurpose(p: LocationRequestPurpose?) {
        savedStateHandle[KEY_PENDING_PURPOSE] = p
    }

    fun setLocationPermissionLaunchInProgress(inProgress: Boolean) {
        savedStateHandle[KEY_PERM_LAUNCH_IN_PROGRESS] = inProgress
    }

    fun clearPendingLocationRequest() {
        setPendingLocationPurpose(null)
        setLocationPermissionLaunchInProgress(false)
    }

    fun markLocationPermissionRequested() {
        savedStateHandle[KEY_PERM_REQ] = true
    }

    fun setGeneralError(resId: Int?) {
        _errorRes.value = resId
    }

    fun requestGpsLocation() {
        _isLocating.value = true
        _errorRes.value = null
        _locationIssue.value = null
        viewModelScope.launch {
            try {
                when (val result = locationProvider.getCurrentLocation()) {
                    is LocationResult.Success -> {
                        setCandidate(PropertyLocationCandidate(PropertyLocationMethod.GPS, result.latitude, result.longitude, result.accuracyMeters.toDouble(), "Current Location"))
                        clearPendingLocationRequest()
                    }
                    LocationResult.PermissionDenied -> {
                        _locationIssue.value = LocationIssue(LocationIssueType.PermissionDenied, R.string.location_issue_permission_denied, canRetry = true, purpose = LocationRequestPurpose.LocateOnly)
                    }
                    LocationResult.PermanentlyDenied -> {
                        _locationIssue.value = LocationIssue(LocationIssueType.PermissionPermanentlyDenied, R.string.location_issue_permission_permanently_denied, canOpenAppSettings = true, purpose = LocationRequestPurpose.LocateOnly)
                    }
                    LocationResult.ProviderDisabled -> {
                        _locationIssue.value = LocationIssue(LocationIssueType.ProviderDisabled, R.string.location_issue_provider_disabled, canOpenLocationSettings = true, purpose = LocationRequestPurpose.LocateOnly)
                    }
                    LocationResult.Timeout -> {
                        _locationIssue.value = LocationIssue(LocationIssueType.Timeout, R.string.location_issue_timeout, canRetry = true, purpose = LocationRequestPurpose.LocateOnly)
                    }
                    LocationResult.LocationUnavailable -> {
                        _locationIssue.value = LocationIssue(LocationIssueType.LocationUnavailable, R.string.location_issue_unavailable, canRetry = true, purpose = LocationRequestPurpose.LocateOnly)
                    }
                    is LocationResult.Error -> {
                        _errorRes.value = R.string.error_generic_location
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _errorRes.value = R.string.error_generic_location
            } finally {
                _isLocating.value = false
            }
        }
    }

    fun cancelLocationIssue() {
        _locationIssue.value = null
        clearPendingLocationRequest()
    }

    fun retryLocationIssue() {
        _locationIssue.value = null
        // Logical purpose is preserved by clearPendingLocationRequest() NOT being called.
        // Screen should call requestPropertyLocationWithPermission for PermissionDenied.
        // For others, requestGpsLocation is fine if permission is already granted.
        requestGpsLocation()
    }

    fun handlePermanentDenial() {
        _locationIssue.value = LocationIssue(LocationIssueType.PermissionPermanentlyDenied, R.string.location_issue_permission_permanently_denied, canOpenAppSettings = true, purpose = LocationRequestPurpose.LocateOnly)
        clearPendingLocationRequest()
    }

    fun handleTransientDenial() {
        _locationIssue.value = LocationIssue(LocationIssueType.PermissionDenied, R.string.location_issue_permission_denied, canRetry = true, purpose = LocationRequestPurpose.LocateOnly)
        clearPendingLocationRequest()
    }

    fun useLocationResultAnyway() {
        _locationIssue.value?.cachedLocation?.let { result ->
            setCandidate(PropertyLocationCandidate(PropertyLocationMethod.GPS, result.latitude, result.longitude, result.accuracyMeters.toDouble(), "Current Location"))
        }
        _locationIssue.value = null
    }

    fun setMapCandidate(lat: Double, lng: Double) {
        setCandidate(PropertyLocationCandidate(PropertyLocationMethod.MAP, lat, lng, null, "Pinned Location"))
    }

    fun validateAndSetManualCandidate() {
        val latInput = savedStateHandle.get<String>(KEY_MANUAL_LAT) ?: ""
        val lngInput = savedStateHandle.get<String>(KEY_MANUAL_LNG) ?: ""
        
        val lat = latInput.toDoubleOrNull()
        val lng = lngInput.toDoubleOrNull()
        if (lat == null || lat < -90.0 || lat > 90.0 || lng == null || lng < -180.0 || lng > 180.0) {
            _errorRes.value = R.string.error_invalid_number
            return
        }
        setCandidate(PropertyLocationCandidate(PropertyLocationMethod.MANUAL, lat, lng, null, "Manual Coordinates"))
    }

    private fun setCandidate(candidate: PropertyLocationCandidate) {
        savedStateHandle[KEY_CAND_METHOD] = candidate.method.name
        savedStateHandle[KEY_CAND_LAT] = candidate.latitude
        savedStateHandle[KEY_CAND_LNG] = candidate.longitude
        savedStateHandle[KEY_CAND_ACC] = candidate.accuracyMeters
        savedStateHandle[KEY_CAND_LABEL] = candidate.displayLabel
    }

    fun clearCandidate() {
        savedStateHandle[KEY_CAND_METHOD] = null
        savedStateHandle[KEY_CAND_LAT] = null
        savedStateHandle[KEY_CAND_LNG] = null
        savedStateHandle[KEY_CAND_ACC] = null
        savedStateHandle[KEY_CAND_LABEL] = null
    }

    fun confirmCandidate() {
        val cand = uiState.value.candidateLocation ?: return
        savedStateHandle[KEY_CONF_METHOD] = cand.method.name
        savedStateHandle[KEY_CONF_LAT] = cand.latitude
        savedStateHandle[KEY_CONF_LNG] = cand.longitude
        savedStateHandle[KEY_CONF_ACC] = cand.accuracyMeters
        savedStateHandle[KEY_CONF_LABEL] = cand.displayLabel
        savedStateHandle[KEY_DEFERRED] = false
        clearCandidate()
        savedStateHandle[KEY_STEP] = SetupStep.REVIEW.name
    }

    fun deferLocation() {
        if (uiState.value.target is PropertySetupTarget.Existing) return
        
        savedStateHandle[KEY_CONF_METHOD] = null
        savedStateHandle[KEY_CONF_LAT] = null
        savedStateHandle[KEY_CONF_LNG] = null
        savedStateHandle[KEY_CONF_ACC] = null
        savedStateHandle[KEY_CONF_LABEL] = null
        savedStateHandle[KEY_DEFERRED] = true
        savedStateHandle[KEY_STEP] = SetupStep.REVIEW.name
    }

    fun handleCameraResult(success: Boolean) {
        val uriStr = getInFlightUri()
        val token = getInFlightToken()
        if (uriStr == null || token == null) return

        _stagedPhoto.value = StagedCreationPhotoState.Loading
        viewModelScope.launch {
            val uri = android.net.Uri.parse(uriStr)
            val inspection = attachmentRepository.inspectTempCameraCapture(token, uri)
            
            if (com.jumastappworks.mapstead.BuildConfig.DEBUG) {
                android.util.Log.d("AddPropertyVM", "Camera result DIAGNOSTIC: success=$success, token=$token, inspection=$inspection")
            }
            
            if (inspection is TempCameraCaptureInspectionResult.Ready) {
                setStagedPhoto(uriStr, token)
            } else {
                if (com.jumastappworks.mapstead.BuildConfig.DEBUG) {
                    android.util.Log.w("AddPropertyVM", "Camera capture validation failed: $inspection for URI: $uriStr")
                }
                if (success || inspection !is TempCameraCaptureInspectionResult.Missing) {
                    _stagedPhoto.value = StagedCreationPhotoState.Failed(R.string.error_file_copy_failed)
                } else {
                    _stagedPhoto.value = StagedCreationPhotoState.None
                }
                attachmentRepository.deleteTempCameraCapture(token)
            }
            clearInFlightCapture()
        }
    }

    fun createProperty() {
        val state = uiState.value
        if (state.isSaving) return

        if (state.target is PropertySetupTarget.Existing && (state.confirmedLocation == null || !state.existingPropertyLoaded)) {
            _errorRes.value = R.string.error_select_location
            return
        }

        _isSaving.value = true
        _errorRes.value = null
        
        viewModelScope.launch {
            try {
                val targetId = state.target.id
                
                when (state.target) {
                    is PropertySetupTarget.New -> {
                        val property = PropertyEntity(
                            id = targetId,
                            name = state.propertyName.trim(),
                            propertyType = state.propertyType ?: "Property",
                            latitude = state.confirmedLocation?.latitude,
                            longitude = state.confirmedLocation?.longitude
                        )
                        propertyRepository.insertPropertyWithDefaultMap(property, context.getString(R.string.setup_property_map_name))
                    }
                    is PropertySetupTarget.Existing -> {
                        val confirmed = state.confirmedLocation 
                            ?: throw IllegalStateException("Coordinates required to save existing property location")
                        
                        propertyRepository.updatePropertyLocationWithOptionalFirstMap(
                            targetId, confirmed.latitude, confirmed.longitude, createFirstMap = true
                        ).getOrThrow()
                    }
                }

                var photoSuccess = true
                try {
                    val currentPhoto = _stagedPhoto.value
                    if (currentPhoto is StagedCreationPhotoState.Ready) {
                        val uri = android.net.Uri.parse(currentPhoto.uri)
                        val owner = AttachmentOwner.Property(targetId)
                        val result = attachmentRepository.importAttachment(
                            owner = owner,
                            uri = uri,
                            type = AttachmentType.Photo,
                            customDisplayName = "Property Photo",
                            caption = null,
                            cameraCaptureToken = currentPhoto.cameraCaptureToken
                        )
                        if (result !is AttachmentWriteResult.Success) {
                            photoSuccess = false
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    android.util.Log.e("AddPropertyVM", "Photo import failed", e)
                    photoSuccess = false
                }

                if (photoSuccess) {
                    consumeStagedPhotoState()
                    _outcome.value = PropertySetupOutcome.PropertyCreated(targetId)
                } else {
                    _outcome.value = PropertySetupOutcome.PropertyCreatedWithPhotoWarning(targetId)
                    _errorRes.value = R.string.property_created_with_photo_error
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _errorRes.value = R.string.error_save_failed
                _outcome.value = PropertySetupOutcome.PropertyCreationFailed
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun retryPropertyPhoto(propertyId: UUID) {
        val state = uiState.value
        val currentPhoto = state.stagedPhoto
        if (currentPhoto !is StagedCreationPhotoState.Ready) return
        if (state.isSaving) return
        
        _isSaving.value = true
        _errorRes.value = null
        
        viewModelScope.launch {
            try {
                val uri = android.net.Uri.parse(currentPhoto.uri)
                val owner = AttachmentOwner.Property(propertyId)
                val result = attachmentRepository.importAttachment(
                    owner = owner,
                    uri = uri,
                    type = AttachmentType.Photo,
                    customDisplayName = "Property Photo",
                    caption = null,
                    cameraCaptureToken = currentPhoto.cameraCaptureToken
                )
                if (result is AttachmentWriteResult.Success) {
                    consumeStagedPhotoState()
                    _outcome.value = PropertySetupOutcome.PropertyCreated(propertyId)
                } else {
                    _errorRes.value = R.string.property_created_with_photo_error
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _errorRes.value = R.string.property_created_with_photo_error
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun continueWithoutPhoto(propertyId: UUID) {
        clearStagedPhoto()
        _outcome.value = PropertySetupOutcome.PropertyCreated(propertyId)
    }

    fun clearOutcome() {
        _outcome.value = null
    }

    fun isDirty(): Boolean {
        val s = uiState.value
        return s.propertyName.isNotBlank() || s.confirmedLocation != null || s.isLocationDeferred
    }
}
