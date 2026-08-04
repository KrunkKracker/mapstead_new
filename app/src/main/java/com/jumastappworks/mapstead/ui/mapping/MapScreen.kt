package com.jumastappworks.mapstead.ui.mapping

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.attachments.AttachmentNavigationOrigin
import com.jumastappworks.mapstead.data.db.entities.*
import com.jumastappworks.mapstead.data.repository.PropertyRepository
import com.jumastappworks.mapstead.data.repository.MapRepository
import com.jumastappworks.mapstead.data.repository.AttachmentRepository
import com.jumastappworks.mapstead.data.repository.InfrastructureRepository
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.data.help.HelpTopicId
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import com.jumastappworks.mapstead.data.backup.TemporaryCameraCapture
import com.jumastappworks.mapstead.data.attachments.StagedCreationPhotoState
import com.jumastappworks.mapstead.ui.components.EmptyState
import com.jumastappworks.mapstead.ui.components.MapActionButtons
import com.jumastappworks.mapstead.ui.components.SearchBarExpressive
import com.jumastappworks.mapstead.util.AdaptiveLayoutInfo
import com.jumastappworks.mapstead.util.GeometryUtils
import com.jumastappworks.mapstead.util.rememberAdaptiveLayoutInfo
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import androidx.compose.ui.semantics.selected
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import org.maplibre.android.maps.Style
import org.maplibre.android.maps.UiSettings
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import java.util.UUID
import kotlin.math.*
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.text.style.TextAlign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    basemapProvider: BasemapProvider,
    userPreferencesRepository: UserPreferencesRepository,
    modifier: Modifier = Modifier,
    onEmergencyClick: () -> Unit = {},
    propertyId: UUID? = null,
    planId: UUID? = null,
    featureId: String? = null,
    viewModel: MapViewModel,
    attachmentCount: Int = 0,
    photoCount: Int = 0,
    coverThumbnailUri: Uri? = null,
    onBack: () -> Unit = {},
    onViewAttachments: (UUID) -> Unit = {},
    navigateToAttachmentDetails: (UUID, UUID) -> Unit = { _, _ -> },
    navigateToInfrastructureDetails: (UUID, UUID) -> Unit = { _, _ -> },
    onNavigateToEditor: (UUID, String, UUID, String?, String?, AttachmentNavigationOrigin) -> Unit = { _, _, _, _, _, _ -> },
    onHelpRequest: (HelpTopicId) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val layoutInfo = rememberAdaptiveLayoutInfo()
    val state by viewModel.uiState.collectAsState()
    val systemItems by viewModel.systemItems.collectAsState()
    val mainScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val mapView = remember { org.maplibre.android.MapLibre.getInstance(context); MapView(context) }
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var isMapReady by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val basemapSheetState = rememberModalBottomSheetState()

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MapEvent.NavigateToAttachmentEditor -> {
                    onNavigateToEditor(event.propertyId, event.ownerType, event.ownerId, event.uri, event.token, event.origin)
                }
                is MapEvent.Error -> {
                    snackbarHostState.showSnackbar(context.getString(event.messageRes))
                }
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val pid = propertyId
        val purpose = state.pendingPhotoPurpose
        if (pid != null && uri != null) {
            when (purpose) {
                is PendingPhotoPurpose.SavedFeatureAttachment -> {
                    onNavigateToEditor(pid, "MAP_FEATURE", purpose.featureId, uri.toString(), null, AttachmentNavigationOrigin.MAP_FEATURE)
                }
                is PendingPhotoPurpose.GuidedFeatureCreation -> {
                    viewModel.setStagedPhoto(uri.toString(), null)
                }
                null -> {}
            }
        }
        viewModel.clearPendingPhotoPurpose()
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val pid = propertyId
        val purpose = state.pendingPhotoPurpose
        if (pid != null && uri != null) {
            when (purpose) {
                is PendingPhotoPurpose.SavedFeatureAttachment -> {
                    onNavigateToEditor(pid, "MAP_FEATURE", purpose.featureId, uri.toString(), null, AttachmentNavigationOrigin.MAP_FEATURE)
                }
                is PendingPhotoPurpose.GuidedFeatureCreation -> {
                    // Staging documents is not supported in the guided review form yet
                }
                null -> {}
            }
        }
        viewModel.clearPendingPhotoPurpose()
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        viewModel.handleCameraResult(success)
    }

    LaunchedEffect(state.isSearchActive) {
        if (!state.isSearchActive) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    BackHandler(enabled = state.isSearchActive) {
        viewModel.setSearchActive(false)
    }

    val permissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        val fineGranted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        state.pendingLocationPurpose?.let { purpose ->
            if (fineGranted || coarseGranted) { viewModel.requestLocation(purpose) }
            else {
                val act = context as? androidx.activity.ComponentActivity
                val shouldShowRationale = act?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) == true || act?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION) == true
                if (!shouldShowRationale) { viewModel.handlePermanentDenial(purpose) } else { viewModel.handleTransientDenial(purpose) }
            }
            viewModel.setPendingLocationPurpose(null)
        }
    }

    val requestLocationWithPermission = { purpose: LocationRequestPurpose ->
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) { viewModel.requestLocation(purpose) }
        else {
            viewModel.setPendingLocationPurpose(purpose)
            val act = context as? androidx.activity.ComponentActivity
            val shouldShowRationale = act?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) == true || act?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION) == true
            if (shouldShowRationale || !state.hasRequestedLocationOnce) { viewModel.setShowPermissionRationale(true); viewModel.setHasRequestedLocationOnce(true) }
            else { permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
        }
    }

    val currentState by rememberUpdatedState(state)
    val fallbackMessage = stringResource(R.string.basemap_fallback_active)
    LaunchedEffect(state.isUsingFallback) {
        if (state.isUsingFallback) {
            snackbarHostState.showSnackbar(
                message = fallbackMessage,
                duration = SnackbarDuration.Long
            )
        }
    }

    val renderSessionId = remember(mapView) { UUID.randomUUID() }
    val basemapLoader = remember(renderSessionId) {
        BasemapStyleLoader(
            basemapProvider = basemapProvider,
            scope = mainScope,
            onStyleLoaded = { _, attempt ->
                val result = viewModel.handleBasemapLoadSuccess(attempt)
                if (!result.accepted && attempt.renderSessionId == renderSessionId) {
                    viewModel.handleStaleStyleApplied(attempt)
                }
            },
            onStyleTerminated = { reason, attempt ->
                viewModel.handleBasemapLoadTerminated(reason, attempt)
            },
            onStaleStyleApplied = { attempt ->
                if (attempt.renderSessionId == renderSessionId) {
                    viewModel.handleStaleStyleApplied(attempt)
                }
            }
        )
    }

    LaunchedEffect(mapLibreMap, renderSessionId) {
        if (mapLibreMap != null) {
            viewModel.onMapReady(renderSessionId)
        }
    }

    LaunchedEffect(state.currentAttempt, mapLibreMap) {
        val map = mapLibreMap ?: return@LaunchedEffect
        val attempt = state.currentAttempt ?: return@LaunchedEffect
        if (attempt.renderSessionId != renderSessionId) return@LaunchedEffect

        // Capture actual camera position immediately before style load
        val pos = map.cameraPosition
        pos.target?.let { target ->
            viewModel.captureCameraSnapshot(
                latitude = target.latitude,
                longitude = target.longitude,
                zoom = pos.zoom,
                bearing = pos.bearing,
                tilt = pos.tilt,
                attempt = attempt
            )
        }

        basemapLoader.loadStyle(
            mapView = mapView,
            map = map,
            attempt = attempt
        )
    }

    var lastRestoredEventId by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.acceptedStyleEvent, mapLibreMap) {
        val map = mapLibreMap ?: return@LaunchedEffect
        val event = state.acceptedStyleEvent ?: return@LaunchedEffect
        if (event.eventId <= lastRestoredEventId) return@LaunchedEffect
        lastRestoredEventId = event.eventId

        if (event.attempt.renderSessionId != renderSessionId) return@LaunchedEffect
        
        // Final identity validation
        if (state.activeSourceId != event.attempt.sourceId) return@LaunchedEffect

        val snapshot = viewModel.getCameraSnapshot(event.attempt) ?: return@LaunchedEffect
        viewModel.consumeCameraSnapshot(event.attempt)

        // Restore camera if user hasn't moved it since attempt began
        if (state.cameraInteractionSequence == snapshot.customerInteractionSequence) {
            val restoredPos = CameraPosition.Builder()
                .target(LatLng(snapshot.latitude, snapshot.longitude))
                .zoom(snapshot.zoom)
                .bearing(snapshot.bearing)
                .tilt(snapshot.tilt)
                .build()
            
            viewModel.programmaticCameraController.beginProgrammaticMove(
                renderSessionId = renderSessionId,
                expectedLatitude = snapshot.latitude,
                expectedLongitude = snapshot.longitude,
                expectedZoom = snapshot.zoom,
                expectedBearing = snapshot.bearing,
                expectedTilt = snapshot.tilt,
                startSequence = state.cameraInteractionSequence,
                movementType = ProgrammaticCameraMovementType.RESTORATION
            )
            map.moveCamera(CameraUpdateFactory.newCameraPosition(restoredPos))
        }
    }

    // Re-install overlays and verify style consistency when status becomes LOADED or style changes
    LaunchedEffect(state.basemapStatus, state.activeSourceId, state.renderSessionId, mapLibreMap) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (state.basemapStatus != BasemapLoadStatus.LOADED) {
            isMapReady = false
            return@LaunchedEffect
        }
        val style = map.style ?: return@LaunchedEffect
        
        reinstallMapsteadOverlays(style, currentState)
        isMapReady = true
    }

    // Health check and recovery monitoring
    LaunchedEffect(isMapReady, mapLibreMap, state.visibleFeatures) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (!isMapReady) return@LaunchedEffect
        
        val style = map.style ?: return@LaunchedEffect
        if (!verifyMapsteadOverlays(style)) {
            reinstallMapsteadOverlays(style, state)
            if (!verifyMapsteadOverlays(style)) {
                viewModel.setMapRecoveryActive(true)
            }
        }
    }

    DisposableEffect(mapLibreMap) {
        val map = mapLibreMap ?: return@DisposableEffect onDispose {}
        
        val moveStartedListener = MapLibreMap.OnCameraMoveStartedListener { reason ->
            viewModel.programmaticCameraController.onCameraMoveStarted(reason, renderSessionId)
        }
        map.addOnCameraMoveStartedListener(moveStartedListener)

        val idleListener = MapLibreMap.OnCameraIdleListener {
            val pos = map.cameraPosition
            pos.target?.let { target ->
                val result = viewModel.programmaticCameraController.consumeProgrammaticIdle(
                    observedLatitude = target.latitude,
                    observedLongitude = target.longitude,
                    observedZoom = pos.zoom,
                    observedBearing = pos.bearing,
                    observedTilt = pos.tilt,
                    renderSessionId = renderSessionId
                )
                
                if (viewModel.programmaticCameraController.shouldPersistCamera(result)) {
                    viewModel.onCameraInteraction()
                    viewModel.onCameraMoved(target.latitude, target.longitude, pos.zoom, pos.bearing)
                }
            }
        }
        map.addOnCameraIdleListener(idleListener)

        onDispose {
            map.removeOnCameraMoveStartedListener(moveStartedListener)
            map.removeOnCameraIdleListener(idleListener)
            viewModel.programmaticCameraController.clearForMapDisposal(renderSessionId)
            viewModel.onRenderSessionDisposed(renderSessionId)
        }
    }

    DisposableEffect(lifecycleOwner.lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(Bundle())
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(mapView) {
        onDispose { basemapLoader.dispose(mapView) }
    }

    LaunchedEffect(featureId) { com.jumastappworks.mapstead.util.UuidHelper.safeParse(featureId)?.let { viewModel.selectFeatureById(it) } }

    var initialCameraApplied by remember(planId) { mutableStateOf(false) }
    LaunchedEffect(isMapReady, state.cameraFocus, planId) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (!isMapReady) return@LaunchedEffect
        val focus = state.cameraFocus ?: return@LaunchedEffect
        
        try {
            when (focus) {
                is MapCameraFocus.Point -> {
                    val pos = CameraPosition.Builder()
                        .target(LatLng(focus.latitude, focus.longitude))
                        .zoom(focus.zoom.toDouble())
                        .bearing(CameraValidation.normalizeBearing(focus.bearing))
                        .build()
                    viewModel.programmaticCameraController.beginProgrammaticMove(
                        renderSessionId = renderSessionId,
                        expectedLatitude = focus.latitude,
                        expectedLongitude = focus.longitude,
                        expectedZoom = focus.zoom.toDouble(),
                        expectedBearing = CameraValidation.normalizeBearing(focus.bearing),
                        expectedTilt = 0.0,
                        startSequence = state.cameraInteractionSequence,
                        movementType = ProgrammaticCameraMovementType.INITIAL_FOCUS
                    )
                    if (initialCameraApplied) {
                        map.animateCamera(CameraUpdateFactory.newCameraPosition(pos))
                    } else {
                        map.moveCamera(CameraUpdateFactory.newCameraPosition(pos))
                        initialCameraApplied = true
                    }
                }
                is MapCameraFocus.Bounds -> {
                    val bounds = LatLngBounds.Builder()
                        .include(LatLng(focus.sw.second, focus.sw.first))
                        .include(LatLng(focus.ne.second, focus.ne.first))
                        .build()
                    
                    val targetPos = map.getCameraForLatLngBounds(bounds, intArrayOf(focus.padding, focus.padding, focus.padding, focus.padding))
                    targetPos?.target?.let { target ->
                        viewModel.programmaticCameraController.beginProgrammaticMove(
                            renderSessionId = renderSessionId,
                            expectedLatitude = target.latitude,
                            expectedLongitude = target.longitude,
                            expectedZoom = targetPos.zoom,
                            expectedBearing = targetPos.bearing,
                            expectedTilt = targetPos.tilt,
                            startSequence = state.cameraInteractionSequence,
                            movementType = ProgrammaticCameraMovementType.INITIAL_FOCUS
                        )
                    }

                    if (initialCameraApplied) {
                        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, focus.padding))
                    } else {
                        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, focus.padding))
                        initialCameraApplied = true
                    }
                }
            }
            
            val pid = state.propertyId
            val plan = state.plan
            if (pid != null && plan != null) {
                viewModel.acknowledgeCameraFocusApplied(pid, plan.id, state.openingToken, focus)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.e("MapScreen", "Failed to apply camera focus", e)
        }
    }

    LaunchedEffect(state.currentPhoneLocation, mapLibreMap, isMapReady) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (!isMapReady) return@LaunchedEffect
        state.currentPhoneLocation?.let { loc -> 
            val pos = CameraPosition.Builder()
                .target(LatLng(loc.latitude, loc.longitude))
                .zoom(17.0)
                .bearing(0.0)
                .build()
            viewModel.programmaticCameraController.beginProgrammaticMove(
                renderSessionId = renderSessionId,
                expectedLatitude = loc.latitude,
                expectedLongitude = loc.longitude,
                expectedZoom = 17.0,
                expectedBearing = 0.0,
                expectedTilt = 0.0,
                startSequence = state.cameraInteractionSequence,
                movementType = ProgrammaticCameraMovementType.MY_LOCATION
            )
            map.animateCamera(CameraUpdateFactory.newCameraPosition(pos)) 
        }
    }

    val currentOnMapClick by rememberUpdatedState { latLng: LatLng ->
        when {
            state.pointMoveState != null -> { viewModel.proposePointMove(latLng.longitude, latLng.latitude) }
            state.editingMode == MapEditingMode.AddLine -> { viewModel.addDraftVertex(latLng.longitude, latLng.latitude) }
            state.editingMode == MapEditingMode.AddPolygon -> { viewModel.addPolygonVertex(latLng.longitude, latLng.latitude) }
            state.editingMode == MapEditingMode.AddPoint && propertyId != null && planId != null && state.activeLayerId != null -> {
                viewModel.addPointAt(latLng.longitude, latLng.latitude)
            }
        }
    }

    val currentFeatures by rememberUpdatedState(state.visibleFeatures)

    val currentMode by rememberUpdatedState(state.editingMode)
    val currentMap by rememberUpdatedState(mapLibreMap)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val hitRadiusPx = with(density) { 24.dp.toPx() }
    val touchHandler = remember(viewModel, hitRadiusPx) { ShapeEditTouchHandler(viewModel, hitRadiusPx) }

    val currentPointMoveActive by rememberUpdatedState(state.pointMoveState != null)
    DisposableEffect(mapView) {
        val touchListener = android.view.View.OnTouchListener { _, event ->
            val map = currentMap ?: return@OnTouchListener false
            touchHandler.handleTouch(event, map, currentMode, currentPointMoveActive)
        }
        mapView.setOnTouchListener(touchListener)
        onDispose {
            mapView.setOnTouchListener(null)
            touchHandler.onDispose()
        }
    }

    DisposableEffect(mapLibreMap, isMapReady, state.editingMode, currentFeatures, state.lineEditState, state.featureEditorTarget, state.pointMoveState) {
        val map = mapLibreMap
        if (map == null || !isMapReady) return@DisposableEffect onDispose {}
        val clickListener = MapLibreMap.OnMapClickListener { latLng ->
            val isEditorActive = state.featureEditorTarget != null || state.lineEditState != null || state.pointMoveState != null
            
            when (state.editingMode) {
                MapEditingMode.EditLine, MapEditingMode.EditPolygon -> {
                }
                MapEditingMode.AddLine, MapEditingMode.AddPoint, MapEditingMode.AddPolygon -> { currentOnMapClick(latLng) }
                MapEditingMode.Select -> {
                    if (isEditorActive && state.pointMoveState == null) {
                        return@OnMapClickListener true
                    }
                    if (state.pointMoveState != null) {
                        currentOnMapClick(latLng)
                        return@OnMapClickListener true
                    }

                    val touchRadiusPx = with(density) { 12.dp.toPx() }
                    val touchPoint = map.projection.toScreenLocation(latLng)
                    val touchBox = android.graphics.RectF(
                        touchPoint.x - touchRadiusPx, touchPoint.y - touchRadiusPx,
                        touchPoint.x + touchRadiusPx, touchPoint.y + touchRadiusPx
                    )
                    
                    val features = map.queryRenderedFeatures(touchBox, MapsteadMapOverlayInstaller.POINTS_LAYER_ID, MapsteadMapOverlayInstaller.LINES_LAYER_ID, MapsteadMapOverlayInstaller.SAVED_POLYGONS_FILL_LAYER_ID)
                    if (features.isNotEmpty()) {
                        val sorted = features.sortedBy { feat ->
                            when (feat.getStringProperty("geom_type")) {
                                "POINT" -> 0
                                "LINESTRING" -> 1
                                "POLYGON" -> 2
                                else -> 3
                            }
                        }
                        val id = try { UUID.fromString(sorted.first().getStringProperty("id")) } catch (e: Exception) { null }
                        currentFeatures.find { it.id == id }?.let { viewModel.selectPersistedFeature(it); return@OnMapClickListener true }
                    }
                }
            }
            true
        }
        map.addOnMapClickListener(clickListener)
        onDispose { map.removeOnMapClickListener(clickListener) }
    }


    LaunchedEffect(state.visibleFeatures, state.layers, state.currentPhoneLocation, state.draftVertices, state.editingMode, state.selectedFeature, state.lineEditState, state.polygonDraft, state.pointMoveState, state.polygonEditState, isMapReady, mapLibreMap) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (!isMapReady) return@LaunchedEffect
        map.style?.let { style ->
            reinstallMapsteadOverlays(style, state)
            
            val pms = state.pointMoveState
            if (pms != null) {
                val proposedLng = pms.proposedLongitude ?: pms.originalLongitude
                val proposedLat = pms.proposedLatitude ?: pms.originalLatitude
                MapsteadMapOverlayInstaller.installOrUpdateSource(style, MapsteadMapOverlayInstaller.POINT_MOVE_SOURCE_ID, GeometryUtils.buildPointGeoJson(proposedLng, proposedLat))
                MapsteadMapOverlayInstaller.installOrUpdateSource(style, MapsteadMapOverlayInstaller.ORIGINAL_LOCATION_GHOST_SOURCE_ID, GeometryUtils.buildPointGeoJson(pms.originalLongitude, pms.originalLatitude))
                MapsteadMapOverlayInstaller.installPointMoveLayers(style)
                MapsteadMapOverlayInstaller.installOriginalLocationGhost(style)
            } else {
                MapsteadMapOverlayInstaller.removeSourceAndLayers(style, MapsteadMapOverlayInstaller.POINT_MOVE_SOURCE_ID, listOf(MapsteadMapOverlayInstaller.POINT_MOVE_LAYER_ID))
                MapsteadMapOverlayInstaller.removeSourceAndLayers(style, MapsteadMapOverlayInstaller.ORIGINAL_LOCATION_GHOST_SOURCE_ID, listOf(MapsteadMapOverlayInstaller.ORIGINAL_LOCATION_GHOST_LAYER_ID))
            }
            
            if (state.editingMode == MapEditingMode.AddPolygon && state.polygonDraft != null) {
                val vertices = state.polygonDraft!!.vertices
                if (vertices.isNotEmpty()) {
                    val verticesList = vertices.map { coord -> 
                        org.json.JSONObject().apply {
                            put("type", "Feature")
                            put("geometry", org.json.JSONObject().apply {
                                put("type", "Point")
                                put("coordinates", org.json.JSONArray(listOf(coord.first, coord.second)))
                            })
                        }
                    }
                    val verticesJson = org.json.JSONObject().apply {
                        put("type", "FeatureCollection")
                        put("features", org.json.JSONArray(verticesList))
                    }.toString()
                    MapsteadMapOverlayInstaller.installOrUpdateSource(style, MapsteadMapOverlayInstaller.DRAFT_POLYGON_VERTICES_SOURCE_ID, verticesJson)
                    
                    if (vertices.size >= 2) {
                        val coords = vertices.toMutableList()
                        if (vertices.size >= 3) coords.add(vertices[0])
                        val coordsArray = org.json.JSONArray().apply {
                            coords.forEach { put(org.json.JSONArray(listOf(it.first, it.second))) }
                        }
                        val isPolygon = vertices.size >= 3
                        val geomJson = org.json.JSONObject().apply {
                            put("type", if (isPolygon) "Polygon" else "LineString")
                            put("coordinates", if (isPolygon) org.json.JSONArray(listOf(coordsArray)) else coordsArray)
                        }
                        val featJson = org.json.JSONObject().apply {
                            put("type", "Feature")
                            put("geometry", geomJson)
                        }.toString()
                        MapsteadMapOverlayInstaller.installOrUpdateSource(style, MapsteadMapOverlayInstaller.DRAFT_POLYGON_SOURCE_ID, featJson)
                    } else {
                        MapsteadMapOverlayInstaller.removeSourceAndLayers(style, MapsteadMapOverlayInstaller.DRAFT_POLYGON_SOURCE_ID, listOf(MapsteadMapOverlayInstaller.DRAFT_POLYGON_FILL_LAYER_ID, MapsteadMapOverlayInstaller.DRAFT_POLYGON_OUTLINE_LAYER_ID))
                    }
                    MapsteadMapOverlayInstaller.installPolygonDraftLayers(style)
                }
            } else {
                MapsteadMapOverlayInstaller.removeSourceAndLayers(style, MapsteadMapOverlayInstaller.DRAFT_POLYGON_SOURCE_ID, listOf(MapsteadMapOverlayInstaller.DRAFT_POLYGON_FILL_LAYER_ID, MapsteadMapOverlayInstaller.DRAFT_POLYGON_OUTLINE_LAYER_ID))
                MapsteadMapOverlayInstaller.removeSourceAndLayers(style, MapsteadMapOverlayInstaller.DRAFT_POLYGON_VERTICES_SOURCE_ID, listOf(MapsteadMapOverlayInstaller.DRAFT_POLYGON_VERTICES_LAYER_ID))
            }

            if (state.editingMode == MapEditingMode.EditLine && state.lineEditState != null) {
                val es = state.lineEditState!!
                val lineCoords = org.json.JSONArray().apply {
                    es.workingVertices.forEach { put(org.json.JSONArray(listOf(it.first, it.second))) }
                }
                val lineFeat = org.json.JSONObject().apply {
                    put("type", "Feature")
                    put("geometry", org.json.JSONObject().apply {
                        put("type", "LineString")
                        put("coordinates", lineCoords)
                    })
                }.toString()
                MapsteadMapOverlayInstaller.installOrUpdateSource(style, MapsteadMapOverlayInstaller.EDIT_LINE_SOURCE_ID, lineFeat)
                
                val verticesList = es.workingVertices.mapIndexed { index, coord -> 
                    org.json.JSONObject().apply {
                        put("type", "Feature")
                        put("geometry", org.json.JSONObject().apply {
                            put("type", "Point")
                            put("coordinates", org.json.JSONArray(listOf(coord.first, coord.second)))
                        })
                        put("properties", org.json.JSONObject().apply { put("index", index) })
                    }
                }
                val verticesJson = org.json.JSONObject().apply {
                    put("type", "FeatureCollection")
                    put("features", org.json.JSONArray(verticesList))
                }.toString()
                MapsteadMapOverlayInstaller.installOrUpdateSource(style, MapsteadMapOverlayInstaller.EDIT_VERTICES_SOURCE_ID, verticesJson)
                
                val midpointsList = mutableListOf<org.json.JSONObject>()
                for (i in 0 until es.workingVertices.size - 1) {
                    val p1 = es.workingVertices[i]; val p2 = es.workingVertices[i+1]
                    val midLng = (p1.first + p2.first) / 2.0
                    val midLat = (p1.second + p2.second) / 2.0
                    midpointsList.add(org.json.JSONObject().apply {
                        put("type", "Feature")
                        put("geometry", org.json.JSONObject().apply {
                            put("type", "Point")
                            put("coordinates", org.json.JSONArray(listOf(midLng, midLat)))
                        })
                        put("properties", org.json.JSONObject().apply { 
                            put("index", i + 1)
                            put("lng", midLng)
                            put("lat", midLat)
                        })
                    })
                }
                val midpointsJson = org.json.JSONObject().apply {
                    put("type", "FeatureCollection")
                    put("features", org.json.JSONArray(midpointsList))
                }.toString()
                MapsteadMapOverlayInstaller.installOrUpdateSource(style, MapsteadMapOverlayInstaller.EDIT_MIDPOINTS_SOURCE_ID, midpointsJson)
                MapsteadMapOverlayInstaller.installEditLayers(style, es.selectedVertexIndex)
            } else {
                MapsteadMapOverlayInstaller.removeSourceAndLayers(style, MapsteadMapOverlayInstaller.EDIT_LINE_SOURCE_ID, listOf(MapsteadMapOverlayInstaller.EDIT_LINE_LAYER_ID))
                MapsteadMapOverlayInstaller.removeSourceAndLayers(style, MapsteadMapOverlayInstaller.EDIT_VERTICES_SOURCE_ID, listOf(MapsteadMapOverlayInstaller.EDIT_VERTICES_LAYER_ID, MapsteadMapOverlayInstaller.EDIT_SELECTED_VERTEX_LAYER_ID))
                MapsteadMapOverlayInstaller.removeSourceAndLayers(style, MapsteadMapOverlayInstaller.EDIT_MIDPOINTS_SOURCE_ID, listOf(MapsteadMapOverlayInstaller.EDIT_MIDPOINTS_LAYER_ID))
            }

            if (state.editingMode == MapEditingMode.EditPolygon && state.polygonEditState != null) {
                val es = state.polygonEditState!!
                
                val ring = es.workingVertices.toMutableList()
                if (ring.isNotEmpty()) ring.add(ring[0])
                val ringCoords = org.json.JSONArray().apply {
                    ring.forEach { put(org.json.JSONArray(listOf(it.first, it.second))) }
                }
                val lineFeat = org.json.JSONObject().apply {
                    put("type", "Feature")
                    put("geometry", org.json.JSONObject().apply {
                        put("type", "LineString")
                        put("coordinates", ringCoords)
                    })
                }.toString()
                MapsteadMapOverlayInstaller.installOrUpdateSource(style, MapsteadMapOverlayInstaller.POLYGON_EDIT_LINE_SOURCE_ID, lineFeat)

                val verticesList = es.workingVertices.mapIndexed { index, coord -> 
                    org.json.JSONObject().apply {
                        put("type", "Feature")
                        put("geometry", org.json.JSONObject().apply {
                            put("type", "Point")
                            put("coordinates", org.json.JSONArray(listOf(coord.first, coord.second)))
                        })
                        put("properties", org.json.JSONObject().apply { put("index", index) })
                    }
                }
                val verticesJson = org.json.JSONObject().apply {
                    put("type", "FeatureCollection")
                    put("features", org.json.JSONArray(verticesList))
                }.toString()
                MapsteadMapOverlayInstaller.installOrUpdateSource(style, MapsteadMapOverlayInstaller.POLYGON_EDIT_VERTICES_SOURCE_ID, verticesJson)

                val midpoints = GeometryUtils.polygonMidpoints(es.workingVertices)
                val midpointsList = midpoints.map { m ->
                    org.json.JSONObject().apply {
                        put("type", "Feature")
                        put("geometry", org.json.JSONObject().apply {
                            put("type", "Point")
                            put("coordinates", org.json.JSONArray(listOf(m.coordinate.first, m.coordinate.second)))
                        })
                        put("properties", org.json.JSONObject().apply { 
                            put("index", m.insertionIndex)
                            put("lng", m.coordinate.first)
                            put("lat", m.coordinate.second)
                        })
                    }
                }
                val midpointsJson = org.json.JSONObject().apply {
                    put("type", "FeatureCollection")
                    put("features", org.json.JSONArray(midpointsList))
                }.toString()
                MapsteadMapOverlayInstaller.installOrUpdateSource(style, MapsteadMapOverlayInstaller.POLYGON_EDIT_MIDPOINTS_SOURCE_ID, midpointsJson)
                MapsteadMapOverlayInstaller.installPolygonEditLayers(style, es.selectedVertexIndex)
            } else {
                MapsteadMapOverlayInstaller.removeSourceAndLayers(style, MapsteadMapOverlayInstaller.POLYGON_EDIT_LINE_SOURCE_ID, listOf(MapsteadMapOverlayInstaller.POLYGON_EDIT_LINE_LAYER_ID))
                MapsteadMapOverlayInstaller.removeSourceAndLayers(style, MapsteadMapOverlayInstaller.POLYGON_EDIT_VERTICES_SOURCE_ID, listOf(MapsteadMapOverlayInstaller.POLYGON_EDIT_VERTICES_LAYER_ID, MapsteadMapOverlayInstaller.POLYGON_EDIT_SELECTED_VERTEX_LAYER_ID))
                MapsteadMapOverlayInstaller.removeSourceAndLayers(style, MapsteadMapOverlayInstaller.POLYGON_EDIT_MIDPOINTS_SOURCE_ID, listOf(MapsteadMapOverlayInstaller.POLYGON_EDIT_MIDPOINTS_LAYER_ID))
            }
        }
    }


    MapScreenContent(
        modifier = modifier, layoutInfo = layoutInfo, state = state, systemItems = systemItems,
        mapViewFactory = { ctx, onCameraMovedCallback -> 
            mapView.apply { 
                getMapAsync { map -> 
                    mapLibreMap = map
                    map.uiSettings.isAttributionEnabled = false
                    map.uiSettings.isLogoEnabled = false 
                } 
            } 
        },
        sheetState = sheetState, basemapSheetState = basemapSheetState,
        isBasemapLoading = state.basemapStatus == BasemapLoadStatus.LOADING_PRIMARY || state.basemapStatus == BasemapLoadStatus.LOADING_BACKUP,
        basemapProvider = basemapProvider,
        onUndoVertexClick = { viewModel.undoDraftVertex() },
        onFinishLineClick = { viewModel.finishDraftLine()?.let { viewModel.setEditingMode(MapEditingMode.Select) } },
        onCancelLineClick = { viewModel.cancelDraftLine(); viewModel.setEditingMode(MapEditingMode.Select) },
        onEmergencyClick = onEmergencyClick, onLayersClick = { viewModel.setLayerPanelOpen(!state.layerPanelOpen) },
        onMyLocationClick = { requestLocationWithPermission(LocationRequestPurpose.LocateOnly) },
        onUsePhoneLocationClick = { requestLocationWithPermission(LocationRequestPurpose.CreatePoint) },
        onClearMapError = { viewModel.clearMapError() }, onClearBasemapError = { viewModel.clearBasemapError() },
        onRetryPrimaryBasemap = { viewModel.retryPrimaryMap() },
        onSelectPreferredBasemap = { viewModel.requestBasemap(it) },
        onSelectBackupBasemap = { viewModel.requestBackupBasemap(it) },
        viewModel = viewModel,
        onDismissLocation = { viewModel.dismissLocation() },
        onDismissRationale = { viewModel.cancelPermissionRationale() },
        onAllowRationale = { viewModel.setShowPermissionRationale(false); permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
        onSelectLayer = { viewModel.setActiveLayer(it) }, onToggleLayerVisibility = { viewModel.toggleLayerVisibility(it) },
        onToggleLayerLock = { viewModel.toggleLayerLock(it) }, onAddLayer = { n, c -> viewModel.addLayer(n, c) },
        onRenameLayer = { id, n -> viewModel.renameLayer(id, n) }, onChangeLayerOpacity = { id, o -> viewModel.changeLayerOpacity(id, o) },
        onMoveLayerUp = { viewModel.moveLayerUp(it) }, onMoveLayerDown = { viewModel.moveLayerDown(it) }, onDeleteLayer = { viewModel.deleteLayer(it) },
        onSaveFeature = { viewModel.saveFeature(it) },
        onDeleteFeature = { viewModel.deleteFeature(it) },
        onOpenSearchResult = { viewModel.openSearchResult(it) },
        onRevealAndOpenSearchResult = { viewModel.revealAndOpenSearchResult(it) },
        onNavigateToEditor = onNavigateToEditor,
        onSaveNewSystemItem = viewModel::prepareSystemItemDraft,
        onMovePointClick = { viewModel.beginMovePoint(it) },
        onEditShapeClick = { viewModel.beginPersistedShapeEdit(it) }, onUndoEditClick = { viewModel.undoLineEdit() },
        onDeleteVertexClick = { viewModel.deleteSelectedVertex() }, onSaveEditClick = { viewModel.saveLineEdit() },
        onCancelEditClick = { viewModel.tryCancelLineEdit() },
        onConfirmDiscardEdit = { viewModel.confirmDiscardEdit() },
        onDismissDiscardDialog = { viewModel.dismissDiscardDialog() },
        onUndoPolygonVertexClick = { viewModel.undoPolygonVertex() },
        onFinishPolygonClick = { viewModel.finishAddPolygon() },
        onCancelPolygonClick = { viewModel.cancelPolygonDraft() },
        onDismissFeatureEditor = { viewModel.dismissFeatureEditor() },
        onConfirmPointMove = { viewModel.confirmPointMove() },
        onCancelPointMove = { viewModel.cancelPointMove() },
        onUndoPolygonEditClick = { viewModel.undoPolygonEdit() },
        onDeletePolygonVertexClick = { viewModel.deletePolygonVertex() },
        onSavePolygonEditClick = { viewModel.savePolygonEdit() },
        onCancelPolygonEditClick = { viewModel.tryCancelPolygonEdit() },
        onSearchQueryChange = { viewModel.setSearchQuery(it) },
        onSearchActiveChange = { viewModel.setSearchActive(it) },
        attachmentCount = attachmentCount,
        photoCount = photoCount,
        coverThumbnailUri = coverThumbnailUri,
        onViewAttachments = onViewAttachments,
        onAttachmentDetails = navigateToAttachmentDetails,
        onOpenInfrastructureDetails = navigateToInfrastructureDetails,
        onTakePhoto = { fid ->
            viewModel.setPendingPhotoPurpose(PendingPhotoPurpose.SavedFeatureAttachment(fid))
            mainScope.launch {
                viewModel.createCameraCapture().onSuccess { capture ->
                    viewModel.setInFlightCapture(capture.uri.toString(), capture.token)
                    cameraLauncher.launch(capture.uri)
                }
            }
        },
        onChoosePhoto = { fid ->
            viewModel.setPendingPhotoPurpose(PendingPhotoPurpose.SavedFeatureAttachment(fid))
            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onChooseDocument = { fid ->
            viewModel.setPendingPhotoPurpose(PendingPhotoPurpose.SavedFeatureAttachment(fid))
            documentPickerLauncher.launch(arrayOf("application/pdf", "text/plain", "image/*"))
        },
        onShowLocationDetails = { viewModel.setShowLocationDetails(it) },
        onPresetSelected = { 
            focusManager.clearFocus()
            keyboardController?.hide()
            viewModel.selectGuidedPresetAndCloseMenu(it) 
        },
        onAcknowledgeBoundary = { viewModel.acknowledgeBoundary() },
        onCancelBoundary = { viewModel.cancelBoundaryAcknowledgment() },
        onDismissGuidance = { viewModel.dismissGuidance() },
        onCreateStarterLayers = { s, _, _, _, _ -> 
            val b = s.contains(SuggestedMapLayer.BUILDINGS_BOUNDARIES)
            val u = s.contains(SuggestedMapLayer.UTILITIES)
            val o = s.contains(SuggestedMapLayer.OUTDOOR_FEATURES)
            val sa = s.contains(SuggestedMapLayer.SAFETY_EMERGENCY)
            viewModel.createStarterLayers(b, u, o, sa, "") 
        },
        onSkipStarterLayers = { viewModel.skipStarterLayers() },
        onShowMapHelp = { viewModel.setShowMapHelp(true) },
        onDismissHelpSheet = { viewModel.setShowMapHelp(false) },
        onSelectGuidedLocationMethod = { viewModel.selectGuidedLocationMethod(it) },
        onRequestLocationWithPermission = requestLocationWithPermission,
        onAddClick = { viewModel.setShowGuidedAddMenu(true) },
        onBasemapClick = { viewModel.setShowBasemapChooser(true) },
        onCloseBasemapChooser = { viewModel.setShowBasemapChooser(false) },
        onHelpClick = onHelpRequest,
        onCameraMoved = { lat, lng, z, b -> viewModel.onCameraMoved(lat, lng, z, b) },
        onReturnToProperty = { viewModel.onReturnToProperty() },
        linkSelection = state.linkSelection,
        onLinkSelectionChange = { viewModel.setLinkSelection(it) },
        systemItemDraft = state.systemItemDraft,
        onClearSystemItemDraft = { viewModel.clearSystemItemDraft() },
        stagedPhoto = state.stagedPhoto,
        onRemoveStagedPhoto = { viewModel.clearStagedPhoto() },
        onTakePhotoCreation = { fid ->
            viewModel.setPendingPhotoPurpose(PendingPhotoPurpose.GuidedFeatureCreation(fid))
            mainScope.launch {
                viewModel.createCameraCapture().onSuccess { capture ->
                    viewModel.setInFlightCapture(capture.uri.toString(), capture.token)
                    cameraLauncher.launch(capture.uri)
                }
            }
        },
        onChoosePhotoCreation = { fid ->
            viewModel.setPendingPhotoPurpose(PendingPhotoPurpose.GuidedFeatureCreation(fid))
            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onCancelGuidedCreation = { viewModel.tryCancelGuidedCreation() },
        onCameraInteraction = viewModel::onCameraInteraction,
        snackbarHostState = snackbarHostState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreenContent(
    modifier: Modifier = Modifier,
    layoutInfo: AdaptiveLayoutInfo,
    state: MapUiState,
    systemItems: List<InfrastructureItemEntity>,
    mapViewFactory: (android.content.Context, onCameraMoved: (Double, Double, Double, Double) -> Unit) -> android.view.View,
    sheetState: SheetState,
    basemapSheetState: SheetState,
    isBasemapLoading: Boolean,
    basemapProvider: BasemapProvider,
    onUndoVertexClick: () -> Unit,
    onFinishLineClick: () -> Unit,
    onFinishPolygonClick: () -> Unit,
    onCancelLineClick: () -> Unit,
    onCancelPolygonClick: () -> Unit,
    onEmergencyClick: () -> Unit,
    onLayersClick: () -> Unit,
    onMyLocationClick: () -> Unit,
    onUsePhoneLocationClick: () -> Unit,
    onClearMapError: () -> Unit,
    onClearBasemapError: () -> Unit,
    viewModel: MapViewModel,
    onDismissLocation: () -> Unit,
    onDismissRationale: () -> Unit,
    onAllowRationale: () -> Unit,
    onSelectLayer: (UUID) -> Unit,
    onToggleLayerVisibility: (UUID) -> Unit,
    onToggleLayerLock: (UUID) -> Unit,
    onAddLayer: (String, String) -> Unit,
    onRenameLayer: (UUID, String) -> Unit,
    onChangeLayerOpacity: (UUID, Float) -> Unit,
    onMoveLayerUp: (UUID) -> Unit,
    onMoveLayerDown: (UUID) -> Unit,
    onDeleteLayer: (UUID) -> Unit,
    onSaveFeature: (MapFeatureEntity) -> Unit,
    onDeleteFeature: (UUID) -> Unit,
    onOpenSearchResult: (MapSearchResult) -> Unit,
    onRevealAndOpenSearchResult: (MapSearchResult) -> Unit,
    onNavigateToEditor: (UUID, String, UUID, String?, String?, AttachmentNavigationOrigin) -> Unit,
    onSaveNewSystemItem: (PendingSystemItemInput) -> UUID,
    onMovePointClick: (UUID) -> Unit,
    onEditShapeClick: (UUID) -> Unit,
    onUndoEditClick: () -> Unit,
    onDeleteVertexClick: () -> Unit,
    onSaveEditClick: () -> Unit,
    onCancelEditClick: () -> Unit,
    onConfirmDiscardEdit: () -> Unit,
    onDismissDiscardDialog: () -> Unit,
    onUndoPolygonVertexClick: () -> Unit,
    onDismissFeatureEditor: () -> Unit,
    onConfirmPointMove: () -> Unit,
    onCancelPointMove: () -> Unit,
    onUndoPolygonEditClick: () -> Unit,
    onDeletePolygonVertexClick: () -> Unit,
    onSavePolygonEditClick: () -> Unit,
    onCancelPolygonEditClick: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    attachmentCount: Int = 0,
    photoCount: Int = 0,
    coverThumbnailUri: Uri? = null,
    onBack: () -> Unit = {},
    onViewAttachments: (UUID) -> Unit = {},
    onAttachmentDetails: (UUID, UUID) -> Unit = { _, _ -> },
    onOpenInfrastructureDetails: (UUID, UUID) -> Unit = { _, _ -> },
    onTakePhoto: (UUID) -> Unit = {},
    onChoosePhoto: (UUID) -> Unit = {},
    onChooseDocument: (UUID) -> Unit = {},
    onShowLocationDetails: (Boolean) -> Unit = {},
    onPresetSelected: (GuidedMapPreset) -> Unit = {},
    onAcknowledgeBoundary: () -> Unit = {},
    onCancelBoundary: () -> Unit = {},
    onDismissGuidance: () -> Unit = {},
    onCreateStarterLayers: (Set<SuggestedMapLayer>, String, String, String, String) -> Unit = { _, _, _, _, _ -> },
    onSkipStarterLayers: () -> Unit = {},
    onShowMapHelp: (Boolean) -> Unit = {},
    onDismissHelpSheet: () -> Unit = {},
    onSelectGuidedLocationMethod: (PlacementMethod) -> Unit = {},
    onRequestLocationWithPermission: (LocationRequestPurpose) -> Unit = {},
    onAddClick: () -> Unit = {},
    onBasemapClick: () -> Unit = {},
    onCloseBasemapChooser: () -> Unit = {},
    onSelectPreferredBasemap: (BasemapId) -> Unit = {},
    onSelectBackupBasemap: (BasemapSourceId) -> Unit = {},
    onRetryPrimaryBasemap: () -> Unit = {},
    onHelpClick: (HelpTopicId) -> Unit = {},
    onCameraMoved: (Double, Double, Double, Double) -> Unit = { _, _, _, _ -> },
    onReturnToProperty: () -> Unit = {},
    linkSelection: SystemItemLinkSelection = SystemItemLinkSelection.None,
    onLinkSelectionChange: (SystemItemLinkSelection) -> Unit = {},
    systemItemDraft: PendingSystemItemDraft? = null,
    onClearSystemItemDraft: () -> Unit = {},
    stagedPhoto: StagedCreationPhotoState = StagedCreationPhotoState.None,
    onRemoveStagedPhoto: () -> Unit = {},
    onTakePhotoCreation: (UUID) -> Unit = {},
    onChoosePhotoCreation: (UUID) -> Unit = {},
    onCancelGuidedCreation: () -> Unit = {},
    onCameraInteraction: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val context = LocalContext.current

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    
    val currentOnCameraMoved by rememberUpdatedState(onCameraMoved)
    val mainScope = rememberCoroutineScope()
    val cameraLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.TakePicture()) { success -> viewModel.handleCameraResult(success) }
    val photoPickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.PickVisualMedia()) { uri ->
        val pid = state.propertyId
        val feature = state.selectedFeature ?: state.featureEditorFeature
        if (pid != null && uri != null && feature != null) {
            onNavigateToEditor(pid, "MAP_FEATURE", feature.id, uri.toString(), null, AttachmentNavigationOrigin.MAP_FEATURE)
        }
        viewModel.clearPendingPhotoPurpose()
    }
    val documentPickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
        val pid = state.propertyId
        val feature = state.selectedFeature ?: state.featureEditorFeature
        if (pid != null && uri != null && feature != null) {
            onNavigateToEditor(pid, "MAP_FEATURE", feature.id, uri.toString(), null, AttachmentNavigationOrigin.MAP_FEATURE)
        }
        viewModel.clearPendingPhotoPurpose()
    }

    keyboardController?.hide()

    LaunchedEffect(state.starterLayersEligible, sheetState.isVisible, basemapSheetState.isVisible) {
        if (state.starterLayersEligible && !sheetState.isVisible && !basemapSheetState.isVisible) {
            viewModel.presentStarterLayers()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val mapLabel = stringResource(R.string.map_view_label)
        val moveHint = stringResource(R.string.movable_point_hint)

        AndroidView(
            factory = { ctx -> 
                mapViewFactory(ctx) { lat, lng, zoom, bearing ->
                    currentOnCameraMoved(lat, lng, zoom, bearing)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .semantics { 
                    contentDescription = if (state.pointMoveState != null) moveHint else mapLabel 
                }
        )

        // MapTiler Logo & Attribution Compliance
        BasemapAttributionOverlay(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = if (layoutInfo.useBottomNavigation) 96.dp else 16.dp, start = 8.dp),
            sourceId = state.activeSourceId,
            basemapProvider = basemapProvider
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isExpanded = !layoutInfo.isWidthCompact
            
            Box(modifier = Modifier.fillMaxWidth()) {
                if (!(isExpanded && state.isSearchActive)) {
                    Card(
                        modifier = Modifier
                            .widthIn(max = 400.dp)
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            SearchBarExpressive(
                                query = state.searchQuery,
                                onQueryChange = onSearchQueryChange,
                                onSearchActiveChange = onSearchActiveChange,
                                active = state.isSearchActive,
                                propertyName = state.propertyName,
                                planName = state.plan?.name,
                                onClearQuery = { onSearchQueryChange("") }
                            )
                        }
                    }
                }
            }

            if (state.isSearchActive && !isExpanded) {
                SearchResultsOverlay(
                    state = state,
                    onResultClick = onOpenSearchResult,
                    onRevealClick = onRevealAndOpenSearchResult
                )
            }

            if (state.editingMode == MapEditingMode.AddPoint) {
                Surface(modifier = Modifier.testTag("PointToolbar"), color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp), tonalElevation = 4.dp) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.add_point), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onUsePhoneLocationClick, modifier = Modifier.testTag("UsePhoneLocationButton")) { Text(stringResource(R.string.use_phone_location)) }
                    }
                }
            }

            if (state.editingMode == MapEditingMode.AddLine) {
                Surface(modifier = Modifier.testTag("LineToolbar"), color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp), tonalElevation = 4.dp) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.add_line), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onUndoVertexClick, enabled = state.draftVertices.isNotEmpty(), modifier = Modifier.testTag("UndoVertexButton")) { Text(stringResource(R.string.undo_vertex)) }
                            Button(onClick = onFinishLineClick, enabled = state.canFinishLine, modifier = Modifier.testTag("FinishLineButton")) { Text(stringResource(R.string.finish_line)) }
                            TextButton(onClick = onCancelLineClick, modifier = Modifier.testTag("CancelLineButton")) { Text(stringResource(R.string.cancel_line)) }
                        }
                    }
                }
            }

            if (state.editingMode == MapEditingMode.AddPolygon && state.polygonDraft != null) {
                Surface(modifier = Modifier.testTag("PolygonToolbar"), color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp), tonalElevation = 4.dp) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.add_area), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        
                        val vertices = state.polygonDraft.vertices
                        val areaStr = GeometryUtils.formatArea(state.liveAreaMeters, state.measurementSystem)
                        val perimeterStr = GeometryUtils.formatDistance(state.livePerimeterMeters, state.measurementSystem)
                        
                        Text(
                            text = if (vertices.size < 3) "${vertices.size} vertices \u00b7 Add ${3-vertices.size} more"
                                   else "${vertices.size} vertices \u00b7 $areaStr \u00b7 $perimeterStr perimeter",
                            style = MaterialTheme.typography.labelSmall
                        )
                        
                        state.polygonValidationRes?.let { res ->
                            Text(stringResource(res), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                        
                        state.polygonFinishBlockReasonRes?.let { reasonRes ->
                            Text(stringResource(reasonRes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }

                        Spacer(Modifier.height(8.dp))
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(onClick = onUndoPolygonVertexClick, enabled = vertices.isNotEmpty(), modifier = Modifier.testTag("UndoPolygonButton")) { Text(stringResource(R.string.undo)) }
                            Button(onClick = onFinishPolygonClick, enabled = state.canFinishPolygon, modifier = Modifier.testTag("FinishPolygonButton")) { Text(stringResource(R.string.finish_area)) }
                            TextButton(onClick = onCancelPolygonClick, modifier = Modifier.testTag("CancelPolygonButton")) { Text(stringResource(R.string.cancel)) }
                        }
                    }
                }
            }

            if (state.pointMoveState != null) {
                val moveTitle = stringResource(R.string.moving_point)
                val saveLabel = stringResource(R.string.save_label)
                val cancelLabel = stringResource(R.string.cancel)
                
                Surface(
                    modifier = Modifier
                        .testTag("MovePointToolbar")
                        .semantics { contentDescription = moveTitle },
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 6.dp
                ) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.moving_point), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(if (state.pointMoveState.proposedLatitude == null) R.string.tap_to_choose_location else R.string.drag_or_tap_to_move), style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onConfirmPointMove,
                                enabled = state.canSavePointMove && !state.isSavingFeature,
                                modifier = Modifier
                                    .testTag("ConfirmMoveButton")
                                    .semantics { contentDescription = saveLabel }
                            ) { 
                                if (state.isSavingFeature) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.confirm_move)) 
                            }
                            OutlinedButton(
                                onClick = onCancelPointMove,
                                enabled = !state.isSavingFeature,
                                modifier = Modifier
                                    .testTag("CancelMoveButton")
                                    .semantics { contentDescription = cancelLabel }
                            ) { Text(stringResource(R.string.cancel_move)) }
                        }
                        state.featureOperationErrorRes?.let { errRes ->
                            Text(stringResource(errRes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }

            if (state.editingMode == MapEditingMode.EditPolygon && state.polygonEditState != null) {
                val es = state.polygonEditState
                Surface(modifier = Modifier.testTag("EditPolygonToolbar"), color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(8.dp), tonalElevation = 6.dp) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.editing_area, es.workingVertices.size), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        
                        val areaStr = GeometryUtils.formatArea(es.workingAreaMeters, state.measurementSystem)
                        val perimeterStr = GeometryUtils.formatDistance(es.workingPerimeterMeters, state.measurementSystem)
                        Text("$areaStr \u00b7 $perimeterStr perimeter", style = MaterialTheme.typography.labelSmall)
                        
                        val areaChange = es.workingAreaMeters - es.originalAreaMeters
                        if (abs(areaChange) > 1e-1) {
                            val changeStr = GeometryUtils.formatArea(abs(areaChange), state.measurementSystem)
                            val prefix = if (areaChange > 0) "+" else "\u2212"
                            Text(stringResource(R.string.area_change_label, "$prefix$changeStr"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        }

                        Spacer(Modifier.height(8.dp))
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            maxItemsInEachRow = if (layoutInfo.isWidthCompact) 2 else 4
                        ) {
                            FilledTonalButton(onClick = onUndoPolygonEditClick, enabled = es.undoStack.isNotEmpty(), modifier = Modifier.testTag("UndoPolygonEditButton").heightIn(min = 48.dp)) { Text(stringResource(R.string.undo)) }
                            Button(onClick = onDeletePolygonVertexClick, enabled = es.selectedVertexIndex != null && es.workingVertices.size > 3, modifier = Modifier.testTag("DeletePolygonVertexButton").heightIn(min = 48.dp)) { Text(stringResource(R.string.delete_vertex)) }
                            Button(onClick = onSavePolygonEditClick, enabled = state.canSavePolygonEdit, modifier = Modifier.testTag("SavePolygonEditButton").heightIn(min = 48.dp)) { if (es.isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.save)) }
                            OutlinedButton(onClick = onCancelPolygonEditClick, modifier = Modifier.testTag("CancelPolygonEditButton").heightIn(min = 48.dp)) { Text(stringResource(R.string.cancel)) }
                        }
                        state.polygonEditSaveBlockReasonRes?.let { reasonRes ->
                            if (es.workingVertices != es.originalVertices) {
                                Text(stringResource(reasonRes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }

            if (state.editingMode == MapEditingMode.EditLine && state.lineEditState != null) {
                val es = state.lineEditState
                Surface(modifier = Modifier.testTag("EditLineToolbar"), color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(8.dp), tonalElevation = 6.dp) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.editing_line, es.workingVertices.size), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            maxItemsInEachRow = if (layoutInfo.isWidthCompact) 2 else 4
                        ) {
                            FilledTonalButton(onClick = onUndoEditClick, enabled = es.undoStack.isNotEmpty(), modifier = Modifier.testTag("UndoEditButton").heightIn(min = 48.dp)) { Text(stringResource(R.string.undo)) }
                            Button(onClick = onDeleteVertexClick, enabled = es.selectedVertexIndex != null && es.workingVertices.size > 2, modifier = Modifier.testTag("DeleteVertexButton").heightIn(min = 48.dp)) { Text(stringResource(R.string.delete_vertex)) }
                            Button(onClick = onSaveEditClick, enabled = state.canSaveLineEdit, modifier = Modifier.testTag("SaveEditButton").heightIn(min = 48.dp)) { if (es.isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.save)) }
                            OutlinedButton(onClick = onCancelEditClick, modifier = Modifier.testTag("CancelEditButton").heightIn(min = 48.dp)) { Text(stringResource(R.string.cancel)) }
                        }
                        state.lineEditSaveBlockReasonRes?.let { reasonRes ->
                            if (state.isLineEditDirty) {
                                Text(stringResource(reasonRes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
        }

        if (state.currentPhoneLocation != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (layoutInfo.useBottomNavigation) 100.dp else 80.dp)
            ) {
                LocationAccuracyChip(
                    location = state.currentPhoneLocation,
                    quality = state.currentPhoneLocationQuality ?: LocationAccuracyQuality.Poor,
                    measurementSystem = state.measurementSystem,
                    onClick = { onShowLocationDetails(true) }
                )
            }
        }

        if (state.mapLoading || state.isLocatingPhone || isBasemapLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        if (!state.guidanceDismissed && !state.hasMappedFeatures && !state.isSearchActive && state.editingMode == MapEditingMode.Select) {
            FirstUseGuidanceCard(
                onAddClick = onAddClick,
                onHelpClick = { onShowMapHelp(true) },
                onDismiss = onDismissGuidance,
                isAddEnabled = state.addToMapAvailability.isAvailable,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp, start = 16.dp, end = 16.dp)
            )
        }

        state.mapErrorRes?.let { errRes ->
            Snackbar(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).padding(bottom = if (layoutInfo.useBottomNavigation) 64.dp else 0.dp), action = { TextButton(onClick = onClearMapError) { Text(stringResource(R.string.dismiss)) } }) { Text(stringResource(errRes)) }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (layoutInfo.useBottomNavigation) 80.dp else 16.dp)
        )

        if (state.mapRecoveryActive) {
            AlertDialog(
                onDismissRequest = { viewModel.setMapRecoveryActive(false) },
                title = { Text(stringResource(R.string.map_recovery_title)) },
                text = { Text(stringResource(R.string.map_recovery_message)) },
                confirmButton = {
                    TextButton(onClick = { viewModel.onReturnToProperty() }) {
                        Text(stringResource(R.string.return_to_property))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onRetryPrimaryBasemap(); viewModel.setMapRecoveryActive(false) }) {
                        Text(stringResource(R.string.retry_map))
                    }
                }
            )
        }

        state.basemapErrorRes?.let { errRes ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp, start = 16.dp, end = 16.dp),
                action = {
                    Row {
                        TextButton(onClick = onRetryPrimaryBasemap) { Text(stringResource(R.string.retry)) }
                        IconButton(onClick = onClearBasemapError) { Icon(Icons.Default.Check, contentDescription = stringResource(R.string.dismiss)) }
                    }
                }
            ) { Text(stringResource(errRes)) }
        }
        

        state.locationIssue?.let { issue ->
            AlertDialog(
                onDismissRequest = { viewModel.cancelLocationIssue() },
                title = { Text(stringResource(R.string.location_issue_title)) },
                text = { Text(stringResource(issue.messageRes)) },
                confirmButton = {
                    Column(horizontalAlignment = Alignment.End) {
                        if (issue.canRetry && issue.purpose != null) { 
                            TextButton(onClick = { viewModel.retryLocationIssue() }) { 
                                Text(if (issue.type == LocationIssueType.CachedLocation || issue.type == LocationIssueType.CachedAndPoorAccuracy) stringResource(R.string.retry_fresh) else stringResource(R.string.retry)) 
                            } 
                        }
                        if (issue.canUseAnyway && issue.cachedLocation != null && issue.purpose != null) { 
                            TextButton(onClick = { viewModel.useLocationResultAnyway() }) { 
                                Text(if (issue.type == LocationIssueType.PoorAccuracy || issue.type == LocationIssueType.CachedAndPoorAccuracy) stringResource(R.string.use_anyway) else stringResource(R.string.use_cached)) 
                            } 
                        }
                        if (issue.canOpenAppSettings) { 
                            TextButton(onClick = { 
                                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", context.packageName, null) }
                                context.startActivity(intent)
                                viewModel.onOpenAppSettings()
                            }) { Text(stringResource(R.string.open_settings)) } 
                        }
                        if (issue.canOpenLocationSettings) { 
                            TextButton(onClick = { 
                                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                viewModel.onOpenLocationSettings()
                            }) { Text(stringResource(R.string.open_location_settings)) } 
                        }
                        if (issue.canContinueManually) { 
                            TextButton(onClick = { viewModel.continueGuidedLocationManually() }) { 
                                Text(stringResource(R.string.continue_manual)) 
                            } 
                        }
                    }
                },
                dismissButton = { 
                    TextButton(onClick = { viewModel.cancelLocationIssue() }) { 
                        Text(stringResource(R.string.cancel)) 
                    } 
                }
            )
        }

        if (state.showPermissionRationale) {
            AlertDialog(onDismissRequest = onDismissRationale, title = { Text(stringResource(R.string.location_access_title)) }, text = { Text(stringResource(R.string.location_permission_rationale)) }, confirmButton = { TextButton(onClick = onAllowRationale) { Text(stringResource(R.string.allow)) } }, dismissButton = { TextButton(onClick = onDismissRationale) { Text(stringResource(R.string.cancel)) } })
        }

        Row(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            Spacer(Modifier.weight(1f))
            
            MapActionButtons(
                modifier = Modifier.wrapContentSize(), layoutInfo = layoutInfo, 
                isBasemapActive = state.showBasemapChooser, isPhoneLocationVisible = state.currentPhoneLocation != null, 
                isLayerPanelOpen = state.layerPanelOpen, onEmergencyClick = onEmergencyClick, 
                onLayersClick = onLayersClick, onAddClick = onAddClick, 
                onBasemapClick = onBasemapClick, onMyLocationClick = onMyLocationClick, 
                onRecenterClick = onReturnToProperty,
                onHelpClick = { onShowMapHelp(true) },
                isAddEnabled = state.addToMapAvailability.isAvailable
            )
        }

        if (state.isSearchActive && !layoutInfo.isWidthCompact) {
            SearchSidePanel(
                state = state,
                onQueryChange = onSearchQueryChange,
                onSearchActiveChange = onSearchActiveChange,
                onResultClick = onOpenSearchResult,
                onRevealClick = onRevealAndOpenSearchResult,
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }

        if (state.showBasemapChooser) {
            val chooser: @Composable () -> Unit = {
                BasemapChooserContent(
                    basemapProvider = basemapProvider,
                    preferredId = state.preferredBasemapId,
                    activeSourceId = state.activeSourceId,
                    basemapStatus = state.basemapStatus,
                    isUsingFallback = state.isUsingFallback,
                    retryPrimaryAvailable = state.retryPrimaryAvailable,
                    showBackupChooser = state.showBackupChooser,
                    onSelectPreferred = onSelectPreferredBasemap,
                    onSelectBackup = onSelectBackupBasemap,
                    onRetryPrimary = onRetryPrimaryBasemap
                )
            }
            if (layoutInfo.isWidthCompact) {
                ModalBottomSheet(onDismissRequest = onCloseBasemapChooser, sheetState = basemapSheetState) { chooser() }
            } else {
                AlertDialog(onDismissRequest = onCloseBasemapChooser, confirmButton = { TextButton(onClick = onCloseBasemapChooser) { Text(stringResource(R.string.dismiss)) } }, text = { Box(modifier = Modifier.size(width = 400.dp, height = 500.dp)) { chooser() } })
            }
        }

        if (state.layerPanelOpen) {
            if (layoutInfo.isWidthCompact) {
                ModalBottomSheet(onDismissRequest = onLayersClick, sheetState = sheetState) { 
                    LayersPanel(
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f), 
                        layers = state.layers, 
                        activeLayerId = state.activeLayerId, 
                        onSelectLayer = onSelectLayer, 
                        onToggleVisibility = onToggleLayerVisibility, 
                        onToggleLock = onToggleLayerLock, 
                        onAddLayer = onAddLayer, 
                        onRenameLayer = onRenameLayer, 
                        onChangeLayerOpacity = onChangeLayerOpacity, 
                        onMoveLayerUp = onMoveLayerUp, 
                        onMoveLayerDown = onMoveLayerDown, 
                        onDeleteLayer = onDeleteLayer, 
                        onClose = onLayersClick, 
                        onHelpClick = onHelpClick
                    ) 
                }
            } else {
                AnimatedVisibility(visible = state.layerPanelOpen, enter = slideInHorizontally(initialOffsetX = { it }), exit = slideOutHorizontally(targetOffsetX = { it }), modifier = Modifier.align(Alignment.CenterEnd)) { 
                    LayersPanel(
                        modifier = Modifier.fillMaxHeight().widthIn(min = 300.dp, max = 400.dp), 
                        layers = state.layers, 
                        activeLayerId = state.activeLayerId, 
                        onSelectLayer = onSelectLayer, 
                        onToggleVisibility = onToggleLayerVisibility, 
                        onToggleLock = onToggleLayerLock, 
                        onAddLayer = onAddLayer, 
                        onRenameLayer = onRenameLayer, 
                        onChangeLayerOpacity = onChangeLayerOpacity, 
                        onMoveLayerUp = onMoveLayerUp, 
                        onMoveLayerDown = onMoveLayerDown, 
                        onDeleteLayer = onDeleteLayer, 
                        onClose = onLayersClick, 
                        onHelpClick = onHelpClick
                    ) 
                }
            }
        }

        if (state.showLocationDetails && state.currentPhoneLocation != null) {
            ModalBottomSheet(
                onDismissRequest = { onShowLocationDetails(false) }
            ) {
                LocationDetailsSheet(
                    location = state.currentPhoneLocation,
                    quality = state.currentPhoneLocationQuality ?: LocationAccuracyQuality.Poor,
                    measurementSystem = state.measurementSystem,
                    onRetry = { onMyLocationClick(); onShowLocationDetails(false) },
                    onHide = { onDismissLocation(); onShowLocationDetails(false) },
                    onDismiss = { onShowLocationDetails(false) },
                    onHelpClick = onHelpClick
                )
            }
        }

        if (state.showPlacementMethod && state.pendingGuidedPreset != null) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.cancelGuidedLocationPlacement() }
            ) {
                PlacementMethodSheet(
                    preset = state.pendingGuidedPreset,
                    onMyLocation = { 
                        onSelectGuidedLocationMethod(PlacementMethod.MY_LOCATION)
                        onRequestLocationWithPermission(LocationRequestPurpose.CreatePoint)
                    },
                    onTapMap = { onSelectGuidedLocationMethod(PlacementMethod.TAP_MAP) },
                    onDismiss = { viewModel.cancelGuidedLocationPlacement() }
                )
            }
        }

        if (state.showGuidedAddMenu) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.setShowGuidedAddMenu(false) }
            ) {
                LaunchedEffect(Unit) {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
                GuidedAddMenu(
                    onPresetSelected = { onPresetSelected(it) },
                    onDismiss = { viewModel.setShowGuidedAddMenu(false) }
                )
            }
        }

        if (state.showBoundaryAcknowledgment) {
            BoundaryAcknowledgmentDialog(
                isSaving = state.isSavingBoundaryAcknowledgment,
                errorRes = state.boundaryAcknowledgmentErrorRes,
                onConfirm = onAcknowledgeBoundary,
                onCancel = onCancelBoundary,
                onLearnMore = { viewModel.setShowSafetyLimitations(true) }
            )
        }

        if (state.showSafetyLimitations) {
            ModalBottomSheet(onDismissRequest = { viewModel.setShowSafetyLimitations(false) }) {
                SafetyLimitationsSheet(onDismiss = { viewModel.setShowSafetyLimitations(false) })
            }
        }

        if (state.showStarterLayersDialog) {
            StarterLayersDialog(
                operation = state.starterLayerOperation,
                errorRes = state.starterLayerErrorRes,
                onConfirm = onCreateStarterLayers,
                onSkip = onSkipStarterLayers
            )
        }

        if (state.showHelpSheet) {
            ModalBottomSheet(onDismissRequest = onDismissHelpSheet) {
                MapControlsHelpSheet(onDismiss = onDismissHelpSheet)
            }
        }

        if (state.showDiscardEditDialog) {
            val titleRes = when (state.discardAction) {
                PendingEditDiscardAction.CancelLineEdit -> R.string.discard_line_shape_changes
                PendingEditDiscardAction.CancelPolygonEdit -> R.string.discard_area_shape_changes
                PendingEditDiscardAction.DiscardNewPoint -> R.string.discard_changes_title
                PendingEditDiscardAction.DiscardNewLine -> R.string.discard_line_shape_changes
                PendingEditDiscardAction.DiscardNewPolygon -> R.string.discard_area_shape_changes
                PendingEditDiscardAction.DiscardGuidedCreation -> R.string.discard_guided_creation_title
                is PendingEditDiscardAction.ChangeProperty -> R.string.discard_property_mapping_changes
                is PendingEditDiscardAction.ChangePlan -> R.string.discard_plan_mapping_changes
                null -> R.string.discard_changes_title
            }
            AlertDialog(
                onDismissRequest = onDismissDiscardDialog,
                title = { Text(stringResource(titleRes)) },
                confirmButton = { TextButton(onClick = onConfirmDiscardEdit) { Text(stringResource(R.string.discard_changes)) } },
                dismissButton = { TextButton(onClick = onDismissDiscardDialog) { Text(stringResource(R.string.keep_editing)) } }
            )
        }

        if (state.featureEditorOpen && (state.selectedFeature != null || state.featureEditorFeature != null)) {
            val sf = state.featureEditorFeature ?: state.selectedFeature!!
            val onDismissHandler = { onDismissFeatureEditor() }

            val detailContent: @Composable () -> Unit = {
                val detailState = state.featureDetailState
                when (detailState) {
                    is FeatureDetailUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is FeatureDetailUiState.Ready -> {
                        UnifiedFeatureDetailSheet(
                            uiState = detailState,
                            onEditClick = { viewModel.onEditFeatureClick() },
                            onDeleteClick = { onDeleteFeature(sf.id) },
                            onTakePhoto = {
                                viewModel.setPendingPhotoPurpose(PendingPhotoPurpose.SavedFeatureAttachment(sf.id))
                                mainScope.launch {
                                    viewModel.createCameraCapture().onSuccess { capture ->
                                        viewModel.setInFlightCapture(capture.uri.toString(), capture.token)
                                        cameraLauncher.launch(capture.uri)
                                    }
                                }
                            },
                            onChoosePhoto = {
                                viewModel.setPendingPhotoPurpose(PendingPhotoPurpose.SavedFeatureAttachment(sf.id))
                                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            onAddFile = {
                                viewModel.setPendingPhotoPurpose(PendingPhotoPurpose.SavedFeatureAttachment(sf.id))
                                documentPickerLauncher.launch(arrayOf("application/pdf", "text/plain", "image/*"))
                            },
                            onViewAllAttachments = { onViewAttachments(sf.id) },
                            onAttachmentClick = { aid -> 
                                state.propertyId?.let { pid -> onAttachmentDetails(pid, aid) }
                            },
                            onOpenLinkedRecord = { iid -> 
                                state.propertyId?.let { pid -> onOpenInfrastructureDetails(pid, iid) }
                            },
                            onDismiss = onDismissHandler,
                            onClearDeleteError = { viewModel.clearDeleteFeatureError() }
                        )
                    }
                    is FeatureDetailUiState.Error -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                            Text(stringResource(detailState.messageRes), textAlign = TextAlign.Center)
                            Button(onClick = { viewModel.retryFeatureDetails() }) {
                                Text(stringResource(R.string.retry))
                            }
                            TextButton(onClick = onDismissHandler) {
                                Text(stringResource(R.string.back))
                            }
                        }
                    }
                    is FeatureDetailUiState.NotFound -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(Icons.Default.SearchOff, contentDescription = null, modifier = Modifier.size(48.dp))
                            Text(stringResource(R.string.error_feature_not_found), textAlign = TextAlign.Center)
                            Button(onClick = onDismissHandler) {
                                Text(stringResource(R.string.dismiss))
                            }
                        }
                    }
                    null -> {
                        // Fallback if state hasn't reached Loading yet
                        Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }

            val editorContent: @Composable () -> Unit = {
                FeatureDetailSheet(
                    feature = sf, layers = state.layers, infrastructureItems = systemItems, 
                    isSaving = state.isSavingFeature, isDeleting = state.isDeletingFeature, 
                    labelError = state.labelError, accuracyError = state.accuracyError,
                    errorMsg = state.featureOperationErrorRes?.let { stringResource(it) }, onSave = onSaveFeature, 
                    onDelete = onDeleteFeature, onDismiss = onDismissHandler, 
                    onSaveNewSystemItem = onSaveNewSystemItem, onMovePointClick = onMovePointClick, 
                    onEditShapeClick = onEditShapeClick, 
                    canEditShape = state.canEditShape,
                    editShapeBlockReason = state.editShapeBlockReasonRes?.let { stringResource(it) },
                    isNewUnsavedFeature = state.isNewUnsavedFeature,
                    attachmentCount = attachmentCount,
                    photoCount = photoCount,
                    coverThumbnailUri = coverThumbnailUri,
                    onViewAttachments = { onViewAttachments(sf.id) },
                    onTakePhoto = { onTakePhoto(sf.id) },
                    onChoosePhoto = { onChoosePhoto(sf.id) },
                    onChooseDocument = { onChooseDocument(sf.id) },
                    measurementSystem = state.measurementSystem,
                    guidedPrefill = state.guidedPrefill,
                    systemItemDraft = systemItemDraft,
                    onClearSystemItemDraft = onClearSystemItemDraft,
                    linkSelection = linkSelection,
                    onLinkSelectionChange = onLinkSelectionChange,
                    stagedPhoto = stagedPhoto,
                    onRemoveStagedPhoto = onRemoveStagedPhoto,
                    onTakePhotoCreation = onTakePhotoCreation,
                    onChoosePhotoCreation = onChoosePhotoCreation,
                    onCancel = if (state.isEditingFeature) { { viewModel.onCancelFeatureEdit() } } else onCancelGuidedCreation,
                    saveOutcome = state.saveOutcome,
                    onRetryPhoto = { pid, fid -> viewModel.retryFeaturePhoto(pid, fid) },
                    onContinueWithoutPhoto = { fid -> viewModel.continueWithoutFeaturePhoto(fid) }
                )
            }

            if (layoutInfo.isWidthCompact) {
                ModalBottomSheet(onDismissRequest = onDismissHandler, sheetState = sheetState) { 
                    if (state.isNewUnsavedFeature || state.isEditingFeature) {
                        editorContent()
                    } else {
                        detailContent()
                    }
                }
            } else {
                Surface(modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight().widthIn(min = 320.dp, max = 450.dp), tonalElevation = 8.dp) { 
                    if (state.isNewUnsavedFeature || state.isEditingFeature) {
                        editorContent()
                    } else {
                        detailContent()
                    }
                }
            }
        }

    }
}

