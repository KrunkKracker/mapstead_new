package com.jumastappworks.mapstead.ui.mapping

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.attachments.*
import com.jumastappworks.mapstead.data.backup.TemporaryCameraCapture
import com.jumastappworks.mapstead.data.db.entities.*
import com.jumastappworks.mapstead.data.repository.*
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.data.prefs.UserPreferences
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import com.jumastappworks.mapstead.util.GeometryUtils
import com.jumastappworks.mapstead.util.PolygonParseResult
import com.jumastappworks.mapstead.util.PolygonValidationReason
import com.jumastappworks.mapstead.util.PolygonValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import androidx.compose.material3.ExperimentalMaterial3Api
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlinx.serialization.json.*

private fun LineEditState.pushUndo(): LineEditState {
    val newStack = undoStack.toMutableList()
    newStack.add(workingVertices)
    if (newStack.size > 50) newStack.removeAt(0)
    return this.copy(undoStack = newStack)
}

private fun LineEditState.popUndo(): LineEditState {
    if (undoStack.isEmpty()) return this
    val newStack = undoStack.toMutableList()
    val prev = newStack.removeAt(newStack.size - 1)
    return this.copy(workingVertices = prev, undoStack = newStack, selectedVertexIndex = null)
}

private fun PolygonEditState.pushUndo(): PolygonEditState {
    val newStack = undoStack.toMutableList()
    newStack.add(workingVertices)
    if (newStack.size > 50) newStack.removeAt(0)
    return this.copy(undoStack = newStack)
}

private fun PolygonEditState.popUndo(): PolygonEditState {
    if (undoStack.isEmpty()) return this
    val newStack = undoStack.toMutableList()
    val prev = newStack.removeAt(newStack.size - 1)
    return this.copy(workingVertices = prev, undoStack = newStack, selectedVertexIndex = null).withValidation()
}

private fun PolygonEditState.withValidation(): PolygonEditState {
    return this.copy(
        validation = GeometryUtils.validatePolygonGeometry(workingVertices),
        workingAreaMeters = GeometryUtils.calculateSphericalArea(workingVertices),
        workingPerimeterMeters = GeometryUtils.calculatePolygonPerimeter(workingVertices)
    )
}

// Internal batch states for type-safe aggregation
private data class CoreSelectionBatch(
    val propertyId: UUID?,
    val propertyName: String?,
    val propertyData: PropertyEntity?,
    val plan: PlanEntity?,
    val openingToken: String?
)

private data class CoreLayerBatch(
    val layers: List<LayerEntity>,
    val activeLayerId: UUID?
)

private data class CoreFeatureBatch(
    val features: List<MapFeatureEntity>,
    val visibleFeatures: List<MapFeatureEntity>,
    val selectedFeature: MapFeatureEntity?
)

private data class CoreStatusBatch(
    val cameraFocus: MapCameraFocus?,
    val editingMode: MapEditingMode,
    val mapLoading: Boolean,
    val mapErrorRes: Int?,
    val layerPanelOpen: Boolean,
    val mapRecoveryActive: Boolean
)

private data class Triple3<A, B, C>(val a: A, val b: B, val c: C)
private data class Triple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

private data class BasemapStatusBatch(
    val preferredBasemapId: BasemapId,
    val requestedSourceId: BasemapSourceId?,
    val activeSourceId: BasemapSourceId?,
    val currentAttempt: BasemapLoadAttempt?,
    val renderSessionId: UUID?,
    val basemapStatus: BasemapLoadStatus,
    val basemapGeneration: Long,
    val basemapErrorRes: Int?,
    val isUsingFallback: Boolean,
    val retryPrimaryAvailable: Boolean,
    val showBackupChooser: Boolean,
    val cameraInteractionSequence: Long,
    val acceptedStyleEvent: AcceptedBasemapStyleEvent?
)

private data class LocationStatusBatch(
    val location: LocationResult.Success?,
    val isLocating: Boolean,
    val issue: LocationIssue?,
    val purpose: LocationRequestPurpose?,
    val rationale: Boolean,
    val showDetails: Boolean,
    val requestedOnce: Boolean
)

private data class EditorStatusBatch(
    val open: Boolean,
    val target: FeatureEditorTarget?,
    val feature: MapFeatureEntity?,
    val isSaving: Boolean,
    val isDeleting: Boolean,
    val operationErrorRes: Int?
)

private data class EditorGeometryBatch(
    val draftVertices: List<Pair<Double, Double>>,
    val polygonDraft: PolygonDraftState?,
    val validationReason: PolygonValidationReason?,
    val isPolygonDirty: Boolean,
    val isNewUnsaved: Boolean
)

private data class EditorEditBatch(
    val isPointMoveActive: Boolean,
    val pointMoveState: PointMoveState?,
    val lineEditState: LineEditState?,
    val polygonEditState: PolygonEditState?,
    val isLineEditDirty: Boolean
)

private data class EditorUiBatch(
    val showDiscardDialog: Boolean,
    val discardAction: PendingEditDiscardAction?,
    val labelError: Int?,
    val accuracyError: Int?,
    val linkSelection: SystemItemLinkSelection,
    val initialLinkSelection: SystemItemLinkSelection
)

private data class EditorPhotoBatch(
    val systemItemDraft: PendingSystemItemDraft?,
    val stagedPhoto: StagedCreationPhotoState,
    val newPointDraft: NewPointDraftState?,
    val pendingPhotoPurpose: PendingPhotoPurpose?
)

private data class WorkflowStatusBatch(
    val showStarterLayersDialog: Boolean,
    val showBoundaryAcknowledgment: Boolean,
    val pendingGuidedPreset: GuidedMapPreset?,
    val guidedSession: GuidedMappingSession?,
    val showGuidedAddMenu: Boolean
)

private data class WorkflowPrefBatch(
    val guidanceDismissed: Boolean,
    val starterLayersCreated: Boolean,
    val starterLayersEligible: Boolean,
    val starterLayerOperation: StarterLayerOperation,
    val starterLayerErrorRes: Int?,
    val isBoundaryAcknowledgmentSaving: Boolean,
    val boundaryAcknowledgmentErrorRes: Int?
)

private data class WorkflowConfigBatch(
    val showPlacementMethod: Boolean,
    val showBasemapChooser: Boolean,
    val showHelpSheet: Boolean,
    val showSafetyLimitations: Boolean,
    val addToMapAvailability: AddToMapAvailability,
    val measurementSystem: com.jumastappworks.mapstead.data.prefs.MeasurementSystem,
    val userPreferences: UserPreferences
)

private data class SearchStatusBatch(
    val query: String,
    val results: List<MapSearchResult>,
    val searchActive: Boolean
)

private data class SearchInputBatch(
    val query: String,
    val propertyId: UUID?,
    val planId: UUID?,
    val features: List<MapFeatureEntity>,
    val layers: List<LayerEntity>
)

private data class MapAggregationPart1(
    val selection: CoreSelectionBatch,
    val layers: CoreLayerBatch,
    val features: CoreFeatureBatch,
    val status: CoreStatusBatch,
    val basemap: BasemapStatusBatch
)

private data class MapAggregationPart2(
    val location: LocationStatusBatch,
    val editorStatus: EditorStatusBatch,
    val editorGeom: EditorGeometryBatch,
    val editorEdit: EditorEditBatch,
    val editorUi: EditorUiBatch
)

private data class Part3A(val ep: EditorPhotoBatch, val ws: WorkflowStatusBatch, val wp: WorkflowPrefBatch)
private data class Part3B(val wc: WorkflowConfigBatch, val sr: SearchStatusBatch, val so: GuidedSaveOutcome?)

private data class MapAggregationPart3(
    val editorPhoto: EditorPhotoBatch,
    val workflowStatus: WorkflowStatusBatch,
    val workflowPref: WorkflowPrefBatch,
    val workflowConfig: WorkflowConfigBatch,
    val search: SearchStatusBatch,
    val saveOutcome: GuidedSaveOutcome?
)

