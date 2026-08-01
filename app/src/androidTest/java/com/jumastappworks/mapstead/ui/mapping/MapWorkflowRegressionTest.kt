package com.jumastappworks.mapstead.ui.mapping

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jumastappworks.mapstead.data.db.entities.LayerEntity
import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class MapWorkflowRegressionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel = mockk<MapViewModel>(relaxed = true)
    private val basemapProvider = mockk<BasemapProvider>(relaxed = true)
    private val userPrefs = mockk<UserPreferencesRepository>(relaxed = true)

    @org.junit.Before
    fun setup() {
        val prefs = com.jumastappworks.mapstead.data.prefs.UserPreferences()
        every { userPrefs.userPreferencesFlow } returns flowOf(prefs)
    }

    @Test
    fun testEditShapeClickClosesDetailsAndShowsToolbar() {
        val featureId = UUID.randomUUID()
        val feature = MapFeatureEntity(
            id = featureId, propertyId = UUID.randomUUID(), planId = UUID.randomUUID(), layerId = UUID.randomUUID(),
            label = "My Fence", geometryType = "LINESTRING", geometryJson = "{}",
            coordinateSpace = "G", styleJson = "{}", accuracySource = "M"
        )
        
        // Initial state: Details sheet open
        val detailsState = MapUiState(
            featureEditorOpen = true,
            featureEditorFeature = feature,
            featureEditorTarget = FeatureEditorTarget.Persisted(featureId),
            layers = listOf(LayerEntity(id = feature.layerId, propertyId = feature.propertyId, planId = feature.planId, name = "L", category = "C"))
        )
        
        val uiStateFlow = MutableStateFlow(detailsState)
        every { viewModel.uiState } returns uiStateFlow
        every { viewModel.systemItems } returns MutableStateFlow(emptyList())

        composeTestRule.setContent {
            MapScreen(basemapProvider = basemapProvider, userPreferencesRepository = userPrefs, viewModel = viewModel)
        }

        // Details should be visible
        composeTestRule.onNodeWithText("Line Feature").assertIsDisplayed()
        
        // Tap Edit Shape
        composeTestRule.onNodeWithTag("edit_shape_button").performClick()
        
        // Update state to simulate ViewModel transition
        uiStateFlow.value = detailsState.copy(
            featureEditorOpen = false,
            editingMode = MapEditingMode.EditLine,
            lineEditState = LineEditState(
                featureId = featureId, propertyId = feature.propertyId, planId = feature.planId, layerId = feature.layerId,
                originalVertices = listOf(Pair(0.0,0.0), Pair(1.0,1.0)), workingVertices = listOf(Pair(0.0,0.0), Pair(1.0,1.0)),
                originalLengthMeters = 100.0, workingLengthMeters = 100.0
            )
        )
        
        // Verify details closed and toolbar appeared
        composeTestRule.onNodeWithText("Line Feature").assertDoesNotExist()
        composeTestRule.onNodeWithTag("EditLineToolbar").assertIsDisplayed()
    }

    @Test
    fun testMovePointClickClosesDetails() {
        val featureId = UUID.randomUUID()
        val feature = MapFeatureEntity(
            id = featureId, propertyId = UUID.randomUUID(), planId = UUID.randomUUID(), layerId = UUID.randomUUID(),
            label = "My Point", geometryType = "POINT", geometryJson = "{}",
            coordinateSpace = "G", styleJson = "{}", accuracySource = "M"
        )
        
        val detailsState = MapUiState(
            featureEditorOpen = true,
            featureEditorFeature = feature,
            featureEditorTarget = FeatureEditorTarget.Persisted(featureId),
            layers = listOf(LayerEntity(id = feature.layerId, propertyId = feature.propertyId, planId = feature.planId, name = "L", category = "C"))
        )
        
        val uiStateFlow = MutableStateFlow(detailsState)
        every { viewModel.uiState } returns uiStateFlow
        every { viewModel.systemItems } returns MutableStateFlow(emptyList())

        composeTestRule.setContent {
            MapScreen(basemapProvider = basemapProvider, userPreferencesRepository = userPrefs, viewModel = viewModel)
        }

        composeTestRule.onNodeWithTag("move_point_button").performClick()
        
        // Update state
        uiStateFlow.value = detailsState.copy(
            featureEditorOpen = false,
            pointMoveState = PointMoveState(featureId, 0.0, 0.0)
        )
        
        composeTestRule.onNodeWithText("Point Feature").assertDoesNotExist()
        composeTestRule.onNodeWithTag("MovePointToolbar").assertIsDisplayed()
    }
}