private fun generateAccuracyCircleGeoJson(lat: Double, lng: Double, radiusMeters: Double): String {
    val points = 64
    val coords = mutableListOf<String>()
    val earthRadius = 6371000.0
    val latRad = lat * PI / 180.0
    val lngRad = lng * PI / 180.0
    val angularDistance = radiusMeters / earthRadius
    for (i in 0..points) {
        val bearing = (i * 360.0 / points) * PI / 180.0
        val pLat = asin(sin(latRad) * cos(angularDistance) + cos(latRad) * sin(angularDistance) * cos(bearing))
        val pLng = lngRad + atan2(sin(bearing) * sin(angularDistance) * cos(latRad), cos(angularDistance) - sin(latRad) * sin(pLat))
        coords.add("[${pLng * 180.0 / PI},${pLat * 180.0 / PI}]")
    }
    return "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[${coords.joinToString(",")}]]}}"
}

private fun verifyMapsteadOverlays(style: Style): Boolean {
    val sourcePresent = style.getSource(MapsteadMapOverlayInstaller.FEATURES_SOURCE_ID) != null
    val pointsPresent = style.getLayer(MapsteadMapOverlayInstaller.POINTS_LAYER_ID) != null
    val linesPresent = style.getLayer(MapsteadMapOverlayInstaller.LINES_LAYER_ID) != null
    val polyPresent = style.getLayer(MapsteadMapOverlayInstaller.SAVED_POLYGONS_FILL_LAYER_ID) != null
    
    val healthy = sourcePresent && pointsPresent && linesPresent && polyPresent
    if (!healthy) {
        android.util.Log.w("MapRecovery", "Overlay health check failed: src=$sourcePresent, p=$pointsPresent, l=$linesPresent, poly=$polyPresent")
    }
    return healthy
}

