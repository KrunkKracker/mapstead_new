package com.jumastappworks.mapstead.ui.mapping

import androidx.lifecycle.SavedStateHandle
import com.jumastappworks.mapstead.data.db.entities.LayerEntity
import com.jumastappworks.mapstead.data.db.entities.PlanEntity
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.data.repository.*
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class AddSomethingWorkflowTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val mapRepo = mockk<MapRepository>(relaxed = true)
    private val infraRepo = mockk<InfrastructureRepository>(relaxed = true)
    private val propertyRepo = mockk<PropertyRepository>(relaxed = true)
    private val userPrefs = mockk<UserPreferencesRepository>(relaxed = true)
    private val namingService = mockk<FeatureNamingService>(relaxed = true)
    private val context = mockk<android.content.Context>(relaxed = true)
    private lateinit var viewModel: MapViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { userPrefs.userPreferencesFlow } returns flowOf(com.jumastappworks.mapstead.data.prefs.UserPreferences())
        every { context.getString(any()) } returns "Localized String"
        
        viewModel = MapViewModel(
            mapRepo, mockk(relaxed = true), infraRepo, propertyRepo, 
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), 
            userPrefs, namingService, context, SavedStateHandle()
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Add Something menu opens with task categories`() {
        viewModel.setShowGuidedAddMenu(true)
        assertTrue(viewModel.uiState.value.showGuidedAddMenu)
    }

    @Test
    fun `Electrical Panel uses AUTOMATIC policy`() {
        val panel = GuidedMapPresets.LOCATIONS.find { it.id == GuidedMapPresetId.ELECTRICAL_PANEL }
        assertEquals(SystemItemPolicy.AUTOMATIC, panel?.systemItemPolicy)
    }

    @Test
    fun `Well uses OPTIONAL policy`() {
        val well = GuidedMapPresets.LOCATIONS.find { it.id == GuidedMapPresetId.WELL }
        assertEquals(SystemItemPolicy.OPTIONAL, well?.systemItemPolicy)
    }

    @Test
    fun `Fence uses MAP_ONLY policy`() {
        val fence = GuidedMapPresets.ROUTES.find { it.id == GuidedMapPresetId.FENCE }
        assertEquals(SystemItemPolicy.MAP_ONLY, fence?.systemItemPolicy)
    }

    @Test
    fun `AUTOMATIC policy initializes CreateSuggested link selection`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "Utilities", category = "Utility")
        
        val plan = PlanEntity(id = planId, propertyId = propId, name = "P", planType = "T", backgroundType = "B")
        coEvery { mapRepo.getLayersForPlan(planId) } returns flowOf(listOf(layer))
        coEvery { mapRepo.getPlanById(planId) } returns plan
        coEvery { mapRepo.getPlansForProperty(propId) } returns flowOf(listOf(plan))
        coEvery { mapRepo.getFeaturesForProperty(propId) } returns flowOf(emptyList())
        coEvery { mapRepo.getFeaturesForPlan(planId) } returns flowOf(emptyList())

        viewModel.setProperty(propId)
        viewModel.selectPlan(planId)
        advanceUntilIdle()
        
        assertTrue("Add to Map should be available", viewModel.uiState.value.addToMapAvailability.isAvailable)
        
        val panelPreset = GuidedMapPresets.LOCATIONS.find { it.id == GuidedMapPresetId.ELECTRICAL_PANEL }!!
        viewModel.setGuidedPreset(panelPreset)
        viewModel.selectGuidedLocationMethod(PlacementMethod.TAP_MAP)
        advanceUntilIdle()
        
        assertNotNull("Guided session should be active", viewModel.uiState.value.guidedSession)

        // Simulate point placement
        viewModel.addPointAt(10.0, 10.0)
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertTrue(state.featureEditorOpen)
        assertEquals(SystemItemLinkSelection.CreateSuggested, state.linkSelection)
    }

    @Test
    fun `OPTIONAL policy initializes None link selection by default`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "Utilities", category = "Utility")

        val plan = PlanEntity(id = planId, propertyId = propId, name = "P", planType = "T", backgroundType = "B")
        coEvery { mapRepo.getLayersForPlan(planId) } returns flowOf(listOf(layer))
        coEvery { mapRepo.getPlanById(planId) } returns plan
        coEvery { mapRepo.getPlansForProperty(propId) } returns flowOf(listOf(plan))
        coEvery { mapRepo.getFeaturesForProperty(propId) } returns flowOf(emptyList())
        coEvery { mapRepo.getFeaturesForPlan(planId) } returns flowOf(emptyList())

        viewModel.setProperty(propId)
        viewModel.selectPlan(planId)
        advanceUntilIdle()
        
        val wellPreset = GuidedMapPresets.LOCATIONS.find { it.id == GuidedMapPresetId.WELL }!!
        viewModel.setGuidedPreset(wellPreset)
        viewModel.selectGuidedLocationMethod(PlacementMethod.TAP_MAP)
        advanceUntilIdle()
        
        viewModel.addPointAt(10.0, 10.0)
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertEquals(SystemItemLinkSelection.None, state.linkSelection)
    }
}