private fun evaluateAddToMapAvailability(
    propertyId: UUID?,
    plan: PlanEntity?,
    layers: List<LayerEntity>,
    activeLayerId: UUID?,
    featureEditorTarget: FeatureEditorTarget?,
    isSearchActive: Boolean,
    guidedSession: GuidedMappingSession?,
    editingMode: MapEditingMode,
    pointMoveState: PointMoveState?,
    isSavingFeature: Boolean,
    isDeletingFeature: Boolean,
    locationIssue: LocationIssue?,
    isLocatingPhone: Boolean,
    pendingLocationPurpose: LocationRequestPurpose?,
    starterLayerOperation: StarterLayerOperation,
    isBoundaryAcknowledgmentSaving: Boolean,
    showDiscardEditDialog: Boolean,
    showBasemapChooser: Boolean,
    showHelpSheet: Boolean,
    showSafetyLimitations: Boolean,
    showLocationDetails: Boolean,
    layerPanelOpen: Boolean
): AddToMapAvailability {
    if (propertyId == null || plan == null) return AddToMapAvailability(false, R.string.property_load_failed)
    if (layers.isEmpty()) return AddToMapAvailability(false, R.string.no_active_layer)
    if (activeLayerId == null) return AddToMapAvailability(false, R.string.no_active_layer)
    
    val isExclusiveWorkflowActive = featureEditorTarget != null || guidedSession != null || 
            editingMode != MapEditingMode.Select || pointMoveState != null || 
            isSavingFeature || isDeletingFeature || locationIssue != null || 
            isLocatingPhone || pendingLocationPurpose != null || 
            starterLayerOperation != StarterLayerOperation.Idle || 
            isBoundaryAcknowledgmentSaving || showDiscardEditDialog || 
            showBasemapChooser || showHelpSheet || showSafetyLimitations || 
            showLocationDetails || layerPanelOpen || isSearchActive
            
    if (isExclusiveWorkflowActive) return AddToMapAvailability(false, R.string.exclusive_workflow_active)
    
    return AddToMapAvailability(true)
}

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalMaterial3Api::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    private val mapRepository: MapRepository,
    private val attachmentRepository: AttachmentRepository,
    private val infrastructureRepository: InfrastructureRepository,
    private val propertyRepository: PropertyRepository,
    private val mapFeatureContextResolver: MapFeatureContextResolver,
    private val locationProvider: CurrentLocationProvider,
    private val basemapProvider: BasemapProvider,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val featureNamingService: FeatureNamingService,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object { 
        private const val KEY_PENDING_PURPOSE = "pending_location_purpose"
        private const val KEY_GUIDED_PHOTO_URI = "guided_staged_photo_uri"
        private const val KEY_GUIDED_PHOTO_TOKEN = "guided_staged_photo_token"
        private const val KEY_SHOW_RATIONALE = "show_permission_rationale"
        private const val KEY_HAS_REQUESTED_ONCE = "has_requested_once" 

        private const val KEY_GUIDED_SESSION_ID = "guided_session_id"
        private const val KEY_GUIDED_PRESET_ID = "guided_preset_id"
        private const val KEY_GUIDED_DRAFT_ID = "guided_draft_id"
        private const val KEY_GUIDED_ITEM_ID = "guided_item_id"
        private const val KEY_GUIDED_PHASE = "guided_phase"
        private const val KEY_GUIDED_NAME = "guided_name"
        private const val KEY_GUIDED_TRACKING = "guided_tracking"
        private const val KEY_PENDING_PHOTO_PURPOSE = "pending_photo_purpose"
        private const val KEY_PENDING_PHOTO_FEATURE_ID = "pending_photo_feature_id"
        private const val KEY_IN_FLIGHT_URI = "in_flight_photo_uri"
        private const val KEY_IN_FLIGHT_TOKEN = "in_flight_photo_token"
        
        fun resolveSuggestions(session: GuidedMappingSession?, target: FeatureEditorTarget?, propertyId: UUID?, planId: UUID?): GuidedMapPreset? {
            if (session == null || target == null || propertyId == null || planId == null) return null
            if (session.propertyId != propertyId || session.planId != planId) return null
            if (session.phase != GuidedMappingPhase.REVIEWING) return null
            
            val matches = when (target) {
                is FeatureEditorTarget.NewPoint -> session.expectedGeometry == GuidedMapGeometry.LOCATION && session.targetDraftId == target.draftId
                is FeatureEditorTarget.NewLine -> session.expectedGeometry == GuidedMapGeometry.ROUTE && session.targetDraftId == target.draftId
                is FeatureEditorTarget.NewPolygon -> session.expectedGeometry == GuidedMapGeometry.AREA && session.targetDraftId == target.draftId
                else -> false
            }
            
            return if (matches) session.preset else null
        }

        fun canStartGuidedWorkflow(sessionId: UUID, session: GuidedMappingSession?, expectedGeometry: GuidedMapGeometry, currentPropertyId: UUID?, currentPlanId: UUID?, hasBlockingWorkflow: () -> Boolean, hasActiveLayer: Boolean): Boolean {
            if (session == null || session.sessionId != sessionId) return false
            if (session.propertyId != currentPropertyId || session.planId != currentPlanId) return false
            if (session.expectedGeometry != expectedGeometry) return false
            if (hasBlockingWorkflow()) return false
            if (!hasActiveLayer) return false
            return true
        }
    }
    
    private val _propertyId = MutableStateFlow<UUID?>(null)
    private val _planId = MutableStateFlow<UUID?>(null)
    private val _manualActiveLayerId = MutableStateFlow<UUID?>(null)
    private val _selectedPersistedFeature = MutableStateFlow<MapFeatureEntity?>(null)
    private val _editingMode = MutableStateFlow(MapEditingMode.Select)
    private val _mapLoading = MutableStateFlow(false)
    private val _mapRecoveryActive = MutableStateFlow(false)
    private val _mapErrorRes = MutableStateFlow<Int?>(null)
    private val _layerPanelOpen = MutableStateFlow(false)
    private val _featureEditorOpen = MutableStateFlow(false)
    private val _isSavingFeature = MutableStateFlow(false)
    private val _isDeletingFeature = MutableStateFlow(false)
    private val _featureOperationErrorRes = MutableStateFlow<Int?>(null)
    private val _draftVertices = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    
    // Basemap State Machine
    private val _preferredBasemapId = MutableStateFlow<BasemapId>(BasemapId.STREETS)
    private var customerBasemapPreferenceOverride: BasemapId? = null
    private var lastObservedRepositoryBasemapId: BasemapId? = null
    private val _requestedSourceId = MutableStateFlow<BasemapSourceId?>(null)
    private val _activeSourceId = MutableStateFlow<BasemapSourceId?>(null)
    private val _currentAttempt = MutableStateFlow<BasemapLoadAttempt?>(null)
    private val _renderSessionId = MutableStateFlow<UUID?>(null)
    private val _basemapStatus = MutableStateFlow(BasemapLoadStatus.IDLE)
    private val _basemapGeneration = MutableStateFlow(0L)
    private val _pendingBasemapRequest = MutableStateFlow<PendingBasemapRequest?>(null)
    private val _basemapErrorRes = MutableStateFlow<Int?>(null)
    private val _isUsingFallback = MutableStateFlow(false)
    private val _fallbackAttempted = MutableStateFlow(false)
    private val _showBackupChooser = MutableStateFlow(false)
    private val _retryPrimaryAvailable = MutableStateFlow(false)
    private val _acceptedStyleEvent = MutableStateFlow<AcceptedBasemapStyleEvent?>(null)
    
    // Interaction state
    private val _cameraInteractionSequence = MutableStateFlow(0L)
    private var nextAttemptId = 1L
    private var nextEventId = 1L
    
    private val terminalAttempts = mutableMapOf<BasemapAttemptKey, BasemapTerminalReason>()
    private val repairEpochs = mutableMapOf<BasemapRepairKey, BasemapRepairEpochState>()
    private val cameraSnapshots = mutableMapOf<BasemapAttemptKey, CameraSnapshot>()
    
    private val _currentPhoneLocation = MutableStateFlow<LocationResult.Success?>(null)
    private val _isLocatingPhone = MutableStateFlow(false)
    private val _locationIssue = MutableStateFlow<LocationIssue?>(null)
    private val _lineEditState = MutableStateFlow<LineEditState?>(null)
    private val _polygonEditState = MutableStateFlow<PolygonEditState?>(null)
    private val _discardAction = MutableStateFlow<PendingEditDiscardAction?>(null)
    private val _showDiscardEditDialog = MutableStateFlow(false)
    private val _polygonDraft = MutableStateFlow<PolygonDraftState?>(null)
    private val _newPointDraft = MutableStateFlow<NewPointDraftState?>(null)
    private val _featureEditorTarget = MutableStateFlow<FeatureEditorTarget?>(null)
    private val _pointMoveState = MutableStateFlow<PointMoveState?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _isSearchActive = MutableStateFlow(false)
    private val _openingToken = MutableStateFlow<String?>(null)
    private val _cameraFocus = MutableStateFlow<MapCameraFocus?>(null)
    private val _showLocationDetails = MutableStateFlow(false)
    private val _showStarterLayersDialog = MutableStateFlow(false)
    private val _showBoundaryAcknowledgment = MutableStateFlow(false)
    private val _showBasemapChooser = MutableStateFlow(false)
    private val _showHelpSheet = MutableStateFlow(false)
    private val _showSafetyLimitations = MutableStateFlow(false)
    private val _showGuidedAddMenu = MutableStateFlow(false)
    private val _showPlacementMethod = MutableStateFlow(false)
    private val _starterLayersCreated = MutableStateFlow(false)
    private val _starterLayerOperation = MutableStateFlow<StarterLayerOperation>(StarterLayerOperation.Idle)
    private val _starterLayerErrorRes = MutableStateFlow<Int?>(null)
    private val _starterPromptPresentedForPlanId = MutableStateFlow<Set<UUID>>(emptySet())
    private val _isBoundaryAcknowledgmentSaving = MutableStateFlow(false)
    private val _boundaryAcknowledgmentErrorRes = MutableStateFlow<Int?>(null)
    private val _pendingGuidedPreset = MutableStateFlow<GuidedMapPreset?>(null)
    private val _polygonValidationReason = MutableStateFlow<PolygonValidationReason?>(null)
    private val _isPolygonEditDirty = MutableStateFlow(false)
    private val _isNewUnsavedFeature = MutableStateFlow(false)
    private val _isPointMoveActive = MutableStateFlow(false)
    private val _featureEditorFeature = MutableStateFlow<MapFeatureEntity?>(null)
    private val _isLineEditDirty = MutableStateFlow(false)
    private val _saveOutcome = MutableStateFlow<GuidedSaveOutcome?>(null)

    private val _pendingPhotoPurposeStr = savedStateHandle.getStateFlow<String?>(KEY_PENDING_PHOTO_PURPOSE, null)
    private val _pendingPhotoFeatureIdStr = savedStateHandle.getStateFlow<String?>(KEY_PENDING_PHOTO_FEATURE_ID, null)

    private val _cameraPersistenceState = MutableStateFlow(CameraPersistenceState.WAITING_FOR_INITIAL_FOCUS)
    private var _lastResolutionSource: CameraSource? = null

    internal val _linkEditorSession = MutableStateFlow<FeatureLinkEditorSession?>(null)

    private val _measurementSystem = userPreferencesRepository.userPreferencesFlow.map { it.measurementSystem }.stateIn(viewModelScope, SharingStarted.Eagerly, com.jumastappworks.mapstead.data.prefs.MeasurementSystem.IMPERIAL)
    private val _guidanceDismissed = combine(userPreferencesRepository.userPreferencesFlow, _propertyId) { prefs, pid ->
        if (pid == null) false else prefs.guidanceDismissedPropertyIds.contains(pid.toString())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val _boundaryAcknowledged = userPreferencesRepository.userPreferencesFlow.map { it.boundaryDisclaimerAcknowledged }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val _starterLayersCompleted = userPreferencesRepository.userPreferencesFlow.map { it.starterLayersCompletedPlanIds }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _labelError = MutableStateFlow<Int?>(null)
    private val _accuracyError = MutableStateFlow<Int?>(null)
    private val _stagedPhoto = MutableStateFlow<StagedCreationPhotoState>(StagedCreationPhotoState.None)

    private val _pendingLocationPurpose = MutableStateFlow<LocationRequestPurpose?>(savedStateHandle[KEY_PENDING_PURPOSE])
    private val _showPermissionRationale = savedStateHandle.getStateFlow(KEY_SHOW_RATIONALE, false)
    private val _hasRequestedLocOnceFlow = savedStateHandle.getStateFlow(KEY_HAS_REQUESTED_ONCE, false)

    private val _layers = _planId.flatMapLatest { if (it == null) flowOf(emptyList()) else mapRepository.getLayersForPlan(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _activeLayerId = combine(_propertyId, _planId, _layers, _manualActiveLayerId) { p, pl, l, m -> resolveValidActiveLayer(p, pl, l, m) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _propertyData = _propertyId.flatMapLatest { id -> 
        if (id == null) flowOf<PropertyEntity?>(null) 
        else propertyRepository.getAllProperties().map { list -> list.find { it.id == id } } 
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _propertyName = _propertyData.map { it?.name }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _plan = combine(_propertyId, _planId) { p, pid -> p to pid }
        .flatMapLatest { (p, pid) -> 
            if (p == null || pid == null) flowOf(null) 
            else mapRepository.getPlansForProperty(p).map { list -> list.find { it.id == pid } } 
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _guidedSession = MutableStateFlow<GuidedMappingSession?>(null)

    private val _cameraPersistenceRequests = kotlinx.coroutines.channels.Channel<CameraPersistenceRequest>(kotlinx.coroutines.channels.Channel.CONFLATED)
    private var lastPersistedLat: Double? = null
    private var lastPersistedLng: Double? = null
    private var lastPersistedZoom: Float? = null
    private var lastPersistedBearing: Double? = null
    private var lastPersistedPlanId: UUID? = null
    
    val programmaticCameraController = ProgrammaticCameraController()
    private var isRenderSessionReady = false
    private var isPreferencesReady = false
    private var repairAttemptedInGeneration = -1L

    private val saveGate = Mutex()

    init {
        val sid: String? = savedStateHandle.get<String>(KEY_GUIDED_SESSION_ID)
        if (!sid.isNullOrBlank()) {
            cancelGuidedCreation()
            _mapErrorRes.value = R.string.unfinished_item_not_saved
        }

        viewModelScope.launch {
            for (req in _cameraPersistenceRequests) {
                if (_planId.value != req.planId) continue
                try {
                    mapRepository.updatePlanCamera(req.planId, req.latitude, req.longitude, req.zoom.toDouble(), req.bearing)
                    lastPersistedLat = req.latitude
                    lastPersistedLng = req.longitude
                    lastPersistedZoom = req.zoom
                    lastPersistedBearing = req.bearing
                    lastPersistedPlanId = req.planId
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    lastPersistedPlanId = null
                }
            }
        }
        
        viewModelScope.launch {
            userPreferencesRepository.userPreferencesFlow
                .map { it.selectedBasemapId }
                .distinctUntilChanged()
                .collect { id ->
                    isPreferencesReady = true
                    val prevLastObserved = lastObservedRepositoryBasemapId
                    lastObservedRepositoryBasemapId = id
                    
                    val override = customerBasemapPreferenceOverride
                    if (override != null) {
                        if (id == override) {
                            customerBasemapPreferenceOverride = null
                        }
                        // Phase 2.2h5R9C: Confirm or filter stale repo emissions
                        return@collect
                    }

                    val pending = _pendingBasemapRequest.value
                    if (pending != null && pending.preferredBasemapId == id) return@collect

                    val previous = _preferredBasemapId.value
                    _preferredBasemapId.value = id
                    
                    if (isRenderSessionReady) {
                        if (id != previous) {
                            startPrimaryLoad(id)
                        } else {
                            ensureInitialBasemapLoad()
                        }
                    }
                }
        }
        
        // Restore staged photo if it was Ready in SavedStateHandle
        val photoUri = savedStateHandle.get<String>(KEY_GUIDED_PHOTO_URI)
        val photoToken = savedStateHandle.get<String>(KEY_GUIDED_PHOTO_TOKEN)
        if (photoUri != null) {
            _stagedPhoto.value = StagedCreationPhotoState.Ready(photoUri, photoToken)
        }
    }

    private val _features: StateFlow<List<MapFeatureEntity>> = _layers.flatMapLatest { list -> 
        if (list.isEmpty()) flowOf(emptyList()) else { 
            val flows = list.map { mapRepository.getFeaturesForLayer(it.id) }
            combine(flows) { arrays -> arrays.toList().flatten() }
        } 
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val systemItems: StateFlow<List<InfrastructureItemEntity>> = _propertyId.flatMapLatest { 
        if (it == null) flowOf(emptyList()) 
        else infrastructureRepository.getItemsForProperty(it) 
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val infrastructureItems: StateFlow<List<InfrastructureItemEntity>> = systemItems

    private val _visibleFeatures = combine(_features, _layers) { features, layers ->
        val visibleLayerIds = layers.filter { it.isVisible }.map { it.id }.toSet()
        features.filter { it.layerId in visibleLayerIds }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _searchResults = combine(_searchQuery, _propertyId, _planId, _features, _layers) { q, p, pl, f, l ->
        SearchInputBatch(q, p, pl, f, l)
    }.combine(systemItems) { input: SearchInputBatch, items ->
        if (input.propertyId == null || input.planId == null) emptyList() 
        else MapSearchEngine.filterAndRank(input.query, input.propertyId, input.planId, input.features, input.layers, items)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _starterLayersEligibleFlow = combine(_layers, _starterLayersCompleted, _starterPromptPresentedForPlanId) { layers, completed, presented ->
        val planId = _planId.value
        if (planId == null || completed.contains(planId.toString()) || presented.contains(planId) || layers.isEmpty()) false 
        else !layers.any { layer -> layer.category == "Utility" || layer.category == "Structure" || layer.category == "Landscape" || layer.category == "Safety" }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Batch combinations (Logical groups)
    private val _coreSelectionFlow = combine(_propertyId, _propertyName, _propertyData, _plan, _openingToken) { p, name, data, plan, token ->
        CoreSelectionBatch(p, name, data, plan, token)
    }

    private val _coreLayerFlow = combine(_layers, _activeLayerId) { l, alid ->
        CoreLayerBatch(l, alid)
    }

    private val _coreFeatureFlow = combine(_features, _visibleFeatures, _selectedPersistedFeature) { f, vf, sf ->
        CoreFeatureBatch(f, vf, sf)
    }

    private val _coreStatusFlow = combine(
        combine(_cameraFocus, _editingMode, _mapLoading, _mapErrorRes, _layerPanelOpen) { f, m, l, e, lp ->
            Triple5(f, m, l, e, lp)
        },
        _mapRecoveryActive
    ) { p1, rec ->
        CoreStatusBatch(p1.a, p1.b, p1.c, p1.d, p1.e, rec)
    }

    private val _basemapStatusFlow = combine(
        combine(_preferredBasemapId, _requestedSourceId, _activeSourceId, _currentAttempt) { p, r, a, att ->
            Quad(p, r, a, att)
        },
        combine(_renderSessionId, _basemapStatus, _basemapGeneration, _basemapErrorRes) { rsi: UUID?, s, g, e ->
            Quad(rsi, s, g, e)
        },
        combine(_isUsingFallback, _retryPrimaryAvailable, _showBackupChooser, _cameraInteractionSequence) { f, r, c, cis ->
            Quad(f, r, c, cis)
        },
        _acceptedStyleEvent
    ) { p1, p2, p3, ase ->
        BasemapStatusBatch(
            preferredBasemapId = p1.a,
            requestedSourceId = p1.b,
            activeSourceId = p1.c,
            currentAttempt = p1.d,
            renderSessionId = p2.a,
            basemapStatus = p2.b,
            basemapGeneration = p2.c,
            basemapErrorRes = p2.d,
            isUsingFallback = p3.a,
            retryPrimaryAvailable = p3.b,
            showBackupChooser = p3.c,
            cameraInteractionSequence = p3.d,
            acceptedStyleEvent = ase
        )
    }

    private val _locationBatchFlow = combine(_currentPhoneLocation, _isLocatingPhone, _locationIssue, _pendingLocationPurpose, _showPermissionRationale) { l, lp, i, p, r ->
        LocationStatusBatch(l, lp, i, p, r, _showLocationDetails.value, _hasRequestedLocOnceFlow.value)
    }

    private val _editorStatusFlow = combine(
        combine(_featureEditorOpen, _featureEditorTarget, _featureEditorFeature, _isSavingFeature) { o, t, f, s ->
            Quad(o, t, f, s)
        },
        combine(_isDeletingFeature, _featureOperationErrorRes) { d, er ->
            d to er
        }
    ) { p1: Quad<Boolean, FeatureEditorTarget?, MapFeatureEntity?, Boolean>, p2: Pair<Boolean, Int?> ->
        EditorStatusBatch(p1.a, p1.b, p1.c, p1.d, p2.first, p2.second)
    }

    private val _editorGeomFlow = combine(_draftVertices, _polygonDraft, _polygonValidationReason, _isPolygonEditDirty, _isNewUnsavedFeature) { v, pd, vr, pdy, nu ->
        EditorGeometryBatch(v, pd, vr, pdy, nu)
    }

    private val _editorEditFlow = combine(_isPointMoveActive, _pointMoveState, _lineEditState, _polygonEditState, _isLineEditDirty) { pa, pms, les, pes, led ->
        EditorEditBatch(pa, pms, les, pes, led)
    }

    private val _editorUiFlow = combine(_showDiscardEditDialog, _discardAction, _labelError, _accuracyError, _linkEditorSession) { sd, da, le, ae, ls ->
        EditorUiBatch(sd, da, le, ae, ls?.currentSelection ?: SystemItemLinkSelection.None, ls?.initialSelection ?: SystemItemLinkSelection.None)
    }

    private val _editorPhotoFlow = combine(_linkEditorSession, _stagedPhoto, _newPointDraft, _pendingPhotoPurposeStr, _pendingPhotoFeatureIdStr) { ls, sp, npd, pps, pfid ->
        val purpose = pps?.let { p ->
            val fid = pfid?.let { UUID.fromString(it) }
            if (p == "SAVED") fid?.let { PendingPhotoPurpose.SavedFeatureAttachment(it) }
            else if (p == "GUIDED") fid?.let { PendingPhotoPurpose.GuidedFeatureCreation(it) }
            else null
        }
        EditorPhotoBatch(ls?.pendingDraft, sp, npd, purpose)
    }

    private val _workflowStatusFlow = combine(_showStarterLayersDialog, _showBoundaryAcknowledgment, _pendingGuidedPreset, _guidedSession, _showGuidedAddMenu) { sld, ba, pgp, gs, gam ->
        WorkflowStatusBatch(sld, ba, pgp, gs, gam)
    }

    private val _workflowPrefFlow = combine(
        combine(_guidanceDismissed, _starterLayersCreated, _starterLayersEligibleFlow, _starterLayerOperation) { gd, slc, sle, slo ->
            Quad(gd, slc, sle, slo)
        },
        combine(_starterLayerErrorRes, _isBoundaryAcknowledgmentSaving, _boundaryAcknowledgmentErrorRes) { sle, bas, bae ->
            Triple3(sle, bas, bae)
        }
    ) { p1: Quad<Boolean, Boolean, Boolean, StarterLayerOperation>, p2: Triple3<Int?, Boolean, Int?> ->
        WorkflowPrefBatch(p1.a, p1.b, p1.c, p1.d, p2.a, p2.b, p2.c)
    }

    private val _workflowConfigFlow = combine(
        combine(
            _propertyId, _plan, _layers, _activeLayerId, _featureEditorTarget
        ) { p, pl, l, al, t ->
            Triple5(p, pl, l, al, t)
        },
        combine(
            _isSearchActive, _guidedSession, _editingMode, _pointMoveState, _isSavingFeature
        ) { sa, gs, em, pms, sf ->
            Triple5(sa, gs, em, pms, sf)
        },
        combine(
            _isDeletingFeature, _locationIssue, _isLocatingPhone, _pendingLocationPurpose, _starterLayerOperation
        ) { df, li, lp, pp, slo ->
            Triple5(df, li, lp, pp, slo)
        },
        combine(
            _isBoundaryAcknowledgmentSaving, _showDiscardEditDialog, _showBasemapChooser, _showHelpSheet, _showSafetyLimitations
        ) { bas, sd, sbc, shs, ssl ->
            Triple5(bas, sd, sbc, shs, ssl)
        },
        combine(
            _showLocationDetails, _layerPanelOpen, _showPlacementMethod, userPreferencesRepository.userPreferencesFlow
        ) { sld, lp, spm, up ->
            Quad(sld, lp, spm, up)
        }
    ) { b1, b2, b3, b4, b5 ->
        val atm = evaluateAddToMapAvailability(
            b1.a, b1.b, b1.c, b1.d, b1.e,
            b2.a, b2.b, b2.c, b2.d, b2.e,
            b3.a, b3.b, b3.c, b3.d, b3.e,
            b4.a, b4.b, b4.c, b4.d, b4.e,
            b5.a, b5.b
        )
        WorkflowConfigBatch(b5.c, b4.c, b4.d, b4.e, atm, b5.d.measurementSystem, b5.d)
    }

    private val _searchStatusFlow = combine(_searchQuery, _searchResults, _isSearchActive) { q, r, a ->
        SearchStatusBatch(q, r, a)
    }

    private val _aggPart1 = combine(_coreSelectionFlow, _coreLayerFlow, _coreFeatureFlow, _coreStatusFlow, _basemapStatusFlow) { selection, layers, features, status, basemap ->
        MapAggregationPart1(selection, layers, features, status, basemap)
    }

    private val _aggPart2 = combine(_locationBatchFlow, _editorStatusFlow, _editorGeomFlow, _editorEditFlow, _editorUiFlow) { location, editorStatus, editorGeom, editorEdit, editorUi ->
        MapAggregationPart2(location, editorStatus, editorGeom, editorEdit, editorUi)
    }

    private val _aggPart3 = combine(
        combine(_editorPhotoFlow, _workflowStatusFlow, _workflowPrefFlow) { ep, ws, wp -> Part3A(ep, ws, wp) },
        combine(_workflowConfigFlow, _searchStatusFlow, _saveOutcome) { wc, sr, so -> Part3B(wc, sr, so) }
    ) { p1: Part3A, p2: Part3B ->
        MapAggregationPart3(p1.ep, p1.ws, p1.wp, p2.wc, p2.sr, p2.so)
    }

    val uiState: StateFlow<MapUiState> = combine(_aggPart1, _aggPart2, _aggPart3) { part1: MapAggregationPart1, part2: MapAggregationPart2, part3: MapAggregationPart3 ->
        val c = part1.selection
        val lrs = part1.layers
        val fts = part1.features
        val st = part1.status
        val bm = part1.basemap
        
        val loc = part2.location
        val es = part2.editorStatus
        val eg = part2.editorGeom
        val ee = part2.editorEdit
        val eu = part2.editorUi
        
        val ep = part3.editorPhoto
        val ws = part3.workflowStatus
        val wp = part3.workflowPref
        val wc = part3.workflowConfig
        val sr = part3.search
        val saveOutcome = part3.saveOutcome

        val polygonValidationMsg = when (val res = eg.polygonDraft?.validation) {
            is PolygonValidationResult.Invalid -> when (res.reason) {
                PolygonValidationReason.TooFewVertices -> R.string.poly_val_too_few
                PolygonValidationReason.InvalidCoordinate -> R.string.poly_val_invalid_coord
                PolygonValidationReason.ConsecutiveDuplicate -> R.string.poly_val_consecutive_dup
                PolygonValidationReason.DuplicateVertices -> R.string.poly_val_dup_vertices
                PolygonValidationReason.ZeroArea -> R.string.poly_val_zero_area
                PolygonValidationReason.SelfIntersection -> R.string.poly_val_self_intersect
                PolygonValidationReason.UnsupportedInteriorRing -> R.string.poly_val_unsupported_interior
                PolygonValidationReason.InvalidRingClosure -> R.string.poly_val_invalid_closure
            }
            else -> null
        }
        val hasProposedMove = ee.pointMoveState?.let { stm -> 
            val proposed = Pair(stm.proposedLongitude ?: stm.originalLongitude, stm.proposedLatitude ?: stm.originalLatitude)
            val original = Pair(stm.originalLongitude, stm.originalLatitude)
            !GeometryUtils.areCoordinatesEqual(proposed, original, 1e-8) 
        } ?: false
        val locationQuality = loc.location?.let { l: LocationResult.Success -> 
            if (l.accuracyMeters <= 5f) LocationAccuracyQuality.Good 
            else if (l.accuracyMeters <= 15f) LocationAccuracyQuality.Moderate 
            else LocationAccuracyQuality.Poor 
        }

        MapUiState(
            propertyId = c.propertyId,
            propertyName = c.propertyName,
            propertyLatitude = c.propertyData?.latitude,
            propertyLongitude = c.propertyData?.longitude,
            plan = c.plan,
            layers = lrs.layers,
            activeLayerId = lrs.activeLayerId,
            visibleFeatures = fts.visibleFeatures,
            hasMappedFeatures = fts.features.isNotEmpty(),
            selectedFeature = fts.selectedFeature,
            activeEditFeatureId = ee.pointMoveState?.featureId ?: ee.lineEditState?.featureId ?: ee.polygonEditState?.featureId ?: eg.polygonDraft?.id ?: ep.newPointDraft?.id ?: (es.target as? FeatureEditorTarget.NewLine)?.draftId ?: (es.target as? FeatureEditorTarget.NewPoint)?.draftId ?: (es.target as? FeatureEditorTarget.NewPolygon)?.draftId ?: (es.target as? FeatureEditorTarget.EditPersistedLine)?.featureId ?: (es.target as? FeatureEditorTarget.EditPersistedPolygon)?.featureId,
            cameraFocus = st.cameraFocus,
            editingMode = st.editingMode,
            mapLoading = st.mapLoading,
            mapErrorRes = st.mapErrorRes,
            layerPanelOpen = st.layerPanelOpen,
            featureEditorOpen = es.open,
            canAddPoint = wc.addToMapAvailability.isAvailable,
            canAddLine = wc.addToMapAvailability.isAvailable,
            canAddArea = wc.addToMapAvailability.isAvailable,
            isSavingFeature = es.isSaving,
            isDeletingFeature = es.isDeleting,
            featureOperationErrorRes = es.operationErrorRes,
            currentPhoneLocation = loc.location,
            currentPhoneLocationQuality = locationQuality,
            isLocatingPhone = loc.isLocating,
            locationIssue = loc.issue,
            pendingLocationPurpose = loc.purpose,
            showPermissionRationale = loc.rationale,
            hasRequestedLocationOnce = loc.requestedOnce,
            draftVertices = eg.draftVertices,
            canFinishLine = eg.draftVertices.size >= 2, 
            
            preferredBasemapId = bm.preferredBasemapId,
            requestedSourceId = bm.requestedSourceId,
            activeSourceId = bm.activeSourceId,
            currentAttempt = bm.currentAttempt,
            renderSessionId = bm.renderSessionId,
            basemapStatus = bm.basemapStatus,
            basemapGeneration = bm.basemapGeneration,
            basemapErrorRes = bm.basemapErrorRes,
            isUsingFallback = bm.isUsingFallback,
            retryPrimaryAvailable = bm.retryPrimaryAvailable,
            showBackupChooser = bm.showBackupChooser,
            cameraInteractionSequence = bm.cameraInteractionSequence,
            acceptedStyleEvent = bm.acceptedStyleEvent,
            
            polygonDraft = eg.polygonDraft,
            liveAreaMeters = ee.polygonEditState?.workingAreaMeters ?: eg.polygonDraft?.vertices?.let { GeometryUtils.calculateSphericalArea(it) } ?: 0.0,
            livePerimeterMeters = ee.polygonEditState?.workingPerimeterMeters ?: eg.polygonDraft?.vertices?.let { GeometryUtils.calculatePolygonPerimeter(it) } ?: 0.0,
            polygonValidationRes = polygonValidationMsg,
            canFinishPolygon = ((eg.polygonDraft?.vertices?.size ?: 0) >= 3 && eg.polygonDraft?.validation is PolygonValidationResult.Valid),
            featureEditorTarget = es.target,
            featureEditorFeature = es.feature,
            pointMoveState = ee.pointMoveState,
            lineEditState = ee.lineEditState,
            polygonEditState = ee.polygonEditState,
            isLineEditDirty = ee.isLineEditDirty,
            showDiscardEditDialog = eu.showDiscardDialog,
            discardAction = eu.discardAction,
            canSaveLineEdit = ee.lineEditState?.let { it.workingVertices != it.originalVertices && GeometryUtils.validateLineGeometry(it.workingVertices) } ?: false,
            canSavePolygonEdit = ee.polygonEditState?.let { it.workingVertices != it.originalVertices && it.validation is PolygonValidationResult.Valid } ?: false,
            canSavePointMove = hasProposedMove,
            canEditShape = es.feature?.let { f -> 
                val layer = lrs.layers.find { it.id == f.layerId }
                if (layer?.isLocked == true) return@let false
                when (f.geometryType) { 
                    "POLYGON" -> { 
                        val pr = GeometryUtils.parsePolygonGeoJson(f.geometryJson)
                        if (pr is PolygonParseResult.Success) GeometryUtils.validatePolygonGeometry(pr.vertices) is PolygonValidationResult.Valid else false 
                    }
                    "LINESTRING", "POINT" -> true
                    else -> false 
                } 
            } ?: true,
            isEditorDirty = isActualEditorDirty(),
            sessionFeatureId = es.feature?.id,
            mapRecoveryActive = st.mapRecoveryActive,
            searchQuery = sr.query,
            searchResults = sr.results,
            isSearchActive = sr.searchActive,
            showLocationDetails = loc.showDetails,
            showStarterLayersDialog = ws.showStarterLayersDialog,
            showBoundaryAcknowledgment = ws.showBoundaryAcknowledgment,
            pendingGuidedPreset = ws.pendingGuidedPreset,
            guidedSession = ws.guidedSession,
            showGuidedAddMenu = ws.showGuidedAddMenu,
            guidanceDismissed = wp.guidanceDismissed,
            starterLayersCreated = wp.starterLayersCreated,
            starterLayersEligible = wp.starterLayersEligible,
            starterLayerOperation = wp.starterLayerOperation,
            starterLayerOperationActive = wp.starterLayerOperation != StarterLayerOperation.Idle,
            starterLayerErrorRes = wp.starterLayerErrorRes,
            isSavingBoundaryAcknowledgment = wp.isBoundaryAcknowledgmentSaving,
            boundaryAcknowledgmentErrorRes = wp.boundaryAcknowledgmentErrorRes,
            showPlacementMethod = wc.showPlacementMethod,
            showBasemapChooser = wc.showBasemapChooser,
            showHelpSheet = wc.showHelpSheet,
            showSafetyLimitations = wc.showSafetyLimitations,
            addToMapAvailability = wc.addToMapAvailability,
            isWorkflowActive = wc.addToMapAvailability.isAvailable == false && wc.addToMapAvailability.reasonRes == R.string.exclusive_workflow_active,
            measurementSystem = wc.measurementSystem,
            labelError = eu.labelError,
            accuracyError = eu.accuracyError,
            systemItemDraft = ep.systemItemDraft,
            linkSelection = eu.linkSelection,
            initialLinkSelection = eu.initialLinkSelection,
            isNewUnsavedFeature = eg.isNewUnsaved,
            isPointMoveActive = ee.isPointMoveActive,
            stagedPhoto = ep.stagedPhoto,
            newPointDraft = ep.newPointDraft,
            saveOutcome = saveOutcome,
            pendingPhotoPurpose = ep.pendingPhotoPurpose,
            guidedPrefill = ws.guidedSession?.let { session ->
                val suggestedLayerId = session.preset.suggestedLayer?.let { type ->
                    userPreferencesRepository.getStarterLayerBinding(session.planId.toString(), type, wc.userPreferences.starterLayerBindings)
                }
                GuidedFeaturePrefill(
                    sessionId = session.sessionId,
                    draftId = session.targetDraftId,
                    suggestedLabelRes = session.preset.suggestedLabelRes,
                    suggestedLabel = session.suggestedLabel,
                    suggestedCategory = session.preset.defaultCategory,
                    suggestedLayerId = suggestedLayerId,
                    systemItemPolicy = session.preset.systemItemPolicy,
                    presetStyle = session.preset.presetStyle
                )
            },
            openingToken = c.openingToken,
            polygonEditSaveBlockReasonRes = ee.polygonEditState?.let { if (it.validation !is PolygonValidationResult.Valid) R.string.poly_val_too_few else null },
            lineEditSaveBlockReasonRes = ee.lineEditState?.let { if (it.workingVertices == it.originalVertices) R.string.no_changes_to_save else null },
            editShapeBlockReasonRes = es.feature?.let { f -> if (lrs.layers.find { it.id == f.layerId }?.isLocked == true) R.string.layer_is_locked else null },
            workflowBlockReasonRes = wc.addToMapAvailability.reasonRes
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MapUiState())

    fun openMapContext(propertyId: UUID, planId: UUID, openingToken: String) {
        setProperty(propertyId)
        selectPlan(planId, openingToken = openingToken)
    }

    fun setProperty(id: UUID, force: Boolean = false) {
        if (_propertyId.value == id) return
        if (!force && _discardAction.value == null && isActualEditorDirty()) {
            _discardAction.value = PendingEditDiscardAction.ChangeProperty(id)
            _showDiscardEditDialog.value = true
            return
        }
        _propertyId.value = id
        _manualActiveLayerId.value = null
        resetEditorStates()
        cancelGuidedCreation()
    }

    fun selectPlan(id: UUID, force: Boolean = false, openingToken: String? = null) {
        val sameId = _planId.value == id
        if (sameId && openingToken == null) return
        if (!force && _discardAction.value == null && isActualEditorDirty()) {
            _discardAction.value = PendingEditDiscardAction.ChangePlan(id)
            _showDiscardEditDialog.value = true
            return
        }
        _planId.value = id
        _manualActiveLayerId.value = null
        _openingToken.value = openingToken
        _cameraFocus.value = null
        _cameraPersistenceState.value = CameraPersistenceState.WAITING_FOR_INITIAL_FOCUS
        resetEditorStates()
        cancelGuidedCreation()
        
        viewModelScope.launch {
            val plan = mapRepository.getPlanById(id)
            if (_planId.value != id) return@launch

            val propId = plan?.propertyId
            val prop = if (propId != null) propertyRepository.getPropertyById(propId) else null
            if (_planId.value != id) return@launch
            
            val allFeatures = mapRepository.getFeaturesForPlan(id).firstOrNull() ?: emptyList()
            if (_planId.value != id) return@launch

            if (openingToken != null && _openingToken.value != openingToken) return@launch
            
            val resolution = MapCameraResolver.resolveInitialCamera(plan, prop, allFeatures)
            _lastResolutionSource = resolution.source
            _cameraFocus.value = resolution.focus
            _cameraPersistenceState.value = CameraPersistenceState.INITIAL_FOCUS_APPLYING
        }
    }

    private fun resetEditorStates() {
        _lineEditState.value = null; _polygonEditState.value = null; _polygonDraft.value = null; _draftVertices.value = emptyList(); _newPointDraft.value = null; _editingMode.value = MapEditingMode.Select; _featureEditorOpen.value = false; _featureEditorTarget.value = null; _selectedPersistedFeature.value = null; _featureEditorFeature.value = null; _isNewUnsavedFeature.value = false; _isPointMoveActive.value = false; _pointMoveState.value = null; _isLineEditDirty.value = false; _isPolygonEditDirty.value = false; _linkEditorSession.value = null
    }

    private suspend fun finishPersistedGeometryEdit(id: UUID, reopen: Boolean) {
        _lineEditState.value = null
        _polygonEditState.value = null
        _featureEditorTarget.value = null
        _featureEditorOpen.value = false
        _featureEditorFeature.value = null
        _selectedPersistedFeature.value = null
        _linkEditorSession.value = null
        _editingMode.value = MapEditingMode.Select
        if (reopen) {
            val f = mapRepository.getFeatureById(id)
            if (f != null) selectPersistedFeature(f, requestCameraFocus = false)
        }
    }

    private fun initializeSystemItemLinkState(featureId: UUID, policy: SystemItemPolicy, existingItemId: UUID?, isNewFeature: Boolean, suggestedName: String? = null, defaultCategory: String? = null) {
        val initialSelection: SystemItemLinkSelection = if (isNewFeature) { 
            if (policy == SystemItemPolicy.AUTOMATIC) SystemItemLinkSelection.CreateSuggested else SystemItemLinkSelection.None 
        } else { 
            if (existingItemId != null) SystemItemLinkSelection.Existing(existingItemId) else SystemItemLinkSelection.None 
        }
        val propId = _propertyId.value ?: return
        
        // Ensure stable ID for suggested items
        val stableItemIdString = savedStateHandle.get<String>(KEY_GUIDED_ITEM_ID)
        val useItemId = if (stableItemIdString != null) {
            try { UUID.fromString(stableItemIdString) } catch (e: Exception) { UUID.randomUUID() }
        } else {
            val newId = UUID.randomUUID()
            savedStateHandle[KEY_GUIDED_ITEM_ID] = newId.toString()
            newId
        }
        
        val draft = if (isNewFeature && policy != SystemItemPolicy.MAP_ONLY) {
            PendingSystemItemDraft(id = useItemId, propertyId = propId, name = suggestedName ?: "New Item", category = defaultCategory ?: "Utility")
        } else null
        
        _linkEditorSession.value = FeatureLinkEditorSession(featureId = featureId, isNewFeature = isNewFeature, initialSelection = initialSelection, currentSelection = initialSelection, pendingDraft = draft)
    }

    private fun isActualEditorDirty(): Boolean {
        val hasGeometry = (_newPointDraft.value != null) || 
                _draftVertices.value.isNotEmpty() || 
                (_polygonDraft.value?.vertices?.isNotEmpty() ?: false) ||
                _isNewUnsavedFeature.value
        
        val linkSession = _linkEditorSession.value
        val linkDirty = linkSession?.let { it.currentSelection != it.initialSelection } ?: false
        
        val lineDirty = _lineEditState.value?.let { it.workingVertices != it.originalVertices } ?: false
        val polyDirty = _polygonEditState.value?.let { it.workingVertices != it.originalVertices } ?: false
        val pointDirty = _pointMoveState.value?.let { it.proposedLongitude != null } ?: false
        
        return hasGeometry || linkDirty || lineDirty || polyDirty || pointDirty
    }

    fun setLinkSelection(selection: SystemItemLinkSelection) {
        val session = _linkEditorSession.value ?: return
        if (selection is SystemItemLinkSelection.PendingDraft && (session.pendingDraft == null || selection.draftId != session.pendingDraft.id)) return
        val newDraft = if (selection is SystemItemLinkSelection.Existing || selection == SystemItemLinkSelection.None) null else session.pendingDraft
        _linkEditorSession.value = session.copy(currentSelection = selection, pendingDraft = newDraft)
        if (newDraft == null && selection != SystemItemLinkSelection.CreateSuggested) savedStateHandle.remove<String>(KEY_GUIDED_ITEM_ID)
    }
    fun clearSystemItemDraft() { _linkEditorSession.value = _linkEditorSession.value?.copy(pendingDraft = null); savedStateHandle.remove<String>(KEY_GUIDED_ITEM_ID) }

    fun setPendingPhotoPurpose(purpose: PendingPhotoPurpose?) {
        when (purpose) {
            is PendingPhotoPurpose.SavedFeatureAttachment -> {
                savedStateHandle[KEY_PENDING_PHOTO_PURPOSE] = "SAVED"
                savedStateHandle[KEY_PENDING_PHOTO_FEATURE_ID] = purpose.featureId.toString()
            }
            is PendingPhotoPurpose.GuidedFeatureCreation -> {
                savedStateHandle[KEY_PENDING_PHOTO_PURPOSE] = "GUIDED"
                savedStateHandle[KEY_PENDING_PHOTO_FEATURE_ID] = purpose.featureId.toString()
            }
            null -> {
                savedStateHandle.remove<String>(KEY_PENDING_PHOTO_PURPOSE)
                savedStateHandle.remove<String>(KEY_PENDING_PHOTO_FEATURE_ID)
            }
        }
    }

    fun clearPendingPhotoPurpose() {
        setPendingPhotoPurpose(null)
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
        savedStateHandle[KEY_GUIDED_PHOTO_URI] = uri
        savedStateHandle[KEY_GUIDED_PHOTO_TOKEN] = token
        _stagedPhoto.value = StagedCreationPhotoState.Ready(uri, token)
    }

    fun clearStagedPhoto() {
        val token = savedStateHandle.get<String>(KEY_GUIDED_PHOTO_TOKEN)
        if (token != null) {
            attachmentRepository.deleteTempCameraCapture(token)
        }
        val inFlightToken = savedStateHandle.get<String>(KEY_IN_FLIGHT_TOKEN)
        if (inFlightToken != null) {
            attachmentRepository.deleteTempCameraCapture(inFlightToken)
        }
        savedStateHandle[KEY_GUIDED_PHOTO_URI] = null
        savedStateHandle[KEY_GUIDED_PHOTO_TOKEN] = null
        _stagedPhoto.value = StagedCreationPhotoState.None
        clearInFlightCapture()
        clearPendingPhotoPurpose()
    }

    fun setActiveLayer(id: UUID) { _manualActiveLayerId.value = id }
    fun setActiveLayerId(id: UUID) { _manualActiveLayerId.value = id }
    fun setEditingMode(m: MapEditingMode) { _editingMode.value = m }

    fun acknowledgeCameraFocusApplied(propertyId: UUID, planId: UUID, token: String?, focus: MapCameraFocus): Boolean {
        if (_propertyId.value == propertyId && _planId.value == planId && _openingToken.value == token && _cameraFocus.value == focus) {
            if (_cameraPersistenceState.value == CameraPersistenceState.INITIAL_FOCUS_APPLYING) {
                _cameraPersistenceState.value = CameraPersistenceState.ARMED
                
                // If we applied a repaired or fallback focus, persist it immediately
                if (_lastResolutionSource in listOf(CameraSource.REPAIRED_DEFAULT_CAMERA, CameraSource.PROPERTY_COORDINATES, CameraSource.FEATURE_BOUNDS)) {
                    if (focus is MapCameraFocus.Point) {
                        viewModelScope.launch {
                            _cameraPersistenceRequests.send(
                                CameraPersistenceRequest(
                                    planId = planId,
                                    latitude = focus.latitude,
                                    longitude = focus.longitude,
                                    zoom = focus.zoom,
                                    bearing = focus.bearing
                                )
                            )
                        }
                    }
                }
            }
            _cameraFocus.value = null
            return true
        }
        return false
    }
    fun onCameraMoved(latitude: Double, longitude: Double, zoom: Double, bearing: Double) {
        val currentPlanId = _planId.value ?: return; val normalizedBearing = CameraValidation.normalizeBearing(bearing)
        if (CameraValidation.isDefaultWorldView(latitude, longitude, zoom)) return

        when (_cameraPersistenceState.value) {
            CameraPersistenceState.WAITING_FOR_INITIAL_FOCUS -> { }
            CameraPersistenceState.INITIAL_FOCUS_APPLYING -> { 
                // Wait for onCameraFocusApplied to arm persistence
            }
            CameraPersistenceState.ARMED -> { 
                if (!CameraValidation.isValid(latitude, longitude, zoom, normalizedBearing)) return
                if (lastPersistedPlanId == currentPlanId && GeometryUtils.areCoordinatesEqual(Pair(lastPersistedLng ?: 0.0, lastPersistedLat ?: 0.0), Pair(longitude, latitude), 1e-6) && abs((lastPersistedZoom ?: 0f).toDouble() - zoom) < 0.01 && abs((lastPersistedBearing ?: 0.0) - normalizedBearing) < 0.1) return
                viewModelScope.launch { _cameraPersistenceRequests.send(CameraPersistenceRequest(currentPlanId, latitude, longitude, zoom.toFloat(), normalizedBearing)) }
            }
        }
    }

    fun onCameraInteraction() {
        _cameraInteractionSequence.value++
    }

    fun onMapReady(sessionId: UUID) {
        _renderSessionId.value = sessionId
        isRenderSessionReady = true
        
        val resolution = PendingBasemapResolver.resolve(
            pending = _pendingBasemapRequest.value,
            currentGeneration = _basemapGeneration.value,
            currentPreferredId = _preferredBasemapId.value,
            sessionId = sessionId,
            basemapProvider = basemapProvider
        )

        when (resolution) {
            is PendingConsumptionResult.IssuePending -> {
                issueAttempt(resolution.request.sourceId, resolution.request.role, resolution.request.reason)
                _pendingBasemapRequest.value = null
            }
            is PendingConsumptionResult.ReissueCurrentAuthority -> {
                val authId = resolution.preferredBasemapId
                val primary = basemapProvider.getPrimaryBasemaps().find { it.preferredId == authId }
                val attempt = if (primary != null) {
                    issueAttempt(primary.sourceId, BasemapRole.PRIMARY, BasemapLoadAttemptReason.INITIAL)
                } else {
                    val backupSourceId = basemapProvider.resolveDefaultBackup(authId)
                    issueAttempt(backupSourceId, BasemapRole.BACKUP, BasemapLoadAttemptReason.BACKUP)
                }
                
                if (attempt != null) {
                    _pendingBasemapRequest.value = null
                } else {
                    _pendingBasemapRequest.value = null
                    _requestedSourceId.value = null
                    _basemapStatus.value = BasemapLoadStatus.FAILED
                }
            }
            PendingConsumptionResult.DefinitionUnavailable -> {
                _pendingBasemapRequest.value = null
                _requestedSourceId.value = null
                _basemapStatus.value = BasemapLoadStatus.FAILED
                _basemapErrorRes.value = R.string.failed_to_load_basemap
                _retryPrimaryAvailable.value = true
            }
            PendingConsumptionResult.NoLiveSession -> {
                val status = _basemapStatus.value
                if (status == BasemapLoadStatus.IDLE) {
                    ensureInitialBasemapLoad()
                } else {
                    val current = _currentAttempt.value
                    if (current != null && current.renderSessionId == sessionId) return

                    val sourceId = if (status == BasemapLoadStatus.LOADED) {
                        _activeSourceId.value
                    } else if (status == BasemapLoadStatus.LOADING_PRIMARY || status == BasemapLoadStatus.LOADING_BACKUP) {
                        _requestedSourceId.value
                    } else null
                    
                    if (sourceId != null) {
                        val role = basemapProvider.getDefinition(sourceId)?.role ?: BasemapRole.PRIMARY
                        issueAttempt(sourceId, role, BasemapLoadAttemptReason.RECREATION)
                    }
                }
            }
        }
    }

    private fun ensureInitialBasemapLoad() {
        if (isRenderSessionReady && isPreferencesReady && _basemapStatus.value == BasemapLoadStatus.IDLE) {
            startPrimaryLoad(_preferredBasemapId.value)
        }
    }

    private fun BasemapLoadAttempt.toKey(): BasemapAttemptKey {
        return BasemapAttemptKey(
            semanticGeneration = this.semanticGeneration,
            attemptId = this.attemptId,
            renderSessionId = this.renderSessionId,
            sourceId = this.sourceId
        )
    }

    private fun validateAttempt(attempt: BasemapLoadAttempt, expectedStatus: BasemapLoadStatus): BasemapLoadSuccessResult {
        val key = attempt.toKey()
        
        // 1. Check terminal state first
        val terminalReason = terminalAttempts[key]
        if (terminalReason != null) {
            return BasemapLoadSuccessResult(false, attempt.sourceId, rejectionReason = BasemapLoadRejectionReason.TERMINAL_ATTEMPT)
        }

        // Phase 2.2h5R4: Full identity check and requested source invariant
        val current = _currentAttempt.value
        if (current == null ||
            attempt.attemptId != current.attemptId ||
            attempt.renderSessionId != current.renderSessionId ||
            attempt.semanticGeneration != current.semanticGeneration ||
            attempt.sourceId != current.sourceId ||
            attempt.capturedSequence != current.capturedSequence ||
            attempt.reason != current.reason ||
            attempt.role != current.role ||
            attempt.provider != current.provider
        ) {
            val reason = when {
                attempt.renderSessionId != _renderSessionId.value -> BasemapLoadRejectionReason.STALE_SESSION
                attempt.semanticGeneration != _basemapGeneration.value -> BasemapLoadRejectionReason.GENERATION_MISMATCH
                attempt.sourceId != _requestedSourceId.value -> BasemapLoadRejectionReason.REQUESTED_SOURCE_MISMATCH
                else -> BasemapLoadRejectionReason.ID_MISMATCH
            }
            return BasemapLoadSuccessResult(false, attempt.sourceId, rejectionReason = reason)
        }

        if (attempt.sourceId != _requestedSourceId.value) {
             return BasemapLoadSuccessResult(false, attempt.sourceId, rejectionReason = BasemapLoadRejectionReason.REQUESTED_SOURCE_MISMATCH)
        }

        if (attempt.reason != BasemapLoadAttemptReason.RECREATION && _basemapStatus.value != expectedStatus) {
            return BasemapLoadSuccessResult(false, attempt.sourceId, rejectionReason = BasemapLoadRejectionReason.STATUS_MISMATCH)
        }
        
        val def = basemapProvider.getDefinition(attempt.sourceId)
        if (def == null) return BasemapLoadSuccessResult(false, attempt.sourceId, rejectionReason = BasemapLoadRejectionReason.DEFINITION_MISMATCH)
        if (def.provider != attempt.provider) return BasemapLoadSuccessResult(false, attempt.sourceId, rejectionReason = BasemapLoadRejectionReason.PROVIDER_MISMATCH)
        if (def.role != attempt.role) return BasemapLoadSuccessResult(false, attempt.sourceId, rejectionReason = BasemapLoadRejectionReason.ROLE_MISMATCH)
        
        return BasemapLoadSuccessResult(true, attempt.sourceId, def.provider, def.role)
    }

    fun handleBasemapLoadSuccess(attempt: BasemapLoadAttempt): BasemapLoadSuccessResult {
        val expectedStatus = if (attempt.role == BasemapRole.PRIMARY) BasemapLoadStatus.LOADING_PRIMARY else BasemapLoadStatus.LOADING_BACKUP
        val result = validateAttempt(attempt, expectedStatus)
        
        if (!result.accepted) return result
        
        // Source identity truth: derived from attempt which passed validation
        val sourceId = attempt.sourceId
        
        _activeSourceId.value = sourceId
        _requestedSourceId.value = null
        _basemapStatus.value = BasemapLoadStatus.LOADED
        _basemapErrorRes.value = null
        
        if (attempt.role == BasemapRole.PRIMARY) {
            _isUsingFallback.value = false
            _showBackupChooser.value = false
            _retryPrimaryAvailable.value = false
        } else {
            _isUsingFallback.value = true
            _showBackupChooser.value = true
            _retryPrimaryAvailable.value = true
        }
        
        // Only exhaust repair epoch if it's genuinely REPAIR
        if (attempt.reason == BasemapLoadAttemptReason.REPAIR) {
            val repairKey = BasemapRepairKey(attempt.renderSessionId, attempt.semanticGeneration, sourceId)
            repairEpochs[repairKey] = BasemapRepairEpochState.EXHAUSTED
        }

        _acceptedStyleEvent.value = AcceptedBasemapStyleEvent(nextEventId++, attempt)
        
        return result
    }

    fun handleBasemapLoadTerminated(reason: BasemapTerminalReason, attempt: BasemapLoadAttempt) {
        val expectedStatus = if (attempt.role == BasemapRole.PRIMARY) BasemapLoadStatus.LOADING_PRIMARY else BasemapLoadStatus.LOADING_BACKUP
        val result = validateAttempt(attempt, expectedStatus)
        
        val key = attempt.toKey()
        terminalAttempts.putIfAbsent(key, reason)
        if (terminalAttempts.size > 100) terminalAttempts.remove(terminalAttempts.keys.first())
        
        // Remove snapshots for terminal attempts
        cameraSnapshots.remove(key)

        if (!result.accepted) {
            // If it was the current repair attempt that failed, exhaust the epoch
            if (attempt.reason == BasemapLoadAttemptReason.REPAIR) {
                val repairKey = BasemapRepairKey(attempt.renderSessionId, attempt.semanticGeneration, attempt.sourceId)
                repairEpochs[repairKey] = BasemapRepairEpochState.EXHAUSTED
            }
            return
        }
        
        if (reason == BasemapTerminalReason.TIMEOUT || reason == BasemapTerminalReason.PROVIDER_FAILURE) {
            if (attempt.role == BasemapRole.PRIMARY && attempt.reason != BasemapLoadAttemptReason.REPAIR) {
                if (!_fallbackAttempted.value) {
                    _fallbackAttempted.value = true
                    val backupSourceId = basemapProvider.resolveDefaultBackup(_preferredBasemapId.value)
                    issueAttempt(backupSourceId, BasemapRole.BACKUP, BasemapLoadAttemptReason.BACKUP)
                } else {
                    _basemapStatus.value = BasemapLoadStatus.FAILED
                    _basemapErrorRes.value = R.string.failed_to_load_basemap
                    _retryPrimaryAvailable.value = true
                }
            } else if (attempt.role != BasemapRole.PRIMARY || attempt.reason == BasemapLoadAttemptReason.REPAIR) {
                _basemapStatus.value = BasemapLoadStatus.FAILED
                _basemapErrorRes.value = R.string.failed_to_load_basemap
                _retryPrimaryAvailable.value = true
            }
        }
        
        // Only exhaust if it's genuinely REPAIR
        if (attempt.reason == BasemapLoadAttemptReason.REPAIR) {
             val repairKey = BasemapRepairKey(attempt.renderSessionId, attempt.semanticGeneration, attempt.sourceId)
             repairEpochs[repairKey] = BasemapRepairEpochState.EXHAUSTED
        }
    }

    fun onRenderSessionDisposed(sessionId: UUID) {
        if (_renderSessionId.value == sessionId) {
            val status = _basemapStatus.value
            if (status == BasemapLoadStatus.LOADING_PRIMARY || status == BasemapLoadStatus.LOADING_BACKUP) {
                _currentAttempt.value?.let { attempt ->
                    if (attempt.renderSessionId == sessionId) {
                        terminalAttempts.putIfAbsent(attempt.toKey(), BasemapTerminalReason.DISPOSED)
                    }
                }
            }
            
            // Clean up snapshots and epochs owned by this session
            cameraSnapshots.keys.filter { it.renderSessionId == sessionId }.toList().forEach { cameraSnapshots.remove(it) }
            repairEpochs.keys.filter { it.renderSessionId == sessionId }.toList().forEach { repairEpochs.remove(it) }
            
            isRenderSessionReady = false
            _renderSessionId.value = null
            _currentAttempt.value = null
        }
    }

    fun getTerminalReason(attempt: BasemapLoadAttempt): BasemapTerminalReason? {
        return terminalAttempts[attempt.toKey()]
    }

    fun captureCameraSnapshot(
        latitude: Double,
        longitude: Double,
        zoom: Double,
        bearing: Double,
        tilt: Double,
        attempt: BasemapLoadAttempt
    ) {
        if (attempt.renderSessionId != _renderSessionId.value) return
        val key = attempt.toKey()
        cameraSnapshots[key] = CameraSnapshot(
            latitude = latitude,
            longitude = longitude,
            zoom = zoom,
            bearing = bearing,
            tilt = tilt,
            customerInteractionSequence = _cameraInteractionSequence.value,
            attemptKey = key
        )
    }

    fun getCameraSnapshot(attempt: BasemapLoadAttempt): CameraSnapshot? {
        return cameraSnapshots[attempt.toKey()]
    }

    fun consumeCameraSnapshot(attempt: BasemapLoadAttempt) {
        cameraSnapshots.remove(attempt.toKey())
    }

    fun retryPrimaryMap() {
        startPrimaryLoad(_preferredBasemapId.value)
    }

    fun requestBackupBasemap(sourceId: BasemapSourceId) {
        issueAttempt(sourceId, basemapProvider.getDefinition(sourceId)?.role ?: BasemapRole.BACKUP, BasemapLoadAttemptReason.RETRY)
    }

    fun handleStaleStyleApplied(attempt: BasemapLoadAttempt) {
        if (!isRenderSessionReady || _renderSessionId.value != attempt.renderSessionId) return
        
        val authoritativeSource = _requestedSourceId.value ?: _activeSourceId.value ?: return
        
        val repairKey = BasemapRepairKey(
            renderSessionId = attempt.renderSessionId,
            semanticGeneration = _basemapGeneration.value,
            authoritativeSourceId = authoritativeSource
        )
        
        val state = repairEpochs[repairKey]
        if (state != null) return // Already IN_FLIGHT or EXHAUSTED
        
        repairEpochs[repairKey] = BasemapRepairEpochState.IN_FLIGHT
        if (repairEpochs.size > 50) repairEpochs.remove(repairEpochs.keys.first())
        
        issueAttempt(authoritativeSource, basemapProvider.getDefinition(authoritativeSource)?.role ?: BasemapRole.PRIMARY, BasemapLoadAttemptReason.REPAIR)
    }

    fun requestBasemap(id: BasemapId) {
        _preferredBasemapId.value = id
        customerBasemapPreferenceOverride = if (id != lastObservedRepositoryBasemapId) id else null
        
        _basemapGeneration.update { it + 1 }
        val newGen = _basemapGeneration.value
        _fallbackAttempted.value = false
        terminalAttempts.clear()
        repairEpochs.clear()
        cameraSnapshots.clear()
        _acceptedStyleEvent.value = null
        
        _activeSourceId.value = null
        _isUsingFallback.value = false
        _showBackupChooser.value = false
        _retryPrimaryAvailable.value = false
        _basemapErrorRes.value = null

        if (isRenderSessionReady) {
            val primarySource = basemapProvider.getPrimaryBasemaps().find { it.preferredId == id }
            if (primarySource != null) {
                issueAttempt(primarySource.sourceId, BasemapRole.PRIMARY, BasemapLoadAttemptReason.INITIAL)
            } else {
                _fallbackAttempted.value = true
                val backupSourceId = basemapProvider.resolveDefaultBackup(id)
                issueAttempt(backupSourceId, BasemapRole.BACKUP, BasemapLoadAttemptReason.BACKUP)
            }
        } else {
            val primary = basemapProvider.getPrimaryBasemaps().find { it.preferredId == id }
            val sourceId: BasemapSourceId
            val role: BasemapRole
            val reason: BasemapLoadAttemptReason
            if (primary != null) {
                sourceId = primary.sourceId
                role = BasemapRole.PRIMARY
                reason = BasemapLoadAttemptReason.INITIAL
            } else {
                sourceId = basemapProvider.resolveDefaultBackup(id)
                role = BasemapRole.BACKUP
                reason = BasemapLoadAttemptReason.BACKUP
            }
            
            _pendingBasemapRequest.value = PendingBasemapRequest(
                preferredBasemapId = id,
                semanticGeneration = newGen,
                sourceId = sourceId,
                role = role,
                reason = reason
            )
            _requestedSourceId.value = sourceId
            _basemapStatus.value = if (role == BasemapRole.PRIMARY) BasemapLoadStatus.LOADING_PRIMARY else BasemapLoadStatus.LOADING_BACKUP
        }
        
        viewModelScope.launch { 
            try {
                userPreferencesRepository.updateSelectedBasemap(id) 
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    private fun startPrimaryLoad(id: BasemapId) {
        val newGen = _basemapGeneration.value + 1
        _basemapGeneration.value = newGen
        _fallbackAttempted.value = false
        
        terminalAttempts.clear()
        repairEpochs.clear()
        cameraSnapshots.clear()
        _acceptedStyleEvent.value = null
        
        val primarySource = basemapProvider.getPrimaryBasemaps().find { it.preferredId == id }
        if (primarySource != null) {
            issueAttempt(primarySource.sourceId, BasemapRole.PRIMARY, BasemapLoadAttemptReason.INITIAL)
            _basemapErrorRes.value = null
        } else {
            _fallbackAttempted.value = true
            val backupSourceId = basemapProvider.resolveDefaultBackup(id)
            issueAttempt(backupSourceId, BasemapRole.BACKUP, BasemapLoadAttemptReason.BACKUP)
        }
    }

    private fun issueAttempt(sourceId: BasemapSourceId, role: BasemapRole, reason: BasemapLoadAttemptReason): BasemapLoadAttempt? {
        val def = basemapProvider.getDefinition(sourceId) ?: run {
            _requestedSourceId.value = null
            _basemapStatus.value = BasemapLoadStatus.FAILED
            _basemapErrorRes.value = R.string.failed_to_load_basemap
            _retryPrimaryAvailable.value = true
            return null
        }
        val sessionId = _renderSessionId.value ?: return null

        _currentAttempt.value?.let { prev ->
             terminalAttempts.putIfAbsent(prev.toKey(), BasemapTerminalReason.SUPERSEDED)
             cameraSnapshots.remove(prev.toKey())
        }

        val attemptId = nextAttemptId++
        
        val attempt = BasemapLoadAttempt(
            semanticGeneration = _basemapGeneration.value,
            attemptId = attemptId,
            renderSessionId = sessionId,
            sourceId = sourceId,
            provider = def.provider,
            role = role,
            reason = reason,
            capturedSequence = _cameraInteractionSequence.value
        )
        
        _currentAttempt.value = attempt
        _requestedSourceId.value = sourceId
        if (reason != BasemapLoadAttemptReason.RECREATION) {
            _basemapStatus.value = if (role == BasemapRole.PRIMARY) BasemapLoadStatus.LOADING_PRIMARY else BasemapLoadStatus.LOADING_BACKUP
        }
        return attempt
    }

    private fun reapplyActiveSource(sourceId: BasemapSourceId) {
        issueAttempt(sourceId, basemapProvider.getDefinition(sourceId)?.role ?: BasemapRole.PRIMARY, BasemapLoadAttemptReason.REPAIR)
    }

    fun clearBasemapError() { _basemapErrorRes.value = null }

    fun setMapRecoveryActive(active: Boolean) { _mapRecoveryActive.value = active }
    fun clearMapError() { _mapErrorRes.value = null }
    fun setLayerPanelOpen(open: Boolean) { _layerPanelOpen.value = open }
    fun clearCameraFocus() { _cameraFocus.value = null }

    fun acknowledgeBoundary() { 
        viewModelScope.launch { 
            try { 
                userPreferencesRepository.updateBoundaryDisclaimerAcknowledged(true)
                _showBoundaryAcknowledgment.value = false
                _editingMode.value = MapEditingMode.AddPolygon 
            } catch (e: Exception) { 
                _boundaryAcknowledgmentErrorRes.value = R.string.error_save_failed 
            } 
        } 
    }
    fun cancelBoundaryAcknowledgment() { _showBoundaryAcknowledgment.value = false; cancelGuidedCreation() }
    fun dismissGuidance() { val pid = _propertyId.value ?: return; viewModelScope.launch { userPreferencesRepository.updateGuidanceDismissed(pid.toString(), true) } }
    fun presentStarterLayers() { _showStarterLayersDialog.value = true }
    fun skipStarterLayers() { val mid = _planId.value ?: return; viewModelScope.launch { userPreferencesRepository.markStarterLayersCompleted(mid.toString()); _showStarterLayersDialog.value = false } }
    fun createStarterLayers(buildings: Boolean, utilities: Boolean, outdoor: Boolean, safety: Boolean, all: String = "") { 
        val mid = _planId.value ?: return; val pid = _propertyId.value ?: return
        _starterLayerErrorRes.value = null
        viewModelScope.launch {
            _starterLayerOperation.value = StarterLayerOperation.Creating
            try {
                val requests = mutableListOf<StarterLayerRequest>()
                if (buildings) requests.add(StarterLayerRequest(SuggestedMapLayer.BUILDINGS_BOUNDARIES, context.getString(R.string.layer_buildings), "Structure"))
                if (utilities) requests.add(StarterLayerRequest(SuggestedMapLayer.UTILITIES, context.getString(R.string.layer_utilities), "Utility"))
                if (outdoor) requests.add(StarterLayerRequest(SuggestedMapLayer.OUTDOOR_FEATURES, context.getString(R.string.layer_outdoor), "Landscape"))
                if (safety) requests.add(StarterLayerRequest(SuggestedMapLayer.SAFETY_EMERGENCY, context.getString(R.string.layer_safety), "Safety"))
                
                val bindings = mapRepository.ensureStarterLayers(pid, mid, requests, emptyMap())
                userPreferencesRepository.saveStarterLayerBindings(mid.toString(), bindings)
                userPreferencesRepository.markStarterLayersCompleted(mid.toString())
                _starterLayersCreated.value = true
                _showStarterLayersDialog.value = false
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _starterLayerErrorRes.value = R.string.save_failed
            } finally {
                _starterLayerOperation.value = StarterLayerOperation.Idle
            }
        }
    }
    fun setShowMapHelp(show: Boolean) { _showHelpSheet.value = show }
    fun onReturnToProperty() { 
        _propertyData.value?.let { p ->
            _cameraFocus.value = MapCameraFocus.Point(p.latitude ?: 0.0, p.longitude ?: 0.0, 17f)
            _mapRecoveryActive.value = false
        }
    }
    fun cancelLocationIssue() { 
        if (_locationIssue.value?.purpose == LocationRequestPurpose.CreatePoint) cancelGuidedCreation()
        val purpose = _locationIssue.value?.purpose
        _locationIssue.value = null
        setPendingLocationPurpose(null)
    }
    fun retryLocationIssue() { _locationIssue.value?.let { requestLocation(it.purpose ?: LocationRequestPurpose.LocateOnly) } }
    fun useLocationResultAnyway() { 
        _locationIssue.value?.cachedLocation?.let { loc ->
            _currentPhoneLocation.value = loc
            if (_locationIssue.value?.purpose == LocationRequestPurpose.CreatePoint) handleLocationForCreatePoint(loc)
        }
        _locationIssue.value = null
    }
    fun onOpenAppSettings() { _locationIssue.value = null }
    fun onOpenLocationSettings() { _locationIssue.value = null }
    fun continueGuidedLocationManually() { 
        val p = _pendingGuidedPreset.value ?: _guidedSession.value?.preset ?: return
        _locationIssue.value = null
        _showPlacementMethod.value = false
        startGuidedMapping(p, PlacementMethod.TAP_MAP)
    }
    fun cancelGuidedLocationPlacement() { cancelGuidedCreation() }
    fun dismissLocation() { _currentPhoneLocation.value = null }
    fun cancelPermissionRationale() { 
        if (savedStateHandle.get<LocationRequestPurpose?>(KEY_PENDING_PURPOSE) == LocationRequestPurpose.CreatePoint) cancelGuidedCreation()
        setPendingLocationPurpose(null)
        savedStateHandle.remove<Boolean>(KEY_SHOW_RATIONALE)
    }
    
    fun dismissFeatureEditor() {
        if (isActualEditorDirty()) {
            _discardAction.value = when (_editingMode.value) {
                MapEditingMode.AddLine -> PendingEditDiscardAction.DiscardNewLine
                MapEditingMode.AddPolygon -> PendingEditDiscardAction.DiscardNewPolygon
                else -> PendingEditDiscardAction.DiscardNewPoint
            }
            _showDiscardEditDialog.value = true
            return
        }
        _featureEditorOpen.value = false; _featureEditorTarget.value = null; _featureEditorFeature.value = null; _linkEditorSession.value = null; _selectedPersistedFeature.value = null
    }

    fun setShowPermissionRationale(show: Boolean) { savedStateHandle[KEY_SHOW_RATIONALE] = show }
    fun setHasRequestedLocationOnce(has: Boolean) { savedStateHandle[KEY_HAS_REQUESTED_ONCE] = has }
    fun showLocationDetails(show: Boolean) { _showLocationDetails.value = show }
    fun setShowLocationDetails(show: Boolean) { _showLocationDetails.value = show }
    fun setShowGuidedAddMenu(show: Boolean) { _showGuidedAddMenu.value = show }
    fun setShowBasemapChooser(show: Boolean) { _showBasemapChooser.value = show }
    fun setShowSafetyLimitations(show: Boolean) { _showSafetyLimitations.value = show }

    suspend fun createCameraCapture(): Result<TemporaryCameraCapture> {
        return attachmentRepository.createTempCameraUri()
    }
    fun deleteCameraCapture(token: String) { attachmentRepository.deleteTempCameraCapture(token) }
    
    fun deletePolygonVertex() {
        _polygonEditState.value?.let { s ->
            val i = s.selectedVertexIndex ?: return@let
            deletePolygonVertex(i)
        }
    }

    fun deletePolygonVertex(index: Int) {
        _polygonEditState.value?.let { s ->
            val updated = s.workingVertices.toMutableList()
            if (updated.size > 3 && index in updated.indices) {
                _polygonEditState.value = s.pushUndo().let { pushed ->
                    val newVertices = pushed.workingVertices.toMutableList()
                    newVertices.removeAt(index)
                    pushed.copy(workingVertices = newVertices, selectedVertexIndex = null).withValidation()
                }
                _isPolygonEditDirty.value = true
            }
        }
    }
    
    fun prepareSystemItemDraft(input: PendingSystemItemInput): UUID {
        val id = UUID.randomUUID(); val pid = _propertyId.value ?: return id
        val draft = PendingSystemItemDraft(id = id, propertyId = pid, name = input.name, category = input.category, subtype = input.subtype, isEmergencyItem = input.isEmergencyItem, emergencyInstructions = input.emergencyInstructions)
        _linkEditorSession.value = _linkEditorSession.value?.copy(
            pendingDraft = draft,
            currentSelection = SystemItemLinkSelection.PendingDraft(id)
        )
        return id
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
                android.util.Log.d("MapViewModel", "Camera result DIAGNOSTIC: success=$success, token=$token, inspection=$inspection")
            }
            
            if (inspection is TempCameraCaptureInspectionResult.Ready) {
                setStagedPhoto(uriStr, token)
            } else {
                if (com.jumastappworks.mapstead.BuildConfig.DEBUG) {
                    android.util.Log.w("MapViewModel", "Camera capture validation failed: $inspection for URI: $uriStr")
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

    fun retryFeaturePhoto(propertyId: UUID, featureId: UUID) {
        val currentPhoto = _stagedPhoto.value
        if (currentPhoto !is StagedCreationPhotoState.Ready) return
        if (_isSavingFeature.value) return
        
        _isSavingFeature.value = true
        _featureOperationErrorRes.value = null
        
        viewModelScope.launch {
            try {
                val uri = android.net.Uri.parse(currentPhoto.uri)
                val owner = AttachmentOwner.MapFeature(propertyId, featureId)
                val result = attachmentRepository.importAttachment(
                    owner = owner,
                    uri = uri,
                    type = AttachmentType.Photo,
                    customDisplayName = "Feature Photo",
                    caption = null,
                    cameraCaptureToken = currentPhoto.cameraCaptureToken
                )
                if (result is AttachmentWriteResult.Success) {
                    consumeStagedPhotoState()
                    _saveOutcome.value = GuidedSaveOutcome.Success(featureId)
                    cancelGuidedCreation()
                    val updated = mapRepository.getFeatureById(featureId)
                    selectPersistedFeature(updated, requestCameraFocus = false)
                } else {
                    _featureOperationErrorRes.value = R.string.feature_saved_with_photo_error
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _featureOperationErrorRes.value = R.string.error_save_failed
            } finally {
                _isSavingFeature.value = false
            }
        }
    }

    fun continueWithoutFeaturePhoto(featureId: UUID) {
        clearStagedPhoto()
        _saveOutcome.value = GuidedSaveOutcome.Success(featureId)
        cancelGuidedCreation()
        viewModelScope.launch {
            val updated = mapRepository.getFeatureById(featureId)
            selectPersistedFeature(updated, requestCameraFocus = false)
        }
    }

    fun clearSaveOutcome() {
        _saveOutcome.value = null
    }

    private fun consumeStagedPhotoState() {
        savedStateHandle[KEY_GUIDED_PHOTO_URI] = null
        savedStateHandle[KEY_GUIDED_PHOTO_TOKEN] = null
        _stagedPhoto.value = StagedCreationPhotoState.None
    }

    private fun handleLocationForCreatePoint(loc: LocationResult.Success) {
        val pid = _propertyId.value ?: return; val mid = _planId.value ?: return; val lid = _activeLayerId.value ?: return; val session = _guidedSession.value
        val useId = session?.targetDraftId ?: UUID.randomUUID()
        _newPointDraft.value = NewPointDraftState(id = useId, propertyId = pid, planId = mid, layerId = lid, longitude = loc.longitude, latitude = loc.latitude, accuracyMeters = loc.accuracyMeters.toDouble())
        if (session?.expectedGeometry == GuidedMapGeometry.LOCATION) {
            savedStateHandle[KEY_GUIDED_PHASE] = GuidedMappingPhase.REVIEWING.name
            savedStateHandle[KEY_GUIDED_DRAFT_ID] = useId.toString()
            _guidedSession.value = session.copy(phase = GuidedMappingPhase.REVIEWING, targetDraftId = useId)
        }
        val feature = MapFeatureEntity(id = useId, propertyId = pid, planId = mid, layerId = lid, geometryType = "POINT", geometryJson = GeometryUtils.buildPointGeoJson(loc.longitude, loc.latitude), coordinateSpace = "GEOGRAPHIC", styleJson = "{}", accuracySource = "GPS", label = session?.suggestedLabel ?: context.getString(R.string.label_default_point))
        initializeSystemItemLinkState(featureId = useId, policy = session?.preset?.systemItemPolicy ?: SystemItemPolicy.MAP_ONLY, existingItemId = null, isNewFeature = true, suggestedName = session?.suggestedLabel, defaultCategory = session?.preset?.defaultCategory)
        _featureEditorFeature.value = feature; _featureEditorOpen.value = true; _isNewUnsavedFeature.value = true; _featureEditorTarget.value = FeatureEditorTarget.NewPoint(useId); _selectedPersistedFeature.value = null; _editingMode.value = MapEditingMode.Select
    }

    fun requestOpenGuidedAddMenu() {
        val avail = evaluateAddToMapAvailability(
            _propertyId.value, _plan.value, _layers.value, _activeLayerId.value,
            _featureEditorTarget.value, _isSearchActive.value, _guidedSession.value,
            _editingMode.value, _pointMoveState.value, _isSavingFeature.value, _isDeletingFeature.value,
            _locationIssue.value, _isLocatingPhone.value, _pendingLocationPurpose.value,
            _starterLayerOperation.value, _isBoundaryAcknowledgmentSaving.value, _showDiscardEditDialog.value,
            _showBasemapChooser.value, _showHelpSheet.value, _showSafetyLimitations.value,
            _showLocationDetails.value, _layerPanelOpen.value
        )
        if (avail.isAvailable) _showGuidedAddMenu.value = true else _mapErrorRes.value = avail.reasonRes
    }
    
    fun deleteLayer(id: UUID) { viewModelScope.launch { mapRepository.softDeleteLayer(id) } }
    fun renameLayer(id: UUID, name: String) { val l = _layers.value.find { it.id == id } ?: return; viewModelScope.launch { mapRepository.updateLayer(l.copy(name = name)) } }
    fun toggleLayerVisibility(id: UUID) { val l = _layers.value.find { it.id == id } ?: return; viewModelScope.launch { mapRepository.updateLayer(l.copy(isVisible = !l.isVisible)) } }
    fun toggleLayerLock(id: UUID) { val l = _layers.value.find { it.id == id } ?: return; viewModelScope.launch { mapRepository.updateLayer(l.copy(isLocked = !l.isLocked)) } }
    fun changeLayerOpacity(id: UUID, opacity: Float) { val l = _layers.value.find { it.id == id } ?: return; viewModelScope.launch { mapRepository.updateLayer(l.copy(opacity = opacity)) } }
    fun moveLayerUp(id: UUID) { val list = _layers.value.toMutableList(); val index = list.indexOfFirst { it.id == id }; if (index > 0) { val l1 = list[index]; val l2 = list[index - 1]; val o1 = l1.displayOrder; val o2 = l2.displayOrder; viewModelScope.launch { mapRepository.updateLayer(l1.copy(displayOrder = o2)); mapRepository.updateLayer(l2.copy(displayOrder = o1)) } } }
    fun moveLayerDown(id: UUID) { val list = _layers.value.toMutableList(); val index = list.indexOfFirst { it.id == id }; if (index >= 0 && index < list.size - 1) { val l1 = list[index]; val l2 = list[index + 1]; val o1 = l1.displayOrder; val o2 = l2.displayOrder; viewModelScope.launch { mapRepository.updateLayer(l1.copy(displayOrder = o2)); mapRepository.updateLayer(l2.copy(displayOrder = o1)) } } }
    fun addLayer(name: String, category: String) { val pid = _propertyId.value ?: return; val mid = _planId.value ?: return; val order = (_layers.value.maxOfOrNull { it.displayOrder } ?: -1) + 1; viewModelScope.launch { mapRepository.insertLayer(LayerEntity(propertyId = pid, planId = mid, name = name, category = category, displayOrder = order)) } }
    fun completeLocationAction(location: LocationResult.Success, purpose: LocationRequestPurpose) { 
        _currentPhoneLocation.value = location
        if (purpose == LocationRequestPurpose.CreatePoint) handleLocationForCreatePoint(location)
        setPendingLocationPurpose(null)
        _locationIssue.value = null
    }

    private fun resolveValidActiveLayer(propId: UUID?, planId: UUID?, layers: List<LayerEntity>, currentId: UUID?): UUID? {
        if (propId == null || planId == null || layers.isEmpty()) return null
        val requested = layers.find { it.id == currentId }
        if (requested != null && requested.isVisible && !requested.isLocked && requested.deletedAt == null && requested.propertyId == propId && requested.planId == planId) return currentId
        return layers.sortedBy { it.displayOrder }.firstOrNull { it.isVisible && !it.isLocked && it.deletedAt == null && it.propertyId == propId && it.planId == planId }?.id
    }

    private fun calculateBounds(vertices: List<Pair<Double, Double>>): Pair<Pair<Double, Double>, Pair<Double, Double>> {
        if (vertices.isEmpty()) return Pair(Pair(0.0, 0.0), Pair(0.0, 0.0))
        var minLng = Double.POSITIVE_INFINITY
        var minLat = Double.POSITIVE_INFINITY
        var maxLng = Double.NEGATIVE_INFINITY
        var maxLat = Double.NEGATIVE_INFINITY
        for (v in vertices) {
            minLng = minOf(minLng, v.first)
            minLat = minOf(minLat, v.second)
            maxLng = maxOf(maxLng, v.first)
            maxLat = maxOf(maxLat, v.second)
        }
        return Pair(Pair(minLng, minLat), Pair(maxLng, maxLat))
    }

    fun cancelGuidedCreation() {
        _guidedSession.value = null
        savedStateHandle.remove<String>(KEY_GUIDED_SESSION_ID)
        savedStateHandle.remove<String>(KEY_GUIDED_PRESET_ID)
        savedStateHandle.remove<String>(KEY_GUIDED_DRAFT_ID)
        savedStateHandle.remove<String>(KEY_GUIDED_ITEM_ID)
        savedStateHandle.remove<String>(KEY_GUIDED_PHASE)
        savedStateHandle.remove<String>(KEY_GUIDED_NAME)
        savedStateHandle.remove<String>(KEY_GUIDED_TRACKING)
        _pendingGuidedPreset.value = null
        _showPlacementMethod.value = false
        _showGuidedAddMenu.value = false
        setPendingLocationPurpose(null)
        clearStagedPhoto()
        _saveOutcome.value = null
        resetEditorStates()
        if (_editingMode.value in listOf(MapEditingMode.AddPoint, MapEditingMode.AddLine, MapEditingMode.AddPolygon)) {
            _editingMode.value = MapEditingMode.Select
        }
    }

    fun tryCancelGuidedCreation() {
        if (isActualEditorDirty()) {
            _discardAction.value = PendingEditDiscardAction.DiscardGuidedCreation
            _showDiscardEditDialog.value = true
        } else {
            cancelGuidedCreation()
        }
    }

    fun selectPersistedFeature(feature: MapFeatureEntity?, requestCameraFocus: Boolean = true) {
        _selectedPersistedFeature.value = feature
        if (feature != null) {
            _featureEditorFeature.value = feature
            _featureEditorOpen.value = true
            _isNewUnsavedFeature.value = false
            _featureEditorTarget.value = FeatureEditorTarget.Persisted(feature.id)
            initializeSystemItemLinkState(feature.id, SystemItemPolicy.MAP_ONLY, feature.infrastructureItemId, false)
            if (requestCameraFocus) {
                val geomRes = GeometryUtils.parseFeatureGeometry(feature.geometryJson, feature.geometryType)
                geomRes.onSuccess { vertices ->
                    if (feature.geometryType == "POINT") {
                        _cameraFocus.value = MapCameraFocus.Point(vertices[0].second, vertices[0].first, 17f)
                    } else {
                        val bounds = calculateBounds(vertices)
                        _cameraFocus.value = MapCameraFocus.Bounds(bounds.first, bounds.second, 100)
                    }
                }
            }
        } else {
            _featureEditorOpen.value = false
            _featureEditorTarget.value = null
            _featureEditorFeature.value = null
        }
    }

    fun selectFeatureById(id: UUID) {
        viewModelScope.launch {
            val f = mapRepository.getFeatureById(id)
            if (f != null) selectPersistedFeature(f)
        }
    }

    fun requestLocation(purpose: LocationRequestPurpose) {
        _isLocatingPhone.value = true
        _locationIssue.value = null
        setPendingLocationPurpose(purpose)
        viewModelScope.launch {
            val result = locationProvider.getCurrentLocation()
            _isLocatingPhone.value = false
            when (result) {
                is LocationResult.Success -> {
                    val isOld = (System.currentTimeMillis() - result.timestampMillis) > 300000 // 5 mins
                    val isPoor = result.accuracyMeters > 15f
                    if (isOld && isPoor) {
                        _locationIssue.value = LocationIssue(
                            LocationIssueType.CachedAndPoorAccuracy,
                            R.string.location_issue_cached_and_poor,
                            canRetry = true,
                            canUseAnyway = true,
                            cachedLocation = result,
                            purpose = purpose
                        )
                    } else if (isPoor) {
                        _locationIssue.value = LocationIssue(
                            LocationIssueType.PoorAccuracy,
                            R.string.location_issue_poor_accuracy,
                            canRetry = true,
                            canUseAnyway = true,
                            cachedLocation = result,
                            purpose = purpose
                        )
                    } else if (isOld) {
                        _locationIssue.value = LocationIssue(
                            LocationIssueType.CachedLocation,
                            R.string.location_issue_cached,
                            canRetry = true,
                            canUseAnyway = true,
                            cachedLocation = result,
                            purpose = purpose
                        )
                    } else {
                        completeLocationAction(result, purpose)
                    }
                }
                LocationResult.PermissionDenied -> handleTransientDenial(purpose)
                LocationResult.PermanentlyDenied -> handlePermanentDenial(purpose)
                LocationResult.ProviderDisabled -> {
                    _locationIssue.value = LocationIssue(LocationIssueType.ProviderDisabled, R.string.location_issue_provider_disabled, canOpenLocationSettings = true, purpose = purpose)
                }
                LocationResult.LocationUnavailable -> {
                    _locationIssue.value = LocationIssue(LocationIssueType.LocationUnavailable, R.string.location_issue_unavailable, canRetry = true, purpose = purpose)
                }
                LocationResult.Timeout -> {
                    _locationIssue.value = LocationIssue(LocationIssueType.Timeout, R.string.location_issue_timeout, canRetry = true, purpose = purpose)
                }
                is LocationResult.Error -> {
                    _locationIssue.value = LocationIssue(LocationIssueType.GenericError, R.string.location_issue_generic, canRetry = true, purpose = purpose)
                }
            }
        }
    }

    fun handlePermanentDenial(purpose: LocationRequestPurpose) {
        _locationIssue.value = LocationIssue(LocationIssueType.PermissionPermanentlyDenied, R.string.location_issue_permission_permanently_denied, purpose = purpose, canOpenAppSettings = true, canContinueManually = (purpose == LocationRequestPurpose.CreatePoint))
    }

    fun handleTransientDenial(purpose: LocationRequestPurpose) {
        _locationIssue.value = LocationIssue(LocationIssueType.PermissionDenied, R.string.location_issue_permission_denied, purpose = purpose, canRetry = true, canContinueManually = (purpose == LocationRequestPurpose.CreatePoint))
    }

    fun setPendingLocationPurpose(purpose: LocationRequestPurpose?) {
        _pendingLocationPurpose.value = purpose
        savedStateHandle[KEY_PENDING_PURPOSE] = purpose
    }

    fun saveFeature(feature: MapFeatureEntity) {
        _isSavingFeature.value = true
        _featureOperationErrorRes.value = null
        viewModelScope.launch {
            try {
                saveGate.withLock {
                    val linkSession = _linkEditorSession.value
                    val linkedItemId = when (val sel = linkSession?.currentSelection) {
                        is SystemItemLinkSelection.Existing -> sel.itemId
                        is SystemItemLinkSelection.PendingDraft -> sel.draftId
                        is SystemItemLinkSelection.CreateSuggested -> linkSession.pendingDraft?.id
                        else -> null
                    }
                    
                    val featureToSave = feature.copy(infrastructureItemId = linkedItemId)
                    
                    val itemToCreate = if (linkSession != null) {
                        val draft = linkSession.pendingDraft
                        if (draft != null && (linkSession.currentSelection is SystemItemLinkSelection.PendingDraft || linkSession.currentSelection == SystemItemLinkSelection.CreateSuggested)) {
                            InfrastructureItemEntity(
                                id = draft.id,
                                propertyId = draft.propertyId,
                                name = draft.name,
                                category = draft.category,
                                subtype = draft.subtype,
                                isEmergencyItem = draft.isEmergencyItem,
                                emergencyInstructions = draft.emergencyInstructions,
                                status = "Active"
                            )
                        } else null
                    } else null

                    mapRepository.saveFeatureWithOptionalItem(featureToSave, itemToCreate)

                    val staged = _stagedPhoto.value
                    if (staged is StagedCreationPhotoState.Ready) {
                        val uri = android.net.Uri.parse(staged.uri)
                        val owner = AttachmentOwner.MapFeature(featureToSave.propertyId, featureToSave.id)
                        val photoResult = attachmentRepository.importAttachment(
                            owner = owner,
                            uri = uri,
                            type = AttachmentType.Photo,
                            customDisplayName = "Feature Photo",
                            caption = null,
                            cameraCaptureToken = staged.cameraCaptureToken
                        )
                        if (photoResult is AttachmentWriteResult.Success) {
                            consumeStagedPhotoState()
                            _saveOutcome.value = GuidedSaveOutcome.Success(featureToSave.id)
                            cancelGuidedCreation()
                            _featureEditorOpen.value = false
                        } else {
                            _saveOutcome.value = GuidedSaveOutcome.FeatureSavedPhotoFailed(featureToSave.propertyId, featureToSave.id)
                        }
                    } else {
                        _saveOutcome.value = GuidedSaveOutcome.Success(featureToSave.id)
                        cancelGuidedCreation()
                        _featureEditorOpen.value = false
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _featureOperationErrorRes.value = R.string.error_save_failed
            } finally {
                _isSavingFeature.value = false
            }
        }
    }

    fun deleteFeature(id: UUID) {
        if (_isDeletingFeature.value) return
        val pid = _propertyId.value ?: return
        val mid = _planId.value ?: return
        _isDeletingFeature.value = true
        _featureOperationErrorRes.value = null
        viewModelScope.launch {
            try {
                mapRepository.softDeleteFeatureWithAttachments(pid, mid, id)
                _featureEditorOpen.value = false
                _featureEditorTarget.value = null
                _selectedPersistedFeature.value = null
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _featureOperationErrorRes.value = R.string.error_delete_failed
            } finally {
                _isDeletingFeature.value = false
            }
        }
    }

    fun beginMovePoint(featureId: UUID) {
        viewModelScope.launch {
            val f = _features.value.find { it.id == featureId } ?: mapRepository.getFeatureById(featureId) ?: run {
                _featureEditorTarget.value = null
                _featureEditorFeature.value = null
                _featureEditorOpen.value = false
                return@launch
            }
            val layer = _layers.value.find { it.id == f.layerId }
            if (layer?.isLocked == true) {
                _featureOperationErrorRes.value = R.string.layer_is_locked
                return@launch
            }
            val coordsRes = GeometryUtils.parseFeatureGeometry(f.geometryJson, f.geometryType)
            coordsRes.onSuccess { vertices ->
                if (vertices.isNotEmpty()) {
                    _pointMoveState.value = PointMoveState(featureId, vertices[0].first, vertices[0].second)
                    _editingMode.value = MapEditingMode.Select
                    _isPointMoveActive.value = true
                    _featureEditorOpen.value = false
                    _featureEditorTarget.value = FeatureEditorTarget.Persisted(featureId)
                }
            }
        }
    }

    fun proposePointMove(longitude: Double, latitude: Double, isDragging: Boolean = false) {
        _pointMoveState.value = _pointMoveState.value?.copy(proposedLongitude = longitude, proposedLatitude = latitude, isDragging = isDragging)
    }

    fun finishPointMoveDrag() { _pointMoveState.value = _pointMoveState.value?.copy(isDragging = false) }
    fun cancelPointMoveDrag() { _pointMoveState.value = _pointMoveState.value?.copy(proposedLongitude = null, proposedLatitude = null, isDragging = false) }

    fun confirmPointMove() {
        val s = _pointMoveState.value ?: return
        val lng = s.proposedLongitude ?: return
        val lat = s.proposedLatitude ?: return
        
        _isSavingFeature.value = true
        _featureOperationErrorRes.value = null
        viewModelScope.launch {
            try {
                val f = mapRepository.getFeatureById(s.featureId) ?: run {
                    _pointMoveState.value = null
                    _isPointMoveActive.value = false
                    _featureEditorTarget.value = null
                    _featureEditorFeature.value = null
                    _featureEditorOpen.value = false
                    return@launch
                }
                mapRepository.updateFeature(f.copy(geometryJson = GeometryUtils.buildPointGeoJson(lng, lat)))
                _pointMoveState.value = null
                _isPointMoveActive.value = false
                selectPersistedFeature(mapRepository.getFeatureById(s.featureId), requestCameraFocus = false)
            } catch (e: Exception) {
                _featureOperationErrorRes.value = R.string.error_save_failed
            } finally {
                _isSavingFeature.value = false
            }
        }
    }

    fun cancelPointMove() {
        _pointMoveState.value = null
        _isPointMoveActive.value = false
    }

    fun beginPersistedShapeEdit(featureId: UUID) {
        viewModelScope.launch {
            val f = _features.value.find { it.id == featureId } ?: mapRepository.getFeatureById(featureId) ?: run {
                _featureEditorTarget.value = null
                _featureEditorFeature.value = null
                _featureEditorOpen.value = false
                return@launch
            }
            val pid = f.propertyId; val mid = f.planId; val lid = f.layerId
            if (f.geometryType == "LINESTRING") {
                val coords = GeometryUtils.parseLineStringGeometry(f.geometryJson)
                _lineEditState.value = LineEditState(featureId, pid, mid, lid, coords, coords, GeometryUtils.calculatePathLength(coords), GeometryUtils.calculatePathLength(coords))
                _editingMode.value = MapEditingMode.EditLine
                _isLineEditDirty.value = false
                _featureEditorTarget.value = FeatureEditorTarget.EditPersistedLine(featureId)
            } else if (f.geometryType == "POLYGON") {
                val res = GeometryUtils.parsePolygonGeoJson(f.geometryJson)
                if (res is PolygonParseResult.Success) {
                    _polygonEditState.value = PolygonEditState(featureId, pid, mid, lid, res.vertices, res.vertices, GeometryUtils.calculateSphericalArea(res.vertices), GeometryUtils.calculateSphericalArea(res.vertices), GeometryUtils.calculatePolygonPerimeter(res.vertices), GeometryUtils.calculatePolygonPerimeter(res.vertices))
                    _editingMode.value = MapEditingMode.EditPolygon
                    _isPolygonEditDirty.value = false
                    _featureEditorTarget.value = FeatureEditorTarget.EditPersistedPolygon(featureId)
                }
            }
            _featureEditorOpen.value = false
        }
    }

    fun beginVertexDrag(index: Int) { 
        _lineEditState.value = _lineEditState.value?.copy(selectedVertexIndex = index, isDraggingVertex = true, dragStartVertices = _lineEditState.value?.workingVertices) 
    }
    fun updateVertexDrag(longitude: Double, latitude: Double) {
        val s = _lineEditState.value ?: return
        val idx = s.selectedVertexIndex ?: return
        val newVertices = s.workingVertices.toMutableList()
        newVertices[idx] = Pair(longitude, latitude)
        _lineEditState.value = s.copy(workingVertices = newVertices, workingLengthMeters = GeometryUtils.calculatePathLength(newVertices))
    }
    fun finishVertexDrag() {
        _lineEditState.value?.let { s ->
             val start = s.dragStartVertices ?: s.workingVertices
             _lineEditState.value = s.copy(workingVertices = start).pushUndo().copy(workingVertices = s.workingVertices, selectedVertexIndex = null, isDraggingVertex = false, dragStartVertices = null)
             _isLineEditDirty.value = true
        }
    }
    fun cancelVertexDrag() { _lineEditState.value = _lineEditState.value?.copy(selectedVertexIndex = null, isDraggingVertex = false) }

    fun insertVertex(index: Int, coordinate: Pair<Double, Double>) {
        val s = _lineEditState.value ?: return
        val newVertices = s.workingVertices.toMutableList()
        newVertices.add(index, coordinate)
        _lineEditState.value = s.pushUndo().copy(workingVertices = newVertices, selectedVertexIndex = index, workingLengthMeters = GeometryUtils.calculatePathLength(newVertices))
        _isLineEditDirty.value = true
    }

    fun deleteSelectedVertex() {
        val s = _lineEditState.value ?: return
        val idx = s.selectedVertexIndex ?: return
        if (s.workingVertices.size <= 2) return
        val newVertices = s.workingVertices.toMutableList()
        newVertices.removeAt(idx)
        _lineEditState.value = s.pushUndo().copy(workingVertices = newVertices, selectedVertexIndex = null, workingLengthMeters = GeometryUtils.calculatePathLength(newVertices))
        _isLineEditDirty.value = true
    }

    fun undoLineEdit() {
        _lineEditState.value?.let { s ->
            if (s.undoStack.isNotEmpty()) {
                _lineEditState.value = s.popUndo()
                _isLineEditDirty.value = _lineEditState.value?.workingVertices != _lineEditState.value?.originalVertices
            }
        }
    }

    fun saveLineEdit() {
        val s = _lineEditState.value ?: return
        if (s.workingVertices == s.originalVertices) return
        _isSavingFeature.value = true
        _featureOperationErrorRes.value = null
        viewModelScope.launch {
            try {
                val f = mapRepository.getFeatureById(s.featureId)
                if (f != null) {
                    mapRepository.updateFeature(f.copy(geometryJson = GeometryUtils.buildLineStringGeoJson(s.workingVertices)))
                }
                finishPersistedGeometryEdit(s.featureId, reopen = true)
            } catch (e: Exception) {
                _featureOperationErrorRes.value = R.string.error_save_failed
            } finally {
                _isSavingFeature.value = false
            }
        }
    }
    
    fun tryCancelLineEdit() {
        _lineEditState.value?.let { s ->
            if (s.workingVertices != s.originalVertices) {
                _discardAction.value = PendingEditDiscardAction.CancelLineEdit
                _showDiscardEditDialog.value = true
            } else {
                viewModelScope.launch { finishPersistedGeometryEdit(s.featureId, reopen = true) }
            }
        } ?: run {
            _editingMode.value = MapEditingMode.Select
        }
    }

    fun beginPolygonVertexDrag(index: Int) { 
        _polygonEditState.value = _polygonEditState.value?.copy(selectedVertexIndex = index, draggingVertexIndex = index, dragStartVertices = _polygonEditState.value?.workingVertices) 
    }
    fun updatePolygonVertexDrag(longitude: Double, latitude: Double) {
        val s = _polygonEditState.value ?: return
        val idx = s.selectedVertexIndex ?: return
        val newVertices = s.workingVertices.toMutableList()
        newVertices[idx] = Pair(longitude, latitude)
        _polygonEditState.value = s.copy(workingVertices = newVertices).withValidation()
    }
    fun finishPolygonVertexDrag() {
        _polygonEditState.value?.let { s ->
            val start = s.dragStartVertices ?: s.workingVertices
            _polygonEditState.value = s.copy(workingVertices = start).pushUndo().copy(workingVertices = s.workingVertices, selectedVertexIndex = null, draggingVertexIndex = null, dragStartVertices = null)
            _isPolygonEditDirty.value = true
        }
    }
    fun cancelPolygonVertexDrag() { _polygonEditState.value = _polygonEditState.value?.copy(selectedVertexIndex = null, draggingVertexIndex = null) }

    fun insertPolygonVertex(index: Int, coordinate: Pair<Double, Double>) {
        val s = _polygonEditState.value ?: return
        val newVertices = s.workingVertices.toMutableList()
        newVertices.add(index, coordinate)
        _polygonEditState.value = s.pushUndo().copy(workingVertices = newVertices, selectedVertexIndex = index).withValidation()
        _isPolygonEditDirty.value = true
    }

    fun undoPolygonEdit() {
        _polygonEditState.value?.let { s ->
            if (s.undoStack.isNotEmpty()) {
                _polygonEditState.value = s.popUndo()
                _isPolygonEditDirty.value = _polygonEditState.value?.workingVertices != _polygonEditState.value?.originalVertices
            }
        }
    }

    fun savePolygonEdit() {
        val s = _polygonEditState.value ?: return
        if (s.workingVertices == s.originalVertices) return
        if (s.validation !is PolygonValidationResult.Valid) return
        
        _isSavingFeature.value = true
        _featureOperationErrorRes.value = null
        viewModelScope.launch {
            try {
                val f = mapRepository.getFeatureById(s.featureId)
                if (f != null) {
                    mapRepository.updateFeature(f.copy(geometryJson = GeometryUtils.buildPolygonGeoJson(s.workingVertices)))
                }
                finishPersistedGeometryEdit(s.featureId, reopen = true)
            } catch (e: Exception) {
                _featureOperationErrorRes.value = R.string.error_save_failed
            } finally {
                _isSavingFeature.value = false
            }
        }
    }

    fun tryCancelPolygonEdit() {
        _polygonEditState.value?.let { s ->
            if (s.workingVertices != s.originalVertices) {
                _discardAction.value = PendingEditDiscardAction.CancelPolygonEdit
                _showDiscardEditDialog.value = true
            } else {
                viewModelScope.launch { finishPersistedGeometryEdit(s.featureId, reopen = true) }
            }
        } ?: run {
            _editingMode.value = MapEditingMode.Select
        }
    }

    fun beginAddPoint(): Boolean {
        if (uiState.value.addToMapAvailability.isAvailable) {
            _editingMode.value = MapEditingMode.AddPoint
            return true
        }
        return false
    }

    fun beginAddLine(): Boolean {
        if (uiState.value.addToMapAvailability.isAvailable) {
            _editingMode.value = MapEditingMode.AddLine
            _draftVertices.value = emptyList()
            return true
        }
        return false
    }

    fun beginAddPolygon(): Boolean {
        if (uiState.value.addToMapAvailability.isAvailable) {
            val pid = _propertyId.value ?: return false
            val mid = _planId.value ?: return false
            val lid = _activeLayerId.value ?: return false
            _editingMode.value = MapEditingMode.AddPolygon
            _polygonDraft.value = PolygonDraftState(propertyId = pid, planId = mid, layerId = lid)
            return true
        }
        return false
    }

    fun addDraftVertex(longitude: Double, latitude: Double) {
        val current = _draftVertices.value.toMutableList()
        val newPoint = Pair(longitude, latitude)
        if (current.isNotEmpty() && GeometryUtils.areCoordinatesEqual(current.last(), newPoint)) return
        current.add(newPoint)
        _draftVertices.value = current
    }

    fun undoDraftVertex() {
        val current = _draftVertices.value.toMutableList()
        if (current.isNotEmpty()) {
            current.removeAt(current.size - 1)
            _draftVertices.value = current
        }
    }

    fun finishDraftLine(): UUID? {
        val vertices = _draftVertices.value
        if (vertices.size < 2) return null
        val pid = _propertyId.value ?: return null
        val mid = _planId.value ?: return null
        val lid = _activeLayerId.value ?: return null
        
        val id = UUID.randomUUID()
        val session = _guidedSession.value
        val label = session?.suggestedLabel ?: context.getString(R.string.label_default_line)
        
        val feature = MapFeatureEntity(id = id, propertyId = pid, planId = mid, layerId = lid, geometryType = "LINESTRING", geometryJson = GeometryUtils.buildLineStringGeoJson(vertices), coordinateSpace = "GEOGRAPHIC", styleJson = "{}", accuracySource = "MANUAL", label = label)
        
        _featureEditorFeature.value = feature
        _featureEditorOpen.value = true
        _isNewUnsavedFeature.value = true
        _featureEditorTarget.value = FeatureEditorTarget.NewLine(id)
        _draftVertices.value = emptyList()
        
        if (session?.expectedGeometry == GuidedMapGeometry.ROUTE) {
            savedStateHandle[KEY_GUIDED_PHASE] = GuidedMappingPhase.REVIEWING.name
            savedStateHandle[KEY_GUIDED_DRAFT_ID] = id.toString()
            _guidedSession.value = session.copy(phase = GuidedMappingPhase.REVIEWING, targetDraftId = id)
        }
        
        val policy = session?.preset?.systemItemPolicy ?: SystemItemPolicy.MAP_ONLY
        initializeSystemItemLinkState(id, policy, null, true, suggestedName = session?.suggestedLabel, defaultCategory = session?.preset?.defaultCategory)
        return id
    }

    fun cancelDraftLine() { _draftVertices.value = emptyList(); _editingMode.value = MapEditingMode.Select }

    fun addPolygonVertex(longitude: Double, latitude: Double) {
        val s = _polygonDraft.value ?: return
        val newPoint = Pair(longitude, latitude)
        if (s.vertices.isNotEmpty() && GeometryUtils.areCoordinatesEqual(s.vertices.last(), newPoint)) return
        val newVertices = s.vertices.toMutableList()
        newVertices.add(newPoint)
        _polygonDraft.value = s.copy(vertices = newVertices, validation = GeometryUtils.validatePolygonGeometry(newVertices))
    }

    fun undoPolygonVertex() {
        val s = _polygonDraft.value ?: return
        val newVertices = s.vertices.toMutableList()
        if (newVertices.isNotEmpty()) {
            newVertices.removeAt(newVertices.size - 1)
            _polygonDraft.value = s.copy(vertices = newVertices, validation = GeometryUtils.validatePolygonGeometry(newVertices))
        }
    }

    fun finishAddPolygon() {
        val s = _polygonDraft.value ?: return
        if (s.vertices.size < 3 || s.validation !is PolygonValidationResult.Valid) return
        val pid = _propertyId.value ?: return
        val mid = _planId.value ?: return
        val lid = _activeLayerId.value ?: return
        
        val id = s.id
        val session = _guidedSession.value
        val label = session?.suggestedLabel ?: context.getString(R.string.label_default_polygon)
        
        val feature = MapFeatureEntity(id = id, propertyId = pid, planId = mid, layerId = lid, geometryType = "POLYGON", geometryJson = GeometryUtils.buildPolygonGeoJson(s.vertices), coordinateSpace = "GEOGRAPHIC", styleJson = "{}", accuracySource = "MANUAL", label = label)
        
        _featureEditorFeature.value = feature
        _featureEditorOpen.value = true
        _isNewUnsavedFeature.value = true
        _featureEditorTarget.value = FeatureEditorTarget.NewPolygon(id)
        _polygonDraft.value = null
        _editingMode.value = MapEditingMode.Select
        
        if (session?.expectedGeometry == GuidedMapGeometry.AREA) {
            savedStateHandle[KEY_GUIDED_PHASE] = GuidedMappingPhase.REVIEWING.name
            savedStateHandle[KEY_GUIDED_DRAFT_ID] = id.toString()
            _guidedSession.value = session.copy(phase = GuidedMappingPhase.REVIEWING, targetDraftId = id)
        }
        
        val policy = session?.preset?.systemItemPolicy ?: SystemItemPolicy.MAP_ONLY
        initializeSystemItemLinkState(id, policy, null, true, suggestedName = session?.suggestedLabel, defaultCategory = session?.preset?.defaultCategory)
    }

    fun cancelPolygonDraft() { _polygonDraft.value = null; _editingMode.value = MapEditingMode.Select }
    
    fun addPointAt(longitude: Double, latitude: Double) {
        val pid = _propertyId.value ?: return
        val mid = _planId.value ?: return
        val lid = _activeLayerId.value ?: return
        
        val session = _guidedSession.value
        val id = session?.targetDraftId ?: UUID.randomUUID()
        val label = session?.suggestedLabel ?: context.getString(R.string.label_default_point)
        
        val feature = MapFeatureEntity(id = id, propertyId = pid, planId = mid, layerId = lid, geometryType = "POINT", geometryJson = GeometryUtils.buildPointGeoJson(longitude, latitude), coordinateSpace = "GEOGRAPHIC", styleJson = "{}", accuracySource = "MANUAL", label = label)
        _featureEditorFeature.value = feature
        _featureEditorOpen.value = true
        _isNewUnsavedFeature.value = true
        _featureEditorTarget.value = FeatureEditorTarget.NewPoint(id)
        _editingMode.value = MapEditingMode.Select
        
        if (session?.expectedGeometry == GuidedMapGeometry.LOCATION) {
            savedStateHandle[KEY_GUIDED_PHASE] = GuidedMappingPhase.REVIEWING.name
            savedStateHandle[KEY_GUIDED_DRAFT_ID] = id.toString()
            _guidedSession.value = session.copy(phase = GuidedMappingPhase.REVIEWING, targetDraftId = id)
        }
        
        val policy = session?.preset?.systemItemPolicy ?: SystemItemPolicy.MAP_ONLY
        initializeSystemItemLinkState(id, policy, null, true, suggestedName = session?.suggestedLabel, defaultCategory = session?.preset?.defaultCategory)
    }

    fun confirmDiscardEdit() {
        val action = _discardAction.value
        _showDiscardEditDialog.value = false
        _discardAction.value = null
        
        when (action) {
            PendingEditDiscardAction.CancelLineEdit -> { 
                val id = _lineEditState.value?.featureId
                if (id != null) viewModelScope.launch { finishPersistedGeometryEdit(id, reopen = true) }
                else { _lineEditState.value = null; _editingMode.value = MapEditingMode.Select }
            }
            PendingEditDiscardAction.CancelPolygonEdit -> { 
                val id = _polygonEditState.value?.featureId
                if (id != null) viewModelScope.launch { finishPersistedGeometryEdit(id, reopen = true) }
                else { _polygonEditState.value = null; _editingMode.value = MapEditingMode.Select }
            }
            PendingEditDiscardAction.DiscardNewPoint -> { 
                _newPointDraft.value = null
                _featureEditorTarget.value = null
                _featureEditorOpen.value = false
                _editingMode.value = MapEditingMode.Select 
            }
            PendingEditDiscardAction.DiscardNewLine -> { 
                _draftVertices.value = emptyList()
                _featureEditorTarget.value = null
                _featureEditorOpen.value = false
                _editingMode.value = MapEditingMode.Select 
            }
            PendingEditDiscardAction.DiscardNewPolygon -> { 
                _polygonDraft.value = null
                _featureEditorTarget.value = null
                _featureEditorOpen.value = false
                _editingMode.value = MapEditingMode.Select 
            }
            PendingEditDiscardAction.DiscardGuidedCreation -> { cancelGuidedCreation() }
            is PendingEditDiscardAction.ChangeProperty -> { setProperty(action.propertyId, force = true) }
            is PendingEditDiscardAction.ChangePlan -> { action.planId?.let { selectPlan(it, force = true) } }
            null -> {}
        }
    }

    fun dismissDiscardDialog() { _showDiscardEditDialog.value = false; _discardAction.value = null }

    fun setGuidedPreset(p: GuidedMapPreset) {
        val avail = evaluateAddToMapAvailability(
            _propertyId.value, _plan.value, _layers.value, _activeLayerId.value,
            _featureEditorTarget.value, _isSearchActive.value, _guidedSession.value,
            _editingMode.value, _pointMoveState.value, _isSavingFeature.value, _isDeletingFeature.value,
            _locationIssue.value, _isLocatingPhone.value, _pendingLocationPurpose.value,
            _starterLayerOperation.value, _isBoundaryAcknowledgmentSaving.value, _showDiscardEditDialog.value,
            _showBasemapChooser.value, _showHelpSheet.value, _showSafetyLimitations.value,
            _showLocationDetails.value, _layerPanelOpen.value
        )
        if (avail.isAvailable) {
            _pendingGuidedPreset.value = p
            if (p.geometry == GuidedMapGeometry.LOCATION) {
                _showPlacementMethod.value = true
            } else {
                startGuidedMapping(p, PlacementMethod.TAP_MAP)
            }
        } else {
            _mapErrorRes.value = avail.reasonRes
        }
    }

    fun selectGuidedPresetAndCloseMenu(p: GuidedMapPreset) {
        _showGuidedAddMenu.value = false
        setGuidedPreset(p)
    }

    fun selectGuidedLocationMethod(m: PlacementMethod) {
        val preset = _pendingGuidedPreset.value ?: return
        _showPlacementMethod.value = false
        startGuidedMapping(preset, m)
    }

    fun startGuidedMapping(p: GuidedMapPreset, m: PlacementMethod) {
        val pid = _propertyId.value ?: return
        val mid = _planId.value ?: return
        
        val sessionId = UUID.randomUUID()
        val draftId = UUID.randomUUID()
        val session = GuidedMappingSession(
            sessionId = sessionId,
            propertyId = pid,
            planId = mid,
            preset = p,
            expectedGeometry = p.geometry,
            suggestedLabel = p.suggestedLabelRes.let { context.getString(it) },
            targetDraftId = draftId,
            phase = if (m == PlacementMethod.MY_LOCATION) GuidedMappingPhase.REVIEWING else GuidedMappingPhase.SELECTING_PLACEMENT
        )
        _guidedSession.value = session
        _pendingGuidedPreset.value = null

        if (m == PlacementMethod.MY_LOCATION) {
            requestLocation(LocationRequestPurpose.CreatePoint)
        } else {
            when (p.geometry) {
                GuidedMapGeometry.LOCATION -> _editingMode.value = MapEditingMode.AddPoint
                GuidedMapGeometry.ROUTE -> {
                    _editingMode.value = MapEditingMode.AddLine
                    _draftVertices.value = emptyList()
                }
                GuidedMapGeometry.AREA -> {
                    if (p.requiresBoundaryAcknowledgment && !_boundaryAcknowledged.value) {
                        _showBoundaryAcknowledgment.value = true
                        _editingMode.value = MapEditingMode.Select
                    } else {
                        _editingMode.value = MapEditingMode.AddPolygon
                        _polygonDraft.value = PolygonDraftState(propertyId = pid, planId = mid, layerId = _activeLayerId.value!!)
                    }
                }
            }
        }
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setSearchActive(active: Boolean) { _isSearchActive.value = active; if (!active) _searchQuery.value = "" }

    fun openSearchResult(result: MapSearchResult) {
        _isSearchActive.value = false
        viewModelScope.launch {
            val feature = mapRepository.getFeatureById(result.featureId)
            selectPersistedFeature(feature)
        }
    }

    fun revealAndOpenSearchResult(result: MapSearchResult) {
        viewModelScope.launch {
            val layer = _layers.value.find { it.id == result.layerId }
            if (layer != null && !layer.isVisible) {
                mapRepository.updateLayer(layer.copy(isVisible = true))
            }
            openSearchResult(result)
        }
    }

    fun updateVertexPosition(index: Int, longitude: Double, latitude: Double) {
        _lineEditState.value?.let { s ->
            val newVertices = s.workingVertices.toMutableList()
            newVertices[index] = Pair(longitude, latitude)
            _lineEditState.value = s.copy(workingVertices = newVertices, workingLengthMeters = GeometryUtils.calculatePathLength(newVertices))
        }
        _polygonEditState.value?.let { s ->
            val newVertices = s.workingVertices.toMutableList()
            newVertices[index] = Pair(longitude, latitude)
            _polygonEditState.value = s.copy(workingVertices = newVertices).withValidation()
        }
    }

    fun selectEditVertex(index: Int) {
        _lineEditState.value = _lineEditState.value?.copy(selectedVertexIndex = index)
        _polygonEditState.value = _polygonEditState.value?.copy(selectedVertexIndex = index)
    }
}