private fun reinstallMapsteadOverlays(style: Style, state: MapUiState) {
    android.util.Log.d("MapOverlay", "Reinstalling overlays: style=${style.isFullyLoaded}, features=${state.visibleFeatures.size}, mode=${state.editingMode}, gen=${state.basemapGeneration}")
    
    val pointsList = mutableListOf<org.json.JSONObject>()
    val linesList = mutableListOf<org.json.JSONObject>()
    val polygonsList = mutableListOf<org.json.JSONObject>()

    state.visibleFeatures.forEach { feature ->
        val layer = state.layers.find { it.id == feature.layerId }
        val alpha = layer?.opacity ?: 1.0f
        val isLocked = layer?.isLocked == true
        try {
            val geomObj = org.json.JSONObject(feature.geometryJson)
            val feat = org.json.JSONObject().apply {
                put("type", "Feature")
                put("geometry", geomObj)
                put("properties", org.json.JSONObject().apply {
                    put("id", feature.id.toString())
                    put("geom_type", feature.geometryType)
                    put("label", feature.label ?: "")
                    put("layer_id", feature.layerId.toString())
                    put("opacity", alpha.toDouble())
                    put("locked", isLocked)
                    
                    try {
                        val style = org.json.JSONObject(feature.styleJson)
                        if (style.has("preset_style")) {
                            put("preset_style", style.getString("preset_style"))
                        }
                    } catch (e: Exception) {}
                })
            }
            when (feature.geometryType) {
                "POINT" -> pointsList.add(feat)
                "LINESTRING" -> linesList.add(feat)
                "POLYGON" -> polygonsList.add(feat)
            }
        } catch (e: Exception) { }
    }

    fun wrapInCollection(list: List<org.json.JSONObject>) = org.json.JSONObject().apply {
        put("type", "FeatureCollection")
        val array = org.json.JSONArray()
        list.forEach { array.put(it) }
        put("features", array)
    }

    MapsteadMapOverlayInstaller.installOrUpdateSource(style, MapsteadMapOverlayInstaller.FEATURES_SOURCE_ID, wrapInCollection(pointsList + linesList + polygonsList).toString())
    MapsteadMapOverlayInstaller.installFeaturesLayers(style)
    MapsteadMapOverlayInstaller.installHighlights(style, state.selectedFeature?.id?.toString())
    MapsteadMapOverlayInstaller.installActiveEditHighlights(style, state.activeEditFeatureId?.toString())
    
    val phoneLoc = state.currentPhoneLocation
    if (phoneLoc != null) {
        MapsteadMapOverlayInstaller.installOrUpdateSource(style, MapsteadMapOverlayInstaller.PHONE_POINT_SOURCE_ID, "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[${phoneLoc.longitude},${phoneLoc.latitude}]}}")
        MapsteadMapOverlayInstaller.installOrUpdateSource(style, MapsteadMapOverlayInstaller.PHONE_ACCURACY_SOURCE_ID, generateAccuracyCircleGeoJson(phoneLoc.latitude, phoneLoc.longitude, phoneLoc.accuracyMeters.toDouble()))
        MapsteadMapOverlayInstaller.installPhoneLocationLayers(style)
    } else {
        MapsteadMapOverlayInstaller.removeSourceAndLayers(style, MapsteadMapOverlayInstaller.PHONE_POINT_SOURCE_ID, listOf(MapsteadMapOverlayInstaller.PHONE_LOCATION_CIRCLE_ID))
        MapsteadMapOverlayInstaller.removeSourceAndLayers(style, MapsteadMapOverlayInstaller.PHONE_ACCURACY_SOURCE_ID, listOf(MapsteadMapOverlayInstaller.PHONE_ACCURACY_FILL_ID, MapsteadMapOverlayInstaller.PHONE_ACCURACY_OUTLINE_ID))
    }
    
    val vertices = state.draftVertices
    if (state.editingMode == MapEditingMode.AddLine && vertices.isNotEmpty()) {
        if (vertices.size >= 2) {
            val coordsStr = vertices.joinToString(",") { "[${it.first},${it.second}]" }
            MapsteadMapOverlayInstaller.installOrUpdateSource(style, MapsteadMapOverlayInstaller.DRAFT_LINE_SOURCE_ID, "{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\",\"coordinates\":[$coordsStr]}}")
        } else {
            MapsteadMapOverlayInstaller.removeSourceAndLayers(style, MapsteadMapOverlayInstaller.DRAFT_LINE_SOURCE_ID, listOf(MapsteadMapOverlayInstaller.DRAFT_LINE_LAYER_ID))
        }
        val featuresVerticesJson = vertices.joinToString(",") { "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[${it.first},${it.second}]}}" }
        MapsteadMapOverlayInstaller.installOrUpdateSource(style, MapsteadMapOverlayInstaller.DRAFT_VERTICES_SOURCE_ID, "{\"type\":\"FeatureCollection\",\"features\":[$featuresVerticesJson]}")
        MapsteadMapOverlayInstaller.installDraftLayers(style)
    } else {
        MapsteadMapOverlayInstaller.removeSourceAndLayers(style, MapsteadMapOverlayInstaller.DRAFT_LINE_SOURCE_ID, listOf(MapsteadMapOverlayInstaller.DRAFT_LINE_LAYER_ID))
        MapsteadMapOverlayInstaller.removeSourceAndLayers(style, MapsteadMapOverlayInstaller.DRAFT_VERTICES_SOURCE_ID, listOf(MapsteadMapOverlayInstaller.DRAFT_VERTICES_LAYER_ID))
    }

    // Polygon Draft
    val polyDraft = state.polygonDraft
    if (state.editingMode == MapEditingMode.AddPolygon && polyDraft != null && polyDraft.vertices.isNotEmpty()) {
        val vertices = polyDraft.vertices
        if (vertices.size >= 3) {
            val ring = vertices + listOf(vertices.first())
            val coordsStr = ring.joinToString(",") { "[${it.first},${it.second}]" }
            MapsteadMapOverlayInstaller.installOrUpdateSource(style, MapsteadMapOverlayInstaller.DRAFT_POLYGON_SOURCE_ID, "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[$coordsStr]]}}")
        } else {
            MapsteadMapOverlayInstaller.removeSourceAndLayers(style, MapsteadMapOverlayInstaller.DRAFT_POLYGON_SOURCE_ID, listOf(MapsteadMapOverlayInstaller.DRAFT_POLYGON_FILL_LAYER_ID, MapsteadMapOverlayInstaller.DRAFT_POLYGON_OUTLINE_LAYER_ID))
        }
        val verticesJson = vertices.joinToString(",") { "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[${it.first},${it.second}]}}" }
        MapsteadMapOverlayInstaller.installOrUpdateSource(style, MapsteadMapOverlayInstaller.DRAFT_POLYGON_VERTICES_SOURCE_ID, verticesJson)
        MapsteadMapOverlayInstaller.installPolygonDraftLayers(style)
    } else {
        MapsteadMapOverlayInstaller.removeSourceAndLayers(style, MapsteadMapOverlayInstaller.DRAFT_POLYGON_SOURCE_ID, listOf(MapsteadMapOverlayInstaller.DRAFT_POLYGON_FILL_LAYER_ID, MapsteadMapOverlayInstaller.DRAFT_POLYGON_OUTLINE_LAYER_ID))
        MapsteadMapOverlayInstaller.removeSourceAndLayers(style, MapsteadMapOverlayInstaller.DRAFT_POLYGON_VERTICES_SOURCE_ID, listOf(MapsteadMapOverlayInstaller.DRAFT_POLYGON_VERTICES_LAYER_ID))
    }

    // Line Edit
    val lineEdit = state.lineEditState
    if (state.editingMode == MapEditingMode.EditLine && lineEdit != null) {
        val vertices = lineEdit.workingVertices
        val coordsStr = vertices.joinToString(",") { "[${it.first},${it.second}]" }
        val lineJson = "{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\",\"coordinates\":[$coordsStr]}}"
        MapsteadMapOverlayInstaller.installOrUpdateSource(style, MapsteadMapOverlayInstaller.EDIT_LINE_SOURCE_ID, lineJson)
        
        val verticesJson = vertices.mapIndexed { index, pair -> "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[${pair.first},${pair.second}]},\"properties\":{\"index\":$index}}" }.joinToString(",")
        MapsteadMapOverlayInstaller.installOrUpdateSource(style, MapsteadMapOverlayInstaller.EDIT_VERTICES_SOURCE_ID, "{\"type\":\"FeatureCollection\",\"features\":[$verticesJson]}")
        
        val midpoints = mutableListOf<String>()
        for (i in 0 until vertices.size - 1) {
            val mid = Pair((vertices[i].first + vertices[i+1].first) / 2, (vertices[i].second + vertices[i+1].second) / 2)
            midpoints.add("{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[${mid.first},${mid.second}]},\"properties\":{\"index\":$i}}")
        }
        MapsteadMapOverlayInstaller.installOrUpdateSource(style, MapsteadMapOverlayInstaller.EDIT_MIDPOINTS_SOURCE_ID, "{\"type\":\"FeatureCollection\",\"features\":[${midpoints.joinToString(",")}]}")
        
        MapsteadMapOverlayInstaller.installEditLayers(style, lineEdit.selectedVertexIndex)
    } else {
        MapsteadMapOverlayInstaller.removeSourceAndLayers(style, MapsteadMapOverlayInstaller.EDIT_LINE_SOURCE_ID, listOf(MapsteadMapOverlayInstaller.EDIT_LINE_LAYER_ID))
        MapsteadMapOverlayInstaller.removeSourceAndLayers(style, MapsteadMapOverlayInstaller.EDIT_VERTICES_SOURCE_ID, listOf(MapsteadMapOverlayInstaller.EDIT_VERTICES_LAYER_ID, MapsteadMapOverlayInstaller.EDIT_SELECTED_VERTEX_LAYER_ID))
        MapsteadMapOverlayInstaller.removeSourceAndLayers(style, MapsteadMapOverlayInstaller.EDIT_MIDPOINTS_SOURCE_ID, listOf(MapsteadMapOverlayInstaller.EDIT_MIDPOINTS_LAYER_ID))
    }

    // Polygon Edit
    val polyEdit = state.polygonEditState
    if (state.editingMode == MapEditingMode.EditPolygon && polyEdit != null) {
        val vertices = polyEdit.workingVertices
        val ring = vertices + listOf(vertices.first())
        val coordsStr = ring.joinToString(",") { "[${it.first},${it.second}]" }
        val lineJson = "{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\",\"coordinates\":[$coordsStr]}}"
        MapsteadMapOverlayInstaller.installOrUpdateSource(style, MapsteadMapOverlayInstaller.POLYGON_EDIT_LINE_SOURCE_ID, lineJson)
        
        val verticesJson = vertices.mapIndexed { index, pair -> "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[${pair.first},${pair.second}]},\"properties\":{\"index\":$index}}" }.joinToString(",")
        MapsteadMapOverlayInstaller.installOrUpdateSource(style, MapsteadMapOverlayInstaller.POLYGON_EDIT_VERTICES_SOURCE_ID, "{\"type\":\"FeatureCollection\",\"features\":[$verticesJson]}")
        
        val midpoints = mutableListOf<String>()
        for (i in 0 until vertices.size) {
            val next = (i + 1) % vertices.size
            val mid = Pair((vertices[i].first + vertices[next].first) / 2, (vertices[i].second + vertices[next].second) / 2)
            midpoints.add("{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[${mid.first},${mid.second}]},\"properties\":{\"index\":$i}}")
        }
        MapsteadMapOverlayInstaller.installOrUpdateSource(style, MapsteadMapOverlayInstaller.POLYGON_EDIT_MIDPOINTS_SOURCE_ID, "{\"type\":\"FeatureCollection\",\"features\":[${midpoints.joinToString(",")}]}")
        
        MapsteadMapOverlayInstaller.installPolygonEditLayers(style, polyEdit.selectedVertexIndex)
    } else {
        MapsteadMapOverlayInstaller.removeSourceAndLayers(style, MapsteadMapOverlayInstaller.POLYGON_EDIT_LINE_SOURCE_ID, listOf(MapsteadMapOverlayInstaller.POLYGON_EDIT_LINE_LAYER_ID))
        MapsteadMapOverlayInstaller.removeSourceAndLayers(style, MapsteadMapOverlayInstaller.POLYGON_EDIT_VERTICES_SOURCE_ID, listOf(MapsteadMapOverlayInstaller.POLYGON_EDIT_VERTICES_LAYER_ID, MapsteadMapOverlayInstaller.POLYGON_EDIT_SELECTED_VERTEX_LAYER_ID))
        MapsteadMapOverlayInstaller.removeSourceAndLayers(style, MapsteadMapOverlayInstaller.POLYGON_EDIT_MIDPOINTS_SOURCE_ID, listOf(MapsteadMapOverlayInstaller.POLYGON_EDIT_MIDPOINTS_LAYER_ID))
    }

    // Point Move
    val moveState = state.pointMoveState
    if (moveState != null) {
        val originalJson = "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[${moveState.originalLongitude},${moveState.originalLatitude}]}}"
        MapsteadMapOverlayInstaller.installOrUpdateSource(style, MapsteadMapOverlayInstaller.ORIGINAL_LOCATION_GHOST_SOURCE_ID, originalJson)
        MapsteadMapOverlayInstaller.installOriginalLocationGhost(style)
        
        val proposedLng = moveState.proposedLongitude ?: moveState.originalLongitude
        val proposedLat = moveState.proposedLatitude ?: moveState.originalLatitude
        val moveJson = "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[$proposedLng,$proposedLat]}}"
        MapsteadMapOverlayInstaller.installOrUpdateSource(style, MapsteadMapOverlayInstaller.POINT_MOVE_SOURCE_ID, moveJson)
        MapsteadMapOverlayInstaller.installPointMoveLayers(style)
    } else {
        MapsteadMapOverlayInstaller.removeSourceAndLayers(style, MapsteadMapOverlayInstaller.ORIGINAL_LOCATION_GHOST_SOURCE_ID, listOf(MapsteadMapOverlayInstaller.ORIGINAL_LOCATION_GHOST_LAYER_ID))
        MapsteadMapOverlayInstaller.removeSourceAndLayers(style, MapsteadMapOverlayInstaller.POINT_MOVE_SOURCE_ID, listOf(MapsteadMapOverlayInstaller.POINT_MOVE_LAYER_ID))
    }
}

