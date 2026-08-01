package com.jumastappworks.mapstead.data.mapping

import androidx.lifecycle.SavedStateHandle
import com.jumastappworks.mapstead.data.db.entities.LayerEntity
import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.data.db.entities.PlanEntity
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.repository.*
import com.jumastappworks.mapstead.ui.mapping.MapEditingMode
import com.jumastappworks.mapstead.ui.mapping.MapViewModel
import com.jumastappworks.mapstead.ui.mapping.FeatureEditorTarget
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
import org.junit.Before
import org.junit.Test
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class PolylineTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mapRepository = mockk<MapRepository>(relaxed = true)
    private val attachmentRepository = mockk<AttachmentRepository>(relaxed = true)
    private val infrastructureRepository = mockk<InfrastructureRepository>(relaxed = true)
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
        every { basemapProvider.defaultBasemap() } returns BasemapId.STREETS
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
        every { propRepo.getAllProperties() } returns flowOf(emptyList())
        
        viewModel = MapViewModel(mapRepository, attachmentRepository, infrastructureRepository, propRepo, resolver, locationProvider, basemapProvider, userPrefs, namingService, context, savedStateHandle)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.setupContext(propId: UUID, planId: UUID, layers: List<LayerEntity> = emptyList(), features: List<MapFeatureEntity> = emptyList()) {
        val plan = PlanEntity(id = planId, propertyId = propId, name = "P", planType = "M", backgroundType = "M")
        val property = PropertyEntity(id = propId, name = "Prop", propertyType = "Home")
        every { mapRepository.getPlansForProperty(propId) } returns flowOf(listOf(plan))
        coEvery { mapRepository.getPlanById(planId) } returns plan
        coEvery { propRepo.getPropertyById(propId) } returns property
        every { propRepo.getAllProperties() } returns flowOf(listOf(property))

        every { mapRepository.getLayersForPlan(planId) } returns flowOf(layers)
        every { mapRepository.getFeaturesForPlan(planId) } returns flowOf(features)

        layers.forEach { layer ->
            val layerFeatures = features.filter { it.layerId == layer.id }
            every { mapRepository.getFeaturesForLayer(layer.id) } returns flowOf(layerFeatures)
        }
        
        viewModel.setProperty(propId)
        viewModel.selectPlan(planId)
        advanceUntilIdle()
    }

    @Test
    fun testDraftLineVertexManagement() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        viewModel.addDraftVertex(-110.0, 45.0)
        viewModel.addDraftVertex(-111.0, 46.0)
        advanceUntilIdle()
        
        assertEquals(2, viewModel.uiState.value.draftVertices.size)
        
        viewModel.undoDraftVertex()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.draftVertices.size)
        
        viewModel.cancelDraftLine()
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.draftVertices.size)
    }

    @Test
    fun `valid line insertion creates draft feature`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C", isVisible = true, isLocked = false)
        
        setupContext(propId, planId, listOf(layer))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        viewModel.setActiveLayer(layerId)
        advanceUntilIdle()

        viewModel.beginAddLine()
        viewModel.addDraftVertex(-110.0, 45.0)
        viewModel.addDraftVertex(-111.0, 46.0)
        advanceUntilIdle()

        val featureId = viewModel.finishDraftLine()
        assertNotNull(featureId)
        
        val state = viewModel.uiState.first { it.featureEditorTarget is FeatureEditorTarget.NewLine }
        assertEquals(FeatureEditorTarget.NewLine(featureId!!), state.featureEditorTarget)
    }

    @Test
    fun `duplicate-vertex handling ignores consecutive identical points`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        viewModel.addDraftVertex(-110.0, 45.0)
        viewModel.addDraftVertex(-110.0, 45.0) // Identical
        advanceUntilIdle()
        
        assertEquals(1, viewModel.uiState.value.draftVertices.size)
    }

    @Test
    fun `too-short line finish returns null`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        viewModel.addDraftVertex(-110.0, 45.0)
        advanceUntilIdle()
        
        val feature = viewModel.finishDraftLine()
        assertEquals(null, feature)
    }

    @Test
    fun `midpoint insertion adds vertex correctly`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        
        val initialGeometry = "{\"type\":\"LineString\",\"coordinates\":[[-122,37],[-121,38]]}"
        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = layerId, geometryType = "LINESTRING", geometryJson = initialGeometry, coordinateSpace = "LOCAL", styleJson = "{}", accuracySource = "MANUAL")
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C")
        
        coEvery { mapRepository.getFeatureById(featureId) } returns feature
        coEvery { mapRepository.getLayerById(layerId) } returns layer
        setupContext(propId, planId, listOf(layer), listOf(feature))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        
        viewModel.beginPersistedShapeEdit(featureId)
        advanceUntilIdle()
        
        viewModel.insertVertex(1, Pair(-121.5, 37.5))
        advanceUntilIdle()
        
        assertEquals(3, viewModel.uiState.value.lineEditState?.workingVertices?.size)
    }

    @Test
    fun `vertex movement updates working length`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        
        val initialGeometry = "{\"type\":\"LineString\",\"coordinates\":[[-122,37],[-121,38]]}"
        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = layerId, geometryType = "LINESTRING", geometryJson = initialGeometry, coordinateSpace = "LOCAL", styleJson = "{}", accuracySource = "MANUAL")
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C")
        
        coEvery { mapRepository.getFeatureById(featureId) } returns feature
        coEvery { mapRepository.getLayerById(layerId) } returns layer
        setupContext(propId, planId, listOf(layer), listOf(feature))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        
        viewModel.beginPersistedShapeEdit(featureId)
        advanceUntilIdle()
        
        val initialLength = viewModel.uiState.value.lineEditState?.workingLengthMeters ?: 0.0
        
        viewModel.beginVertexDrag(1)
        viewModel.updateVertexDrag(-120.0, 39.0)
        advanceUntilIdle()
        
        val newLength = viewModel.uiState.value.lineEditState?.workingLengthMeters ?: 0.0
        assertTrue(newLength != initialLength)
    }

    @Test
    fun `vertex deletion works in line edit`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        
        val initialGeometry = "{\"type\":\"LineString\",\"coordinates\":[[-122,37],[-121.5,37.5],[-121,38]]}"
        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = layerId, geometryType = "LINESTRING", geometryJson = initialGeometry, coordinateSpace = "LOCAL", styleJson = "{}", accuracySource = "MANUAL")
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C")
        
        coEvery { mapRepository.getFeatureById(featureId) } returns feature
        coEvery { mapRepository.getLayerById(layerId) } returns layer
        setupContext(propId, planId, listOf(layer), listOf(feature))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        
        viewModel.beginPersistedShapeEdit(featureId)
        advanceUntilIdle()
        
        viewModel.selectEditVertex(1)
        viewModel.deleteSelectedVertex()
        advanceUntilIdle()
        
        assertEquals(2, viewModel.uiState.value.lineEditState?.workingVertices?.size)
    }
}
