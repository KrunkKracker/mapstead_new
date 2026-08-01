package com.jumastappworks.mapstead.ui.mapping

import androidx.lifecycle.SavedStateHandle
import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.data.db.entities.LayerEntity
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.data.prefs.MeasurementSystem
import com.jumastappworks.mapstead.data.prefs.UserPreferences
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import com.jumastappworks.mapstead.data.repository.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class GeometryEditExitTest {

    private val testDispatcher = StandardTestDispatcher()
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

    private val testPropertyId = UUID.randomUUID()
    private val testPlanId = UUID.randomUUID()
    private val testLayerId = UUID.randomUUID()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { basemapProvider.defaultBasemap() } returns BasemapId.STREETS
        every { userPrefs.userPreferencesFlow } returns flowOf(UserPreferences(selectedBasemapId = BasemapId.STREETS, measurementSystem = MeasurementSystem.IMPERIAL))
        
        val layer = LayerEntity(id = testLayerId, propertyId = testPropertyId, planId = testPlanId, name = "L", category = "C")
        every { mapRepo.getLayersForPlan(any()) } returns flowOf(listOf(layer))
        every { mapRepo.getFeaturesForLayer(testLayerId) } returns flowOf(emptyList())
        every { propRepo.getAllProperties() } returns flowOf(emptyList())
        
        viewModel = MapViewModel(mapRepo, attachmentRepo, infraRepo, propRepo, resolver, locationProvider, basemapProvider, userPrefs, namingService, context, savedStateHandle)
        viewModel.setProperty(testPropertyId)
        viewModel.selectPlan(testPlanId)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `line save exits edit mode and reopens feature details`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        
        val featureId = UUID.randomUUID()
        val infraId = UUID.randomUUID()
        val feature = MapFeatureEntity(
            id = featureId, propertyId = testPropertyId, planId = testPlanId, layerId = testLayerId,
            infrastructureItemId = infraId, geometryType = "LINESTRING", 
            geometryJson = "{\"type\":\"LineString\",\"coordinates\":[[0,0],[1,1]]}",
            coordinateSpace = "G", styleJson = "{}", accuracySource = "M"
        )
        coEvery { mapRepo.getFeatureById(featureId) } returns feature
        every { mapRepo.getFeaturesForLayer(testLayerId) } returns flowOf(listOf(feature))
        
        // 1. Initial selection
        viewModel.selectPersistedFeature(feature)
        advanceUntilIdle()
        assertEquals(FeatureEditorTarget.Persisted(featureId), viewModel.uiState.value.featureEditorTarget)
        
        // 2. Enter edit mode
        viewModel.beginPersistedShapeEdit(featureId)
        advanceUntilIdle()
        assertEquals(MapEditingMode.EditLine, viewModel.uiState.value.editingMode)
        assertTrue(viewModel.uiState.value.featureEditorTarget is FeatureEditorTarget.EditPersistedLine)
        
        // 3. Modify and Save
        viewModel.updateVertexPosition(0, 0.5, 0.5)
        viewModel.saveLineEdit()
        advanceUntilIdle()
        
        // 4. Verify exit and re-selection
        assertEquals(MapEditingMode.Select, viewModel.uiState.value.editingMode)
        assertNull(viewModel.uiState.value.lineEditState)
        assertEquals(FeatureEditorTarget.Persisted(featureId), viewModel.uiState.value.featureEditorTarget)
        assertTrue(viewModel.uiState.value.featureEditorOpen)
        
        // Link state should be re-initialized
        val session = viewModel._linkEditorSession.value
        assertNotNull(session)
        assertEquals(SystemItemLinkSelection.Existing(infraId), session?.currentSelection)
    }

    @Test
    fun `polygon cancel exits edit mode and reopens feature details`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val featureId = UUID.randomUUID()
        val feature = MapFeatureEntity(
            id = featureId, propertyId = testPropertyId, planId = testPlanId, layerId = testLayerId,
            geometryType = "POLYGON", 
            geometryJson = "{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,0]]]}",
            coordinateSpace = "G", styleJson = "{}", accuracySource = "M"
        )
        coEvery { mapRepo.getFeatureById(featureId) } returns feature
        every { mapRepo.getFeaturesForLayer(testLayerId) } returns flowOf(listOf(feature))
        
        viewModel.selectPersistedFeature(feature)
        advanceUntilIdle()
        
        viewModel.beginPersistedShapeEdit(featureId)
        advanceUntilIdle()
        
        // Modify to make it "dirty"
        viewModel.updateVertexPosition(0, 0.1, 0.1)
        advanceUntilIdle()
        
        // Trigger cancel with confirmation
        viewModel.tryCancelPolygonEdit()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showDiscardEditDialog)
        
        viewModel.confirmDiscardEdit()
        advanceUntilIdle()
        
        // Verify exit
        assertEquals(MapEditingMode.Select, viewModel.uiState.value.editingMode)
        assertNull(viewModel.uiState.value.polygonEditState)
        assertEquals(FeatureEditorTarget.Persisted(featureId), viewModel.uiState.value.featureEditorTarget)
    }

    @Test
    fun `handling missing feature during geometry exit`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val featureId = UUID.randomUUID()
        val feature = MapFeatureEntity(
            id = featureId, propertyId = testPropertyId, planId = testPlanId, layerId = testLayerId,
            geometryType = "LINESTRING", geometryJson = "{\"type\":\"LineString\",\"coordinates\":[[0,0],[1,1]]}",
            coordinateSpace = "G", styleJson = "{}", accuracySource = "M"
        )
        
        every { mapRepo.getFeaturesForLayer(testLayerId) } returns flowOf(listOf(feature))
        
        viewModel.selectPersistedFeature(feature)
        advanceUntilIdle()
        
        viewModel.beginPersistedShapeEdit(featureId)
        advanceUntilIdle()

        // Modify to trigger actual save path
        viewModel.updateVertexPosition(0, 0.1, 0.1)
        advanceUntilIdle()

        // Feature disappears from repo
        coEvery { mapRepo.getFeatureById(featureId) } returns null
        
        viewModel.saveLineEdit()
        advanceUntilIdle()
        
        // Should exit cleanly to Select mode with NO active target
        assertEquals(MapEditingMode.Select, viewModel.uiState.value.editingMode)
        assertNull(viewModel.uiState.value.featureEditorTarget)
        assertFalse(viewModel.uiState.value.featureEditorOpen)
    }
}
