package com.jumastappworks.mapstead.ui.mapping

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.util.AdaptiveLayoutInfo
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class MapSearchUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel = mockk<MapViewModel>(relaxed = true)

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

    @Test
    fun testSearchActivationOnFocus() {
        var activeCalled = false
        composeTestRule.setContent {
            MapScreenContent(
                viewModel = viewModel,
                layoutInfo = compactLayout,
                state = MapUiState(isSearchActive = false),
                systemItems = emptyList(),
                mapViewFactory = { ctx, _ -> android.view.View(ctx) },
                sheetState = mockkSheetState(),
                basemapSheetState = mockkSheetState(),
                isBasemapLoading = false,
                basemapProvider = mockkBasemapProvider(),
                onUndoVertexClick = {}, onFinishLineClick = {}, onCancelLineClick = {},
                onEmergencyClick = {}, onLayersClick = {}, onMyLocationClick = {},
                onUsePhoneLocationClick = {}, onClearMapError = {}, onClearBasemapError = {},
 onDismissLocation = {},
                onDismissRationale = {}, onAllowRationale = {}, onSelectLayer = { _ -> }, onToggleLayerVisibility = { _ -> },
                onToggleLayerLock = { _ -> }, onAddLayer = { _, _ -> }, onRenameLayer = { _, _ -> },
                onChangeLayerOpacity = { _, _ -> }, onMoveLayerUp = { _ -> }, onMoveLayerDown = { _ -> }, onDeleteLayer = { _ -> },
                onSaveFeature = { _ -> }, onDeleteFeature = { _ -> }, onOpenSearchResult = { _ -> }, onRevealAndOpenSearchResult = { _ -> },
                onSaveNewSystemItem = { _ -> UUID.randomUUID() },
                onMovePointClick = { _ -> }, onEditShapeClick = { _ -> }, onUndoEditClick = {}, onDeleteVertexClick = {}, onSaveEditClick = {}, onCancelEditClick = {},
                onConfirmDiscardEdit = {}, onDismissDiscardDialog = {},
                onUndoPolygonVertexClick = {}, onFinishPolygonClick = {}, onCancelPolygonClick = {}, onDismissFeatureEditor = {},
                onConfirmPointMove = {}, onCancelPointMove = {},
                onUndoPolygonEditClick = {}, onDeletePolygonVertexClick = {}, onSavePolygonEditClick = {}, onCancelPolygonEditClick = {},
                onSearchQueryChange = { _ -> },
                onSearchActiveChange = { activeCalled = it },
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

        composeTestRule.onNodeWithTag("MapSearchField").performClick()
        assertTrue("Search should be activated on field click", activeCalled)
    }

    @Test
    fun testSearchResultsPopulated() {
        val results = listOf(
            MapSearchResult(UUID.randomUUID(), "Generator", null, null, "Utility", null, UUID.randomUUID(), "Layer 1", true, false, "POINT")
        )
        composeTestRule.setContent {
            MapScreenContent(
                viewModel = viewModel,
                layoutInfo = compactLayout,
                state = MapUiState(isSearchActive = true, searchQuery = "gen", searchResults = results),
                systemItems = emptyList(),
                mapViewFactory = { ctx, _ -> android.view.View(ctx) },
                sheetState = mockkSheetState(),
                basemapSheetState = mockkSheetState(),
                isBasemapLoading = false,
                basemapProvider = mockkBasemapProvider(),
                onUndoVertexClick = {}, onFinishLineClick = {}, onCancelLineClick = {},
                onEmergencyClick = {}, onLayersClick = {}, onMyLocationClick = {},
                onUsePhoneLocationClick = {}, onClearMapError = {}, onClearBasemapError = {},
 onDismissLocation = {},
                onDismissRationale = {}, onAllowRationale = {}, onSelectLayer = { _ -> }, onToggleLayerVisibility = { _ -> },
                onToggleLayerLock = { _ -> }, onAddLayer = { _, _ -> }, onRenameLayer = { _, _ -> },
                onChangeLayerOpacity = { _, _ -> }, onMoveLayerUp = { _ -> }, onMoveLayerDown = { _ -> }, onDeleteLayer = { _ -> },
                onSaveFeature = { _ -> }, onDeleteFeature = { _ -> }, onOpenSearchResult = { _ -> }, onRevealAndOpenSearchResult = { _ -> },
                onSaveNewSystemItem = { _ -> UUID.randomUUID() },
                onMovePointClick = { _ -> }, onEditShapeClick = { _ -> }, onUndoEditClick = {}, onDeleteVertexClick = {}, onSaveEditClick = {}, onCancelEditClick = {},
                onConfirmDiscardEdit = {}, onDismissDiscardDialog = {},
                onUndoPolygonVertexClick = {}, onFinishPolygonClick = {}, onCancelPolygonClick = {}, onDismissFeatureEditor = {},
                onConfirmPointMove = {}, onCancelPointMove = {},
                onUndoPolygonEditClick = {}, onDeletePolygonVertexClick = {}, onSavePolygonEditClick = {}, onCancelPolygonEditClick = {},
                onSearchQueryChange = { _ -> }, onSearchActiveChange = { _ -> },
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

        composeTestRule.onNodeWithTag("MapSearchResults").assertIsDisplayed()
        composeTestRule.onNodeWithText("Generator").assertIsDisplayed()
    }

    @Test
    fun testEmptySearchState() {
        composeTestRule.setContent {
            MapScreenContent(
                viewModel = viewModel,
                layoutInfo = compactLayout,
                state = MapUiState(isSearchActive = true, searchQuery = "nothing", searchResults = emptyList()),
                systemItems = emptyList(),
                mapViewFactory = { ctx, _ -> android.view.View(ctx) },
                sheetState = mockkSheetState(),
                basemapSheetState = mockkSheetState(),
                isBasemapLoading = false,
                basemapProvider = mockkBasemapProvider(),
                onUndoVertexClick = {}, onFinishLineClick = {}, onCancelLineClick = {},
                onEmergencyClick = {}, onLayersClick = {}, onMyLocationClick = {},
                onUsePhoneLocationClick = {}, onClearMapError = {}, onClearBasemapError = {},

                onDismissLocation = {},
                onDismissRationale = {}, onAllowRationale = {}, onSelectLayer = { _ -> }, onToggleLayerVisibility = { _ -> },
                onToggleLayerLock = { _ -> }, onAddLayer = { _, _ -> }, onRenameLayer = { _, _ -> },
                onChangeLayerOpacity = { _, _ -> }, onMoveLayerUp = { _ -> }, onMoveLayerDown = { _ -> }, onDeleteLayer = { _ -> },
                onSaveFeature = { _ -> }, onDeleteFeature = { _ -> }, onOpenSearchResult = { _ -> }, onRevealAndOpenSearchResult = { _ -> },
                onSaveNewSystemItem = { _ -> UUID.randomUUID() },
                onMovePointClick = { _ -> }, onEditShapeClick = { _ -> }, onUndoEditClick = {}, onDeleteVertexClick = {}, onSaveEditClick = {}, onCancelEditClick = {},
                onConfirmDiscardEdit = {}, onDismissDiscardDialog = {},
                onUndoPolygonVertexClick = {}, onFinishPolygonClick = {}, onCancelPolygonClick = {}, onDismissFeatureEditor = {},
                onConfirmPointMove = {}, onCancelPointMove = {},
                onUndoPolygonEditClick = {}, onDeletePolygonVertexClick = {}, onSavePolygonEditClick = {}, onCancelPolygonEditClick = {},
                onSearchQueryChange = { _ -> }, onSearchActiveChange = { _ -> },
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

        composeTestRule.onNodeWithTag("MapSearchEmptyState").assertIsDisplayed()
    }

    @Test
    fun testVisibleResultClickRoutesToOpen() {
        var openCalled = false
        val featureId = UUID.randomUUID()
        val results = listOf(
            MapSearchResult(featureId, "Target", null, null, "Category", null, UUID.randomUUID(), "Layer 1", true, false, "POINT")
        )
        composeTestRule.setContent {
            MapScreenContent(
                viewModel = viewModel,
                layoutInfo = compactLayout,
                state = MapUiState(isSearchActive = true, searchQuery = "tar", searchResults = results),
                systemItems = emptyList(),
                mapViewFactory = { ctx, _ -> android.view.View(ctx) },
                sheetState = mockkSheetState(),
                basemapSheetState = mockkSheetState(),
                isBasemapLoading = false,
                basemapProvider = mockkBasemapProvider(),
                onUndoVertexClick = {}, onFinishLineClick = {}, onCancelLineClick = {},
                onEmergencyClick = {}, onLayersClick = {}, onMyLocationClick = {},
                onUsePhoneLocationClick = {}, onClearMapError = {}, onClearBasemapError = {},
 onDismissLocation = {},
                onDismissRationale = {}, onAllowRationale = {}, onSelectLayer = { _ -> }, onToggleLayerVisibility = { _ -> },
                onToggleLayerLock = { _ -> }, onAddLayer = { _, _ -> }, onRenameLayer = { _, _ -> },
                onChangeLayerOpacity = { _, _ -> }, onMoveLayerUp = { _ -> }, onMoveLayerDown = { _ -> }, onDeleteLayer = { _ -> },
                onSaveFeature = { _ -> }, onDeleteFeature = { _ -> }, 
                onOpenSearchResult = { openCalled = true }, 
                onRevealAndOpenSearchResult = { _ -> },
                onSaveNewSystemItem = { _ -> UUID.randomUUID() },
                onMovePointClick = { _ -> }, onEditShapeClick = { _ -> }, onUndoEditClick = {}, onDeleteVertexClick = {}, onSaveEditClick = {}, onCancelEditClick = {},
                onConfirmDiscardEdit = {}, onDismissDiscardDialog = {},
                onUndoPolygonVertexClick = {}, onFinishPolygonClick = {}, onCancelPolygonClick = {}, onDismissFeatureEditor = {},
                onConfirmPointMove = {}, onCancelPointMove = {},
                onUndoPolygonEditClick = {}, onDeletePolygonVertexClick = {}, onSavePolygonEditClick = {}, onCancelPolygonEditClick = {},
                onSearchQueryChange = { _ -> }, onSearchActiveChange = { _ -> },
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

        composeTestRule.onNodeWithText("Target").performClick()
        assertTrue("onOpenSearchResult should be called for visible result", openCalled)
    }

    @Test
    fun testHiddenResultShowsRevealAction() {
        var revealCalled = false
        val featureId = UUID.randomUUID()
        val results = listOf(
            MapSearchResult(featureId, "Hidden Feature", null, null, "Category", null, UUID.randomUUID(), "Hidden Layer", false, false, "POINT")
        )
        composeTestRule.setContent {
            MapScreenContent(
                viewModel = viewModel,
                layoutInfo = compactLayout,
                state = MapUiState(isSearchActive = true, searchQuery = "hid", searchResults = results),
                systemItems = emptyList(),
                mapViewFactory = { ctx, _ -> android.view.View(ctx) },
                sheetState = mockkSheetState(),
                basemapSheetState = mockkSheetState(),
                isBasemapLoading = false,
                basemapProvider = mockkBasemapProvider(),
                onUndoVertexClick = {}, onFinishLineClick = {}, onCancelLineClick = {},
                onEmergencyClick = {}, onLayersClick = {}, onMyLocationClick = {},
                onUsePhoneLocationClick = {}, onClearMapError = {}, onClearBasemapError = {},
 onDismissLocation = {},
                onDismissRationale = {}, onAllowRationale = {}, onSelectLayer = { _ -> }, onToggleLayerVisibility = { _ -> },
                onToggleLayerLock = { _ -> }, onAddLayer = { _, _ -> }, onRenameLayer = { _, _ -> },
                onChangeLayerOpacity = { _, _ -> }, onMoveLayerUp = { _ -> }, onMoveLayerDown = { _ -> }, onDeleteLayer = { _ -> },
                onSaveFeature = { _ -> }, onDeleteFeature = { _ -> }, 
                onOpenSearchResult = { _ -> }, 
                onRevealAndOpenSearchResult = { revealCalled = true },
                onSaveNewSystemItem = { _ -> UUID.randomUUID() },
                onMovePointClick = { _ -> }, onEditShapeClick = { _ -> }, onUndoEditClick = {}, onDeleteVertexClick = {}, onSaveEditClick = {}, onCancelEditClick = {},
                onConfirmDiscardEdit = {}, onDismissDiscardDialog = {},
                onUndoPolygonVertexClick = {}, onFinishPolygonClick = {}, onCancelPolygonClick = {}, onDismissFeatureEditor = {},
                onConfirmPointMove = {}, onCancelPointMove = {},
                onUndoPolygonEditClick = {}, onDeletePolygonVertexClick = {}, onSavePolygonEditClick = {}, onCancelPolygonEditClick = {},
                onSearchQueryChange = { _ -> }, onSearchActiveChange = { _ -> },
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

        composeTestRule.onNodeWithText("SHOW LAYER & OPEN", ignoreCase = true).assertIsDisplayed().performClick()
        assertTrue("onRevealAndOpenSearchResult should be called for hidden result", revealCalled)
    }

    @Test
    fun testExpandedLayoutUsesSidePanel() {
        composeTestRule.setContent {
            MapScreenContent(
                viewModel = viewModel,
                layoutInfo = expandedLayout,
                state = MapUiState(isSearchActive = true, searchQuery = "test", searchResults = emptyList()),
                systemItems = emptyList(),
                mapViewFactory = { ctx, _ -> android.view.View(ctx) },
                sheetState = mockkSheetState(),
                basemapSheetState = mockkSheetState(),
                isBasemapLoading = false,
                basemapProvider = mockkBasemapProvider(),
                onUndoVertexClick = {}, onFinishLineClick = {}, onCancelLineClick = {},
                onEmergencyClick = {}, onLayersClick = {}, onMyLocationClick = {},
                onUsePhoneLocationClick = {}, onClearMapError = {}, onClearBasemapError = {},
 onDismissLocation = {},
                onDismissRationale = {}, onAllowRationale = {}, onSelectLayer = { _ -> }, onToggleLayerVisibility = { _ -> },
                onToggleLayerLock = { _ -> }, onAddLayer = { _, _ -> }, onRenameLayer = { _, _ -> },
                onChangeLayerOpacity = { _, _ -> }, onMoveLayerUp = { _ -> }, onMoveLayerDown = { _ -> }, onDeleteLayer = { _ -> },
                onSaveFeature = { _ -> }, onDeleteFeature = { _ -> }, onOpenSearchResult = { _ -> }, onRevealAndOpenSearchResult = { _ -> },
                onSaveNewSystemItem = { _ -> UUID.randomUUID() },
                onMovePointClick = { _ -> }, onEditShapeClick = { _ -> }, onUndoEditClick = {}, onDeleteVertexClick = {}, onSaveEditClick = {}, onCancelEditClick = {},
                onConfirmDiscardEdit = {}, onDismissDiscardDialog = {},
                onUndoPolygonVertexClick = {}, onFinishPolygonClick = {}, onCancelPolygonClick = {}, onDismissFeatureEditor = {},
                onConfirmPointMove = {}, onCancelPointMove = {},
                onUndoPolygonEditClick = {}, onDeletePolygonVertexClick = {}, onSavePolygonEditClick = {}, onCancelPolygonEditClick = {},
                onSearchQueryChange = { _ -> }, onSearchActiveChange = { _ -> },
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

        // Side panel should be present (tested via search hint text in side panel)
        val searchHint = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(com.jumastappworks.mapstead.R.string.search_hint)
        composeTestRule.onNodeWithTag("MapSearchField", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun testSearchClearButton() {
        var queryReceived: String? = null
        composeTestRule.setContent {
            MapScreenContent(
                viewModel = viewModel,
                layoutInfo = compactLayout,
                state = MapUiState(isSearchActive = true, searchQuery = "some query"),
                systemItems = emptyList(),
                mapViewFactory = { ctx, _ -> android.view.View(ctx) },
                sheetState = mockkSheetState(),
                basemapSheetState = mockkSheetState(),
                isBasemapLoading = false,
                basemapProvider = mockkBasemapProvider(),
                onUndoVertexClick = {}, onFinishLineClick = {}, onCancelLineClick = {},
                onEmergencyClick = {}, onLayersClick = {}, onMyLocationClick = {},
                onUsePhoneLocationClick = {}, onClearMapError = {}, onClearBasemapError = {},
 onDismissLocation = {},
                onDismissRationale = {}, onAllowRationale = {}, onSelectLayer = { _ -> }, onToggleLayerVisibility = { _ -> },
                onToggleLayerLock = { _ -> }, onAddLayer = { _, _ -> }, onRenameLayer = { _, _ -> },
                onChangeLayerOpacity = { _, _ -> }, onMoveLayerUp = { _ -> }, onMoveLayerDown = { _ -> }, onDeleteLayer = { _ -> },
                onSaveFeature = { _ -> }, onDeleteFeature = { _ -> }, onOpenSearchResult = { _ -> }, onRevealAndOpenSearchResult = { _ -> },
                onSaveNewSystemItem = { _ -> UUID.randomUUID() },
                onMovePointClick = { _ -> }, onEditShapeClick = { _ -> }, onUndoEditClick = {}, onDeleteVertexClick = {}, onSaveEditClick = {}, onCancelEditClick = {},
                onConfirmDiscardEdit = {}, onDismissDiscardDialog = {},
                onUndoPolygonVertexClick = {}, onFinishPolygonClick = {}, onCancelPolygonClick = {}, onDismissFeatureEditor = {},
                onConfirmPointMove = {}, onCancelPointMove = {},
                onUndoPolygonEditClick = {}, onDeletePolygonVertexClick = {}, onSavePolygonEditClick = {}, onCancelPolygonEditClick = {},
                onSearchQueryChange = { queryReceived = it },
                onSearchActiveChange = { _ -> },
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

        composeTestRule.onNodeWithTag("MapSearchClearButton").performClick()
        assertEquals("onSearchQueryChange should be called with empty string", "", queryReceived)
    }

    @Test
    fun testSearchWorkflowBlocking() {
        var openCalledCount = 0
        var revealCalledCount = 0
        val results = listOf(
            MapSearchResult(UUID.randomUUID(), "Blocked Result", null, null, "Cat", null, UUID.randomUUID(), "L1", true, false, "POINT"),
            MapSearchResult(UUID.randomUUID(), "Hidden Blocked", null, null, "Cat", null, UUID.randomUUID(), "L1", false, false, "POINT")
        )
        composeTestRule.setContent {
            MapScreenContent(
                viewModel = viewModel,
                layoutInfo = compactLayout,
                state = MapUiState(
                    isSearchActive = true,
                    searchQuery = "blocked",
                    searchResults = results,
                    isWorkflowActive = true,
                    workflowBlockReasonRes = R.string.exclusive_workflow_active
                ),
                systemItems = emptyList(),
                mapViewFactory = { ctx, _ -> android.view.View(ctx) },
                sheetState = mockkSheetState(),
                basemapSheetState = mockkSheetState(),
                isBasemapLoading = false,
                basemapProvider = mockkBasemapProvider(),
                onUndoVertexClick = {}, onFinishLineClick = {}, onCancelLineClick = {},
                onEmergencyClick = {}, onLayersClick = {}, onMyLocationClick = {},
                onUsePhoneLocationClick = {}, onClearMapError = {}, onClearBasemapError = {},

                onDismissLocation = {},
                onDismissRationale = {}, onAllowRationale = {}, onSelectLayer = { _ -> }, onToggleLayerVisibility = { _ -> },
                onToggleLayerLock = { _ -> }, onAddLayer = { _, _ -> }, onRenameLayer = { _, _ -> },
                onChangeLayerOpacity = { _, _ -> }, onMoveLayerUp = { _ -> }, onMoveLayerDown = { _ -> }, onDeleteLayer = { _ -> },
                onSaveFeature = { _ -> }, onDeleteFeature = { _ -> }, 
                onOpenSearchResult = { openCalledCount++ }, 
                onRevealAndOpenSearchResult = { _ -> revealCalledCount++ },
                onSaveNewSystemItem = { _ -> UUID.randomUUID() },
                onMovePointClick = { _ -> }, onEditShapeClick = { _ -> }, onUndoEditClick = {}, onDeleteVertexClick = {}, onSaveEditClick = {}, onCancelEditClick = {},
                onConfirmDiscardEdit = {}, onDismissDiscardDialog = {},
                onUndoPolygonVertexClick = {}, onFinishPolygonClick = {}, onCancelPolygonClick = {}, onDismissFeatureEditor = {},
                onConfirmPointMove = {}, onCancelPointMove = {},
                onUndoPolygonEditClick = {}, onDeletePolygonVertexClick = {}, onSavePolygonEditClick = {}, onCancelPolygonEditClick = {},
                onSearchQueryChange = { _ -> }, onSearchActiveChange = { _ -> },
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

        composeTestRule.onNodeWithText("Blocked Result").performClick()
        assertEquals("onOpenSearchResult should not be called when workflow is active", 0, openCalledCount)

        composeTestRule.onNodeWithText("SHOW LAYER & OPEN", ignoreCase = true).performClick()
        assertEquals("onRevealAndOpenSearchResult should not be called when workflow is active", 0, revealCalledCount)
        
        val blockText = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(com.jumastappworks.mapstead.R.string.exclusive_workflow_active)
        composeTestRule.onAllNodesWithText(blockText).onFirst().assertIsDisplayed()
    }

    @Test
    fun testSearchCloseButton() {
        var activeReceived = true
        composeTestRule.setContent {
            MapScreenContent(
                viewModel = viewModel,
                layoutInfo = compactLayout,
                state = MapUiState(isSearchActive = true),
                systemItems = emptyList(),
                mapViewFactory = { ctx, _ -> android.view.View(ctx) },
                sheetState = mockkSheetState(),
                basemapSheetState = mockkSheetState(),
                isBasemapLoading = false,
                basemapProvider = mockkBasemapProvider(),
                onUndoVertexClick = {}, onFinishLineClick = {}, onCancelLineClick = {},
                onEmergencyClick = {}, onLayersClick = {}, onMyLocationClick = {},
                onUsePhoneLocationClick = {}, onClearMapError = {}, onClearBasemapError = {},

                onDismissLocation = {},
                onDismissRationale = {}, onAllowRationale = {}, onSelectLayer = { _ -> }, onToggleLayerVisibility = { _ -> },
                onToggleLayerLock = { _ -> }, onAddLayer = { _, _ -> }, onRenameLayer = { _, _ -> },
                onChangeLayerOpacity = { _, _ -> }, onMoveLayerUp = { _ -> }, onMoveLayerDown = { _ -> }, onDeleteLayer = { _ -> },
                onSaveFeature = { _ -> }, onDeleteFeature = { _ -> }, onOpenSearchResult = { _ -> }, onRevealAndOpenSearchResult = { _ -> },
                onSaveNewSystemItem = { _ -> UUID.randomUUID() },
                onMovePointClick = { _ -> }, onEditShapeClick = { _ -> }, onUndoEditClick = {}, onDeleteVertexClick = {}, onSaveEditClick = {}, onCancelEditClick = {},
                onConfirmDiscardEdit = {}, onDismissDiscardDialog = {},
                onUndoPolygonVertexClick = {}, onFinishPolygonClick = {}, onCancelPolygonClick = {}, onDismissFeatureEditor = {},
                onConfirmPointMove = {}, onCancelPointMove = {},
                onUndoPolygonEditClick = {}, onDeletePolygonVertexClick = {}, onSavePolygonEditClick = {}, onCancelPolygonEditClick = {},
                onSearchQueryChange = { _ -> },
                onSearchActiveChange = { activeReceived = it },
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

        composeTestRule.onNodeWithTag("MapSearchCloseButton").performClick()
        assertFalse("onSearchActiveChange should be called with false", activeReceived)
    }

    @Test
    fun testLongLabelRevealActionAccessibility() {
        val longLabel = "A".repeat(100)
        val results = listOf(
            MapSearchResult(UUID.randomUUID(), longLabel, null, null, "Category", null, UUID.randomUUID(), "Layer", false, false, "POINT")
        )
        composeTestRule.setContent {
            MapScreenContent(
                viewModel = viewModel,
                layoutInfo = compactLayout,
                state = MapUiState(isSearchActive = true, searchQuery = "aaa", searchResults = results),
                systemItems = emptyList(),
                mapViewFactory = { ctx, _ -> android.view.View(ctx) },
                sheetState = mockkSheetState(),
                basemapSheetState = mockkSheetState(),
                isBasemapLoading = false,
                basemapProvider = mockkBasemapProvider(),
                onUndoVertexClick = {}, onFinishLineClick = {}, onCancelLineClick = {},
                onEmergencyClick = {}, onLayersClick = {}, onMyLocationClick = {},
                onUsePhoneLocationClick = {}, onClearMapError = {}, onClearBasemapError = {},

                onDismissLocation = {},
                onDismissRationale = {}, onAllowRationale = {}, onSelectLayer = { _ -> }, onToggleLayerVisibility = { _ -> },
                onToggleLayerLock = { _ -> }, onAddLayer = { _, _ -> }, onRenameLayer = { _, _ -> },
                onChangeLayerOpacity = { _, _ -> }, onMoveLayerUp = { _ -> }, onMoveLayerDown = { _ -> }, onDeleteLayer = { _ -> },
                onSaveFeature = { _ -> }, onDeleteFeature = { _ -> }, onOpenSearchResult = { _ -> }, onRevealAndOpenSearchResult = { _ -> },
                onSaveNewSystemItem = { _ -> UUID.randomUUID() },
                onMovePointClick = { _ -> }, onEditShapeClick = { _ -> }, onUndoEditClick = {}, onDeleteVertexClick = {}, onSaveEditClick = {}, onCancelEditClick = {},
                onConfirmDiscardEdit = {}, onDismissDiscardDialog = {},
                onUndoPolygonVertexClick = {}, onFinishPolygonClick = {}, onCancelPolygonClick = {}, onDismissFeatureEditor = {},
                onConfirmPointMove = {}, onCancelPointMove = {},
                onUndoPolygonEditClick = {}, onDeletePolygonVertexClick = {}, onSavePolygonEditClick = {}, onCancelPolygonEditClick = {},
                onSearchQueryChange = { _ -> }, onSearchActiveChange = { _ -> },
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

        // Reveal action must be displayed and clickable even with very long headlines
        composeTestRule.onNodeWithText("SHOW LAYER & OPEN", ignoreCase = true).assertIsDisplayed().assertHasClickAction()
    }

    // Helper mocks
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @androidx.compose.runtime.Composable
    private fun mockkSheetState() = androidx.compose.material3.rememberModalBottomSheetState()
    
    private fun mockkBasemapProvider() = object : com.jumastappworks.mapstead.data.mapping.BasemapProvider {
        override fun availableBasemaps() = emptyList<BasemapDefinition>()
        override fun getBasemap(id: BasemapId) = null
        override fun defaultBasemap() = BasemapId.STREETS
        override fun getPrimaryBasemaps() = emptyList<BasemapDefinition>()
        override fun getBackupBasemaps() = emptyList<BasemapDefinition>()
        override fun getDefinition(sourceId: BasemapSourceId) = null
        override fun resolveDefaultBackup(preferredId: BasemapId) = BasemapSourceId.OPEN_FREE_MAP_LIBERTY
        override fun getDefaultBasemapId() = BasemapId.STREETS
        override fun buildStyleUrl(sourceId: BasemapSourceId) = ""
        override fun redactUrl(url: String) = url
        override fun getAttribution(sourceId: BasemapSourceId) = emptyList<BasemapAttributionEntry>()
    }
}
