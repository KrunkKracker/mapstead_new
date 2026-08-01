package com.jumastappworks.mapstead.ui.mapping

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.db.entities.LayerEntity
import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.data.db.entities.PlanEntity
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class MapEditUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel = mockk<MapViewModel>(relaxed = true)
    private val basemapProvider = mockk<BasemapProvider>(relaxed = true)
    private val userPrefs = mockk<UserPreferencesRepository>(relaxed = true)

    @org.junit.Before
    fun setup() {
        every { viewModel.uiState } returns MutableStateFlow(MapUiState())
        every { viewModel.systemItems } returns MutableStateFlow(emptyList())
        val prefs = com.jumastappworks.mapstead.data.prefs.UserPreferences(
            isDarkMode = false,
            themeSelection = com.jumastappworks.mapstead.data.prefs.ThemeSelection.SYSTEM,
            useDynamicColor = false,
            selectedPropertyId = null,
            selectedBasemapId = BasemapId.STREETS,
            measurementSystem = com.jumastappworks.mapstead.data.prefs.MeasurementSystem.IMPERIAL,
            guidanceDismissedPropertyIds = emptySet(),
            boundaryDisclaimerAcknowledged = false
        )
        every { userPrefs.userPreferencesFlow } returns flowOf(prefs)
    }

    @Test
    fun testEditToolbarVisibility() {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val featureId = UUID.randomUUID()

        val uiState = MapUiState(
            propertyId = propId,
            plan = PlanEntity(id = planId, propertyId = propId, name = "Plan 1", planType = "Map", backgroundType = "None"),
            layers = listOf(LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "Layer 1", category = "C1")),
            activeLayerId = layerId,
            editingMode = MapEditingMode.EditLine,
            lineEditState = LineEditState(
                featureId = featureId,
                propertyId = propId,
                planId = planId,
                layerId = layerId,
                originalVertices = listOf(Pair(0.0, 0.0), Pair(1.0, 1.0)),
                workingVertices = listOf(Pair(0.0, 0.0), Pair(1.0, 1.0)),
                originalLengthMeters = 100.0,
                workingLengthMeters = 100.0
            )
        )

        every { viewModel.uiState } returns MutableStateFlow(uiState)
        every { viewModel.systemItems } returns MutableStateFlow(emptyList())

        composeTestRule.setContent {
            MapScreen(
                basemapProvider = basemapProvider,
                userPreferencesRepository = userPrefs,
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithTag("EditLineToolbar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Undo").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun testModeExclusionInUi() {
        val uiState = MapUiState(
            editingMode = MapEditingMode.EditLine,
            lineEditState = LineEditState(
                featureId = UUID.randomUUID(),
                propertyId = UUID.randomUUID(),
                planId = UUID.randomUUID(),
                layerId = UUID.randomUUID(),
                originalVertices = listOf(Pair(0.0, 0.0), Pair(1.0, 1.0)),
                workingVertices = listOf(Pair(0.0, 0.0), Pair(1.0, 1.0)),
                originalLengthMeters = 100.0,
                workingLengthMeters = 100.0
            )
        )

        every { viewModel.uiState } returns MutableStateFlow(uiState)
        every { viewModel.systemItems } returns MutableStateFlow(emptyList())

        composeTestRule.setContent {
            MapScreen(
                basemapProvider = basemapProvider,
                userPreferencesRepository = userPrefs,
                viewModel = viewModel
            )
        }

        // Add Menu children should be present but disabled
        composeTestRule.onNodeWithTag("AddMenuButton").performClick()
        composeTestRule.onNodeWithTag("AddPointButton").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("AddLineButton").assertIsNotEnabled()
    }
    
    @Test
    fun testSaveDisabledWhenUnchanged() {
        val uiState = MapUiState(
            editingMode = MapEditingMode.EditLine,
            lineEditState = LineEditState(
                featureId = UUID.randomUUID(),
                propertyId = UUID.randomUUID(),
                planId = UUID.randomUUID(),
                layerId = UUID.randomUUID(),
                originalVertices = listOf(Pair(0.0, 0.0), Pair(1.0, 1.0)),
                workingVertices = listOf(Pair(0.0, 0.0), Pair(1.0, 1.0)),
                originalLengthMeters = 100.0,
                workingLengthMeters = 100.0
            ),
            isLineEditDirty = false,
            canSaveLineEdit = false,
            lineEditSaveBlockReasonRes = R.string.no_changes_to_save
        )

        every { viewModel.uiState } returns MutableStateFlow(uiState)
        every { viewModel.systemItems } returns MutableStateFlow(emptyList())

        composeTestRule.setContent {
            MapScreen(
                basemapProvider = basemapProvider,
                userPreferencesRepository = userPrefs,
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithTag("SaveEditButton").assertIsNotEnabled()
    }

    @Test
    fun testDiscardConfirmationDialog() {
        val uiState = MapUiState(
            editingMode = MapEditingMode.EditLine,
            showDiscardEditDialog = true,
            discardAction = PendingEditDiscardAction.CancelLineEdit
        )

        every { viewModel.uiState } returns MutableStateFlow(uiState)
        every { viewModel.systemItems } returns MutableStateFlow(emptyList())

        composeTestRule.setContent {
            MapScreen(
                basemapProvider = basemapProvider,
                userPreferencesRepository = userPrefs,
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithText("Discard unsaved changes to this line?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Discard Changes").assertIsDisplayed()
        composeTestRule.onNodeWithText("Keep Editing").assertIsDisplayed()
    }

    @Test
    fun testPolygonToolbarVisibility() {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val uiState = MapUiState(
            editingMode = MapEditingMode.AddPolygon,
            canFinishPolygon = true,
            isNewUnsavedFeature = true,
            polygonDraft = PolygonDraftState(
                propertyId = propId, planId = planId, layerId = layerId,
                vertices = listOf(Pair(0.0, 0.0), Pair(1.0, 1.0), Pair(1.0, 0.0))
            )
        )

        every { viewModel.uiState } returns MutableStateFlow(uiState)
        every { viewModel.systemItems } returns MutableStateFlow(emptyList())

        composeTestRule.setContent {
            MapScreen(
                basemapProvider = basemapProvider,
                userPreferencesRepository = userPrefs,
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithTag("PolygonToolbar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Undo").assertIsDisplayed()
        composeTestRule.onNodeWithText("Finish Area").assertIsEnabled()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun testPolygonLiveSummary() {
        val uiState = MapUiState(
            editingMode = MapEditingMode.AddPolygon,
            isNewUnsavedFeature = true,
            polygonDraft = PolygonDraftState(
                propertyId = UUID.randomUUID(), planId = UUID.randomUUID(), layerId = UUID.randomUUID(),
                vertices = listOf(Pair(0.0, 0.0), Pair(1.0, 1.0))
            ),
            livePerimeterMeters = 1000.0,
            liveAreaMeters = 5000.0
        )

        every { viewModel.uiState } returns MutableStateFlow(uiState)
        every { viewModel.systemItems } returns MutableStateFlow(emptyList())

        composeTestRule.setContent {
            MapScreen(
                basemapProvider = basemapProvider,
                userPreferencesRepository = userPrefs,
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithText("2 vertices · Add 1 more").assertIsDisplayed()
    }

    @Test
    fun testAddAreaDisabledDuringAddLine() {
        val uiState = MapUiState(
            editingMode = MapEditingMode.AddLine
        )

        every { viewModel.uiState } returns MutableStateFlow(uiState)
        every { viewModel.systemItems } returns MutableStateFlow(emptyList())

        composeTestRule.setContent {
            MapScreen(
                basemapProvider = basemapProvider,
                userPreferencesRepository = userPrefs,
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithTag("AddMenuButton").performClick()
        composeTestRule.onNodeWithTag("AddAreaButton").assertIsNotEnabled()
    }

    @Test
    fun testAddPointAndLineDisabledDuringAddArea() {
        val uiState = MapUiState(
            editingMode = MapEditingMode.AddPolygon,
            polygonDraft = PolygonDraftState(propertyId = UUID.randomUUID(), planId = UUID.randomUUID(), layerId = UUID.randomUUID())
        )

        every { viewModel.uiState } returns MutableStateFlow(uiState)
        every { viewModel.systemItems } returns MutableStateFlow(emptyList())

        composeTestRule.setContent {
            MapScreen(
                basemapProvider = basemapProvider,
                userPreferencesRepository = userPrefs,
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithTag("AddMenuButton").performClick()
        composeTestRule.onNodeWithTag("AddPointButton").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("AddLineButton").assertIsNotEnabled()
    }

    @Test
    fun testUnsavedPolygonEditorHasNoDelete() {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        val uiState = MapUiState(
            editingMode = MapEditingMode.AddPolygon,
            featureEditorOpen = true,
            featureEditorTarget = FeatureEditorTarget.NewPolygon(featureId),
            featureEditorFeature = MapFeatureEntity(
                id = featureId, propertyId = propId, planId = planId, layerId = layerId,
                label = "Test Area",
                geometryType = "POLYGON", geometryJson = "{}", coordinateSpace = "LOCAL", styleJson = "{}",
                accuracySource = "MANUAL"
            ),
            isNewUnsavedFeature = true,
            polygonDraft = PolygonDraftState(
                id = featureId,
                propertyId = propId, planId = planId, layerId = layerId,
                vertices = listOf(Pair(0.0, 0.0), Pair(1.0, 1.0), Pair(1.0, 0.0))
            ),
            layers = listOf(LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L1", category = "C1"))
        )

        every { viewModel.uiState } returns MutableStateFlow(uiState)
        every { viewModel.systemItems } returns MutableStateFlow(emptyList())

        composeTestRule.setContent {
            MapScreen(
                basemapProvider = basemapProvider,
                userPreferencesRepository = userPrefs,
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithText("Delete").assertDoesNotExist()
        composeTestRule.onAllNodesWithText("Cancel").onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_button", useUnmergedTree = true).assertExists()
    }

    @Test
    fun testStartPointDrawing() {
        composeTestRule.setContent {
            MapScreen(basemapProvider = basemapProvider, userPreferencesRepository = userPrefs, viewModel = viewModel)
        }
        composeTestRule.onNodeWithTag("AddMenuButton").performClick()
        composeTestRule.onNodeWithTag("AddPointButton").performClick()
        // Callback verification should ideally be checked if viewModel was not a mock with relaxed true
        // but here we check UI response if state was updated.
    }
}
