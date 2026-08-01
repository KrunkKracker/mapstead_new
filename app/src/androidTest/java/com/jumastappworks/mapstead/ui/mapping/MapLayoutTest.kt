package com.jumastappworks.mapstead.ui.mapping

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.util.AdaptiveLayoutInfo
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MapLayoutTest {

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

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun testCompactLayoutMapControls() {
        composeTestRule.setContent {
            MapScreenContent(
                layoutInfo = compactLayout,
                state = MapUiState(),
                systemItems = emptyList(),
                mapViewFactory = { ctx, _ -> android.view.View(ctx) },
                sheetState = rememberModalBottomSheetState(),
                basemapSheetState = rememberModalBottomSheetState(),
                isBasemapLoading = false,
                basemapProvider = object : BasemapProvider {
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
                },
                onUndoVertexClick = {}, onFinishLineClick = {}, onCancelLineClick = {},
                onEmergencyClick = {}, onLayersClick = {}, onMyLocationClick = {},
                onUsePhoneLocationClick = {}, onClearMapError = {}, onClearBasemapError = {},

                viewModel = viewModel,
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
                onSelectGuidedLocationMethod = { _ -> }, onRequestLocationWithPermission = { _ -> }, onAddClick = {}, onBasemapClick = {}, 
                onCloseBasemapChooser = {},
                onSelectPreferredBasemap = { _ -> },
                onSelectBackupBasemap = { _ -> },
                onRetryPrimaryBasemap = {}
            )
        }

        composeTestRule.onNodeWithTag("MapActionButtons").assertIsDisplayed()
        composeTestRule.onNodeWithTag("AddMenuButton").performClick()
        composeTestRule.onNodeWithTag("AddPointButton").assertIsDisplayed()
        composeTestRule.onNodeWithTag("AddLineButton").assertIsDisplayed()
        composeTestRule.onNodeWithTag("AddAreaButton").assertIsDisplayed()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun testExpandedLayoutMapControls() {
        composeTestRule.setContent {
            MapScreenContent(
                layoutInfo = expandedLayout,
                state = MapUiState(),
                systemItems = emptyList(),
                mapViewFactory = { ctx, _ -> android.view.View(ctx) },
                sheetState = rememberModalBottomSheetState(),
                basemapSheetState = rememberModalBottomSheetState(),
                isBasemapLoading = false,
                basemapProvider = object : BasemapProvider {
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
                },
                onUndoVertexClick = {}, onFinishLineClick = {}, onCancelLineClick = {},
                onEmergencyClick = {}, onLayersClick = {}, onMyLocationClick = {},
                onUsePhoneLocationClick = {}, onClearMapError = {}, onClearBasemapError = {},

                viewModel = viewModel,
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
                onSelectGuidedLocationMethod = { _ -> }, onRequestLocationWithPermission = { _ -> }, onAddClick = {}, onBasemapClick = {}, 
                onCloseBasemapChooser = {},
                onSelectPreferredBasemap = { _ -> },
                onSelectBackupBasemap = { _ -> },
                onRetryPrimaryBasemap = {}
            )
        }

        composeTestRule.onNodeWithTag("MapActionButtons").assertIsDisplayed()
        composeTestRule.onNodeWithTag("AddMenuButton").performClick()
        composeTestRule.onNodeWithTag("AddPointButton").assertIsDisplayed()
        composeTestRule.onNodeWithTag("AddLineButton").assertIsDisplayed()
        composeTestRule.onNodeWithTag("AddAreaButton").assertIsDisplayed()
    }
}
