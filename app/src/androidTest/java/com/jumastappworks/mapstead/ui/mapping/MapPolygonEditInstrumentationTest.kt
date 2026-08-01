package com.jumastappworks.mapstead.ui.mapping

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import com.jumastappworks.mapstead.util.AdaptiveLayoutInfo
import com.jumastappworks.mapstead.util.PolygonValidationResult
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
class MapPolygonEditInstrumentationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel = mockk<MapViewModel>(relaxed = true)
    private val basemapProvider = mockk<BasemapProvider>(relaxed = true)

    @Test
    fun testPolygonEditToolbarAppears() {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        
        val pes = PolygonEditState(
            featureId = featureId,
            propertyId = propId,
            planId = planId,
            layerId = UUID.randomUUID(),
            originalVertices = listOf(Pair(0.0, 0.0), Pair(1.0, 0.0), Pair(0.0, 1.0)),
            workingVertices = listOf(Pair(0.0, 0.0), Pair(1.0, 0.0), Pair(0.0, 1.0)),
            validation = PolygonValidationResult.Valid,
            originalAreaMeters = 1000.0,
            workingAreaMeters = 1000.0,
            originalPerimeterMeters = 100.0,
            workingPerimeterMeters = 100.0
        )
        
        val state = MapUiState(
            editingMode = MapEditingMode.EditPolygon,
            polygonEditState = pes
        )
        
        every { viewModel.uiState } returns MutableStateFlow(state)
        
        composeTestRule.setContent {
            MapScreenContent(
                state = state,
                systemItems = emptyList(),
                mapViewFactory = { ctx, _ -> android.view.View(ctx) },
                sheetState = rememberModalBottomSheetState(),
                basemapSheetState = rememberModalBottomSheetState(),
                isBasemapLoading = false,
                basemapProvider = basemapProvider,
                onUndoVertexClick = {},
                onFinishLineClick = {},
                onCancelLineClick = {},
                onEmergencyClick = {},
                onLayersClick = {},
                onMyLocationClick = {},
                onUsePhoneLocationClick = {},
                onClearMapError = {},
                onClearBasemapError = {},
                viewModel = viewModel,
                onDismissLocation = {},
                onDismissRationale = {},
                onAllowRationale = {},
                onSelectLayer = { _ -> },
                onToggleLayerVisibility = { _ -> },
                onToggleLayerLock = { _ -> },
                onAddLayer = { _, _ -> },
                onRenameLayer = { _, _ -> },
                onChangeLayerOpacity = { _, _ -> },
                onMoveLayerUp = { _ -> },
                onMoveLayerDown = { _ -> },
                onDeleteLayer = { _ -> },
                onSaveFeature = { _ -> },
                onDeleteFeature = { _ -> },
                onSaveNewSystemItem = { _ -> UUID.randomUUID() },
                onMovePointClick = { _ -> },
                onEditShapeClick = { _ -> },
                onUndoEditClick = {},
                onDeleteVertexClick = {},
                onSaveEditClick = {},
                onCancelEditClick = {},
                onConfirmDiscardEdit = {},
                onDismissDiscardDialog = {},
                onUndoPolygonVertexClick = {},
                onFinishPolygonClick = {},
                onCancelPolygonClick = {},
                onDismissFeatureEditor = {},
                onConfirmPointMove = {},
                onCancelPointMove = {},
                onUndoPolygonEditClick = {},
                onDeletePolygonVertexClick = {},
                onSavePolygonEditClick = {},
                onCancelPolygonEditClick = {},
                onSearchQueryChange = { _ -> }, onSearchActiveChange = { _ -> },
                onOpenSearchResult = { _ -> }, onRevealAndOpenSearchResult = { _ -> },
                layoutInfo = AdaptiveLayoutInfo(
                    isWidthCompact = true, isWidthMedium = false, isWidthExpanded = false,
                    isHeightCompact = false, isHeightMedium = true, isHeightExpanded = false,
                    isLandscape = false, showPersistentSupportingPane = false,
                    useBottomNavigation = true, useNavigationRail = false
                ),
                onSelectGuidedLocationMethod = { _ -> }, onRequestLocationWithPermission = { _ -> }, onAddClick = {}, onBasemapClick = {},
                onCloseBasemapChooser = {},
                onSelectPreferredBasemap = { _ -> },
                onSelectBackupBasemap = { _ -> },
                onRetryPrimaryBasemap = {}
            )
        }
        
        composeTestRule.onNodeWithTag("EditPolygonToolbar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Editing Area: 3 vertices", substring = true).assertIsDisplayed()
    }

    @Test
    fun testDeleteVertexDisabledOnTriangle() {
        val pes = PolygonEditState(
            featureId = UUID.randomUUID(),
            propertyId = UUID.randomUUID(),
            planId = UUID.randomUUID(),
            layerId = UUID.randomUUID(),
            originalVertices = listOf(Pair(0.0, 0.0), Pair(1.0, 0.0), Pair(0.0, 1.0)),
            workingVertices = listOf(Pair(0.0, 0.0), Pair(1.0, 0.0), Pair(0.0, 1.0)),
            selectedVertexIndex = 0,
            validation = PolygonValidationResult.Valid,
            originalAreaMeters = 1000.0,
            workingAreaMeters = 1000.0,
            originalPerimeterMeters = 100.0,
            workingPerimeterMeters = 100.0
        )
        
        val state = MapUiState(
            editingMode = MapEditingMode.EditPolygon,
            polygonEditState = pes
        )
        
        every { viewModel.uiState } returns MutableStateFlow(state)
        
        composeTestRule.setContent {
            MapScreenContent(
                state = state,
                systemItems = emptyList(),
                mapViewFactory = { ctx, _ -> android.view.View(ctx) },
                sheetState = rememberModalBottomSheetState(),
                basemapSheetState = rememberModalBottomSheetState(),
                isBasemapLoading = false,
                basemapProvider = basemapProvider,
                onUndoVertexClick = {},
                onFinishLineClick = {},
                onCancelLineClick = {},
                onEmergencyClick = {},
                onLayersClick = {},
                onMyLocationClick = {},
                onUsePhoneLocationClick = {},
                onClearMapError = {},
                onClearBasemapError = {},
                viewModel = viewModel,
                onDismissLocation = {},
                onDismissRationale = {},
                onAllowRationale = {},
                onSelectLayer = { _ -> },
                onToggleLayerVisibility = { _ -> },
                onToggleLayerLock = { _ -> },
                onAddLayer = { _, _ -> },
                onRenameLayer = { _, _ -> },
                onChangeLayerOpacity = { _, _ -> },
                onMoveLayerUp = { _ -> },
                onMoveLayerDown = { _ -> },
                onDeleteLayer = { _ -> },
                onSaveFeature = { _ -> },
                onDeleteFeature = { _ -> },
                onSaveNewSystemItem = { _ -> UUID.randomUUID() },
                onMovePointClick = { _ -> },
                onEditShapeClick = { _ -> },
                onUndoEditClick = {},
                onDeleteVertexClick = {},
                onSaveEditClick = {},
                onCancelEditClick = {},
                onConfirmDiscardEdit = {},
                onDismissDiscardDialog = {},
                onUndoPolygonVertexClick = {},
                onFinishPolygonClick = {},
                onCancelPolygonClick = {},
                onDismissFeatureEditor = {},
                onConfirmPointMove = {},
                onCancelPointMove = {},
                onUndoPolygonEditClick = {},
                onDeletePolygonVertexClick = {},
                onSavePolygonEditClick = {},
                onCancelPolygonEditClick = {},
                onSearchQueryChange = { _ -> }, onSearchActiveChange = { _ -> },
                onOpenSearchResult = { _ -> }, onRevealAndOpenSearchResult = { _ -> },
                layoutInfo = AdaptiveLayoutInfo(
                    isWidthCompact = true, isWidthMedium = false, isWidthExpanded = false,
                    isHeightCompact = false, isHeightMedium = true, isHeightExpanded = false,
                    isLandscape = false, showPersistentSupportingPane = false,
                    useBottomNavigation = true, useNavigationRail = false
                ),
                onSelectGuidedLocationMethod = { _ -> }, onRequestLocationWithPermission = { _ -> }, onAddClick = {}, onBasemapClick = {},
                onCloseBasemapChooser = {},
                onSelectPreferredBasemap = { _ -> },
                onSelectBackupBasemap = { _ -> },
                onRetryPrimaryBasemap = {}
            )
        }
        
        composeTestRule.onNodeWithTag("DeletePolygonVertexButton").assertIsNotEnabled()
    }
}
