package com.jumastappworks.mapstead.ui.mapping

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jumastappworks.mapstead.data.db.entities.LayerEntity
import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class LinkSelectionUiTest {

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
    fun existingLinkedFeatureDisplaysItemName() {
        val infraId = UUID.randomUUID()
        val item = InfrastructureItemEntity(id = infraId, propertyId = UUID.randomUUID(), name = "Linked Pump", category = "Utility", status = "Active")
        val feature = MapFeatureEntity(
            id = UUID.randomUUID(), propertyId = item.propertyId, planId = UUID.randomUUID(), layerId = UUID.randomUUID(),
            infrastructureItemId = infraId, label = "Test Feature", geometryType = "POINT", geometryJson = "{}",
            coordinateSpace = "G", styleJson = "{}", accuracySource = "M"
        )
        
        val uiState = MapUiState(
            featureEditorOpen = true,
            featureEditorFeature = feature,
            linkSelection = SystemItemLinkSelection.Existing(infraId),
            layers = listOf(LayerEntity(id = feature.layerId, propertyId = feature.propertyId, planId = feature.planId, name = "L", category = "C"))
        )
        
        every { viewModel.uiState } returns MutableStateFlow(uiState)
        every { viewModel.systemItems } returns MutableStateFlow(listOf(item))

        composeTestRule.setContent {
            MapScreen(basemapProvider = basemapProvider, userPreferencesRepository = userPrefs, viewModel = viewModel)
        }

        composeTestRule.onNodeWithText("Linked: Linked Pump").assertIsDisplayed()
    }

    @Test
    fun unavailableLinkedItemDisplaysSafeWording() {
        val infraId = UUID.randomUUID()
        val feature = MapFeatureEntity(
            id = UUID.randomUUID(), propertyId = UUID.randomUUID(), planId = UUID.randomUUID(), layerId = UUID.randomUUID(),
            infrastructureItemId = infraId, label = "Missing Link", geometryType = "POINT", geometryJson = "{}",
            coordinateSpace = "G", styleJson = "{}", accuracySource = "M"
        )
        
        val uiState = MapUiState(
            featureEditorOpen = true,
            featureEditorFeature = feature,
            linkSelection = SystemItemLinkSelection.Existing(infraId),
            layers = listOf(LayerEntity(id = feature.layerId, propertyId = feature.propertyId, planId = feature.planId, name = "L", category = "C"))
        )
        
        every { viewModel.uiState } returns MutableStateFlow(uiState)
        every { viewModel.systemItems } returns MutableStateFlow(emptyList())

        composeTestRule.setContent {
            MapScreen(basemapProvider = basemapProvider, userPreferencesRepository = userPrefs, viewModel = viewModel)
        }

        val unavailableText = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(com.jumastappworks.mapstead.R.string.linked_system_item_unavailable)
        composeTestRule.onNodeWithText("Linked: $unavailableText").assertIsDisplayed()
    }

    @Test
    fun optionalTrackingCheckboxRemainsOffByDefault() {
        val featureId = UUID.randomUUID()
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        
        val feature = MapFeatureEntity(
            id = featureId, propertyId = propId, planId = planId, layerId = layerId,
            label = "Optional", geometryType = "POINT", geometryJson = "{}",
            coordinateSpace = "G", styleJson = "{}", accuracySource = "M"
        )
        
        val uiState = MapUiState(
            featureEditorOpen = true,
            featureEditorFeature = feature,
            isNewUnsavedFeature = true,
            linkSelection = SystemItemLinkSelection.None, 
            guidedPrefill = GuidedFeaturePrefill(
                sessionId = UUID.randomUUID(), draftId = featureId, 
                suggestedLabelRes = null, suggestedCategory = "Utility", suggestedLayerId = layerId,
                systemItemPolicy = SystemItemPolicy.OPTIONAL, presetStyle = null
            ),
            layers = listOf(LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C"))
        )
        
        every { viewModel.uiState } returns MutableStateFlow(uiState)

        composeTestRule.setContent {
            MapScreen(basemapProvider = basemapProvider, userPreferencesRepository = userPrefs, viewModel = viewModel)
        }

        val trackingText = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(com.jumastappworks.mapstead.R.string.tracking_keep_records)
        composeTestRule.onNodeWithText(trackingText).assertIsDisplayed()
    }
}