@Composable
fun BasemapChooserContent(
    basemapProvider: BasemapProvider,
    preferredId: BasemapId,
    activeSourceId: BasemapSourceId?,
    basemapStatus: BasemapLoadStatus,
    isUsingFallback: Boolean,
    retryPrimaryAvailable: Boolean,
    showBackupChooser: Boolean,
    onSelectPreferred: (BasemapId) -> Unit,
    onSelectBackup: (BasemapSourceId) -> Unit,
    onRetryPrimary: () -> Unit
) {
    var showBackupsLocal by remember { mutableStateOf(false) }
    val showBackups = showBackupChooser || showBackupsLocal

    Column(modifier = Modifier.padding(16.dp).testTag("BasemapChooser")) {
        Text(stringResource(R.string.choose_basemap), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        if (isUsingFallback || basemapStatus == BasemapLoadStatus.FAILED) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(if (basemapStatus == BasemapLoadStatus.FAILED) R.string.failed_to_load_basemap else R.string.basemap_fallback_notice),
                        style = MaterialTheme.typography.bodySmall, 
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    if (retryPrimaryAvailable) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onRetryPrimary, modifier = Modifier.align(Alignment.End)) {
                            Text(stringResource(R.string.retry_primary_map))
                        }
                    }
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(stringResource(R.string.preferred_map_section), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
            }
            items(basemapProvider.getPrimaryBasemaps()) { def ->
                val isPreferred = def.preferredId == preferredId
                val isActive = def.sourceId == activeSourceId
                val preferredLabel = stringResource(R.string.basemap_preferred_label)
                val activeLabel = stringResource(R.string.basemap_active_label)
                
                val displayName = stringResource(def.displayNameRes)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { def.preferredId?.let { onSelectPreferred(it) } }
                        .testTag("BasemapOption_${def.sourceId.name}")
                        .semantics { 
                            selected = isPreferred
                            contentDescription = "$displayName. ${if (isPreferred) preferredLabel else ""}. ${if (isActive) activeLabel else ""}"
                        },
                    colors = CardDefaults.cardColors(containerColor = if (isPreferred) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(def.displayNameRes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            if (def.preferredId == BasemapId.SATELLITE_HYBRID) {
                                Text(stringResource(R.string.satellite_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(stringResource(def.descriptionRes), style = MaterialTheme.typography.bodySmall)
                        }
                        if (isPreferred) { Icon(Icons.Default.Star, contentDescription = preferredLabel, modifier = Modifier.padding(end = 8.dp)) }
                        if (isActive) { Icon(Icons.Default.Check, contentDescription = activeLabel, tint = MaterialTheme.colorScheme.primary) }
                    }
                }
            }

            if (showBackups) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showBackupsLocal = !showBackupsLocal },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.backup_maps_section), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
                        Icon(if (showBackupsLocal) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                    }
                    Spacer(Modifier.height(4.dp))
                }
                if (showBackupsLocal || showBackupChooser) {
                    items(basemapProvider.getBackupBasemaps()) { def ->
                        val isActive = def.sourceId == activeSourceId
                        val activeLabel = stringResource(R.string.basemap_active_label)
                        val displayName = stringResource(def.displayNameRes)
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectBackup(def.sourceId) }
                                .testTag("BackupOption_${def.sourceId.name}")
                                .semantics { 
                                    selected = isActive
                                    contentDescription = "$displayName. ${if (isActive) activeLabel else ""}"
                                },
                            colors = CardDefaults.cardColors(containerColor = if (isActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(2.dp))
                                    Text(stringResource(def.descriptionRes), style = MaterialTheme.typography.bodySmall)
                                }
                                if (isActive) { Icon(Icons.Default.Check, contentDescription = activeLabel, tint = MaterialTheme.colorScheme.primary) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsOverlay(
    state: MapUiState,
    onResultClick: (MapSearchResult) -> Unit,
    onRevealClick: (MapSearchResult) -> Unit
) {
    if (state.searchResults.isNotEmpty()) {
        Card(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("MapSearchResults"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                items(state.searchResults) { result ->
                    SearchResultItem(
                        result = result,
                        isWorkflowActive = state.isWorkflowActive,
                        workflowBlockReason = state.workflowBlockReasonRes?.let { stringResource(it) },
                        onResultClick = onResultClick,
                        onRevealClick = onRevealClick
                    )
                    HorizontalDivider()
                }
            }
        }
    } else if (state.searchQuery.isNotBlank()) {
        Card(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("MapSearchEmptyState"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_matches_found))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSidePanel(
    state: MapUiState,
    onQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onResultClick: (MapSearchResult) -> Unit,
    onRevealClick: (MapSearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .widthIn(min = 320.dp, max = 400.dp),
        tonalElevation = 8.dp,
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchBarExpressive(
                query = state.searchQuery,
                onQueryChange = onQueryChange,
                onSearchActiveChange = onSearchActiveChange,
                active = true,
                propertyName = state.propertyName,
                planName = state.plan?.name,
                onClearQuery = { onQueryChange("") },
                modifier = Modifier.padding(8.dp)
            )
            
            if (state.searchResults.isNotEmpty()) {
                LazyColumn(modifier = Modifier.weight(1f).testTag("MapSearchResults")) {
                    items(state.searchResults) { result ->
                        SearchResultItem(
                            result = result,
                            isWorkflowActive = state.isWorkflowActive,
                            workflowBlockReason = state.workflowBlockReasonRes?.let { stringResource(it) },
                            onResultClick = onResultClick,
                            onRevealClick = onRevealClick
                        )
                        HorizontalDivider()
                    }
                }
            } else if (state.searchQuery.isNotBlank()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().testTag("MapSearchEmptyState"), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = stringResource(R.string.no_matches_found),
                        description = "Try searching for a different name or category.",
                        icon = Icons.Default.Search,
                        useFullHeight = false
                    )
                }
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun SearchResultItem(
    result: MapSearchResult,
    isWorkflowActive: Boolean,
    workflowBlockReason: String?,
    onResultClick: (MapSearchResult) -> Unit,
    onRevealClick: (MapSearchResult) -> Unit
) {
    val isHidden = !result.isLayerVisible
    
    val headline = remember(result.featureLabel, result.systemItemName) {
        val label = result.featureLabel?.trim()
        val systemName = result.systemItemName?.trim()
        when {
            !label.isNullOrBlank() -> label
            !systemName.isNullOrBlank() -> systemName
            else -> null
        }
    }

    val metadata = remember(result.category, result.subtype) {
        val cat = result.category?.trim()
        val sub = result.subtype?.trim()
        when {
            !cat.isNullOrBlank() && !sub.isNullOrBlank() -> "$cat \u2022 $sub"
            !cat.isNullOrBlank() -> cat
            !sub.isNullOrBlank() -> sub
            else -> ""
        }
    }

    ListItem(
        headlineContent = { 
            Text(headline ?: stringResource(when(result.geometryType) {
                "LINESTRING" -> R.string.feature_details_line
                "POLYGON" -> R.string.feature_details_area
                else -> R.string.feature_details_point
            })) 
        },
        supportingContent = { 
            Column {
                val hiddenSuffix = if (isHidden) stringResource(R.string.layer_hidden_suffix) else ""
                Text("${result.layerName}$hiddenSuffix")
                if (metadata.isNotBlank()) {
                    Text(metadata)
                }
                if (isWorkflowActive && workflowBlockReason != null) {
                    Text(workflowBlockReason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        leadingContent = {
            Icon(
                when(result.geometryType) {
                    "POINT" -> Icons.Default.Place
                    "LINESTRING" -> Icons.Default.Timeline
                    else -> Icons.Default.Category
                },
                contentDescription = null,
                tint = if (result.isEmergency) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        },
        trailingContent = {
            if (isHidden) {
                TextButton(
                    onClick = { onRevealClick(result) },
                    enabled = !isWorkflowActive
                ) {
                    Text(stringResource(R.string.show_layer_and_open))
                }
            }
        },
        modifier = Modifier.clickable(enabled = !isHidden && !isWorkflowActive) { 
            onResultClick(result)
        }
    )
}
