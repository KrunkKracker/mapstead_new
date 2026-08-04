package com.jumastappworks.mapstead.ui.mapping

import com.jumastappworks.mapstead.data.db.entities.LayerEntity
import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.data.db.entities.PlanEntity
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.data.prefs.UserPreferences
import com.jumastappworks.mapstead.data.attachments.StagedCreationPhotoState
import com.jumastappworks.mapstead.util.PolygonValidationReason
import com.jumastappworks.mapstead.util.PolygonValidationResult
import kotlinx.serialization.Serializable
import java.util.UUID

enum class BasemapLoadStatus { IDLE, LOADING_PRIMARY, LOADING_BACKUP, LOADED, FAILED }

enum class SecondaryMapStatus { IDLE, LOADING_PRIMARY, LOADING_BACKUP, LOADED_PRIMARY, LOADED_BACKUP, FAILED }

enum class BasemapLoadAttemptReason { INITIAL, BACKUP, RECREATION, REPAIR, RETRY }

enum class BasemapTerminalReason { TIMEOUT, PROVIDER_FAILURE, SUPERSEDED, DISPOSED }

enum class BasemapRepairEpochState { IN_FLIGHT, EXHAUSTED }

enum class BasemapLoadRejectionReason {
    STALE_SESSION,
    TERMINAL_ATTEMPT,
    PROVIDER_MISMATCH,
    ROLE_MISMATCH,
    STATUS_MISMATCH,
    GENERATION_MISMATCH,
    ID_MISMATCH,
    SOURCE_MISMATCH,
    DEFINITION_MISMATCH,
    REQUESTED_SOURCE_MISMATCH
}

data class BasemapAttemptKey(
    val semanticGeneration: Long,
    val attemptId: Long,
    val renderSessionId: UUID,
    val sourceId: BasemapSourceId
)

data class TerminalBasemapAttempt(
    val key: BasemapAttemptKey,
    val reason: BasemapTerminalReason
)

data class BasemapLoadAttempt(
    val semanticGeneration: Long,
    val attemptId: Long,
    val renderSessionId: UUID,
    val sourceId: BasemapSourceId,
    val provider: BasemapProviderType,
    val role: BasemapRole,
    val reason: BasemapLoadAttemptReason,
    val capturedSequence: Long
)

data class AcceptedBasemapStyleEvent(
    val eventId: Long,
    val attempt: BasemapLoadAttempt
)

data class BasemapRepairKey(
    val renderSessionId: UUID,
    val semanticGeneration: Long,
    val authoritativeSourceId: BasemapSourceId
)

data class BasemapLoadSuccessResult(
    val accepted: Boolean, 
    val sourceId: BasemapSourceId?,
    val provider: BasemapProviderType? = null,
    val role: BasemapRole? = null,
    val rejectionReason: BasemapLoadRejectionReason? = null
)

data class CameraSnapshot(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
    val bearing: Double,
    val tilt: Double,
    val customerInteractionSequence: Long,
    val attemptKey: BasemapAttemptKey
)

enum class ProgrammaticCameraMovementType {
    RESTORATION,
    INITIAL_FOCUS,
    MY_LOCATION,
    SEARCH_RESULT
}

enum class ProgrammaticCameraSessionState {
    PENDING,
    CONSUMED,
    CANCELLED,
    DISPOSED
}

data class ProgrammaticCameraSession(
    val sessionId: UUID,
    val renderSessionId: UUID,
    val expectedLatitude: Double,
    val expectedLongitude: Double,
    val expectedZoom: Double,
    val expectedBearing: Double,
    val expectedTilt: Double,
    val latTolerance: Double = 1e-6,
    val lngTolerance: Double = 1e-6,
    val zoomTolerance: Double = 0.05,
    val bearingTolerance: Double = 0.5,
    val tiltTolerance: Double = 0.1,
    val startSequence: Long,
    val movementType: ProgrammaticCameraMovementType,
    val state: ProgrammaticCameraSessionState = ProgrammaticCameraSessionState.PENDING
)

enum class ProgrammaticIdleResult {
    MATCHED_CURRENT_SESSION,
    CAMERA_DOES_NOT_MATCH,
    WRONG_RENDER_SESSION,
    NO_PENDING_SESSION
}

enum class SecondaryValidationResult {
    ACCEPTED,
    STALE_SESSION,
    TERMINAL_ATTEMPT,
    SOURCE_MISMATCH,
    PROVIDER_MISMATCH,
    ROLE_MISMATCH,
    STATUS_MISMATCH,
    GENERATION_MISMATCH,
    ID_MISMATCH,
    DEFINITION_MISMATCH
}

