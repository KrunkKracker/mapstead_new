package com.jumastappworks.mapstead.ui.mapping

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.util.AdaptiveLayoutInfo
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MapWorkflowUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel = mockk<MapViewModel>(relaxed = true)

    private val fakeBasemapProvider = object : BasemapProvider {
        override fun availableBasemaps() = getPrimaryBasemaps()
        override fun getBasemap(id: BasemapId) = getPrimaryBasemaps().find { it.preferredId == id }
        override fun defaultBasemap() = BasemapId.STREETS

        override fun getPrimaryBasemaps() = listOf(
            BasemapDefinition(BasemapSourceId.MAPTILER_STREETS, BasemapProviderType.MAPTILER, BasemapRole.PRIMARY, "url", com.jumastappworks.mapstead.R.string.street, com.jumastappworks.mapstead.R.string.basemap_street_desc, true, BasemapId.STREETS, BasemapSourceId.OPEN_FREE_MAP_LIBERTY),
            BasemapDefinition(BasemapSourceId.MAPTILER_BASE, BasemapProviderType.MAPTILER, BasemapRole.PRIMARY, "url", com.jumastappworks.mapstead.R.string.light, com.jumastappworks.mapstead.R.string.basemap_light_desc, true, BasemapId.BASE, BasemapSourceId.OPEN_FREE_MAP_POSITRON),
            BasemapDefinition(BasemapSourceId.MAPTILER_TOPO, BasemapProviderType.MAPTILER, BasemapRole.PRIMARY, "url", com.jumastappworks.mapstead.R.string.topo, com.jumastappworks.mapstead.R.string.basemap_topo_desc, true, BasemapId.TOPO, BasemapSourceId.OPEN_FREE_MAP_FIORD),
            BasemapDefinition(BasemapSourceId.MAPTILER_HYBRID, BasemapProviderType.MAPTILER, BasemapRole.PRIMARY, "url", com.jumastappworks.mapstead.R.string.satellite_hybrid, com.jumastappworks.mapstead.R.string.basemap_satellite_desc, true, BasemapId.SATELLITE_HYBRID, BasemapSourceId.OPEN_FREE_MAP_LIBERTY),
            BasemapDefinition(BasemapSourceId.MAPTILER_OUTDOOR, BasemapProviderType.MAPTILER, BasemapRole.PRIMARY, "url", com.jumastappworks.mapstead.R.string.outdoor, com.jumastappworks.mapstead.R.string.basemap_outdoor_desc, true, BasemapId.OUTDOOR, BasemapSourceId.OPEN_FREE_MAP_FIORD)
        )
        override fun getBackupBasemaps() = listOf(
            BasemapDefinition(BasemapSourceId.OPEN_FREE_MAP_LIBERTY, BasemapProviderType.OPEN_FREE_MAP, BasemapRole.BACKUP, "url", com.jumastappworks.mapstead.R.string.street, com.jumastappworks.mapstead.R.string.basemap_street_desc, true),
            BasemapDefinition(BasemapSourceId.OPEN_FREE_MAP_DARK, BasemapProviderType.OPEN_FREE_MAP, BasemapRole.BACKUP, "url", com.jumastappworks.mapstead.R.string.dark, com.jumastappworks.mapstead.R.string.basemap_dark_desc, true)
        )
        override fun getDefinition(sourceId: BasemapSourceId) = (getPrimaryBasemaps() + getBackupBasemaps()).find { it.sourceId == sourceId }
        override fun resolveDefaultBackup(preferredId: BasemapId) = BasemapSourceId.OPEN_FREE_MAP_LIBERTY
        override fun getDefaultBasemapId() = BasemapId.STREETS
        override fun buildStyleUrl(sourceId: BasemapSourceId) = "url"
        override fun redactUrl(url: String) = url
        override fun getAttribution(sourceId: BasemapSourceId) = emptyList<BasemapAttributionEntry>()
    }

    private val compactLayout = AdaptiveLayoutInfo(
        isWidthCompact = true, isWidthMedium = false, isWidthExpanded = false,
        isHeightCompact = false, isHeightMedium = true, isHeightExpanded = false,
        isLandscape = false, showPersistentSupportingPane = false,
        useBottomNavigation = true, useNavigationRail = false
    )

    private val expandedLayout = AdaptiveLayoutInfo(
        isWidthCompact = false, isWidthMedium = false, isWidthExpanded = true,
        isHeightCompact = false, isHeightMedium = true, isHeightExpanded = false,
        isLandscape = false, showPersistentSupportingPane = false,
        useBottomNavigation = false, useNavigationRail = true
    )

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun testBasemapChooserDismissalOnSelection() {
        composeTestRule.setContent {
            var showChooser by remember { mutableStateOf(true) }
            
            MapScreenContent(
                viewModel = viewModel,
                layoutInfo = compactLayout,
                state = MapUiState(),
                systemItems = emptyList(),
                mapViewFactory = { ctx, _ -> android.view.View(ctx) },
                sheetState = rememberModalBottomSheetState(),
                basemapSheetState = rememberModalBottomSheetState(),
                isBasemapLoading = false,
                basemapProvider = fakeBasemapProvider,
                onUndoVertexClick = {}, 
                onFinishLineClick = {}, onCancelLineClick = {},
                onEmergencyClick = {}, onLayersClick = {}, onMyLocationClick = {},
                onUsePhoneLocationClick = {}, onClearMapError = {}, onClearBasemapError = {},
 onDismissLocation = {},
                onDismissRationale = {}, onAllowRationale = {}, onSelectLayer = { _ -> }, onToggleLayerVisibility = { _ -> },
                onToggleLayerLock = { _ -> }, onAddLayer = { _, _ -> }, onRenameLayer = { _, _ -> },
                onChangeLayerOpacity = { _, _ -> }, onMoveLayerUp = { _ -> }, onMoveLayerDown = { _ -> }, onDeleteLayer = { _ -> },
                onSaveFeature = { _ -> }, onDeleteFeature = { _ -> }, onSaveNewSystemItem = { _ -> UUID.randomUUID() },
                onEditShapeClick = { _ -> }, onUndoEditClick = {}, onDeleteVertexClick = {}, onSaveEditClick = {}, onCancelEditClick = {},
                onConfirmDiscardEdit = {}, onDismissDiscardDialog = {},
                onUndoPolygonVertexClick = {}, onFinishPolygonClick = {}, onCancelPolygonClick = {}, onDismissFeatureEditor = {},
                onConfirmPointMove = {}, onCancelPointMove = {},
                onMovePointClick = { _ -> },
                onUndoPolygonEditClick = {}, onDeletePolygonVertexClick = {}, onSavePolygonEditClick = {}, onCancelPolygonEditClick = {},
                onSearchQueryChange = { _ -> }, onSearchActiveChange = { _ -> }, onOpenSearchResult = { _ -> }, onRevealAndOpenSearchResult = { _ -> },
                onSelectGuidedLocationMethod = { _ -> },
                onRequestLocationWithPermission = { _ -> },
                onAddClick = {},
                onBasemapClick = { showChooser = true },
                onCloseBasemapChooser = { showChooser = false },
                onSelectPreferredBasemap = { id -> showChooser = false },
                onSelectBackupBasemap = { id -> showChooser = false },
                onRetryPrimaryBasemap = {}
            )
        }

        composeTestRule.onNodeWithTag("BasemapChooser").assertIsDisplayed()
        composeTestRule.onNodeWithTag("BasemapOption_MAPTILER_STREETS").performClick()
        composeTestRule.onNodeWithTag("BasemapChooser").assertDoesNotExist()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun testPolylineDrawingAndToolbarInteractions() {
        var finishClicked = false
        var cancelClicked = false

        composeTestRule.setContent {
            MapScreenContent(
                viewModel = viewModel,
                layoutInfo = compactLayout,
                state = MapUiState(
                    editingMode = MapEditingMode.AddLine,
                    draftVertices = listOf(Pair(0.0, 0.0), Pair(1.0, 1.0)),
                    canFinishLine = true
                ),
                systemItems = emptyList(),
                mapViewFactory = { ctx, _ -> android.view.View(ctx) },
                sheetState = rememberModalBottomSheetState(),
                basemapSheetState = rememberModalBottomSheetState(),
                isBasemapLoading = false,
                basemapProvider = fakeBasemapProvider,
                onUndoVertexClick = {}, 
                onFinishLineClick = { finishClicked = true }, 
                onCancelLineClick = { cancelClicked = true },
                onEmergencyClick = {}, onLayersClick = {}, onMyLocationClick = {},
                onUsePhoneLocationClick = {}, onClearMapError = {}, onClearBasemapError = {},
 onDismissLocation = {},
                onDismissRationale = {}, onAllowRationale = {}, onSelectLayer = { _ -> }, onToggleLayerVisibility = { _ -> },
                onToggleLayerLock = { _ -> }, onAddLayer = { _, _ -> }, onRenameLayer = { _, _ -> },
                onChangeLayerOpacity = { _, _ -> }, onMoveLayerUp = { _ -> }, onMoveLayerDown = { _ -> }, onDeleteLayer = { _ -> },
                onSaveFeature = { _ -> }, onDeleteFeature = { _ -> }, onSaveNewSystemItem = { _ -> UUID.randomUUID() },
                onEditShapeClick = { _ -> }, onUndoEditClick = {}, onDeleteVertexClick = {}, onSaveEditClick = {}, onCancelEditClick = {},
                onConfirmDiscardEdit = {}, onDismissDiscardDialog = {},
                onUndoPolygonVertexClick = {}, onFinishPolygonClick = {}, onCancelPolygonClick = {}, onDismissFeatureEditor = {},
                onConfirmPointMove = {}, onCancelPointMove = {},
                onMovePointClick = { _ -> },
                onUndoPolygonEditClick = {}, onDeletePolygonVertexClick = {}, onSavePolygonEditClick = {}, onCancelPolygonEditClick = {},
                onSearchQueryChange = { _ -> }, onSearchActiveChange = { _ -> }, onOpenSearchResult = { _ -> }, onRevealAndOpenSearchResult = { _ -> },
                onSelectGuidedLocationMethod = { _ -> },
                onRequestLocationWithPermission = { _ -> },
                onAddClick = {},
                onBasemapClick = {},
                onCloseBasemapChooser = {},
                onSelectPreferredBasemap = { _ -> },
                onSelectBackupBasemap = { _ -> },
                onRetryPrimaryBasemap = {}
            )
        }

        composeTestRule.onNodeWithTag("LineToolbar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("FinishLineButton").assertIsEnabled().performClick()
        assertTrue("Finish must be clicked", finishClicked)

        composeTestRule.onNodeWithTag("CancelLineButton").performClick()
        assertTrue("Cancel must be clicked", cancelClicked)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun testMovePointEntersMoveMode() {
        val pointId = UUID.randomUUID()
        composeTestRule.setContent {
            MapScreenContent(
                viewModel = viewModel,
                layoutInfo = compactLayout,
                state = MapUiState(
                    pointMoveState = PointMoveState(pointId, 0.0, 0.0)
                ),
                systemItems = emptyList(),
                mapViewFactory = { ctx, _ -> android.view.View(ctx) },
                sheetState = rememberModalBottomSheetState(),
                basemapSheetState = rememberModalBottomSheetState(),
                isBasemapLoading = false,
                basemapProvider = fakeBasemapProvider,
                onUndoVertexClick = {}, onFinishLineClick = {}, onCancelLineClick = {},
                onEmergencyClick = {}, onLayersClick = {}, onMyLocationClick = {},
                onUsePhoneLocationClick = {}, onClearMapError = {}, onClearBasemapError = {},
 onDismissLocation = {},
                onDismissRationale = {}, onAllowRationale = {}, onSelectLayer = { _ -> }, onToggleLayerVisibility = { _ -> },
                onToggleLayerLock = { _ -> }, onAddLayer = { _, _ -> }, onRenameLayer = { _, _ -> },
                onChangeLayerOpacity = { _, _ -> }, onMoveLayerUp = { _ -> }, onMoveLayerDown = { _ -> }, onDeleteLayer = { _ -> },
                onSaveFeature = { _ -> }, onDeleteFeature = { _ -> }, onSaveNewSystemItem = { _ -> UUID.randomUUID() },
                onEditShapeClick = { _ -> }, onUndoEditClick = {}, onDeleteVertexClick = {}, onSaveEditClick = {}, onCancelEditClick = {},
                onConfirmDiscardEdit = {}, onDismissDiscardDialog = {},
                onUndoPolygonVertexClick = {}, onFinishPolygonClick = {}, onCancelPolygonClick = {}, onDismissFeatureEditor = {},
                onConfirmPointMove = {}, onCancelPointMove = {},
                onMovePointClick = { _ -> },
                onUndoPolygonEditClick = {}, onDeletePolygonVertexClick = {}, onSavePolygonEditClick = {}, onCancelPolygonEditClick = {},
                onSearchQueryChange = { _ -> }, onSearchActiveChange = { _ -> }, onOpenSearchResult = { _ -> }, onRevealAndOpenSearchResult = { _ -> },
                onSelectGuidedLocationMethod = { _ -> },
                onRequestLocationWithPermission = { _ -> },
                onAddClick = {},
                onBasemapClick = {},
                onCloseBasemapChooser = {},
                onSelectPreferredBasemap = { _ -> },
                onSelectBackupBasemap = { _ -> },
                onRetryPrimaryBasemap = {}
            )
        }

        composeTestRule.onNodeWithTag("MovePointToolbar").assertIsDisplayed()
        val movingPointText = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(com.jumastappworks.mapstead.R.string.moving_point)
        composeTestRule.onNodeWithText(movingPointText).assertIsDisplayed()
        composeTestRule.onNodeWithTag("ConfirmMoveButton").assertIsNotEnabled()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun testConfirmMoveReturnsToPointDetails() {
        var confirmClicked = false
        val pointId = UUID.randomUUID()
        composeTestRule.setContent {
            MapScreenContent(
                viewModel = viewModel,
                layoutInfo = compactLayout,
                state = MapUiState(
                    pointMoveState = PointMoveState(pointId, 0.0, 0.0, 1.1, 2.2)
                ),
                systemItems = emptyList(),
                mapViewFactory = { ctx, _ -> android.view.View(ctx) },
                sheetState = rememberModalBottomSheetState(),
                basemapSheetState = rememberModalBottomSheetState(),
                isBasemapLoading = false,
                basemapProvider = fakeBasemapProvider,
                onUndoVertexClick = {}, onFinishLineClick = {}, onCancelLineClick = {},
                onEmergencyClick = {}, onLayersClick = {}, onMyLocationClick = {},
                onUsePhoneLocationClick = {}, onClearMapError = {}, onClearBasemapError = {},

                onDismissLocation = {},
                onDismissRationale = {}, onAllowRationale = {}, onSelectLayer = { _ -> }, onToggleLayerVisibility = { _ -> },
                onToggleLayerLock = { _ -> }, onAddLayer = { _, _ -> }, onRenameLayer = { _, _ -> },
                onChangeLayerOpacity = { _, _ -> }, onMoveLayerUp = { _ -> }, onMoveLayerDown = { _ -> }, onDeleteLayer = { _ -> },
                onSaveFeature = { _ -> }, onDeleteFeature = { _ -> }, onSaveNewSystemItem = { _ -> UUID.randomUUID() },
                onEditShapeClick = { _ -> }, onUndoEditClick = {}, onDeleteVertexClick = {}, onSaveEditClick = {}, onCancelEditClick = {},
                onConfirmDiscardEdit = {}, onDismissDiscardDialog = {},
                onUndoPolygonVertexClick = {}, onFinishPolygonClick = {}, onCancelPolygonClick = {}, onDismissFeatureEditor = {},
                onConfirmPointMove = { confirmClicked = true }, onCancelPointMove = {},
                onMovePointClick = { _ -> },
                onUndoPolygonEditClick = {}, onDeletePolygonVertexClick = {}, onSavePolygonEditClick = {}, onCancelPolygonEditClick = {},
                onSearchQueryChange = { _ -> }, onSearchActiveChange = { _ -> }, onOpenSearchResult = { _ -> }, onRevealAndOpenSearchResult = { _ -> },
                onSelectGuidedLocationMethod = { _ -> },
                onRequestLocationWithPermission = { _ -> },
                onAddClick = {},
                onBasemapClick = {},
                onCloseBasemapChooser = {},
                onSelectPreferredBasemap = { _ -> },
                onSelectBackupBasemap = { _ -> },
                onRetryPrimaryBasemap = {}
            )
        }

        composeTestRule.onNodeWithTag("ConfirmMoveButton").assertIsEnabled().performClick()
        assertTrue("Confirm must be clicked", confirmClicked)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun testCancelMoveReturnsToOriginalPointDetails() {
        var cancelClicked = false
        val pointId = UUID.randomUUID()
        composeTestRule.setContent {
            MapScreenContent(
                viewModel = viewModel,
                layoutInfo = compactLayout,
                state = MapUiState(
                    pointMoveState = PointMoveState(pointId, 0.0, 0.0)
                ),
                systemItems = emptyList(),
                mapViewFactory = { ctx, _ -> android.view.View(ctx) },
                sheetState = rememberModalBottomSheetState(),
                basemapSheetState = rememberModalBottomSheetState(),
                isBasemapLoading = false,
                basemapProvider = fakeBasemapProvider,
                onUndoVertexClick = {}, onFinishLineClick = {}, onCancelLineClick = {},
                onEmergencyClick = {}, onLayersClick = {}, onMyLocationClick = {},
                onUsePhoneLocationClick = {}, onClearMapError = {}, onClearBasemapError = {},

                onDismissLocation = {},
                onDismissRationale = {}, onAllowRationale = {}, onSelectLayer = { _ -> }, onToggleLayerVisibility = { _ -> },
                onToggleLayerLock = { _ -> }, onAddLayer = { _, _ -> }, onRenameLayer = { _, _ -> },
                onChangeLayerOpacity = { _, _ -> }, onMoveLayerUp = { _ -> }, onMoveLayerDown = { _ -> }, onDeleteLayer = { _ -> },
                onSaveFeature = { _ -> }, onDeleteFeature = { _ -> }, onSaveNewSystemItem = { _ -> UUID.randomUUID() },
                onEditShapeClick = { _ -> }, onUndoEditClick = {}, onDeleteVertexClick = {}, onSaveEditClick = {}, onCancelEditClick = {},
                onConfirmDiscardEdit = {}, onDismissDiscardDialog = {},
                onUndoPolygonVertexClick = {}, onFinishPolygonClick = {}, onCancelPolygonClick = {}, onDismissFeatureEditor = {},
                onConfirmPointMove = {}, onCancelPointMove = { cancelClicked = true },
                onMovePointClick = { _ -> },
                onUndoPolygonEditClick = {}, onDeletePolygonVertexClick = {}, onSavePolygonEditClick = {}, onCancelPolygonEditClick = {},
                onSearchQueryChange = { _ -> }, onSearchActiveChange = { _ -> }, onOpenSearchResult = { _ -> }, onRevealAndOpenSearchResult = { _ -> },
                onSelectGuidedLocationMethod = { _ -> },
                onRequestLocationWithPermission = { _ -> },
                onAddClick = {},
                onBasemapClick = {},
                onCloseBasemapChooser = {},
                onSelectPreferredBasemap = { _ -> },
                onSelectBackupBasemap = { _ -> },
                onRetryPrimaryBasemap = {}
            )
        }

        composeTestRule.onNodeWithTag("CancelMoveButton").assertIsEnabled().performClick()
        assertTrue("Cancel must be clicked", cancelClicked)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun testPolygonToolbarVisibility() {
        val featureId = UUID.randomUUID()
        composeTestRule.setContent {
            MapScreenContent(
                viewModel = viewModel,
                layoutInfo = compactLayout,
                state = MapUiState(
                    editingMode = MapEditingMode.EditPolygon,
                    polygonEditState = PolygonEditState(
                        featureId = featureId,
                        propertyId = UUID.randomUUID(),
                        planId = UUID.randomUUID(),
                        layerId = UUID.randomUUID(),
                        originalVertices = listOf(Pair(0.0,0.0), Pair(1.0,0.0), Pair(0.5,1.0)),
                        workingVertices = listOf(Pair(0.0,0.0), Pair(1.0,0.0), Pair(0.5,1.0)),
                        originalAreaMeters = 100.0,
                        workingAreaMeters = 100.0,
                        originalPerimeterMeters = 30.0,
                        workingPerimeterMeters = 30.0
                    )
                ),
                systemItems = emptyList(),
                mapViewFactory = { ctx, _ -> android.view.View(ctx) },
                sheetState = rememberModalBottomSheetState(),
                basemapSheetState = rememberModalBottomSheetState(),
                isBasemapLoading = false,
                basemapProvider = fakeBasemapProvider,
                onUndoVertexClick = {}, onFinishLineClick = {}, onCancelLineClick = {},
                onEmergencyClick = {}, onLayersClick = {}, onMyLocationClick = {},
                onUsePhoneLocationClick = {}, onClearMapError = {}, onClearBasemapError = {},

                onDismissLocation = {},
                onDismissRationale = {}, onAllowRationale = {}, onSelectLayer = { _ -> }, onToggleLayerVisibility = { _ -> },
                onToggleLayerLock = { _ -> }, onAddLayer = { _, _ -> }, onRenameLayer = { _, _ -> },
                onChangeLayerOpacity = { _, _ -> }, onMoveLayerUp = { _ -> }, onMoveLayerDown = { _ -> }, onDeleteLayer = { _ -> },
                onSaveFeature = { _ -> }, onDeleteFeature = { _ -> }, onSaveNewSystemItem = { _ -> UUID.randomUUID() },
                onEditShapeClick = { _ -> }, onUndoEditClick = {}, onDeleteVertexClick = {}, onSaveEditClick = {}, onCancelEditClick = {},
                onConfirmDiscardEdit = {}, onDismissDiscardDialog = {},
                onUndoPolygonVertexClick = {}, onFinishPolygonClick = {}, onCancelPolygonClick = {}, onDismissFeatureEditor = {},
                onConfirmPointMove = {}, onCancelPointMove = {},
                onMovePointClick = { _ -> },
                onUndoPolygonEditClick = {}, onDeletePolygonVertexClick = {}, onSavePolygonEditClick = {}, onCancelPolygonEditClick = {},
                onSearchQueryChange = { _ -> }, onSearchActiveChange = { _ -> }, onOpenSearchResult = { _ -> }, onRevealAndOpenSearchResult = { _ -> },
                onSelectGuidedLocationMethod = { _ -> },
                onRequestLocationWithPermission = { _ -> },
                onAddClick = {},
                onBasemapClick = {},
                onCloseBasemapChooser = {},
                onSelectPreferredBasemap = { _ -> },
                onSelectBackupBasemap = { _ -> },
                onRetryPrimaryBasemap = {}
            )
        }

        composeTestRule.onNodeWithTag("EditPolygonToolbar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save").assertIsDisplayed()
    }
}
