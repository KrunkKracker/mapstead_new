package com.jumastappworks.mapstead.ui.mapping

import androidx.lifecycle.SavedStateHandle
import com.jumastappworks.mapstead.data.db.entities.LayerEntity
import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.data.db.entities.PlanEntity
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.data.prefs.MeasurementSystem
import com.jumastappworks.mapstead.data.prefs.UserPreferences
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import com.jumastappworks.mapstead.data.repository.*
import com.jumastappworks.mapstead.util.PolygonParseResult
import com.jumastappworks.mapstead.util.GeometryUtils
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelPolygonEditTest {

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

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel() {
        // Use UnconfinedTestDispatcher for better flow synchronization in tests
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
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

    private fun stubContext(propId: UUID, planId: UUID, layers: List<LayerEntity> = emptyList(), features: List<MapFeatureEntity> = emptyList()) {
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
            coEvery { mapRepo.getLayerById(layer.id) } returns layer
        }
        features.forEach { feature ->
            coEvery { mapRepo.getFeatureById(feature.id) } returns feature
        }
    }

    @Test
    fun `polygon draft vertex management`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C", isVisible = true)
        
        stubContext(propId, planId, listOf(layer))
        createViewModel()
        backgroundScope.launch { viewModel.uiState.collect() }
        
        viewModel.setProperty(propId)
        viewModel.selectPlan(planId)
        viewModel.setActiveLayer(layerId)
        advanceUntilIdle()
        
        viewModel.beginAddPolygon()
        viewModel.addPolygonVertex(-110.0, 45.0)
        viewModel.addPolygonVertex(-111.0, 46.0)
        advanceUntilIdle()
        
        assertEquals(2, viewModel.uiState.value.polygonDraft?.vertices?.size)
        
        viewModel.undoPolygonVertex()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.polygonDraft?.vertices?.size)
    }

    @Test
    fun `begin edit for valid polygon loads state`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        
        val initialGeometry = "{\"type\":\"Polygon\",\"coordinates\":[[[-122,37],[-121,37],[-121,38],[-122,37]]]}"
        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = layerId, geometryType = "POLYGON", geometryJson = initialGeometry, coordinateSpace = "LOCAL", styleJson = "{}", accuracySource = "MANUAL")
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C", isVisible = true)
        
        stubContext(propId, planId, listOf(layer), listOf(feature))
        createViewModel()
        backgroundScope.launch { viewModel.uiState.collect() }

        viewModel.setProperty(propId)
        viewModel.selectPlan(planId)
        advanceUntilIdle()
        
        viewModel.beginPersistedShapeEdit(featureId)
        advanceUntilIdle()
        
        assertNotNull(viewModel.uiState.value.polygonEditState)
        assertEquals(3, viewModel.uiState.value.polygonEditState?.workingVertices?.size)
    }

    @Test
    fun `polygon midpoint insertion adds vertex`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        
        val initialGeometry = "{\"type\":\"Polygon\",\"coordinates\":[[[-122,37],[-121,37],[-121,38],[-122,37]]]}"
        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = layerId, geometryType = "POLYGON", geometryJson = initialGeometry, coordinateSpace = "LOCAL", styleJson = "{}", accuracySource = "MANUAL")
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C", isVisible = true)
        
        stubContext(propId, planId, listOf(layer), listOf(feature))
        createViewModel()
        backgroundScope.launch { viewModel.uiState.collect() }

        viewModel.setProperty(propId)
        viewModel.selectPlan(planId)
        advanceUntilIdle()
        
        viewModel.beginPersistedShapeEdit(featureId)
        advanceUntilIdle()
        
        viewModel.insertPolygonVertex(1, Pair(-121.5, 36.5))
        advanceUntilIdle()
        
        assertNotNull(viewModel.uiState.value.polygonEditState)
        assertEquals(4, viewModel.uiState.value.polygonEditState?.workingVertices?.size)
    }

    @Test
    fun `polygon save failure preserves edit state`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        
        val initialGeometry = "{\"type\":\"Polygon\",\"coordinates\":[[[-122,37],[-121,37],[-121,38],[-122,37]]]}"
        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = layerId, geometryType = "POLYGON", geometryJson = initialGeometry, coordinateSpace = "LOCAL", styleJson = "{}", accuracySource = "MANUAL")
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C", isVisible = true)
        
        stubContext(propId, planId, listOf(layer), listOf(feature))
        createViewModel()
        backgroundScope.launch { viewModel.uiState.collect() }

        viewModel.setProperty(propId)
        viewModel.selectPlan(planId)
        advanceUntilIdle()
        
        viewModel.beginPersistedShapeEdit(featureId)
        advanceUntilIdle()
        
        viewModel.insertPolygonVertex(1, Pair(-121.5, 36.5))
        advanceUntilIdle()
        
        coEvery { mapRepo.updateFeature(any()) } throws RuntimeException("DB Error")
        
        viewModel.savePolygonEdit()
        advanceUntilIdle()
        
        assertNotNull(viewModel.uiState.value.polygonEditState)
        assertEquals(4, viewModel.uiState.value.polygonEditState?.workingVertices?.size)
    }

    @Test
    fun `dirty polygon cancellation requires confirmation`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        
        val initialGeometry = "{\"type\":\"Polygon\",\"coordinates\":[[[-122,37],[-121,37],[-121,38],[-122,37]]]}"
        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = layerId, geometryType = "POLYGON", geometryJson = initialGeometry, coordinateSpace = "LOCAL", styleJson = "{}", accuracySource = "MANUAL")
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C", isVisible = true)
        
        stubContext(propId, planId, listOf(layer), listOf(feature))
        createViewModel()
        backgroundScope.launch { viewModel.uiState.collect() }

        viewModel.setProperty(propId)
        viewModel.selectPlan(planId)
        advanceUntilIdle()
        
        viewModel.beginPersistedShapeEdit(featureId)
        advanceUntilIdle()
        
        viewModel.insertPolygonVertex(1, Pair(-121.5, 36.5))
        advanceUntilIdle()
        
        viewModel.tryCancelPolygonEdit()
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.showDiscardEditDialog)
        assertTrue(viewModel.uiState.value.discardAction is PendingEditDiscardAction.CancelPolygonEdit)
    }

    @Test
    fun `polygon midpoint insertion routes correctly for closing edge`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        
        // 3 vertices: [-122,37], [-121,37], [-121,38]
        val initialGeometry = "{\"type\":\"Polygon\",\"coordinates\":[[[-122,37],[-121,37],[-121,38],[-122,37]]]}"
        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = layerId, geometryType = "POLYGON", geometryJson = initialGeometry, coordinateSpace = "LOCAL", styleJson = "{}", accuracySource = "MANUAL")
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C", isVisible = true)
        
        stubContext(propId, planId, listOf(layer), listOf(feature))
        createViewModel()
        backgroundScope.launch { viewModel.uiState.collect() }

        viewModel.setProperty(propId)
        viewModel.selectPlan(planId)
        advanceUntilIdle()
        
        viewModel.beginPersistedShapeEdit(featureId)
        advanceUntilIdle()
        
        // Insertion at index 3 (after the last vertex, before closing)
        viewModel.insertPolygonVertex(3, Pair(-121.5, 37.5))
        advanceUntilIdle()
        
        assertEquals(4, viewModel.uiState.value.polygonEditState?.workingVertices?.size)
        // Verify it was inserted at index 3
        assertEquals(Pair(-121.5, 37.5), viewModel.uiState.value.polygonEditState?.workingVertices?.get(3))
    }

    @Test
    fun `polygon undo restores mixed edit state correctly`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        
        val initialGeometry = "{\"type\":\"Polygon\",\"coordinates\":[[[-122,37],[-121,37],[-121,38],[-122,37]]]}"
        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = layerId, geometryType = "POLYGON", geometryJson = initialGeometry, coordinateSpace = "LOCAL", styleJson = "{}", accuracySource = "MANUAL")
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C", isVisible = true)
        
        stubContext(propId, planId, listOf(layer), listOf(feature))
        createViewModel()
        backgroundScope.launch { viewModel.uiState.collect() }

        viewModel.setProperty(propId)
        viewModel.selectPlan(planId)
        advanceUntilIdle()
        
        viewModel.beginPersistedShapeEdit(featureId)
        advanceUntilIdle()
        
        viewModel.insertPolygonVertex(1, Pair(-121.5, 36.5)) // Action 1
        advanceUntilIdle()
        viewModel.beginPolygonVertexDrag(2)
        viewModel.updatePolygonVertexDrag(-120.0, 36.0)
        viewModel.finishPolygonVertexDrag() // Action 2
        advanceUntilIdle()
        
        assertEquals(4, viewModel.uiState.value.polygonEditState?.workingVertices?.size)
        
        viewModel.undoPolygonEdit() // Undo drag
        advanceUntilIdle()
        assertEquals(4, viewModel.uiState.value.polygonEditState?.workingVertices?.size)
        assertEquals(Pair(-121.0, 37.0), viewModel.uiState.value.polygonEditState?.workingVertices?.get(2))
        
        viewModel.undoPolygonEdit() // Undo insertion
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.polygonEditState?.workingVertices?.size)
    }

    @Test
    fun `keep editing preserves polygon edit state`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        
        val initialGeometry = "{\"type\":\"Polygon\",\"coordinates\":[[[-122,37],[-121,37],[-121,38],[-122,37]]]}"
        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = layerId, geometryType = "POLYGON", geometryJson = initialGeometry, coordinateSpace = "LOCAL", styleJson = "{}", accuracySource = "MANUAL")
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C", isVisible = true)
        
        stubContext(propId, planId, listOf(layer), listOf(feature))
        createViewModel()
        backgroundScope.launch { viewModel.uiState.collect() }

        viewModel.setProperty(propId)
        viewModel.selectPlan(planId)
        advanceUntilIdle()
        
        viewModel.beginPersistedShapeEdit(featureId)
        viewModel.insertPolygonVertex(1, Pair(-121.5, 36.5))
        advanceUntilIdle()
        
        viewModel.selectPlan(UUID.randomUUID()) // Trigger discard dialog
        advanceUntilIdle()
        
        viewModel.dismissDiscardDialog()
        advanceUntilIdle()
        
        assertNotNull(viewModel.uiState.value.polygonEditState)
        assertEquals(4, viewModel.uiState.value.polygonEditState?.workingVertices?.size)
    }

    @Test
    fun `polygon save reloads feature and exits edit`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        
        val initialGeometry = "{\"type\":\"Polygon\",\"coordinates\":[[[-122,37],[-121,37],[-121,38],[-122,37]]]}"
        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = layerId, geometryType = "POLYGON", geometryJson = initialGeometry, coordinateSpace = "LOCAL", styleJson = "{}", accuracySource = "MANUAL")
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C", isVisible = true)
        
        stubContext(propId, planId, listOf(layer), listOf(feature))
        createViewModel()
        backgroundScope.launch { viewModel.uiState.collect() }

        viewModel.setProperty(propId)
        viewModel.selectPlan(planId)
        advanceUntilIdle()
        
        viewModel.beginPersistedShapeEdit(featureId)
        advanceUntilIdle()
        
        viewModel.insertPolygonVertex(1, Pair(-121.5, 36.5))
        advanceUntilIdle()
        
        coEvery { mapRepo.updateFeature(any()) } just Runs
        
        viewModel.savePolygonEdit()
        advanceUntilIdle()
        
        assertNull(viewModel.uiState.value.polygonEditState)
    }
}