sealed interface SecondaryControllerAction {
    data object Accepted : SecondaryControllerAction
    data class LoadAttempt(val attempt: BasemapLoadAttempt) : SecondaryControllerAction
    data object Failed : SecondaryControllerAction
    data object Ignored : SecondaryControllerAction
}

enum class MapEditingMode { Select, AddPoint, AddLine, EditLine, AddPolygon, EditPolygon }

enum class CameraPersistenceState {
    WAITING_FOR_INITIAL_FOCUS,
    INITIAL_FOCUS_APPLYING,
    ARMED
}

enum class StarterLayerOperation { Idle, Creating, Skipping }

data class AddToMapAvailability(
    val isAvailable: Boolean,
    val reasonRes: Int? = null
)

sealed interface MapCameraFocus {
    data class Point(val latitude: Double, val longitude: Double, val zoom: Float = 17f, val bearing: Double = 0.0) : MapCameraFocus
    data class Bounds(val sw: Pair<Double, Double>, val ne: Pair<Double, Double>, val padding: Int = 100) : MapCameraFocus
}

data class PolygonDraftState(
    val id: UUID = UUID.randomUUID(),
    val propertyId: UUID,
    val planId: UUID,
    val layerId: UUID,
    val vertices: List<Pair<Double, Double>> = emptyList(),
    val validation: PolygonValidationResult = PolygonValidationResult.Invalid(com.jumastappworks.mapstead.util.PolygonValidationReason.TooFewVertices)
)

data class NewPointDraftState(
    val id: UUID,
    val propertyId: UUID,
    val planId: UUID,
    val layerId: UUID,
    val longitude: Double,
    val latitude: Double,
    val accuracyMeters: Double?
)

data class GuidedMappingSession(
    val sessionId: UUID,
    val propertyId: UUID,
    val planId: UUID,
    val preset: GuidedMapPreset,
    val expectedGeometry: GuidedMapGeometry,
    val suggestedLabel: String,
    val targetDraftId: UUID,
    val phase: GuidedMappingPhase
)

data class PointMoveState(
    val featureId: UUID,
    val originalLongitude: Double,
    val originalLatitude: Double,
    val proposedLongitude: Double? = null,
    val proposedLatitude: Double? = null,
    val isDragging: Boolean = false
)

data class LineEditState(
    val featureId: UUID,
    val propertyId: UUID,
    val planId: UUID,
    val layerId: UUID,
    val originalVertices: List<Pair<Double, Double>>,
    val workingVertices: List<Pair<Double, Double>>,
    val originalLengthMeters: Double,
    val workingLengthMeters: Double,
    val selectedVertexIndex: Int? = null,
    val isDraggingVertex: Boolean = false,
    val dragStartVertices: List<Pair<Double, Double>>? = null,
    val undoStack: List<List<Pair<Double, Double>>> = emptyList(),
    val isSaving: Boolean = false
)

data class PolygonEditState(
    val featureId: UUID,
    val propertyId: UUID,
    val planId: UUID,
    val layerId: UUID,
    val originalVertices: List<Pair<Double, Double>>,
    val workingVertices: List<Pair<Double, Double>>,
    val originalAreaMeters: Double,
    val workingAreaMeters: Double,
    val originalPerimeterMeters: Double,
    val workingPerimeterMeters: Double,
    val validation: PolygonValidationResult = PolygonValidationResult.Valid,
    val selectedVertexIndex: Int? = null,
    val draggingVertexIndex: Int? = null,
    val dragStartVertices: List<Pair<Double, Double>>? = null,
    val undoStack: List<List<Pair<Double, Double>>> = emptyList(),
    val isSaving: Boolean = false
)

data class MapSearchResult(
    val featureId: UUID,
    val featureLabel: String?,
    val systemItemId: UUID?,
    val systemItemName: String?,
    val category: String,
    val subtype: String?,
    val layerId: UUID,
    val layerName: String,
    val isLayerVisible: Boolean,
    val isEmergency: Boolean,
    val geometryType: String
)

sealed interface GuidedSaveOutcome {
    data class Success(val featureId: UUID) : GuidedSaveOutcome
    data class FeatureSavedPhotoFailed(val propertyId: UUID, val featureId: UUID) : GuidedSaveOutcome
    data object Failure : GuidedSaveOutcome
}

