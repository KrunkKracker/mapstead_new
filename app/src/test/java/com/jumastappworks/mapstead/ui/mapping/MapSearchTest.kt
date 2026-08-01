package com.jumastappworks.mapstead.ui.mapping

import androidx.lifecycle.SavedStateHandle
import com.jumastappworks.mapstead.data.db.entities.LayerEntity
import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.data.db.entities.PlanEntity
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.mapping.BasemapId
import com.jumastappworks.mapstead.data.mapping.BasemapProvider
import com.jumastappworks.mapstead.data.mapping.CurrentLocationProvider
import com.jumastappworks.mapstead.data.mapping.MapFeatureContextResolver
import com.jumastappworks.mapstead.data.prefs.MeasurementSystem
import com.jumastappworks.mapstead.data.prefs.UserPreferences
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import com.jumastappworks.mapstead.data.repository.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class MapSearchTest {

    private val mapRepo = mockk<MapRepository>(relaxed = true)
    private val attachmentRepo = mockk<AttachmentRepository>(relaxed = true)
    private val infraRepo = mockk<InfrastructureRepository>(relaxed = true)
    private val propRepo = mockk<PropertyRepository>(relaxed = true)
    private val resolver = mockk<MapFeatureContextResolver>(relaxed = true)
    private val locationProvider = mockk<CurrentLocationProvider>(relaxed = true)
    private val basemapProvider = mockk<BasemapProvider>(relaxed = true)
    private val userPrefs = mockk<UserPreferencesRepository>(relaxed = true)
    private val namingService = mockk<com.jumastappworks.mapstead.data.mapping.FeatureNamingService>(relaxed = true)
    private val context = mockk<android.content.Context>(relaxed = true)
    private val savedStateHandle = SavedStateHandle()

    private lateinit var viewModel: MapViewModel

    @org.junit.Before
    fun setup() {
        val testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        every { userPrefs.userPreferencesFlow } returns flowOf(
            UserPreferences(
                isDarkMode = false,
                themeSelection = com.jumastappworks.mapstead.data.prefs.ThemeSelection.SYSTEM,
                useDynamicColor = false,
                selectedPropertyId = null,
                selectedBasemapId = BasemapId.STREETS,
                measurementSystem = MeasurementSystem.IMPERIAL,
                guidanceDismissedPropertyIds = emptySet(),
                gettingStartedDismissedPropertyIds = emptySet(),
                boundaryDisclaimerAcknowledged = false
            )
        )
        every { propRepo.getAllProperties() } returns flowOf(emptyList())
        viewModel = MapViewModel(mapRepo, attachmentRepo, infraRepo, propRepo, resolver, locationProvider, basemapProvider, userPrefs, namingService, context, savedStateHandle)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.setupContext(propId: UUID, planId: UUID, layers: List<LayerEntity> = emptyList()) {
        val plan = PlanEntity(id = planId, propertyId = propId, name = "P", planType = "M", backgroundType = "M")
        val property = PropertyEntity(id = propId, name = "Prop", propertyType = "Home")
        every { mapRepo.getPlansForProperty(propId) } returns flowOf(listOf(plan))
        coEvery { mapRepo.getPlanById(planId) } returns plan
        coEvery { propRepo.getPropertyById(propId) } returns property
        every { propRepo.getAllProperties() } returns flowOf(listOf(property))

        every { mapRepo.getLayersForPlan(planId) } returns flowOf(layers)
        every { mapRepo.getFeaturesForPlan(planId) } returns flowOf(emptyList())
        
        viewModel.setProperty(propId)
        viewModel.selectPlan(planId)
        advanceUntilIdle()
    }

    @Test
    fun `search activation updates state`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.setSearchActive(true)
        assertTrue(viewModel.uiState.value.isSearchActive)
        
        viewModel.setSearchQuery("pipe")
        assertEquals("pipe", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun `search clearing resets query and results`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.setSearchActive(true)
        viewModel.setSearchQuery("leak")
        advanceUntilIdle()
        
        viewModel.setSearchActive(false)
        advanceUntilIdle()
        assertEquals("", viewModel.uiState.value.searchQuery)
        assertFalse(viewModel.uiState.value.isSearchActive)
    }

    @Test
    fun `search is blocked during active editing`() = runTest {

        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C", isVisible = true)
        
        setupContext(propId, planId, listOf(layer))
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.setActiveLayer(layerId)
        advanceUntilIdle()
        
        viewModel.beginAddPoint()
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.isWorkflowActive)
    }

    @Test
    fun `opening search result selects feature`() = runTest {

        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = layerId, geometryType = "POINT", geometryJson = "{}", coordinateSpace = "LOCAL", styleJson = "{}", accuracySource = "MANUAL")
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C", isVisible = true)

        coEvery { mapRepo.getFeatureById(featureId) } returns feature
        coEvery { mapRepo.getLayerById(layerId) } returns layer
        
        setupContext(propId, planId, listOf(layer))
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        val result = MapSearchResult(featureId, "Label", null, null, "Category", null, layerId, "Layer", true, false, "POINT")
        viewModel.openSearchResult(result)
        advanceUntilIdle()

        assertEquals(feature, viewModel.uiState.value.selectedFeature)
        assertFalse(viewModel.uiState.value.isSearchActive)
    }

    @Test
    fun `revealAndOpenSearchResult on hidden layer reveals it`() = runTest {

        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        
        val hiddenLayer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C", isVisible = false)
        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = layerId, geometryType = "POINT", geometryJson = "{}", coordinateSpace = "LOCAL", styleJson = "{}", accuracySource = "MANUAL")

        coEvery { mapRepo.getFeatureById(featureId) } returns feature
        coEvery { mapRepo.getLayerById(layerId) } returns hiddenLayer
        coEvery { mapRepo.updateLayer(any()) } just Runs
        
        setupContext(propId, planId, listOf(hiddenLayer))
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        val result = MapSearchResult(featureId, "Label", null, null, "Category", null, layerId, "Layer", false, false, "POINT")
        viewModel.revealAndOpenSearchResult(result)
        advanceUntilIdle()
        
        coVerify { mapRepo.updateLayer(match { it.id == layerId && it.isVisible }) }
    }

    @Test
    fun `revealAndOpenSearchResult on locked layer reveals but does not unlock it`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        
        val hiddenLockedLayer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C", isVisible = false, isLocked = true)
        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = layerId, geometryType = "POINT", geometryJson = "{}", coordinateSpace = "LOCAL", styleJson = "{}", accuracySource = "MANUAL")

        coEvery { mapRepo.getFeatureById(featureId) } returns feature
        coEvery { mapRepo.getLayerById(layerId) } returns hiddenLockedLayer
        coEvery { mapRepo.updateLayer(any()) } just Runs
        
        setupContext(propId, planId, listOf(hiddenLockedLayer))
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        val result = MapSearchResult(featureId, "Label", null, null, "Category", null, layerId, "Layer", false, false, "POINT")
        viewModel.revealAndOpenSearchResult(result)
        advanceUntilIdle()
        
        // Should update visibility to true but preserve isLocked = true
        coVerify { mapRepo.updateLayer(match { it.id == layerId && it.isVisible && it.isLocked }) }
    }

    @Test
    fun `opening visible result does not mutate layer visibility`() = runTest {

        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        
        val visibleLayer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C", isVisible = true)
        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = layerId, geometryType = "POINT", geometryJson = "{}", coordinateSpace = "LOCAL", styleJson = "{}", accuracySource = "MANUAL")

        coEvery { mapRepo.getFeatureById(featureId) } returns feature
        coEvery { mapRepo.getLayerById(layerId) } returns visibleLayer
        
        setupContext(propId, planId, listOf(visibleLayer))
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        val result = MapSearchResult(featureId, "Label", null, null, "Category", null, layerId, "Layer", true, false, "POINT")
        viewModel.openSearchResult(result)
        advanceUntilIdle()
        
        coVerify(exactly = 0) { mapRepo.updateLayer(any()) }
    }
}
