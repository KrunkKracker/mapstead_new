package com.jumastappworks.mapstead.ui.mapping

import androidx.lifecycle.SavedStateHandle
import com.jumastappworks.mapstead.data.db.entities.LayerEntity
import com.jumastappworks.mapstead.data.db.entities.PlanEntity
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.data.prefs.MeasurementSystem
import com.jumastappworks.mapstead.data.prefs.UserPreferences
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import com.jumastappworks.mapstead.data.repository.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class GuidedSessionIntegrityTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mapRepo = mockk<MapRepository>(relaxed = true)
    private val attachmentRepo = mockk<AttachmentRepository>(relaxed = true)
    private val infraRepo = mockk<InfrastructureRepository>(relaxed = true)
    private val propRepo = mockk<PropertyRepository>(relaxed = true)
    private val resolver = mockk<MapFeatureContextResolver>(relaxed = true)
    private val locationProvider = mockk<CurrentLocationProvider>(relaxed = true)
    private val basemapProvider = mockk<BasemapProvider>(relaxed = true)
    private val userPrefs = mockk<UserPreferencesRepository>(relaxed = true)
    private val namingService = mockk<FeatureNamingService>(relaxed = true)
    private val context = mockk<android.content.Context>(relaxed = true)
    private val savedStateHandle = SavedStateHandle()

    private lateinit var viewModel: MapViewModel

    private val testPropertyId = UUID.randomUUID()
    private val testPlanId = UUID.randomUUID()
    private val testLayerId = UUID.randomUUID()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { basemapProvider.defaultBasemap() } returns BasemapId.STREETS
        every { userPrefs.userPreferencesFlow } returns flowOf(UserPreferences(selectedBasemapId = BasemapId.STREETS, measurementSystem = MeasurementSystem.IMPERIAL))
        
        val property = PropertyEntity(id = testPropertyId, name = "P", propertyType = "T", latitude = 0.0, longitude = 0.0)
        val plan = PlanEntity(id = testPlanId, propertyId = testPropertyId, name = "Plan", planType = "T", backgroundType = "M", centerLatitude = 0.0, centerLongitude = 0.0, zoom = 15.0)
        val layer = LayerEntity(id = testLayerId, propertyId = testPropertyId, planId = testPlanId, name = "L", category = "C")
        
        every { mapRepo.getLayersForPlan(any()) } returns flowOf(listOf(layer))
        every { mapRepo.getFeaturesForLayer(testLayerId) } returns flowOf(emptyList())
        every { propRepo.getAllProperties() } returns flowOf(listOf(property))
        every { mapRepo.getPlansForProperty(testPropertyId) } returns flowOf(listOf(plan))
        coEvery { mapRepo.getPlanById(testPlanId) } returns plan
        
        viewModel = MapViewModel(mapRepo, attachmentRepo, infraRepo, propRepo, resolver, locationProvider, basemapProvider, userPrefs, namingService, context, savedStateHandle)
        viewModel.setProperty(testPropertyId)
        viewModel.selectPlan(testPlanId)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `property switch cancels a guided session`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }
        advanceUntilIdle()
        
        val preset = GuidedMapPresets.LOCATIONS.find { it.id == GuidedMapPresetId.ELECTRICAL_PANEL }!!
        viewModel.setGuidedPreset(preset)
        advanceUntilIdle()
        viewModel.selectGuidedLocationMethod(PlacementMethod.TAP_MAP)
        advanceUntilIdle()
        
        assertNotNull("Guided session should be active", viewModel.uiState.value.guidedSession)
        
        val newPropertyId = UUID.randomUUID()
        viewModel.setProperty(newPropertyId, force = true)
        advanceUntilIdle()
        
        assertNull("Guided session should be cleared after property switch", viewModel.uiState.value.guidedSession)
    }

    @Test
    fun `map switch cancels a guided session`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }
        advanceUntilIdle()
        
        val preset = GuidedMapPresets.LOCATIONS.find { it.id == GuidedMapPresetId.ELECTRICAL_PANEL }!!
        viewModel.setGuidedPreset(preset)
        advanceUntilIdle()
        viewModel.selectGuidedLocationMethod(PlacementMethod.TAP_MAP)
        advanceUntilIdle()
        
        assertNotNull("Guided session should be active", viewModel.uiState.value.guidedSession)
        
        val newPlanId = UUID.randomUUID()
        viewModel.selectPlan(newPlanId, force = true)
        advanceUntilIdle()
        
        assertNull("Guided session should be cleared after map switch", viewModel.uiState.value.guidedSession)
    }

    @Test
    fun `confirming discard clears guided session`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }
        advanceUntilIdle()
        
        val preset = GuidedMapPresets.LOCATIONS.find { it.id == GuidedMapPresetId.ELECTRICAL_PANEL }!!
        viewModel.setGuidedPreset(preset)
        advanceUntilIdle()
        viewModel.selectGuidedLocationMethod(PlacementMethod.TAP_MAP)
        advanceUntilIdle()
        
        viewModel.addPointAt(0.0, 0.0)
        advanceUntilIdle()
        
        viewModel.tryCancelGuidedCreation()
        advanceUntilIdle()
        
        assertTrue("Discard dialog should be shown", viewModel.uiState.value.showDiscardEditDialog)
        
        viewModel.confirmDiscardEdit()
        advanceUntilIdle()
        
        assertNull("Guided session should be cleared after confirming discard", viewModel.uiState.value.guidedSession)
        assertFalse("Editor should be closed", viewModel.uiState.value.featureEditorOpen)
    }

    @Test
    fun `keep editing preserves guided session`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }
        advanceUntilIdle()
        
        val preset = GuidedMapPresets.LOCATIONS.find { it.id == GuidedMapPresetId.ELECTRICAL_PANEL }!!
        viewModel.setGuidedPreset(preset)
        advanceUntilIdle()
        viewModel.selectGuidedLocationMethod(PlacementMethod.TAP_MAP)
        advanceUntilIdle()
        
        viewModel.addPointAt(0.0, 0.0)
        advanceUntilIdle()
        
        viewModel.tryCancelGuidedCreation()
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.showDiscardEditDialog)
        
        viewModel.dismissDiscardDialog()
        advanceUntilIdle()
        
        assertNotNull("Guided session should remain", viewModel.uiState.value.guidedSession)
        assertTrue("Editor should remain open", viewModel.uiState.value.featureEditorOpen)
    }

    @Test
    fun `incomplete restored session is safely cleared`() = runTest {
        val handle = SavedStateHandle(mapOf("guided_session_id" to UUID.randomUUID().toString()))
        val vm = MapViewModel(mapRepo, attachmentRepo, infraRepo, propRepo, resolver, locationProvider, basemapProvider, userPrefs, namingService, context, handle)
        
        backgroundScope.launch(testDispatcher) { vm.uiState.collect() }
        advanceUntilIdle()
        
        assertNull("Restored incomplete session should be cleared", vm.uiState.value.guidedSession)
    }
}