data class CameraPersistenceRequest(
    val planId: UUID,
    val latitude: Double,
    val longitude: Double,
    val zoom: Float,
    val bearing: Double
)

sealed interface FeatureEditorTarget {
    data class Persisted(val featureId: UUID) : FeatureEditorTarget
    data class NewPoint(val draftId: UUID) : FeatureEditorTarget
    data class NewLine(val draftId: UUID) : FeatureEditorTarget
    data class NewPolygon(val draftId: UUID) : FeatureEditorTarget
    data class EditPersistedLine(val featureId: UUID) : FeatureEditorTarget
    data class EditPersistedPolygon(val featureId: UUID) : FeatureEditorTarget
}

data class FeatureMeasurementSummary(
    val length: String? = null,
    val area: String? = null,
    val perimeter: String? = null,
    val pointsCount: Int? = null
)

data class FeatureAccuracySummary(
    val accuracy: String? = null,
    val source: String? = null,
    val sourceRes: Int? = null
)

sealed interface LinkedRecordState {
    data object None : LinkedRecordState
    data class Available(val item: com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity) : LinkedRecordState
    data class Unavailable(val itemId: java.util.UUID) : LinkedRecordState
}

sealed interface FeatureDetailUiState {
    data object Loading : FeatureDetailUiState
    data class Ready(
        val feature: MapFeatureEntity,
        val geometryLabel: String,
        val layerName: String?,
        val category: String?,
        val notes: String?,
        val measurementSummary: FeatureMeasurementSummary,
        val accuracySummary: FeatureAccuracySummary,
        val pointCoordinates: String?,
        val linkedRecord: LinkedRecordState,
        val attachments: List<com.jumastappworks.mapstead.ui.attachments.AttachmentListItemUiModel>,
        val isDeleting: Boolean = false,
        val deleteErrorRes: Int? = null
    ) : FeatureDetailUiState
    data class Error(val messageRes: Int) : FeatureDetailUiState
    data object NotFound : FeatureDetailUiState
}

data class FeatureLinkEditorSession(
    val featureId: UUID,
    val isNewFeature: Boolean,
    val initialSelection: com.jumastappworks.mapstead.data.mapping.SystemItemLinkSelection,
    val currentSelection: com.jumastappworks.mapstead.data.mapping.SystemItemLinkSelection,
    val pendingDraft: com.jumastappworks.mapstead.data.mapping.PendingSystemItemDraft? = null
)

@Serializable
data class CameraRestorationRequest(
    @Serializable(with = com.jumastappworks.mapstead.util.UuidSerializer::class) val planId: UUID,
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
    val bearing: Double,
    val timestamp: Long = System.currentTimeMillis()
)

