package com.jumastappworks.mapstead.ui.mapping

import androidx.lifecycle.SavedStateHandle
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.attachments.*
import com.jumastappworks.mapstead.data.db.entities.*
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.data.prefs.*
import com.jumastappworks.mapstead.data.repository.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MapFeatureDetailTest {

    private val mapRepo = mockk<MapRepository>(relaxed = true)
    private val attachmentRepo = mockk<AttachmentRepository>(relaxed = true)
    private val infraRepo = mockk<InfrastructureRepository>(relaxed = true)
    private val propRepo = mockk<PropertyRepository>(relaxed = true)
    private val resolver = mockk<MapFeatureContextResolver>(relaxed = true)
    private val locationProvider = mockk<CurrentLocationProvider>(relaxed = true)
    private val basemapProvider = mockk<BasemapProvider>(relaxed = true)
    private val userPrefsRepo = mockk<UserPreferencesRepository>(relaxed = true)
    private val namingService = mockk<FeatureNamingService>(relaxed = true)
    private val context = mockk<android.content.Context>(relaxed = true)
    private val savedState = SavedStateHandle()

    private val testDispatcher = StandardTestDispatcher()
    private val userPrefsFlow = MutableStateFlow(UserPreferences(measurementSystem = MeasurementSystem.IMPERIAL))

    private lateinit var viewModel: MapViewModel

    @Before
    fun setup() {
        mockkStatic(android.net.Uri::class)
        every { android.net.Uri.parse(any()) } returns mockk(relaxed = true)
        
        Dispatchers.setMain(testDispatcher)
        every { userPrefsRepo.userPreferencesFlow } returns userPrefsFlow
        
        every { mapRepo.getLayersForPlan(any()) } returns flowOf(emptyList())
        every { attachmentRepo.getAttachmentsForMapFeature(any(), any()) } returns flowOf(emptyList())
        every { infraRepo.observeActiveItem(any(), any()) } returns flowOf(null)
        every { propRepo.getAllProperties() } returns flowOf(emptyList())
        every { infraRepo.getItemsForProperty(any()) } returns flowOf(emptyList())
        
        viewModel = MapViewModel(mapRepo, attachmentRepo, infraRepo, propRepo, resolver, locationProvider, basemapProvider, userPrefsRepo, namingService, context, savedState)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `selecting existing feature lands on details with beginner labels`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.featureDetailState.collect {} }
        
        val propId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        val feature = MapFeatureEntity(
            id = featureId, propertyId = propId, planId = UUID.randomUUID(), layerId = UUID.randomUUID(),
            geometryType = "LINESTRING", geometryJson = "{\"type\":\"LineString\",\"coordinates\":[[0,0],[1,1]]}",
            coordinateSpace = "GEOGRAPHIC", styleJson = "{}", accuracySource = "MANUAL", label = "Test Route"
        )
        
        viewModel.setProperty(propId)
        viewModel.selectPersistedFeature(feature, requestCameraFocus = false)
        advanceUntilIdle()
        
        val ready = viewModel.featureDetailState.value as? FeatureDetailUiState.Ready
        assertNotNull("Expected Ready state", ready)
        assertEquals("Drawn Route", ready?.geometryLabel)
    }

    @Test
    fun `explicit retry trigger forces pipeline restart and recovers to Ready`() = runTest {
        val states = mutableListOf<FeatureDetailUiState?>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { 
            viewModel.featureDetailState.collect { states.add(it) } 
        }
        
        val propId = UUID.randomUUID()
        val feature = MapFeatureEntity(
            id = UUID.randomUUID(), propertyId = propId, planId = UUID.randomUUID(), layerId = UUID.randomUUID(),
            geometryType = "POINT", geometryJson = "{\"type\":\"Point\",\"coordinates\":[0,0]}",
            coordinateSpace = "GEOGRAPHIC", styleJson = "{}", accuracySource = "MANUAL", label = "Test Point",
            infrastructureItemId = UUID.randomUUID()
        )
        
        viewModel.setProperty(propId)
        viewModel.selectPersistedFeature(feature, requestCameraFocus = false)
        advanceUntilIdle()
        
        assertTrue("Baseline Ready state missing", states.any { it is FeatureDetailUiState.Ready })

        states.clear()
        val failFlow = flow<InfrastructureItemEntity?> { throw RuntimeException("Fail") }
        every { infraRepo.observeActiveItem(any(), any()) } returns failFlow
        viewModel.retryFeatureDetails()
        advanceUntilIdle()
        
        assertTrue("Error state missing after retry. States recorded: $states", states.any { it is FeatureDetailUiState.Error })
        
        states.clear()
        every { infraRepo.observeActiveItem(any(), any()) } returns flowOf(null)
        viewModel.retryFeatureDetails()
        advanceUntilIdle()
        
        assertTrue("Ready state missing after recovery", states.any { it is FeatureDetailUiState.Ready })
        job.cancel()
    }

    @Test
    fun `viewport is restored after MapView recreation`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val token = "token"
        val restoration = CameraRestorationRequest(planId, 45.0, -90.0, 15.0, 0.0)
        
        val plan = PlanEntity(id = planId, propertyId = propId, name = "Plan", planType = "M", backgroundType = "M")
        coEvery { mapRepo.getPlanById(planId) } returns plan
        every { mapRepo.getPlansForProperty(propId) } returns flowOf(listOf(plan))
        coEvery { propRepo.getPropertyById(propId) } returns PropertyEntity(id = propId, name = "P", propertyType = "H")
        
        savedState["viewport_restoration"] = kotlinx.serialization.json.Json.encodeToString(CameraRestorationRequest.serializer(), restoration)
        runCurrent()

        viewModel.openMapContext(propId, planId, token)
        advanceUntilIdle()
        
        val initialFocus = viewModel.uiState.value.cameraFocus as MapCameraFocus.Point
        assertEquals(45.0, initialFocus.latitude, 0.001)
        
        viewModel.acknowledgeCameraFocusApplied(propId, planId, token, initialFocus)
        runCurrent()
        assertNull("Focus should be null after acknowledgement", viewModel.uiState.value.cameraFocus)
        
        viewModel.onMapReady(UUID.randomUUID())
        runCurrent()
        
        val restoredFocus = viewModel.uiState.value.cameraFocus as MapCameraFocus.Point
        assertEquals(45.0, restoredFocus.latitude, 0.001)
    }

    @Test
    fun `saved map-feature camera attachment triggers navigation event`() = runTest {
        val events = mutableListOf<MapEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(events) }
        
        val propId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        
        viewModel.setProperty(propId)
        viewModel.setPendingPhotoPurpose(PendingPhotoPurpose.SavedFeatureAttachment(featureId))
        viewModel.setInFlightCapture("content://test", "token")
        
        coEvery { attachmentRepo.inspectTempCameraCapture(any(), any()) } returns TempCameraCaptureInspectionResult.Ready
        
        viewModel.handleCameraResult(true)
        advanceUntilIdle()
        
        val event = events.filterIsInstance<MapEvent.NavigateToAttachmentEditor>().lastOrNull()
        assertNotNull("Expected NavigateToAttachmentEditor event in $events", event)
        assertEquals(featureId, event?.ownerId)
        job.cancel()
    }
}
