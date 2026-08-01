package com.jumastappworks.mapstead.ui.mapping

import androidx.lifecycle.SavedStateHandle
import com.jumastappworks.mapstead.data.attachments.AttachmentDeleteState
import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelCorrectiveTest {

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
    
    private val featuresFlow = MutableStateFlow<List<MapFeatureEntity>>(emptyList())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { basemapProvider.defaultBasemap() } returns BasemapId.STREETS
        every { userPrefs.userPreferencesFlow } returns flowOf(UserPreferences(selectedBasemapId = BasemapId.STREETS, measurementSystem = MeasurementSystem.IMPERIAL))
        
        val layer = LayerEntity(id = testLayerId, propertyId = testPropertyId, planId = testPlanId, name = "L", category = "C")
        val plan = PlanEntity(id = testPlanId, propertyId = testPropertyId, name = "P", planType = "M", backgroundType = "M", centerLatitude = 45.0, centerLongitude = -90.0, zoom = 15.0)
        val property = PropertyEntity(id = testPropertyId, name = "Property", propertyType = "T", latitude = 0.0, longitude = 0.0)
        
        every { mapRepo.getLayersForPlan(any()) } returns flowOf(listOf(layer))
        every { mapRepo.getFeaturesForLayer(any()) } returns featuresFlow
        every { propRepo.getAllProperties() } returns flowOf(listOf(property))
        every { mapRepo.getPlansForProperty(testPropertyId) } returns flowOf(listOf(plan))
        coEvery { mapRepo.getPlanById(testPlanId) } returns plan
        
        viewModel = MapViewModel(mapRepo, attachmentRepo, infraRepo, propRepo, resolver, locationProvider, basemapProvider, userPrefs, namingService, context, savedStateHandle)
        
        viewModel.setProperty(testPropertyId)
        viewModel.setActiveLayer(testLayerId)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial MapLibre default camera is not persisted`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.selectPlan(testPlanId)
        advanceUntilIdle()
        clearMocks(mapRepo, answers = false) 
        
        viewModel.onCameraMoved(0.0, 0.0, 0.0, 0.0)
        advanceUntilIdle()
        coVerify(exactly = 0) { mapRepo.updatePlanCamera(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `camera persistence is unarmed before initial focus applied`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.selectPlan(testPlanId)
        advanceUntilIdle()
        clearMocks(mapRepo, answers = false)
        
        viewModel.onCameraMoved(45.0, -90.0, 15.0, 0.0)
        advanceUntilIdle()
        coVerify(exactly = 0) { mapRepo.updatePlanCamera(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `customer pan after arming is persisted`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.selectPlan(testPlanId)
        advanceUntilIdle()
        val focus = viewModel.uiState.value.cameraFocus!!
        viewModel.acknowledgeCameraFocusApplied(testPropertyId!!, testPlanId!!, null, focus)
        advanceUntilIdle()
        
        viewModel.onCameraMoved(39.9, -98.6, 4.0, 0.0)
        advanceUntilIdle()
        
        coVerify(exactly = 1) { mapRepo.updatePlanCamera(testPlanId, 39.9, -98.6, 4.0, 0.0) }
    }

    @Test
    fun `opening persisted line and tapping Edit Shape enters line-edit mode`() = runTest {
        viewModel.selectPlan(testPlanId)
        val featureId = UUID.randomUUID()
        val feature = MapFeatureEntity(
            id = featureId, propertyId = testPropertyId, planId = testPlanId, layerId = testLayerId,
            geometryType = "LINESTRING", geometryJson = "{\"type\":\"LineString\",\"coordinates\":[[0,0],[1,1]]}",
            coordinateSpace = "G", styleJson = "{}", accuracySource = "M"
        )
        coEvery { mapRepo.getFeatureById(featureId) } returns feature
        featuresFlow.value = listOf(feature)
        
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.beginPersistedShapeEdit(featureId)
        advanceUntilIdle()
        
        assertEquals(MapEditingMode.EditLine, viewModel.uiState.value.editingMode)
    }

    @Test
    fun `existing feature link survives geometry save`() = runTest {
        viewModel.selectPlan(testPlanId)
        val infraId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        val feature = MapFeatureEntity(
            id = featureId, propertyId = testPropertyId, planId = testPlanId, layerId = testLayerId,
            infrastructureItemId = infraId, geometryType = "LINESTRING", geometryJson = "{\"type\":\"LineString\",\"coordinates\":[[0,0],[1,1]]}",
            coordinateSpace = "G", styleJson = "{}", accuracySource = "M"
        )
        coEvery { mapRepo.getFeatureById(featureId) } returns feature
        featuresFlow.value = listOf(feature)
        
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.selectFeatureById(featureId)
        advanceUntilIdle()
        
        coEvery { mapRepo.updateFeature(any()) } returns Unit

        viewModel.beginPersistedShapeEdit(featureId)
        advanceUntilIdle()
        
        viewModel.updateVertexPosition(0, 0.5, 0.5)
        advanceUntilIdle()
        
        viewModel.saveLineEdit()
        advanceUntilIdle()
        
        coVerify(atLeast = 1) { mapRepo.updateFeature(match { it.id == featureId && it.infrastructureItemId == infraId }) }
    }

    @Test
    fun `repair of default-like camera works automatically`() = runTest {
        mockkObject(MapCameraResolver)
        every { MapCameraResolver.resolveInitialCamera(any(), any(), any()) } returns CameraResolution(MapCameraFocus.Point(10.0, 20.0), CameraSource.REPAIRED_DEFAULT_CAMERA)
        
        viewModel.setProperty(testPropertyId!!)
        viewModel.selectPlan(testPlanId!!)
        advanceUntilIdle()
        val focus = viewModel.uiState.value.cameraFocus!!
        viewModel.acknowledgeCameraFocusApplied(testPropertyId!!, testPlanId!!, null, focus)
        advanceUntilIdle()
        
        viewModel.onCameraMoved(1.0, 2.0, 15.0, 0.0)
        advanceUntilIdle()
        
        coVerify { mapRepo.updatePlanCamera(testPlanId!!, any(), any(), any(), any()) }
        unmockkObject(MapCameraResolver)
    }

    @Test
    fun `selectPersistedFeature null fully clears session`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.selectPlan(testPlanId)
        val featureId = UUID.randomUUID()
        val feature = MapFeatureEntity(id = featureId, propertyId = testPropertyId, planId = testPlanId, layerId = testLayerId, geometryType = "POINT", geometryJson = "{}", coordinateSpace = "G", styleJson = "{}", accuracySource = "M")
        
        viewModel.selectPersistedFeature(feature)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.featureEditorFeature)
        
        viewModel.selectPersistedFeature(null)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.featureEditorFeature)
        assertNull(viewModel.uiState.value.selectedFeature)
    }

    @Test
    fun `opening unchanged linked feature is not dirty`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.selectPlan(testPlanId)
        val featureId = UUID.randomUUID()
        val feature = MapFeatureEntity(id = featureId, propertyId = testPropertyId, planId = testPlanId, layerId = testLayerId, geometryType = "POINT", geometryJson = "{}", coordinateSpace = "G", styleJson = "{}", accuracySource = "M")
        
        viewModel.selectPersistedFeature(feature)
        advanceUntilIdle()
        
        assertFalse("Unchanged feature should not be dirty", viewModel.uiState.value.isEditorDirty)
    }

    @Test
    fun `explicitly unlinking a feature is dirty`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.selectPlan(testPlanId)
        val featureId = UUID.randomUUID()
        val feature = MapFeatureEntity(id = featureId, propertyId = testPropertyId, planId = testPlanId, layerId = testLayerId, infrastructureItemId = UUID.randomUUID(), geometryType = "POINT", geometryJson = "{}", coordinateSpace = "G", styleJson = "{}", accuracySource = "M")
        
        viewModel.selectPersistedFeature(feature)
        advanceUntilIdle()
        
        viewModel.setLinkSelection(SystemItemLinkSelection.None)
        advanceUntilIdle()
        
        assertTrue("Explicit unlink should be dirty", viewModel.uiState.value.isEditorDirty)
    }

    @Test
    fun `invalid PendingDraft selection is rejected`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.selectPlan(testPlanId)
        val featureId = UUID.randomUUID()
        val feature = MapFeatureEntity(id = featureId, propertyId = testPropertyId, planId = testPlanId, layerId = testLayerId, geometryType = "POINT", geometryJson = "{}", coordinateSpace = "G", styleJson = "{}", accuracySource = "M")
        
        viewModel.selectPersistedFeature(feature)
        advanceUntilIdle()
        
        val randomId = UUID.randomUUID()
        viewModel.setLinkSelection(SystemItemLinkSelection.PendingDraft(randomId))
        advanceUntilIdle()
        
        assertNotEquals(SystemItemLinkSelection.PendingDraft(randomId), viewModel.uiState.value.linkSelection)
    }

    @Test
    fun `selecting No linked item prevents suggested creation`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.selectPlan(testPlanId)
        viewModel.setActiveLayer(testLayerId)
        advanceUntilIdle()
        val preset = GuidedMapPresets.LOCATIONS.first { it.systemItemPolicy == SystemItemPolicy.AUTOMATIC }
        viewModel.setGuidedPreset(preset)
        viewModel.selectGuidedLocationMethod(PlacementMethod.TAP_MAP)
        advanceUntilIdle()
        
        viewModel.addPointAt(0.0, 0.0)
        advanceUntilIdle()
        
        assertEquals(SystemItemLinkSelection.CreateSuggested, viewModel.uiState.value.linkSelection)
        
        viewModel.setLinkSelection(SystemItemLinkSelection.None)
        advanceUntilIdle()
        
        val featureToSave = viewModel.uiState.value.featureEditorFeature!!.copy(label = "Test")
        coEvery { mapRepo.saveFeatureWithOptionalItem(any(), any()) } returns Unit
        
        viewModel.saveFeature(featureToSave)
        advanceUntilIdle()
        
        coVerify { mapRepo.saveFeatureWithOptionalItem(match { it.infrastructureItemId == null }, null) }
    }
}