data class MapUiState(
    val propertyId: UUID? = null,
    val propertyName: String? = null,
    val propertyLatitude: Double? = null,
    val propertyLongitude: Double? = null,
    val plan: PlanEntity? = null,
    val layers: List<LayerEntity> = emptyList(),
    val activeLayerId: UUID? = null,
    val visibleFeatures: List<MapFeatureEntity> = emptyList(),
    val hasMappedFeatures: Boolean = false,
    val selectedFeature: MapFeatureEntity? = null,
    val activeEditFeatureId: UUID? = null,
    val cameraFocus: MapCameraFocus? = null,
    val editingMode: MapEditingMode = MapEditingMode.Select,
    val mapLoading: Boolean = false,
    val mapErrorRes: Int? = null,
    val layerPanelOpen: Boolean = false,
    val featureEditorOpen: Boolean = false,
    val isEditingFeature: Boolean = false,
    val deleteFeatureErrorRes: Int? = null,
    val canAddPoint: Boolean = false,
    val canAddLine: Boolean = false,
    val canAddArea: Boolean = false,
    val isSavingFeature: Boolean = false,
    val isDeletingFeature: Boolean = false,
    val featureOperationErrorRes: Int? = null,
    val currentPhoneLocation: LocationResult.Success? = null,
    val currentPhoneLocationQuality: LocationAccuracyQuality? = null,
    val isLocatingPhone: Boolean = false,
    val locationIssue: LocationIssue? = null,
    val pendingLocationPurpose: LocationRequestPurpose? = null,
    val showPermissionRationale: Boolean = false,
    val hasRequestedLocationOnce: Boolean = false,
    val draftVertices: List<Pair<Double, Double>> = emptyList(),
    val canFinishLine: Boolean = false,
    
    // Basemap state
    val preferredBasemapId: BasemapId = BasemapId.STREETS,
    val requestedSourceId: BasemapSourceId? = null,
    val activeSourceId: BasemapSourceId? = null,
    val currentAttempt: BasemapLoadAttempt? = null,
    val renderSessionId: UUID? = null,
    val basemapStatus: BasemapLoadStatus = BasemapLoadStatus.IDLE,
    val basemapGeneration: Long = 0L,
    val basemapErrorRes: Int? = null,
    val isUsingFallback: Boolean = false,
    val retryPrimaryAvailable: Boolean = false,
    val showBackupChooser: Boolean = false,
    
    // Interaction state
    val cameraInteractionSequence: Long = 0L,
    val acceptedStyleEvent: AcceptedBasemapStyleEvent? = null,

    val polygonDraft: PolygonDraftState? = null,
    val liveAreaMeters: Double = 0.0,
    val livePerimeterMeters: Double = 0.0,
    val polygonValidationRes: Int? = null,
    val polygonFinishBlockReasonRes: Int? = null,
    val canFinishPolygon: Boolean = false,
    val featureEditorTarget: FeatureEditorTarget? = null,
    val featureEditorFeature: MapFeatureEntity? = null,
    val pointMoveState: PointMoveState? = null,
    val lineEditState: LineEditState? = null,
    val polygonEditState: PolygonEditState? = null,
    val isLineEditDirty: Boolean = false,
    val showDiscardEditDialog: Boolean = false,
    val discardAction: PendingEditDiscardAction? = null,
    val canSaveLineEdit: Boolean = false,
    val canSavePolygonEdit: Boolean = false,
    val canSavePointMove: Boolean = false,
    val canEditShape: Boolean = true,
    val isEditorDirty: Boolean = false,
    val sessionFeatureId: UUID? = null,
    val mapRecoveryActive: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<MapSearchResult> = emptyList(),
    val isSearchActive: Boolean = false,
    val showLocationDetails: Boolean = false,
    val showStarterLayersDialog: Boolean = false,
    val showBoundaryAcknowledgment: Boolean = false,
    val pendingGuidedPreset: GuidedMapPreset? = null,
    val guidedSession: GuidedMappingSession? = null,
    val showGuidedAddMenu: Boolean = false,
    val guidanceDismissed: Boolean = false,
    val starterLayersCreated: Boolean = false,
    val starterLayersEligible: Boolean = false,
    val starterLayerOperation: StarterLayerOperation = StarterLayerOperation.Idle,
    val starterLayerOperationActive: Boolean = false,
    val starterLayerErrorRes: Int? = null,
    val isSavingBoundaryAcknowledgment: Boolean = false,
    val boundaryAcknowledgmentErrorRes: Int? = null,
    val showPlacementMethod: Boolean = false,
    val showBasemapChooser: Boolean = false,
    val showHelpSheet: Boolean = false,
    val showSafetyLimitations: Boolean = false,
    val addToMapAvailability: AddToMapAvailability = AddToMapAvailability(false),
    val isWorkflowActive: Boolean = false,
    val measurementSystem: com.jumastappworks.mapstead.data.prefs.MeasurementSystem = com.jumastappworks.mapstead.data.prefs.MeasurementSystem.IMPERIAL,
    val labelError: Int? = null,
    val accuracyError: Int? = null,
    val systemItemDraft: PendingSystemItemDraft? = null,
    val linkSelection: SystemItemLinkSelection = SystemItemLinkSelection.None,
    val initialLinkSelection: SystemItemLinkSelection = SystemItemLinkSelection.None,
    val isNewUnsavedFeature: Boolean = false,
    val isPointMoveActive: Boolean = false,
    val stagedPhoto: StagedCreationPhotoState = StagedCreationPhotoState.None,
    val newPointDraft: NewPointDraftState? = null,
    val saveOutcome: GuidedSaveOutcome? = null,
    val pendingPhotoPurpose: PendingPhotoPurpose? = null,
    val guidedPrefill: GuidedFeaturePrefill? = null,
    val featureDetailState: FeatureDetailUiState? = null,
    val openingToken: String? = null,
    val polygonEditSaveBlockReasonRes: Int? = null,
    val lineEditSaveBlockReasonRes: Int? = null,
    val editShapeBlockReasonRes: Int? = null,
    val workflowBlockReasonRes: Int? = null
)
