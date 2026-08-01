package com.jumastappworks.mapstead.ui.mapping

import androidx.lifecycle.SavedStateHandle
import com.jumastappworks.mapstead.data.db.entities.LayerEntity
import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.R
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
class PolygonReliabilityTest {

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
        Dispatchers.setMain(StandardTestDispatcher())
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
        viewModel = MapViewModel(mapRepo, attachmentRepo, infraRepo, propRepo, resolver, locationProvider, basemapProvider, userPrefs, namingService, context, savedStateHandle)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.setupContext(propId: UUID, planId: UUID, layers: List<LayerEntity> = emptyList(), features: List<MapFeatureEntity> = emptyList()) {
        val plan = PlanEntity(id = planId, propertyId = propId, name = "P", planType = "M", backgroundType = "M")
        val property = PropertyEntity(id = propId, name = "Prop", propertyType = "Home")
        every { mapRepo.getPlansForProperty(propId) } returns flowOf(listOf(plan))
        coEvery { mapRepo.getPlanById(planId) } returns plan
        coEvery { propRepo.getPropertyById(propId) } returns property
        every { propRepo.getAllProperties() } returns flowOf(listOf(property))

        every { mapRepo.getLayersForPlan(planId) } returns flowOf(layers)
        every { mapRepo.getFeaturesForPlan(planId) } returns flowOf(features)

        layers.forEach { layer ->
            val layerFeatures = features.filter { it.layerId == layer.id }
            every { mapRepo.getFeaturesForLayer(layer.id) } returns flowOf(layerFeatures)
        }
        
        viewModel.setProperty(propId)
        viewModel.selectPlan(planId)
        advanceUntilIdle()
    }

    @Test
    fun `cannot finish polygon with too few vertices`() = runTest {

        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C", isVisible = true)
        
        setupContext(propId, planId, listOf(layer))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        viewModel.setActiveLayer(layerId)
        advanceUntilIdle()
        
        viewModel.beginAddPolygon()
        viewModel.addPolygonVertex(-110.0, 45.0)
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.canFinishPolygon)
    }

    @Test
    fun `rejects self-intersecting polygon`() = runTest {

        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C", isVisible = true, isLocked = false)
        setupContext(propId, planId, listOf(layer))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        viewModel.setActiveLayer(layerId)
        advanceUntilIdle()

        viewModel.beginAddPolygon()
        // Create hourglass shape
        viewModel.addPolygonVertex(-122.0, 37.0)
        viewModel.addPolygonVertex(-120.0, 38.0)
        viewModel.addPolygonVertex(-122.0, 38.0)
        viewModel.addPolygonVertex(-120.0, 37.0) // Crosses back
        
        val state = viewModel.uiState.first { it.polygonValidationRes != null }
        assertFalse(state.canFinishPolygon)
        assertNotNull(state.polygonValidationRes)
        assertEquals(R.string.poly_val_self_intersect, state.polygonValidationRes)
    }

    @Test
    fun `camera focus request is consumed and cleared`() = runTest {

        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = UUID.randomUUID(), geometryType = "POINT", geometryJson = "{\"type\":\"Point\",\"coordinates\":[-110,45]}", coordinateSpace = "LOCAL", styleJson = "{}", accuracySource = "MANUAL")
        
        setupContext(propId, planId)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        viewModel.selectPersistedFeature(feature, requestCameraFocus = true)
        advanceUntilIdle()
        
        assertNotNull(viewModel.uiState.value.cameraFocus)
        
        viewModel.clearCameraFocus()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.cameraFocus)
    }

    @Test
    fun `polygon edit undo history respects the intended bound`() = runTest {

        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        
        val initialGeometry = "{\"type\":\"Polygon\",\"coordinates\":[[[-122,37],[-121,37],[-121,38],[-122,37]]]}"
        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = layerId, geometryType = "POLYGON", geometryJson = initialGeometry, coordinateSpace = "LOCAL", styleJson = "{}", accuracySource = "MANUAL")
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C", isVisible = true)
        
        coEvery { mapRepo.getFeatureById(featureId) } returns feature
        coEvery { mapRepo.getLayerById(layerId) } returns layer
        
        setupContext(propId, planId, listOf(layer), listOf(feature))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        
        viewModel.beginPersistedShapeEdit(featureId)
        advanceUntilIdle()
        
        // Push 60 states
        repeat(60) { i ->
            viewModel.insertPolygonVertex(1, Pair(-121.5, 36.5 + i * 0.001))
            advanceUntilIdle()
        }
        
        // Undo stack should be capped at 50
        val state = viewModel.uiState.value.polygonEditState
        assertNotNull(state)
        assertEquals(50, state?.undoStack?.size)
    }
}
