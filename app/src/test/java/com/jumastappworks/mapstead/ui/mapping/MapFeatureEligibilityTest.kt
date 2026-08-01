package com.jumastappworks.mapstead.ui.mapping

import androidx.lifecycle.SavedStateHandle
import com.jumastappworks.mapstead.data.db.entities.LayerEntity
import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.data.db.entities.PlanEntity
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.mapping.BasemapProvider
import com.jumastappworks.mapstead.data.mapping.*
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
class MapFeatureEligibilityTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mapRepo = mockk<MapRepository>(relaxed = true)
    private val attachmentRepo = mockk<AttachmentRepository>(relaxed = true)
    private val infraRepo = mockk<InfrastructureRepository>(relaxed = true)
    private val propRepo = mockk<PropertyRepository>(relaxed = true)
    private val resolver = mockk<MapFeatureContextResolver>(relaxed = true)
    private val locationProvider = mockk<CurrentLocationProvider>(relaxed = true)
    private val basemapProvider = mockk<BasemapProvider>(relaxed = true)
    private val userPrefs = mockk<com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository>(relaxed = true)
    private val namingService = mockk<com.jumastappworks.mapstead.data.mapping.FeatureNamingService>(relaxed = true)
    private val context = mockk<android.content.Context>(relaxed = true)
    private val savedStateHandle = SavedStateHandle()

    private lateinit var viewModel: MapViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { userPrefs.userPreferencesFlow } returns kotlinx.coroutines.flow.flowOf(
            com.jumastappworks.mapstead.data.prefs.UserPreferences(
                isDarkMode = false,
                themeSelection = com.jumastappworks.mapstead.data.prefs.ThemeSelection.SYSTEM,
                useDynamicColor = false,
                selectedPropertyId = null,
                selectedBasemapId = BasemapId.STREETS,
                measurementSystem = com.jumastappworks.mapstead.data.prefs.MeasurementSystem.IMPERIAL,
                guidanceDismissedPropertyIds = emptySet(),
                gettingStartedDismissedPropertyIds = emptySet(),
                boundaryDisclaimerAcknowledged = false
            )
        )
        viewModel = MapViewModel(mapRepo, attachmentRepo, infraRepo, propRepo, resolver, locationProvider, basemapProvider, userPrefs, namingService, context, savedStateHandle)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.setupContext(propId: UUID, planId: UUID, layers: List<LayerEntity> = emptyList()) {
        val plan = PlanEntity(id = planId, propertyId = propId, name = "P", planType = "M", backgroundType = "M")
        val property = PropertyEntity(id = propId, name = "Prop", propertyType = "Home")
        
        // Ensure layers are visible and not locked by default unless specified
        val preparedLayers = layers.map { 
            if (it.isLocked) it else it.copy(isVisible = true, isLocked = false, deletedAt = null) 
        }

        every { mapRepo.getPlansForProperty(propId) } returns flowOf(listOf(plan))
        coEvery { mapRepo.getPlanById(planId) } returns plan
        coEvery { propRepo.getPropertyById(propId) } returns property
        every { propRepo.getAllProperties() } returns flowOf(listOf(property))

        every { mapRepo.getLayersForPlan(planId) } returns flowOf(preparedLayers)
        every { mapRepo.getFeaturesForPlan(planId) } returns flowOf(emptyList())
        preparedLayers.forEach { layer ->
            every { mapRepo.getFeaturesForLayer(layer.id) } returns flowOf(emptyList())
        }
        
        viewModel.setProperty(propId)
        viewModel.selectPlan(planId)
        advanceUntilIdle()
    }

    @Test
    fun `canEditShape is false for locked layer`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C", isLocked = true)
        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = layerId, geometryType = "POINT", geometryJson = "{}", coordinateSpace = "LOCAL", styleJson = "{}", accuracySource = "MANUAL")
        
        setupContext(propId, planId, listOf(layer))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        coEvery { resolver.resolve(propId, planId, featureId) } returns ActiveMapFeatureContext(feature, mockk(relaxed=true), layer)
        
        viewModel.selectPersistedFeature(feature)
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.canEditShape)
    }

    @Test
    fun `valid polygon enables shape editing`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C")
        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = layerId, geometryType = "POLYGON", geometryJson = "{\"type\":\"Polygon\",\"coordinates\":[[[-122,37],[-121,37],[-121,38],[-122,37]]]}", coordinateSpace = "LOCAL", styleJson = "{}", accuracySource = "MANUAL")
        
        setupContext(propId, planId, listOf(layer))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        coEvery { resolver.resolve(propId, planId, featureId) } returns ActiveMapFeatureContext(feature, mockk(relaxed=true), layer)
        
        viewModel.selectPersistedFeature(feature)
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.canEditShape)
    }

    @Test
    fun `polygon with hole is rejected for editing`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C")
        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = layerId, geometryType = "POLYGON", geometryJson = "{\"type\":\"Polygon\",\"coordinates\":[[[-122,37],[-120,37],[-120,39],[-122,37]], [[-121,37.5],[-120.5,37.5],[-120.5,38],[-121,37.5]]]}", coordinateSpace = "LOCAL", styleJson = "{}", accuracySource = "MANUAL")
        
        setupContext(propId, planId, listOf(layer))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        coEvery { resolver.resolve(propId, planId, featureId) } returns ActiveMapFeatureContext(feature, mockk(relaxed=true), layer)
        
        viewModel.selectPersistedFeature(feature)
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.canEditShape)
    }

    @Test
    fun `self-intersecting polygon is rejected for editing`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C")
        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = layerId, geometryType = "POLYGON", geometryJson = "{\"type\":\"Polygon\",\"coordinates\":[[[-122,37],[-120,38],[-122,38],[-120,37],[-122,37]]]}", coordinateSpace = "LOCAL", styleJson = "{}", accuracySource = "MANUAL")
        
        setupContext(propId, planId, listOf(layer))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        coEvery { resolver.resolve(propId, planId, featureId) } returns ActiveMapFeatureContext(feature, mockk(relaxed=true), layer)
        
        viewModel.selectPersistedFeature(feature)
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.canEditShape)
    }

    @Test
    fun `valid linestring enables editing`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C")
        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = layerId, geometryType = "LINESTRING", geometryJson = "{\"type\":\"LineString\",\"coordinates\":[[-122,37],[-121,38]]}", coordinateSpace = "LOCAL", styleJson = "{}", accuracySource = "MANUAL")
        
        setupContext(propId, planId, listOf(layer))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        coEvery { resolver.resolve(propId, planId, featureId) } returns ActiveMapFeatureContext(feature, mockk(relaxed=true), layer)
        
        viewModel.selectPersistedFeature(feature)
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.canEditShape)
    }
}
