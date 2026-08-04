package com.jumastappworks.mapstead.ui.mapping

import androidx.lifecycle.SavedStateHandle
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.attachments.*
import com.jumastappworks.mapstead.data.db.entities.*
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.data.prefs.*
import com.jumastappworks.mapstead.data.repository.*
import com.jumastappworks.mapstead.data.backup.TemporaryCameraCapture
import com.jumastappworks.mapstead.ui.infrastructure.*
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
    private lateinit var infraViewModel: InfrastructureItemDetailViewModel

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
        infraViewModel = InfrastructureItemDetailViewModel(infraRepo, propRepo, mapRepo, attachmentRepo, mockk(relaxed = true), mockk(relaxed = true))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `selecting existing feature lands on details with beginner labels`() = runTest {
        backgroundScope.launch { viewModel.featureDetailState.collect {} }
        
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
        val job = backgroundScope.launch { 
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
        
        assertTrue("Baseline Ready state missing. States: $states", states.any { it is FeatureDetailUiState.Ready })

        // 2. Change mock and retry
        states.clear()
        every { infraRepo.observeActiveItem(any(), any()) } returns flow { throw RuntimeException("Fail") }
        viewModel.retryFeatureDetails()
        advanceUntilIdle()
        
        assertTrue("Error state missing after retry. States recorded: $states", states.any { it is FeatureDetailUiState.Error })
        
        // 3. Fix dependency and Retry to recover
        states.clear()
        every { infraRepo.observeActiveItem(any(), any()) } returns flowOf(null)
        viewModel.retryFeatureDetails()
        advanceUntilIdle()
        
        assertTrue("Ready state missing after recovery. States recorded: $states", states.any { it is FeatureDetailUiState.Ready })
        job.cancel()
    }

    @Test
    fun `viewport is restored after MapView recreation`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val token = "token"
        val restoration = CameraRestorationRequest(planId, 45.0, -90.0, 15.0, 0.0)
        
        val plan = PlanEntity(id = planId, propertyId = propId, name = "Plan", planType = "M", backgroundType = "M")
        coEvery { mapRepo.getPlanById(planId) } returns plan
        every { mapRepo.getPlansForProperty(propId) } returns flowOf(listOf(plan))
        coEvery { propRepo.getPropertyById(propId) } returns PropertyEntity(id = propId, name = "P", propertyType = "H")
        
        // IMPORTANT: Set restoration in handle before triggering the load
        savedState["viewport_restoration"] = kotlinx.serialization.json.Json.encodeToString(CameraRestorationRequest.serializer(), restoration)
        
        // Initial load
        viewModel.openMapContext(propId, planId, token)
        advanceUntilIdle()
        
        val initialFocus = viewModel.uiState.value.cameraFocus as? MapCameraFocus.Point
        assertNotNull("Camera focus should be set from restoration", initialFocus)
        assertEquals(45.0, initialFocus?.latitude ?: 0.0, 0.001)
        
        viewModel.acknowledgeCameraFocusApplied(propId, planId, token, initialFocus!!)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.cameraFocus)
        
        // MapView recreation (onMapReady with new session)
        viewModel.onMapReady(UUID.randomUUID())
        advanceUntilIdle()
        
        val restoredFocus = viewModel.uiState.value.cameraFocus as? MapCameraFocus.Point
        assertNotNull("Camera focus should be restored on MapReady", restoredFocus)
        assertEquals(45.0, restoredFocus?.latitude ?: 0.0, 0.001)
    }

    @Test
    fun `saved map-feature camera attachment triggers navigation event`() = runTest {
        val events = mutableListOf<MapEvent>()
        val job = backgroundScope.launch { viewModel.events.collect { events.add(it) } }
        
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

    @Test
    fun `same property, plan, and token does not reset map state`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val token = "token123"
        val plan = PlanEntity(id = planId, propertyId = propId, name = "P", planType = "M", backgroundType = "M")
        
        coEvery { mapRepo.getPlanById(planId) } returns plan
        every { mapRepo.getPlansForProperty(propId) } returns flowOf(listOf(plan))
        
        viewModel.openMapContext(propId, planId, token)
        advanceUntilIdle()
        
        val initialSessionId = viewModel.uiState.value.renderSessionId
        
        // Call again with SAME params
        viewModel.openMapContext(propId, planId, token)
        advanceUntilIdle()
        
        assertEquals("Session should not have changed", initialSessionId, viewModel.uiState.value.renderSessionId)
    }

    @Test
    fun `different plan initializes normally`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        val propId = UUID.randomUUID()
        val planId1 = UUID.randomUUID()
        val planId2 = UUID.randomUUID()
        
        val plan1 = PlanEntity(id = planId1, propertyId = propId, name = "P1", planType = "M", backgroundType = "M")
        val plan2 = PlanEntity(id = planId2, propertyId = propId, name = "P2", planType = "M", backgroundType = "M")
        
        coEvery { mapRepo.getPlanById(planId1) } returns plan1
        coEvery { mapRepo.getPlanById(planId2) } returns plan2
        every { mapRepo.getPlansForProperty(propId) } returns flowOf(listOf(plan1, plan2))
        
        viewModel.openMapContext(propId, planId1, "token1")
        advanceUntilIdle()
        assertEquals(planId1, viewModel.uiState.value.plan?.id)
        
        viewModel.openMapContext(propId, planId2, "token2")
        advanceUntilIdle()
        assertEquals(planId2, viewModel.uiState.value.plan?.id)
    }

    @Test
    fun `default-world viewport is rejected for restoration`() = runTest {
        val planId = UUID.randomUUID()
        viewModel.selectPlan(planId)
        
        // Attempt to move to default world view
        viewModel.onCameraMoved(0.0, 0.0, 0.5, 0.0) 
        
        val stored = savedState.get<String>("viewport_restoration")
        assertNull("Default world view should not be stored for restoration", stored)
    }

    @Test
    fun `recenter uses valid property coordinates`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        val propId = UUID.randomUUID()
        val property = PropertyEntity(id = propId, name = "Prop", propertyType = "Home", latitude = 40.0, longitude = -80.0)
        
        every { propRepo.getAllProperties() } returns flowOf(listOf(property))
        viewModel.setProperty(propId)
        advanceUntilIdle()
        
        viewModel.onReturnToProperty()
        advanceUntilIdle()
        
        val focus = viewModel.uiState.value.cameraFocus as? MapCameraFocus.Point
        assertNotNull("Expected Point focus for recenter", focus)
        assertEquals(40.0, focus?.latitude ?: 0.0, 0.001)
        assertEquals(-80.0, focus?.longitude ?: 0.0, 0.001)
    }

    @Test
    fun `recenter does not use 0,0`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        val propId = UUID.randomUUID()
        val property = PropertyEntity(id = propId, name = "Prop", propertyType = "Home", latitude = null, longitude = null)
        
        every { propRepo.getAllProperties() } returns flowOf(listOf(property))
        viewModel.setProperty(propId)
        advanceUntilIdle()
        
        viewModel.onReturnToProperty()
        advanceUntilIdle()
        
        val focus = viewModel.uiState.value.cameraFocus as? MapCameraFocus.Point
        assertNotNull(focus)
        assertNotEquals(0.0, focus?.latitude ?: 0.0, 0.001)
        assertNotEquals(0.0, focus?.longitude ?: 0.0, 0.001)
    }

    @Test
    fun `guided feature creation still stages its photo`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.setPendingPhotoPurpose(PendingPhotoPurpose.GuidedFeatureCreation(UUID.randomUUID()))
        viewModel.setInFlightCapture("content://test", "token")
        
        coEvery { attachmentRepo.inspectTempCameraCapture(any(), any()) } returns TempCameraCaptureInspectionResult.Ready
        
        viewModel.handleCameraResult(true)
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.stagedPhoto is StagedCreationPhotoState.Ready)
        assertEquals("content://test", (viewModel.uiState.value.stagedPhoto as StagedCreationPhotoState.Ready).uri)
    }

    @Test
    fun `camera cancellation clears pending and temporary state`() = runTest {
        viewModel.setPendingPhotoPurpose(PendingPhotoPurpose.SavedFeatureAttachment(UUID.randomUUID()))
        viewModel.setInFlightCapture("content://temp", "token")
        
        viewModel.handleCameraResult(false)
        advanceUntilIdle()
        
        assertNull(viewModel.getInFlightUri())
        assertNull(savedState.get<String>("pending_photo_purpose"))
        verify { attachmentRepo.deleteTempCameraCapture("token") }
    }

    @Test
    fun `recenter preserves selected feature`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        val propId = UUID.randomUUID()
        val feature = MapFeatureEntity(
            id = UUID.randomUUID(), propertyId = propId, planId = UUID.randomUUID(), layerId = UUID.randomUUID(),
            geometryType = "POINT", geometryJson = "{}", coordinateSpace = "G", styleJson = "{}", accuracySource = "M", label = "F"
        )
        
        viewModel.setProperty(propId)
        viewModel.selectPersistedFeature(feature, requestCameraFocus = false)
        advanceUntilIdle()
        assertEquals(feature.id, viewModel.uiState.value.selectedFeature?.id)
        
        viewModel.onReturnToProperty()
        advanceUntilIdle()
        
        assertEquals("Feature selection should be preserved after recenter", feature.id, viewModel.uiState.value.selectedFeature?.id)
    }

    @Test
    fun `infrastructure camera capture delegate produces result`() = runTest {
        val capture = TemporaryCameraCapture(mockk(), "token")
        coEvery { attachmentRepo.createTempCameraUri() } returns Result.success(capture)
        
        val result = infraViewModel.createCameraCapture()
        assertEquals(capture, result)
    }

    @Test
    fun `infrastructure camera capture deletion delegate calls repository`() = runTest {
        infraViewModel.deleteCameraCapture("token")
        verify { attachmentRepo.deleteTempCameraCapture("token") }
    }
}
