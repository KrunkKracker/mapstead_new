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
import kotlin.math.abs
import kotlinx.serialization.json.*

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
    val cameraInteractionSequence: Long
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
    private val _requestedSourceId = MutableStateFlow<BasemapSourceId?>(null)
    private val _activeSourceId = MutableStateFlow<BasemapSourceId?>(null)
    private val _currentAttempt = MutableStateFlow<BasemapLoadAttempt?>(null)
    private val _renderSessionId = MutableStateFlow<UUID?>(null)
    private val _basemapStatus = MutableStateFlow(BasemapLoadStatus.IDLE)
    private val _basemapGeneration = MutableStateFlow(0L)
    private val _basemapErrorRes = MutableStateFlow<Int?>(null)
    private val _isUsingFallback = MutableStateFlow(false)
    private val _fallbackAttempted = MutableStateFlow(false)
    private val _showBackupChooser = MutableStateFlow(false)
    private val _retryPrimaryAvailable = MutableStateFlow(false)
    
    // Interaction state
    private val _cameraInteractionSequence = MutableStateFlow(0L)
    private var nextAttemptId = 1L
    
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

    private val terminalAttempts = mutableSetOf<Long>()
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
                    val previous = _preferredBasemapId.value
                    _preferredBasemapId.value = id
                    isPreferencesReady = true
                    
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
        }
    ) { p1, p2, p3 ->
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
            cameraInteractionSequence = p3.d
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
            propertyId = c.propertyId, propertyName = c.propertyName, propertyLatitude = c.propertyData?.latitude, propertyLongitude = c.propertyData?.longitude, plan = c.plan, layers = lrs.layers, activeLayerId = lrs.activeLayerId, visibleFeatures = fts.visibleFeatures, hasMappedFeatures = fts.features.isNotEmpty(), selectedFeature = fts.selectedFeature, activeEditFeatureId = ee.pointMoveState?.featureId ?: ee.lineEditState?.featureId ?: ee.polygonEditState?.featureId ?: eg.polygonDraft?.id, cameraFocus = st.cameraFocus, editingMode = st.editingMode, mapLoading = st.mapLoading, mapErrorRes = st.mapErrorRes, layerPanelOpen = st.layerPanelOpen, featureEditorOpen = es.open, canAddPoint = wc.addToMapAvailability.isAvailable, canAddLine = wc.addToMapAvailability.isAvailable, canAddArea = wc.addToMapAvailability.isAvailable, isSavingFeature = es.isSaving, isDeletingFeature = es.isDeleting, featureOperationErrorRes = es.operationErrorRes, currentPhoneLocation = loc.location, currentPhoneLocationQuality = locationQuality, isLocatingPhone = loc.isLocating, locationIssue = loc.issue, pendingLocationPurpose = loc.purpose, showPermissionRationale = loc.rationale, hasRequestedLocationOnce = loc.requestedOnce, draftVertices = eg.draftVertices, canFinishLine = eg.draftVertices.size >= 2, 
            
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
            
            polygonDraft = eg.polygonDraft, liveAreaMeters = eg.polygonDraft?.vertices?.let { GeometryUtils.calculateSphericalArea(it) } ?: 0.0, livePerimeterMeters = eg.polygonDraft?.vertices?.let { GeometryUtils.calculatePolygonPerimeter(it) } ?: 0.0, polygonValidationRes = polygonValidationMsg, canFinishPolygon = (eg.polygonDraft?.vertices?.size ?: 0) >= 3 && eg.polygonDraft?.validation is PolygonValidationResult.Valid, featureEditorTarget = es.target, featureEditorFeature = es.feature, pointMoveState = ee.pointMoveState, lineEditState = ee.lineEditState, polygonEditState = ee.polygonEditState, isLineEditDirty = ee.isLineEditDirty, showDiscardEditDialog = eu.showDiscardDialog, discardAction = eu.discardAction, canSaveLineEdit = ee.lineEditState?.let { it.workingVertices != it.originalVertices && GeometryUtils.validateLineGeometry(it.workingVertices) } ?: false, canSavePolygonEdit = ee.polygonEditState?.let { it.workingVertices != it.originalVertices && it.validation is PolygonValidationResult.Valid } ?: false, canSavePointMove = hasProposedMove,
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
            isEditorDirty = isActualEditorDirty(), sessionFeatureId = es.feature?.id, mapRecoveryActive = st.mapRecoveryActive, searchQuery = sr.query, searchResults = sr.results, isSearchActive = sr.searchActive, showLocationDetails = loc.showDetails, showStarterLayersDialog = ws.showStarterLayersDialog, showBoundaryAcknowledgment = ws.showBoundaryAcknowledgment, pendingGuidedPreset = ws.pendingGuidedPreset, guidedSession = ws.guidedSession, showGuidedAddMenu = ws.showGuidedAddMenu, guidanceDismissed = wp.guidanceDismissed, starterLayersCreated = wp.starterLayersCreated, starterLayersEligible = wp.starterLayersEligible, starterLayerOperation = wp.starterLayerOperation, starterLayerOperationActive = wp.starterLayerOperation != StarterLayerOperation.Idle, starterLayerErrorRes = wp.starterLayerErrorRes, isSavingBoundaryAcknowledgment = wp.isBoundaryAcknowledgmentSaving, boundaryAcknowledgmentErrorRes = wp.boundaryAcknowledgmentErrorRes, showPlacementMethod = wc.showPlacementMethod, showBasemapChooser = wc.showBasemapChooser, showHelpSheet = wc.showHelpSheet, showSafetyLimitations = wc.showSafetyLimitations, addToMapAvailability = wc.addToMapAvailability, isWorkflowActive = wc.addToMapAvailability.isAvailable == false && wc.addToMapAvailability.reasonRes == R.string.exclusive_workflow_active, measurementSystem = wc.measurementSystem, labelError = eu.labelError, accuracyError = eu.accuracyError, systemItemDraft = ep.systemItemDraft, linkSelection = eu.linkSelection, initialLinkSelection = eu.initialLinkSelection,
            isNewUnsavedFeature = eg.isNewUnsaved, isPointMoveActive = ee.isPointMoveActive,
            stagedPhoto = ep.stagedPhoto,
            newPointDraft = ep.newPointDraft,
            saveOutcome = saveOutcome,
            pendingPhotoPurpose = ep.pendingPhotoPurpose,
            guidedPrefill = ws.guidedSession?.let { session -> val suggestedLayerId = session.preset.suggestedLayer?.let { type -> userPreferencesRepository.getStarterLayerBinding(session.planId.toString(), type, wc.userPreferences.starterLayerBindings) }; GuidedFeaturePrefill(sessionId = session.sessionId, draftId = session.targetDraftId ?: UUID.randomUUID(), suggestedLabelRes = session.preset.suggestedLabelRes, suggestedLabel = session.suggestedLabel, suggestedCategory = session.preset.defaultCategory, suggestedLayerId = suggestedLayerId, systemItemPolicy = session.preset.systemItemPolicy, presetStyle = session.preset.presetStyle) },
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
                (_polygonDraft.value?.vertices?.isNotEmpty() ?: false)
        
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
        
        val status = _basemapStatus.value
        when (status) {
            BasemapLoadStatus.IDLE -> {
                ensureInitialBasemapLoad()
            }
            BasemapLoadStatus.LOADING_PRIMARY -> {
                val current = _requestedSourceId.value ?: basemapProvider.getPrimaryBasemaps().find { it.preferredId == _preferredBasemapId.value }?.sourceId
                if (current != null) {
                    issueAttempt(current, BasemapRole.PRIMARY, BasemapLoadAttemptReason.RECREATION)
                }
            }
            BasemapLoadStatus.LOADING_BACKUP -> {
                val current = _requestedSourceId.value ?: basemapProvider.resolveDefaultBackup(_preferredBasemapId.value)
                issueAttempt(current, BasemapRole.BACKUP, BasemapLoadAttemptReason.RECREATION)
            }
            BasemapLoadStatus.LOADED -> {
                val active = _activeSourceId.value
                if (active != null) {
                    issueAttempt(active, basemapProvider.getDefinition(active)?.role ?: BasemapRole.PRIMARY, BasemapLoadAttemptReason.RECREATION)
                }
            }
            BasemapLoadStatus.FAILED -> {
                // Preserve terminal state.
            }
        }
    }

    private fun ensureInitialBasemapLoad() {
        if (isRenderSessionReady && isPreferencesReady && _basemapStatus.value == BasemapLoadStatus.IDLE) {
            startPrimaryLoad(_preferredBasemapId.value)
        }
    }

    private fun issueAttempt(sourceId: BasemapSourceId, role: BasemapRole, reason: BasemapLoadAttemptReason) {
        val def = basemapProvider.getDefinition(sourceId) ?: return
        val attemptId = nextAttemptId++
        
        val attempt = BasemapLoadAttempt(
            semanticGeneration = _basemapGeneration.value,
            attemptId = attemptId,
            renderSessionId = _renderSessionId.value,
            sourceId = sourceId,
            provider = def.provider,
            role = role,
            reason = reason
        )
        
        _currentAttempt.value = attempt
        _requestedSourceId.value = sourceId
        _basemapStatus.value = if (role == BasemapRole.PRIMARY) BasemapLoadStatus.LOADING_PRIMARY else BasemapLoadStatus.LOADING_BACKUP
    }

    private fun reapplyActiveSource(sourceId: BasemapSourceId) {
        issueAttempt(sourceId, basemapProvider.getDefinition(sourceId)?.role ?: BasemapRole.PRIMARY, BasemapLoadAttemptReason.REPAIR)
    }

    fun addPointAt(lng: Double, lat: Double) {
        if (_isSavingFeature.value) return
        val pid = _propertyId.value ?: return; val mid = _planId.value ?: return; val lid = _activeLayerId.value ?: return; val session = _guidedSession.value
        val useId = session?.targetDraftId ?: UUID.randomUUID()
        _newPointDraft.value = NewPointDraftState(id = useId, propertyId = pid, planId = mid, layerId = lid, longitude = lng, latitude = lat, accuracyMeters = null)
        if (session?.expectedGeometry == GuidedMapGeometry.LOCATION) { 
            savedStateHandle[KEY_GUIDED_PHASE] = GuidedMappingPhase.REVIEWING.name
            savedStateHandle[KEY_GUIDED_DRAFT_ID] = useId.toString()
            _guidedSession.value = session.copy(phase = GuidedMappingPhase.REVIEWING, targetDraftId = useId) 
        }
        val feature = MapFeatureEntity(id = useId, propertyId = pid, planId = mid, layerId = lid, geometryType = "POINT", geometryJson = GeometryUtils.buildPointGeoJson(lng, lat), coordinateSpace = "GEOGRAPHIC", styleJson = "{}", accuracySource = "Manual", label = session?.suggestedLabel ?: context.getString(R.string.label_default_point))
        initializeSystemItemLinkState(featureId = useId, policy = session?.preset?.systemItemPolicy ?: SystemItemPolicy.MAP_ONLY, existingItemId = null, isNewFeature = true, suggestedName = session?.suggestedLabel, defaultCategory = session?.preset?.defaultCategory)
        _featureEditorFeature.value = feature; _featureEditorOpen.value = true; _isNewUnsavedFeature.value = true; _featureEditorTarget.value = FeatureEditorTarget.NewPoint(useId); _selectedPersistedFeature.value = null; _editingMode.value = MapEditingMode.Select
    }

    fun confirmDiscardEdit() {
        val action = _discardAction.value; _showDiscardEditDialog.value = false; _discardAction.value = null
        when (action) {
            PendingEditDiscardAction.CancelLineEdit -> cancelLineEdit()
            PendingEditDiscardAction.CancelPolygonEdit -> cancelPolygonEdit()
            PendingEditDiscardAction.DiscardNewPoint, PendingEditDiscardAction.DiscardNewLine, PendingEditDiscardAction.DiscardNewPolygon, PendingEditDiscardAction.DiscardGuidedCreation -> cancelGuidedCreation()
            is PendingEditDiscardAction.ChangeProperty -> setProperty(action.propertyId, force = true)
            is PendingEditDiscardAction.ChangePlan -> { val id = action.planId; if (id != null) selectPlan(id, force = true) }
            null -> {}
        }
    }

    fun tryCancelGuidedCreation() {
        val session = _guidedSession.value ?: run { cancelGuidedCreation(); return }
        val hasGeometry = (_newPointDraft.value != null) || 
                _draftVertices.value.isNotEmpty() || 
                (_polygonDraft.value?.vertices?.isNotEmpty() ?: false)
        
        if (hasGeometry) {
            _discardAction.value = PendingEditDiscardAction.DiscardGuidedCreation
            _showDiscardEditDialog.value = true
        } else {
            cancelGuidedCreation()
        }
    }

    fun dismissDiscardDialog() { _showDiscardEditDialog.value = false; _discardAction.value = null }

    fun cancelGuidedCreation() {
        val token = savedStateHandle.get<String>(KEY_GUIDED_PHOTO_TOKEN)
        if (token != null) {
            attachmentRepository.deleteTempCameraCapture(token)
        }
        val inFlightToken = savedStateHandle.get<String>(KEY_IN_FLIGHT_TOKEN)
        if (inFlightToken != null) {
            attachmentRepository.deleteTempCameraCapture(inFlightToken)
        }

        savedStateHandle[KEY_GUIDED_SESSION_ID] = null
        savedStateHandle[KEY_GUIDED_PRESET_ID] = null
        savedStateHandle[KEY_GUIDED_DRAFT_ID] = null
        savedStateHandle[KEY_GUIDED_PHASE] = null
        savedStateHandle[KEY_GUIDED_NAME] = null
        savedStateHandle[KEY_GUIDED_ITEM_ID] = null
        savedStateHandle[KEY_GUIDED_TRACKING] = null
        savedStateHandle[KEY_GUIDED_PHOTO_URI] = null
        savedStateHandle[KEY_GUIDED_PHOTO_TOKEN] = null
        setPendingLocationPurpose(null)
        clearPendingPhotoPurpose()
        clearInFlightCapture()
        
        _guidedSession.value = null
        _pendingGuidedPreset.value = null
        _showPlacementMethod.value = false
        _showBoundaryAcknowledgment.value = false
        _featureEditorOpen.value = false
        _featureEditorTarget.value = null
        _featureEditorFeature.value = null
        _newPointDraft.value = null
        _draftVertices.value = emptyList()
        _polygonDraft.value = null
        _selectedPersistedFeature.value = null
        _featureOperationErrorRes.value = null
        _isNewUnsavedFeature.value = false
        _discardAction.value = null
        _showDiscardEditDialog.value = false
        _pointMoveState.value = null
        _isPointMoveActive.value = false
        _linkEditorSession.value = null
        _stagedPhoto.value = StagedCreationPhotoState.None
        _editingMode.value = MapEditingMode.Select
    }

    fun selectPersistedFeature(f: MapFeatureEntity?, requestCameraFocus: Boolean = true) {
        if (_isSavingFeature.value || _isDeletingFeature.value) return
        _selectedPersistedFeature.value = f
        _featureEditorFeature.value = f
        _featureEditorOpen.value = (f != null)
        _featureEditorTarget.value = f?.let { FeatureEditorTarget.Persisted(it.id) }
        _isNewUnsavedFeature.value = false
        if (f != null) {
            initializeSystemItemLinkState(featureId = f.id, policy = SystemItemPolicy.MAP_ONLY, existingItemId = f.infrastructureItemId, isNewFeature = false)
            if (requestCameraFocus) { 
                viewModelScope.launch { 
                    getFeatureCenter(f.geometryJson)?.let { _cameraFocus.value = MapCameraFocus.Point(it.second, it.first, 17f) } 
                } 
            }
        } else {
            _linkEditorSession.value = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        // ViewModel destruction cleanup
        if (_saveOutcome.value == null || _saveOutcome.value is GuidedSaveOutcome.Failure) {
            clearStagedPhoto()
        }
    }

    fun selectFeatureById(id: UUID) { viewModelScope.launch { mapRepository.getFeatureById(id)?.let { selectPersistedFeature(it) } } }

    private fun getFeatureCenter(geometryJson: String): Pair<Double, Double>? {
        return try {
            val element = Json.decodeFromString<JsonObject>(geometryJson)
            val type = element["type"]?.jsonPrimitive?.content
            val coords = element["coordinates"]?.jsonArray
            when (type) {
                "Point" -> Pair(coords!![0].jsonPrimitive.double, coords[1].jsonPrimitive.double)
                "LineString" -> { val pts = coords!!.map { it.jsonArray }; val mid = pts[pts.size / 2]; Pair(mid[0].jsonPrimitive.double, mid[1].jsonPrimitive.double) }
                "Polygon" -> { val ring = coords!![0].jsonArray; val pts = ring.map { it.jsonArray }; Pair(pts.map { it[0].jsonPrimitive.double }.average(), pts.map { it[1].jsonPrimitive.double }.average()) }
                else -> null
            }
        } catch (e: Exception) { null }
    }

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
        if (!avail.isAvailable) { _mapErrorRes.value = avail.reasonRes; return }
        
        _showGuidedAddMenu.value = false
        _pendingGuidedPreset.value = p; savedStateHandle[KEY_GUIDED_PRESET_ID] = p.id.name
        if (p.geometry == GuidedMapGeometry.LOCATION) _showPlacementMethod.value = true else startGuidedMapping(p, PlacementMethod.TAP_MAP)
    }

    fun selectGuidedPresetAndCloseMenu(p: GuidedMapPreset) {
        setGuidedPreset(p)
    }

    fun selectGuidedLocationMethod(m: PlacementMethod) {
        val p = _pendingGuidedPreset.value ?: return; _showPlacementMethod.value = false; _pendingGuidedPreset.value = null; startGuidedMapping(p, m)
    }

    private fun startGuidedMapping(p: GuidedMapPreset, m: PlacementMethod) {
        val pid = _propertyId.value ?: return; val mid = _planId.value ?: return; val lid = _activeLayerId.value ?: return; val sessionId = UUID.randomUUID(); val draftId = UUID.randomUUID()
        val phase = if (m == PlacementMethod.MY_LOCATION) GuidedMappingPhase.SELECTING_PLACEMENT else GuidedMappingPhase.DRAWING
        
        _saveOutcome.value = null
        savedStateHandle[KEY_GUIDED_SESSION_ID] = sessionId.toString()
        savedStateHandle[KEY_GUIDED_PRESET_ID] = p.id.name
        savedStateHandle[KEY_GUIDED_DRAFT_ID] = draftId.toString()
        savedStateHandle[KEY_GUIDED_PHASE] = phase.name
        
        val newSession = GuidedMappingSession(sessionId = sessionId, propertyId = pid, planId = mid, preset = p, expectedGeometry = p.geometry, suggestedLabel = context.getString(p.suggestedLabelRes), targetDraftId = draftId, phase = phase)
        _guidedSession.value = newSession
        
        if (m == PlacementMethod.MY_LOCATION) {
            requestLocation(LocationRequestPurpose.CreatePoint)
        } else {
            _editingMode.value = when (p.geometry) { 
                GuidedMapGeometry.LOCATION -> MapEditingMode.AddPoint
                GuidedMapGeometry.ROUTE -> MapEditingMode.AddLine
                GuidedMapGeometry.AREA -> {
                    _polygonDraft.value = PolygonDraftState(id = draftId, propertyId = pid, planId = mid, layerId = lid)
                    if (p.id == GuidedMapPresetId.PROPERTY_BOUNDARY && !_boundaryAcknowledged.value) { _showBoundaryAcknowledgment.value = true; MapEditingMode.Select }
                    else MapEditingMode.AddPolygon
                }
            }
        }

        viewModelScope.launch {
            val uniqueLabel = featureNamingService.generateUniqueName(pid, context.getString(p.suggestedLabelRes))
            if (_guidedSession.value?.sessionId == sessionId) {
                _guidedSession.value = _guidedSession.value?.copy(suggestedLabel = uniqueLabel)
                savedStateHandle[KEY_GUIDED_NAME] = uniqueLabel
            }
        }
    }

    fun requestLocation(purpose: LocationRequestPurpose) {
        _pendingLocationPurpose.value = purpose; savedStateHandle[KEY_PENDING_PURPOSE] = purpose; _isLocatingPhone.value = true; _locationIssue.value = null
        if (purpose == LocationRequestPurpose.CreatePoint) _editingMode.value = MapEditingMode.AddPoint
        viewModelScope.launch {
            try {
                when (val result = locationProvider.getCurrentLocation()) {
                    is LocationResult.Success -> {
                        val isOld = (System.currentTimeMillis() - result.timestampMillis) > 300000
                        val isPoor = result.accuracyMeters > 15f
                        if (isOld || isPoor) {
                            val type = if (isOld && isPoor) LocationIssueType.CachedAndPoorAccuracy
                            else if (isOld) LocationIssueType.CachedLocation
                            else LocationIssueType.PoorAccuracy
                            val msg = if (isOld && isPoor) R.string.location_issue_cached_and_poor
                            else if (isOld) R.string.location_issue_cached
                            else R.string.location_issue_poor_accuracy
                            _locationIssue.value = LocationIssue(type, msg, canUseAnyway = true, purpose = purpose, cachedLocation = result)
                        } else {
                            _currentPhoneLocation.value = result
                            if (purpose == LocationRequestPurpose.CreatePoint) handleLocationForCreatePoint(result)
                            setPendingLocationPurpose(null)
                        }
                    }
                    LocationResult.PermissionDenied -> { _locationIssue.value = LocationIssue(LocationIssueType.PermissionDenied, R.string.location_issue_permission_denied, purpose = purpose) }
                    LocationResult.PermanentlyDenied -> { _locationIssue.value = LocationIssue(LocationIssueType.PermissionPermanentlyDenied, R.string.location_issue_permission_permanently_denied, canOpenAppSettings = true, purpose = purpose) }
                    LocationResult.ProviderDisabled -> { _locationIssue.value = LocationIssue(LocationIssueType.ProviderDisabled, R.string.location_issue_provider_disabled, canOpenLocationSettings = true, purpose = purpose) }
                    LocationResult.Timeout -> { _locationIssue.value = LocationIssue(LocationIssueType.Timeout, R.string.location_issue_timeout, canRetry = true, purpose = purpose) }
                    LocationResult.LocationUnavailable -> { _locationIssue.value = LocationIssue(LocationIssueType.LocationUnavailable, R.string.location_issue_unavailable, canRetry = true, purpose = purpose) }
                    is LocationResult.Error -> {
                        _locationIssue.value = LocationIssue(LocationIssueType.GenericError, R.string.location_issue_generic, purpose = purpose)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _locationIssue.value = LocationIssue(LocationIssueType.GenericError, R.string.location_issue_generic, purpose = purpose)
            } finally {
                _isLocatingPhone.value = false
            }
        }
    }

    fun handlePermanentDenial(purpose: LocationRequestPurpose) { _locationIssue.value = LocationIssue(LocationIssueType.PermissionPermanentlyDenied, R.string.location_issue_permission_permanently_denied, canOpenAppSettings = true, purpose = purpose) }
    fun handleTransientDenial(purpose: LocationRequestPurpose) { _locationIssue.value = LocationIssue(LocationIssueType.PermissionDenied, R.string.location_issue_permission_denied, purpose = purpose) }
    fun setPendingLocationPurpose(p: LocationRequestPurpose?) { 
        _pendingLocationPurpose.value = p
        if (p == null) savedStateHandle.remove<LocationRequestPurpose>(KEY_PENDING_PURPOSE)
        else savedStateHandle[KEY_PENDING_PURPOSE] = p
    }

    fun saveFeature(f: MapFeatureEntity) {
        if (!saveGate.tryLock()) return
        val session = _linkEditorSession.value
        if (session != null && session.featureId != f.id) {
            _featureOperationErrorRes.value = R.string.error_occurred
            saveGate.unlock()
            return 
        }
        
        _isSavingFeature.value = true
        _featureOperationErrorRes.value = null
        _labelError.value = null
        _accuracyError.value = null
        _saveOutcome.value = null
        
        if (f.label.isNullOrBlank()) {
            _labelError.value = R.string.error_name_required
            _isSavingFeature.value = false
            saveGate.unlock()
            return
        }
        
        viewModelScope.launch {
            try {
                val selection = session?.currentSelection ?: SystemItemLinkSelection.None
                val manualDraft = session?.pendingDraft
                
                if (selection is SystemItemLinkSelection.PendingDraft && (manualDraft == null || manualDraft.id != selection.draftId)) {
                    _featureOperationErrorRes.value = R.string.error_occurred
                    return@launch
                }
                
                if (selection == SystemItemLinkSelection.CreateSuggested && manualDraft == null) {
                    _featureOperationErrorRes.value = R.string.error_save_failed
                    return@launch
                }

                val itemToCreate: InfrastructureItemEntity? = when (selection) {
                    is SystemItemLinkSelection.PendingDraft -> if (manualDraft != null) InfrastructureItemEntity(id = manualDraft.id, propertyId = f.propertyId, name = f.label.trim(), category = manualDraft.category, subtype = manualDraft.subtype, isEmergencyItem = manualDraft.isEmergencyItem, emergencyInstructions = manualDraft.emergencyInstructions, status = "Active", createdAt = java.time.Instant.now(), updatedAt = java.time.Instant.now(), revision = 1L) else null
                    SystemItemLinkSelection.CreateSuggested -> if (manualDraft != null) InfrastructureItemEntity(id = manualDraft.id, propertyId = f.propertyId, name = f.label.trim(), category = manualDraft.category, subtype = manualDraft.subtype, isEmergencyItem = manualDraft.isEmergencyItem, emergencyInstructions = manualDraft.emergencyInstructions, status = "Active", createdAt = java.time.Instant.now(), updatedAt = java.time.Instant.now(), revision = 1L) else null
                    else -> null
                }
                val finalInfraId = when (selection) { is SystemItemLinkSelection.Existing -> selection.itemId; is SystemItemLinkSelection.PendingDraft -> itemToCreate?.id; SystemItemLinkSelection.CreateSuggested -> itemToCreate?.id; else -> null }
                mapRepository.saveFeatureWithOptionalItem(f.copy(infrastructureItemId = finalInfraId), itemToCreate)

                var photoSuccess = true
                try {
                    val currentPhoto = _stagedPhoto.value
                    if (currentPhoto is StagedCreationPhotoState.Ready) {
                        val uri = android.net.Uri.parse(currentPhoto.uri)
                        val owner = AttachmentOwner.MapFeature(f.propertyId, f.id)
                        val result = attachmentRepository.importAttachment(
                            owner = owner,
                            uri = uri,
                            type = AttachmentType.Photo,
                            customDisplayName = "Feature Photo",
                            caption = null,
                            cameraCaptureToken = currentPhoto.cameraCaptureToken
                        )
                        if (result !is AttachmentWriteResult.Success) {
                            photoSuccess = false
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    android.util.Log.e("MapViewModel", "Photo import failed", e)
                    photoSuccess = false
                }

                if (photoSuccess) {
                    consumeStagedPhotoState()
                    _saveOutcome.value = GuidedSaveOutcome.Success(f.id)
                    cancelGuidedCreation()
                    val updated = mapRepository.getFeatureById(f.id)
                    selectPersistedFeature(updated, requestCameraFocus = false)
                } else {
                    _saveOutcome.value = GuidedSaveOutcome.FeatureSavedPhotoFailed(f.propertyId, f.id)
                    _featureOperationErrorRes.value = R.string.feature_saved_with_photo_error
                    // Keep the editor open to allow retry
                    val updated = mapRepository.getFeatureById(f.id)
                    if (updated != null) {
                        _selectedPersistedFeature.value = updated
                        _featureEditorFeature.value = updated
                        _featureEditorTarget.value = FeatureEditorTarget.Persisted(updated.id)
                        _isNewUnsavedFeature.value = false
                    }
                }
            } catch (c: CancellationException) { throw c } catch (e: Exception) { _featureOperationErrorRes.value = R.string.error_save_failed; _saveOutcome.value = GuidedSaveOutcome.Failure } finally { _isSavingFeature.value = false; saveGate.unlock() }
        }
    }

    fun deleteFeature(id: UUID) {
        if (_isDeletingFeature.value) return
        val pid = _propertyId.value ?: return; val mid = _planId.value ?: return
        _isDeletingFeature.value = true; _featureOperationErrorRes.value = null
        viewModelScope.launch {
            try {
                val res = mapRepository.softDeleteFeatureWithAttachments(pid, mid, id)
                if (res is AttachmentDeleteState.Error) {
                    _featureOperationErrorRes.value = res.messageRes
                } else {
                    cancelGuidedCreation() 
                    _selectedPersistedFeature.value = null
                    _featureEditorOpen.value = false
                    _featureEditorTarget.value = null
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _featureOperationErrorRes.value = R.string.error_occurred
            } finally {
                _isDeletingFeature.value = false
            }
        }
    }

    fun beginMovePoint(featureId: UUID) {
        _features.value.find { it.id == featureId }?.let { f ->
            val layer = _layers.value.find { it.id == f.layerId }
            if (layer == null || layer.isLocked) {
                _featureOperationErrorRes.value = R.string.layer_is_locked
                return
            }
            getFeatureCenter(f.geometryJson)?.let { coords ->
                _pointMoveState.value = PointMoveState(featureId = featureId, originalLongitude = coords.first, originalLatitude = coords.second)
                _isPointMoveActive.value = true; _editingMode.value = MapEditingMode.Select
                _featureEditorOpen.value = false; _featureEditorTarget.value = null
                _selectedPersistedFeature.value = null
            }
        }
    }
    fun proposePointMove(lng: Double, lat: Double, isDragging: Boolean = false) { _pointMoveState.value = _pointMoveState.value?.copy(proposedLongitude = lng, proposedLatitude = lat, isDragging = isDragging) }
    fun finishPointMoveDrag() { _pointMoveState.value = _pointMoveState.value?.copy(isDragging = false) }
    fun cancelPointMove() { _pointMoveState.value = null; _isPointMoveActive.value = false }
    fun cancelPointMoveDrag() { _pointMoveState.value = _pointMoveState.value?.copy(proposedLongitude = null, proposedLatitude = null, isDragging = false) }
    fun confirmPointMove() {
        val s = _pointMoveState.value ?: return
        viewModelScope.launch {
            mapRepository.getFeatureById(s.featureId)?.let { f ->
                val updated = f.copy(geometryJson = GeometryUtils.buildPointGeoJson(s.proposedLongitude ?: s.originalLongitude, s.proposedLatitude ?: s.originalLatitude))
                mapRepository.updateFeature(updated)
                _pointMoveState.value = null; _isPointMoveActive.value = false; selectPersistedFeature(updated, requestCameraFocus = false)
            }
        }
    }

    fun beginPersistedShapeEdit(featureId: UUID) {
        _features.value.find { it.id == featureId }?.let { f ->
            when (f.geometryType) {
                "LINESTRING" -> {
                    val vertices = GeometryUtils.parseLineStringGeometry(f.geometryJson)
                    _lineEditState.value = LineEditState(featureId = f.id, propertyId = f.propertyId, planId = f.planId, layerId = f.layerId, originalVertices = vertices, workingVertices = vertices, originalLengthMeters = GeometryUtils.calculatePathLength(vertices), workingLengthMeters = GeometryUtils.calculatePathLength(vertices))
                    _editingMode.value = MapEditingMode.EditLine; _featureEditorOpen.value = false; _featureEditorTarget.value = FeatureEditorTarget.EditPersistedLine(f.id)
                }
                "POLYGON" -> {
                    val pr = GeometryUtils.parsePolygonGeoJson(f.geometryJson)
                    if (pr is PolygonParseResult.Success) {
                        val vertices = pr.vertices
                        _polygonEditState.value = PolygonEditState(featureId = f.id, propertyId = f.propertyId, planId = _planId.value!!, layerId = f.layerId, originalVertices = vertices, workingVertices = vertices, originalAreaMeters = GeometryUtils.calculateSphericalArea(vertices), workingAreaMeters = GeometryUtils.calculateSphericalArea(vertices), originalPerimeterMeters = GeometryUtils.calculatePolygonPerimeter(vertices), workingPerimeterMeters = GeometryUtils.calculatePolygonPerimeter(vertices))
                        _editingMode.value = MapEditingMode.EditPolygon; _featureEditorOpen.value = false; _featureEditorTarget.value = FeatureEditorTarget.EditPersistedPolygon(f.id)
                    }
                }
            }
        }
    }

    fun updateVertexPosition(index: Int, lng: Double, lat: Double) {
        _lineEditState.value?.let { s -> 
            val updated = s.workingVertices.toMutableList()
            if (index in updated.indices) { 
                updated[index] = Pair(lng, lat)
                _lineEditState.value = s.copy(workingVertices = updated, workingLengthMeters = GeometryUtils.calculatePathLength(updated)) 
            } 
        }
        _polygonEditState.value?.let { s -> 
            val updated = s.workingVertices.toMutableList()
            if (index in updated.indices) { 
                updated[index] = Pair(lng, lat)
                val validation = GeometryUtils.validatePolygonGeometry(updated)
                _polygonEditState.value = s.copy(workingVertices = updated, validation = validation, workingAreaMeters = GeometryUtils.calculateSphericalArea(updated), workingPerimeterMeters = GeometryUtils.calculatePolygonPerimeter(updated)) 
            } 
        }
    }

    fun selectEditVertex(index: Int?) {
        _lineEditState.value?.let { _lineEditState.value = it.copy(selectedVertexIndex = index) }
        _polygonEditState.value?.let { _polygonEditState.value = it.copy(selectedVertexIndex = index) }
    }

    fun beginVertexDrag(index: Int) { _lineEditState.value = _lineEditState.value?.copy(selectedVertexIndex = index, isDraggingVertex = true, dragStartVertices = _lineEditState.value?.workingVertices) }
    fun updateVertexDrag(lng: Double, lat: Double) { _lineEditState.value?.let { s -> val i = s.selectedVertexIndex ?: return@let; val updated = s.workingVertices.toMutableList(); if (i in updated.indices) { updated[i] = Pair(lng, lat); _lineEditState.value = s.copy(workingVertices = updated, workingLengthMeters = GeometryUtils.calculatePathLength(updated)) } } }
    fun finishVertexDrag() {
        _lineEditState.value?.let { s ->
            if (s.isDraggingVertex && s.dragStartVertices != null && s.dragStartVertices != s.workingVertices) {
                val newUndo = (s.undoStack + listOf(s.dragStartVertices)).takeLast(50)
                _lineEditState.value = s.copy(isDraggingVertex = false, dragStartVertices = null, undoStack = newUndo)
            } else {
                _lineEditState.value = s.copy(isDraggingVertex = false, dragStartVertices = null)
            }
        }
    }
    fun cancelVertexDrag() { _lineEditState.value?.let { s -> _lineEditState.value = s.copy(workingVertices = s.dragStartVertices ?: s.originalVertices, isDraggingVertex = false) } }
    fun insertVertex(index: Int, coords: Pair<Double, Double>) {
        _lineEditState.value?.let { s -> 
            val newUndo = (s.undoStack + listOf(s.workingVertices)).takeLast(50)
            val updated = s.workingVertices.toMutableList(); updated.add(index, coords)
            _lineEditState.value = s.copy(workingVertices = updated, workingLengthMeters = GeometryUtils.calculatePathLength(updated), undoStack = newUndo, selectedVertexIndex = index) 
        }
        _polygonEditState.value?.let { s ->
            val newUndo = (s.undoStack + listOf(s.workingVertices)).takeLast(50)
            val updated = s.workingVertices.toMutableList(); updated.add(index, coords)
            val validation = GeometryUtils.validatePolygonGeometry(updated)
            _polygonEditState.value = s.copy(workingVertices = updated, validation = validation, workingAreaMeters = GeometryUtils.calculateSphericalArea(updated), workingPerimeterMeters = GeometryUtils.calculatePolygonPerimeter(updated), undoStack = newUndo, selectedVertexIndex = index)
        }
    }
    fun deleteSelectedVertex() {
        _lineEditState.value?.let { s -> 
            val i = s.selectedVertexIndex ?: return@let; val updated = s.workingVertices.toMutableList()
            if (updated.size > 2 && i in updated.indices) { 
                val newUndo = (s.undoStack + listOf(s.workingVertices)).takeLast(50)
                updated.removeAt(i)
                _lineEditState.value = s.copy(workingVertices = updated, selectedVertexIndex = null, workingLengthMeters = GeometryUtils.calculatePathLength(updated), undoStack = newUndo) 
            } 
        }
        _polygonEditState.value?.let { s ->
            val i = s.selectedVertexIndex ?: return@let; val updated = s.workingVertices.toMutableList()
            if (updated.size > 3 && i in updated.indices) {
                val newUndo = (s.undoStack + listOf(s.workingVertices)).takeLast(50)
                updated.removeAt(i)
                val validation = GeometryUtils.validatePolygonGeometry(updated)
                _polygonEditState.value = s.copy(workingVertices = updated, selectedVertexIndex = null, validation = validation, workingAreaMeters = GeometryUtils.calculateSphericalArea(updated), workingPerimeterMeters = GeometryUtils.calculatePolygonPerimeter(updated), undoStack = newUndo)
            }
        }
    }
    fun undoLineEdit() { _lineEditState.value?.let { s -> if (s.undoStack.isNotEmpty()) { val prev = s.undoStack.last(); _lineEditState.value = s.copy(workingVertices = prev, undoStack = s.undoStack.dropLast(1), workingLengthMeters = GeometryUtils.calculatePathLength(prev)) } } }

    fun beginPolygonVertexDrag(index: Int) { _polygonEditState.value = _polygonEditState.value?.copy(draggingVertexIndex = index, dragStartVertices = _polygonEditState.value?.workingVertices) }
    fun updatePolygonVertexDrag(lng: Double, lat: Double) { _polygonEditState.value?.let { s -> val i = s.draggingVertexIndex ?: return@let; val updated = s.workingVertices.toMutableList(); if (i in updated.indices) { updated[i] = Pair(lng, lat); _polygonEditState.value = s.copy(workingVertices = updated, validation = GeometryUtils.validatePolygonGeometry(updated), workingAreaMeters = GeometryUtils.calculateSphericalArea(updated), workingPerimeterMeters = GeometryUtils.calculatePolygonPerimeter(updated)) } } }
    fun finishPolygonVertexDrag() {
        _polygonEditState.value?.let { s ->
            if (s.draggingVertexIndex != null && s.dragStartVertices != null && s.dragStartVertices != s.workingVertices) {
                val newUndo = (s.undoStack + listOf(s.dragStartVertices)).takeLast(50)
                _polygonEditState.value = s.copy(draggingVertexIndex = null, dragStartVertices = null, undoStack = newUndo)
            } else {
                _polygonEditState.value = s.copy(draggingVertexIndex = null, dragStartVertices = null)
            }
        }
    }
    fun cancelPolygonVertexDrag() { _polygonEditState.value?.let { s -> _polygonEditState.value = s.copy(workingVertices = s.dragStartVertices ?: s.originalVertices, draggingVertexIndex = null) } }
    fun insertPolygonVertex(index: Int, coords: Pair<Double, Double>) = insertVertex(index, coords)
    fun deletePolygonVertex(index: Int) {
        _polygonEditState.value?.let { s -> 
            val updated = s.workingVertices.toMutableList()
            if (updated.size > 3 && index in updated.indices) { 
                val newUndo = (s.undoStack + listOf(s.workingVertices)).takeLast(50)
                updated.removeAt(index)
                val validation = GeometryUtils.validatePolygonGeometry(updated)
                _polygonEditState.value = s.copy(workingVertices = updated, validation = validation, workingAreaMeters = GeometryUtils.calculateSphericalArea(updated), workingPerimeterMeters = GeometryUtils.calculatePolygonPerimeter(updated), undoStack = newUndo)
            }
        }
    }
    fun undoPolygonEdit() { _polygonEditState.value?.let { s -> if (s.undoStack.isNotEmpty()) { val prev = s.undoStack.last(); _polygonEditState.value = s.copy(workingVertices = prev, undoStack = s.undoStack.dropLast(1), validation = GeometryUtils.validatePolygonGeometry(prev), workingAreaMeters = GeometryUtils.calculateSphericalArea(prev), workingPerimeterMeters = GeometryUtils.calculatePolygonPerimeter(prev)) } } }

    fun addDraftVertex(lng: Double, lat: Double) {
        val current = _draftVertices.value
        val newVertex = Pair(lng, lat)
        if (current.isNotEmpty() && GeometryUtils.areCoordinatesEqual(current.last(), newVertex, 1e-9)) return
        _draftVertices.value = current + newVertex
    }

    fun undoDraftVertex() { if (_draftVertices.value.isNotEmpty()) _draftVertices.value = _draftVertices.value.dropLast(1) }
    fun finishDraftLine(): UUID? { 
        val pid = _propertyId.value ?: return null; val mid = _planId.value ?: return null; val lid = _activeLayerId.value ?: return null
        val vertices = _draftVertices.value; if (vertices.size < 2) return null
        val session = _guidedSession.value
        val useId = session?.targetDraftId ?: UUID.randomUUID()
        val feature = MapFeatureEntity(id = useId, propertyId = pid, planId = mid, layerId = lid, geometryType = "LINESTRING", geometryJson = GeometryUtils.buildLineStringGeoJson(vertices), coordinateSpace = "GEOGRAPHIC", styleJson = "{}", accuracySource = "Manual", label = session?.suggestedLabel ?: context.getString(R.string.label_default_point))
        if (session != null && session.expectedGeometry == GuidedMapGeometry.ROUTE) {
            savedStateHandle[KEY_GUIDED_PHASE] = GuidedMappingPhase.REVIEWING.name
            savedStateHandle[KEY_GUIDED_DRAFT_ID] = useId.toString()
            _guidedSession.value = session.copy(phase = GuidedMappingPhase.REVIEWING, targetDraftId = useId)
        }
        _featureEditorFeature.value = feature; _featureEditorOpen.value = true; _isNewUnsavedFeature.value = true; _featureEditorTarget.value = FeatureEditorTarget.NewLine(useId); _editingMode.value = MapEditingMode.Select; return useId 
    }
    fun cancelDraftLine() { _draftVertices.value = emptyList(); _editingMode.value = MapEditingMode.Select }

    fun beginAddPoint(): Boolean { if (!uiState.value.addToMapAvailability.isAvailable) return false; _editingMode.value = MapEditingMode.AddPoint; return true }
    fun beginAddLine(): Boolean { if (!uiState.value.addToMapAvailability.isAvailable) return false; _editingMode.value = MapEditingMode.AddLine; return true }
    fun beginAddPolygon(): Boolean {
        if (!uiState.value.addToMapAvailability.isAvailable) return false
        val pid = _propertyId.value ?: return false
        val mid = _planId.value ?: return false
        val lid = _activeLayerId.value ?: return false
        _polygonDraft.value = PolygonDraftState(propertyId = pid, planId = mid, layerId = lid)
        _editingMode.value = MapEditingMode.AddPolygon
        _selectedPersistedFeature.value = null
        return true
    }

    fun addPolygonVertex(lng: Double, lat: Double) {
        val pid = _propertyId.value ?: return; val mid = _planId.value ?: return; val lid = _activeLayerId.value ?: return
        val current = _polygonDraft.value ?: PolygonDraftState(propertyId = pid, planId = mid, layerId = lid)
        if (current.vertices.isNotEmpty() && GeometryUtils.areCoordinatesEqual(current.vertices.last(), Pair(lng, lat), 1e-9)) return
        val updated = current.vertices + Pair(lng, lat)
        _polygonDraft.value = current.copy(vertices = updated, validation = GeometryUtils.validatePolygonGeometry(updated))
    }
    fun undoPolygonVertex() { _polygonDraft.value?.let { s -> if (s.vertices.isNotEmpty()) { val updated = s.vertices.dropLast(1); _polygonDraft.value = s.copy(vertices = updated, validation = GeometryUtils.validatePolygonGeometry(updated)) } } }
    fun finishAddPolygon(): UUID? { 
        val s = _polygonDraft.value ?: return null; if (s.validation !is PolygonValidationResult.Valid) return null
        val session = _guidedSession.value
        val useId = s.id
        val feature = MapFeatureEntity(id = useId, propertyId = s.propertyId, planId = s.planId, layerId = s.layerId, geometryType = "POLYGON", geometryJson = GeometryUtils.buildPolygonGeoJson(s.vertices), coordinateSpace = "GEOGRAPHIC", styleJson = "{}", accuracySource = "Manual", label = session?.suggestedLabel ?: context.getString(R.string.label_default_point))
        if (session != null && session.expectedGeometry == GuidedMapGeometry.AREA) {
            savedStateHandle[KEY_GUIDED_PHASE] = GuidedMappingPhase.REVIEWING.name
            savedStateHandle[KEY_GUIDED_DRAFT_ID] = useId.toString()
            _guidedSession.value = session.copy(phase = GuidedMappingPhase.REVIEWING, targetDraftId = useId)
        }
        _featureEditorFeature.value = feature; _featureEditorOpen.value = true; _isNewUnsavedFeature.value = true; _featureEditorTarget.value = FeatureEditorTarget.NewPolygon(useId); _editingMode.value = MapEditingMode.Select; return useId 
    }
    fun cancelPolygonDraft() { _polygonDraft.value = null; _editingMode.value = MapEditingMode.Select }

    fun saveLineEdit() {
        val s = _lineEditState.value ?: return
        _isSavingFeature.value = true
        _lineEditState.value = s.copy(isSaving = true)
        viewModelScope.launch {
            try {
                val f = mapRepository.getFeatureById(s.featureId)
                if (f != null) {
                    val updated = f.copy(geometryJson = GeometryUtils.buildLineStringGeoJson(s.workingVertices))
                    mapRepository.updateFeature(updated)
                    _isSavingFeature.value = false
                    finishPersistedGeometryEdit(updated.id, reopen = true)
                } else {
                    _isSavingFeature.value = false
                    finishPersistedGeometryEdit(s.featureId, reopen = false)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _isSavingFeature.value = false
                _lineEditState.value = _lineEditState.value?.copy(isSaving = false)
                _featureOperationErrorRes.value = R.string.error_save_failed
            }
        }
    }

    private fun cancelLineEdit() {
        val fid = _lineEditState.value?.featureId
        if (fid != null) {
            viewModelScope.launch { finishPersistedGeometryEdit(fid, reopen = true) }
        } else {
            _lineEditState.value = null
            _editingMode.value = MapEditingMode.Select
        }
    }

    fun tryCancelLineEdit() {
        val s = _lineEditState.value ?: return
        if (s.workingVertices != s.originalVertices) {
            _discardAction.value = PendingEditDiscardAction.CancelLineEdit
            _showDiscardEditDialog.value = true
        } else {
            cancelLineEdit()
        }
    }

    fun savePolygonEdit() {
        val s = _polygonEditState.value ?: return
        if (s.validation !is PolygonValidationResult.Valid) return
        _isSavingFeature.value = true
        _polygonEditState.value = s.copy(isSaving = true)
        viewModelScope.launch {
            try {
                val f = mapRepository.getFeatureById(s.featureId)
                if (f != null) {
                    val updated = f.copy(geometryJson = GeometryUtils.buildPolygonGeoJson(s.workingVertices))
                    mapRepository.updateFeature(updated)
                    _isSavingFeature.value = false
                    finishPersistedGeometryEdit(updated.id, reopen = true)
                } else {
                    _isSavingFeature.value = false
                    finishPersistedGeometryEdit(s.featureId, reopen = false)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _isSavingFeature.value = false
                _polygonEditState.value = _polygonEditState.value?.copy(isSaving = false)
                _featureOperationErrorRes.value = R.string.error_save_failed
            }
        }
    }

    private fun cancelPolygonEdit() {
        val fid = _polygonEditState.value?.featureId
        if (fid != null) {
            viewModelScope.launch { finishPersistedGeometryEdit(fid, reopen = true) }
        } else {
            _polygonEditState.value = null
            _editingMode.value = MapEditingMode.Select
        }
    }

    fun tryCancelPolygonEdit() {
        val s = _polygonEditState.value ?: return
        if (s.workingVertices != s.originalVertices) {
            _discardAction.value = PendingEditDiscardAction.CancelPolygonEdit
            _showDiscardEditDialog.value = true
        } else {
            cancelPolygonEdit()
        }
    }

    private suspend fun finishPersistedGeometryEdit(id: UUID, reopen: Boolean) {
        _lineEditState.value = null; _polygonEditState.value = null; _featureEditorTarget.value = null; _featureEditorOpen.value = false; _featureEditorFeature.value = null; _selectedPersistedFeature.value = null; _linkEditorSession.value = null; _editingMode.value = MapEditingMode.Select
        if (reopen) { val f = mapRepository.getFeatureById(id); if (f != null) selectPersistedFeature(f, requestCameraFocus = false) }
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setSearchActive(active: Boolean) { 
        _isSearchActive.value = active
        if (!active) _searchQuery.value = ""
    }
    fun openSearchResult(result: MapSearchResult) { selectFeatureById(result.featureId) }
    fun revealAndOpenSearchResult(result: MapSearchResult) { 
        val l = _layers.value.find { it.id == result.layerId }
        if (l != null && !l.isVisible) { viewModelScope.launch { mapRepository.updateLayer(l.copy(isVisible = true)); selectFeatureById(result.featureId) } }
        else { selectFeatureById(result.featureId) }
    }

    // Basemap State Machine Methods
    private fun validateAttempt(attempt: BasemapLoadAttempt, expectedSourceId: BasemapSourceId?, expectedRole: BasemapRole? = null): Boolean {
        if (attempt.renderSessionId != _renderSessionId.value) return false
        if (attempt.semanticGeneration != _basemapGeneration.value) return false
        if (attempt.attemptId != _currentAttempt.value?.attemptId) return false
        if (attempt.sourceId != expectedSourceId) return false
        if (expectedRole != null && attempt.role != expectedRole) return false
        if (terminalAttempts.contains(attempt.attemptId)) return false
        return true
    }

    fun handleBasemapLoadSuccess(sourceId: BasemapSourceId, attempt: BasemapLoadAttempt): BasemapLoadSuccessResult {
        val currentRequested = _requestedSourceId.value
        val currentStatus = _basemapStatus.value
        
        if (!validateAttempt(attempt, sourceId)) return BasemapLoadSuccessResult(false, null)
        if (sourceId != currentRequested) return BasemapLoadSuccessResult(false, null)
        
        val expectedStatus = if (attempt.role == BasemapRole.PRIMARY) BasemapLoadStatus.LOADING_PRIMARY else BasemapLoadStatus.LOADING_BACKUP
        if (currentStatus != expectedStatus) return BasemapLoadSuccessResult(false, null)
        
        _activeSourceId.value = sourceId
        _requestedSourceId.value = null
        _basemapStatus.value = BasemapLoadStatus.LOADED
        _basemapErrorRes.value = null
        
        val def = basemapProvider.getDefinition(sourceId)
        if (def?.role == BasemapRole.PRIMARY) {
            _isUsingFallback.value = false
            _showBackupChooser.value = false
            _retryPrimaryAvailable.value = false
        } else if (def?.role == BasemapRole.BACKUP) {
            _isUsingFallback.value = true
            _showBackupChooser.value = true
            _retryPrimaryAvailable.value = true
        }
        
        return BasemapLoadSuccessResult(true, sourceId, def?.provider, def?.role)
    }

    fun handleBasemapLoadFailure(error: String, attempt: BasemapLoadAttempt) {
        if (!validateAttempt(attempt, _requestedSourceId.value)) return
        
        terminalAttempts.add(attempt.attemptId)
        
        val currentStatus = _basemapStatus.value
        if (currentStatus == BasemapLoadStatus.LOADING_PRIMARY) {
            if (!_fallbackAttempted.value) {
                _fallbackAttempted.value = true
                val backupSourceId = basemapProvider.resolveDefaultBackup(_preferredBasemapId.value)
                issueAttempt(backupSourceId, BasemapRole.BACKUP, BasemapLoadAttemptReason.BACKUP)
            } else {
                _basemapStatus.value = BasemapLoadStatus.FAILED
                _basemapErrorRes.value = R.string.failed_to_load_basemap
                _retryPrimaryAvailable.value = true
            }
        } else if (currentStatus == BasemapLoadStatus.LOADING_BACKUP) {
            _basemapStatus.value = BasemapLoadStatus.FAILED
            _basemapErrorRes.value = R.string.failed_to_load_basemap
            _retryPrimaryAvailable.value = true
        }
    }

    fun requestBasemap(id: BasemapId) {
        val previous = _preferredBasemapId.value
        _preferredBasemapId.value = id
        
        if (isRenderSessionReady) {
            if (id != previous || _basemapStatus.value == BasemapLoadStatus.IDLE || _basemapStatus.value == BasemapLoadStatus.FAILED) {
                startPrimaryLoad(id)
            }
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

    fun retryPrimaryMap() {
        startPrimaryLoad(_preferredBasemapId.value)
    }
    
    fun requestBackupBasemap(sourceId: BasemapSourceId) {
        issueAttempt(sourceId, BasemapRole.BACKUP, BasemapLoadAttemptReason.RETRY)
    }
    
    fun handleStaleStyleApplied(attempt: BasemapLoadAttempt) {
        if (attempt.renderSessionId != _renderSessionId.value) return
        
        val current = _currentAttempt.value ?: return
        if (attempt.attemptId == current.attemptId) return // Not stale
        
        if (repairAttemptedInGeneration == current.semanticGeneration) return // Bounded
        
        repairAttemptedInGeneration = current.semanticGeneration
        issueAttempt(current.sourceId, current.role, BasemapLoadAttemptReason.REPAIR)
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
        if (_isNewUnsavedFeature.value) {
            val hasGeom = _draftVertices.value.isNotEmpty() || (_polygonDraft.value?.vertices?.isNotEmpty() == true) || (_newPointDraft.value != null)
            if (hasGeom) {
                _discardAction.value = when (_editingMode.value) {
                    MapEditingMode.AddLine -> PendingEditDiscardAction.DiscardNewLine
                    MapEditingMode.AddPolygon -> PendingEditDiscardAction.DiscardNewPolygon
                    else -> PendingEditDiscardAction.DiscardNewPoint
                }
                _showDiscardEditDialog.value = true
                return
            }
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
        savedStateHandle.remove<LocationRequestPurpose?>(KEY_PENDING_PURPOSE)
        _locationIssue.value = null
    }

    private fun resolveValidActiveLayer(propId: UUID?, planId: UUID?, layers: List<LayerEntity>, currentId: UUID?): UUID? {
        if (propId == null || planId == null || layers.isEmpty()) return null
        val requested = layers.find { it.id == currentId }
        if (requested != null && requested.isVisible && !requested.isLocked && requested.deletedAt == null && requested.propertyId == propId && requested.planId == planId) return currentId
        return layers.sortedBy { it.displayOrder }.firstOrNull { it.isVisible && !it.isLocked && it.deletedAt == null && it.propertyId == propId && it.planId == planId }?.id
    }
}
