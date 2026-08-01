package com.jumastappworks.mapstead.ui.mapping

import com.jumastappworks.mapstead.data.db.entities.PlanEntity
import com.jumastappworks.mapstead.data.repository.MapRepository
import com.jumastappworks.mapstead.data.repository.PropertyRepository
import com.jumastappworks.mapstead.data.repository.AttachmentRepository
import com.jumastappworks.mapstead.data.mapping.CurrentLocationProvider
import com.jumastappworks.mapstead.data.mapping.MapFeatureContextResolver
import com.jumastappworks.mapstead.data.mapping.BasemapProvider
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import com.jumastappworks.mapstead.data.mapping.FeatureNamingService
import androidx.lifecycle.SavedStateHandle
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class MapFocusRegressionTest {

    private val mapRepo = mockk<MapRepository>(relaxed = true)
    private val propRepo = mockk<PropertyRepository>(relaxed = true)
    private val attachmentRepo = mockk<AttachmentRepository>(relaxed = true)
    private val locProvider = mockk<CurrentLocationProvider>(relaxed = true)
    private val basemapProvider = mockk<BasemapProvider>(relaxed = true)
    private val userPrefsRepo = mockk<UserPreferencesRepository>(relaxed = true)
    private val featureNamingService = mockk<FeatureNamingService>(relaxed = true)
    private val context = mockk<android.content.Context>(relaxed = true)
    private val mapFeatureContextResolver = mockk<MapFeatureContextResolver>(relaxed = true)

    private lateinit var viewModel: MapViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { userPrefsRepo.userPreferencesFlow } returns flowOf(mockk(relaxed = true))
        every { mapRepo.getLayersForPlan(any()) } returns flowOf(emptyList())
        every { mapRepo.getFeaturesForLayer(any()) } returns flowOf(emptyList())
        every { mapRepo.getFeaturesForPlan(any()) } returns flowOf(emptyList())
        
        viewModel = MapViewModel(
            mapRepo, attachmentRepo, mockk(relaxed = true), propRepo,
            mapFeatureContextResolver, locProvider, basemapProvider,
            userPrefsRepo, featureNamingService, context, SavedStateHandle()
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `openMapContext with same plan ID still resolves camera when token is unique`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val plan = PlanEntity(id = planId, propertyId = propId, name = "Map", planType = "M", backgroundType = "M", centerLatitude = 10.0, centerLongitude = 20.0, zoom = 15.0)
        coEvery { mapRepo.getPlanById(planId) } returns plan
        
        viewModel.openMapContext(propId, planId, "token1")
        advanceUntilIdle()
        val focus1 = viewModel.uiState.value.cameraFocus!!
        assertNotNull(focus1)
        
        viewModel.acknowledgeCameraFocusApplied(propId, planId, "token1", focus1)
        
        viewModel.openMapContext(propId, planId, "token2")
        advanceUntilIdle()
        assertNotNull("Camera focus should be resolved again for new opening token", viewModel.uiState.value.cameraFocus)
    }

    @Test
    fun `stale camera resolution results are ignored`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val plan = PlanEntity(id = planId, propertyId = propId, name = "Map", planType = "M", backgroundType = "M", centerLatitude = 10.0, centerLongitude = 20.0, zoom = 15.0)
        
        val deferredPlan = CompletableDeferred<PlanEntity?>()
        coEvery { mapRepo.getPlanById(planId) } coAnswers { deferredPlan.await() }
        
        viewModel.openMapContext(propId, planId, "token1")
        
        viewModel.openMapContext(propId, planId, "token2")
        
        deferredPlan.complete(plan)
        advanceUntilIdle()
        
        assertEquals("token2", viewModel.uiState.value.openingToken)
    }

    @Test
    fun `world camera is not persisted`() = runTest {
        val planId = UUID.randomUUID()
        viewModel.selectPlan(planId)
        viewModel.onCameraMoved(0.0, 0.0, 0.0, 0.0) // World view
        
        coVerify(exactly = 0) { mapRepo.updatePlanCamera(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `successful focus acknowledgement arms persistence`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val token = "token123"
        coEvery { mapRepo.getPlanById(planId) } returns PlanEntity(id = planId, propertyId = propId, name = "P", planType = "M", backgroundType = "M")
        
        viewModel.openMapContext(propId, planId, token)
        advanceUntilIdle()
        val focus = viewModel.uiState.value.cameraFocus!!
        
        viewModel.acknowledgeCameraFocusApplied(propId, planId, token, focus)
        advanceUntilIdle()
        
        viewModel.onCameraMoved(1.0, 2.0, 15.0, 0.0)
        advanceUntilIdle()
        coVerify(atLeast = 1) { mapRepo.updatePlanCamera(planId, any(), any(), any(), any()) }
    }

    @Test
    fun `failed focus remaining pending does not arm persistence`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val token = "token123"
        coEvery { mapRepo.getPlanById(planId) } returns PlanEntity(id = planId, propertyId = propId, name = "P", planType = "M", backgroundType = "M")
        
        viewModel.openMapContext(propId, planId, token)
        advanceUntilIdle()
        
        viewModel.onCameraMoved(1.0, 2.0, 15.0, 0.0)
        advanceUntilIdle()
        
        coVerify(exactly = 0) { mapRepo.updatePlanCamera(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `stale opening token does not clear current focus`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        
        viewModel.openMapContext(propId, planId, "token_new")
        advanceUntilIdle()
        val focus = viewModel.uiState.value.cameraFocus!!
        assertNotNull(focus)
        
        viewModel.acknowledgeCameraFocusApplied(propId, planId, "token_STALE", focus)
        advanceUntilIdle()
        
        assertNotNull("Focus should not be cleared by stale token acknowledgement", viewModel.uiState.value.cameraFocus)
    }

    @Test
    fun `successful retry clears the correct focus`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val token = "token123"
        coEvery { mapRepo.getPlanById(planId) } returns PlanEntity(id = planId, propertyId = propId, name = "P", planType = "M", backgroundType = "M")
        
        viewModel.openMapContext(propId, planId, token)
        advanceUntilIdle()
        val focus = viewModel.uiState.value.cameraFocus!!
        assertNotNull(focus)
        
        viewModel.acknowledgeCameraFocusApplied(propId, planId, token, focus)
        advanceUntilIdle()
        
        assertNull("Focus should be cleared after successful retry/acknowledgement", viewModel.uiState.value.cameraFocus)
    }
}
