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
import io.mockk.every
import io.mockk.coEvery
import io.mockk.mockk
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
class MapViewModelLocationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mapRepository = mockk<MapRepository>(relaxed = true)
    private val attachmentRepository = mockk<AttachmentRepository>(relaxed = true)
    private val infrastructureRepository = mockk<InfrastructureRepository>(relaxed = true)
    private val propRepo = mockk<PropertyRepository>(relaxed = true)
    private val resolver = mockk<MapFeatureContextResolver>(relaxed = true)
    private val basemapProvider = mockk<BasemapProvider>(relaxed = true)
    private val userPrefs = mockk<UserPreferencesRepository>(relaxed = true)
    private val namingService = mockk<com.jumastappworks.mapstead.data.mapping.FeatureNamingService>(relaxed = true)
    private val context = mockk<android.content.Context>(relaxed = true)
    private val savedStateHandle = SavedStateHandle()
    private val fakeLocationProvider = FakeCurrentLocationProvider()

    private lateinit var viewModel: MapViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { basemapProvider.defaultBasemap() } returns BasemapId.STREETS
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
        
        viewModel = MapViewModel(mapRepository, attachmentRepository, infrastructureRepository, propRepo, resolver, fakeLocationProvider, basemapProvider, userPrefs, namingService, context, savedStateHandle)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.setupContext(propId: UUID, planId: UUID, layers: List<LayerEntity> = emptyList()) {
        val plan = PlanEntity(id = planId, propertyId = propId, name = "P", planType = "M", backgroundType = "M")
        val property = PropertyEntity(id = propId, name = "Prop", propertyType = "Home")
        every { mapRepository.getPlansForProperty(propId) } returns flowOf(listOf(plan))
        coEvery { mapRepository.getPlanById(planId) } returns plan
        coEvery { propRepo.getPropertyById(propId) } returns property
        every { propRepo.getAllProperties() } returns flowOf(listOf(property))

        every { mapRepository.getLayersForPlan(planId) } returns flowOf(layers)
        every { mapRepository.getFeaturesForPlan(planId) } returns flowOf(emptyList())

        viewModel.setProperty(propId)
        viewModel.selectPlan(planId)
        advanceUntilIdle()
    }

    @Test
    fun testSuccessfulLocationRequest() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        val success = LocationResult.Success(45.0, -110.0, 5f, System.currentTimeMillis(), LocationResult.Success.Source.Fresh, true)
        fakeLocationProvider.result = success
        
        viewModel.requestLocation(LocationRequestPurpose.LocateOnly)
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals(success, viewModel.uiState.value.currentPhoneLocation)
        assertNull(viewModel.uiState.value.locationIssue)
    }

    @Test
    fun testPermissionDeniedShowsIssue() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        fakeLocationProvider.result = LocationResult.PermissionDenied
        
        viewModel.requestLocation(LocationRequestPurpose.LocateOnly)
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertNotNull(viewModel.uiState.value.locationIssue)
        assertEquals(LocationIssueType.PermissionDenied, viewModel.uiState.value.locationIssue?.type)
    }

    @Test
    fun `cached and poor accuracy shows issue with anyway option`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        val oldTimestamp = System.currentTimeMillis() - 600000 // 10 mins old
        val success = LocationResult.Success(45.0, -110.0, 20f, oldTimestamp, LocationResult.Success.Source.LastKnown, true)
        fakeLocationProvider.result = success
        
        viewModel.requestLocation(LocationRequestPurpose.CreatePoint)
        advanceUntilIdle()
        
        val issue = viewModel.uiState.value.locationIssue
        assertNotNull(issue)
        assertEquals(LocationIssueType.CachedAndPoorAccuracy, issue?.type)
        assertTrue(issue?.canUseAnyway == true)
    }

    @Test
    fun `completeLocationAction sets creating point draft`() = runTest {
        val success = LocationResult.Success(45.0, -110.0, 5f, System.currentTimeMillis(), LocationResult.Success.Source.Fresh, true)
        val layerId = UUID.randomUUID()
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C", isVisible = true, isLocked = false)
        
        setupContext(propId, planId, listOf(layer))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        
        viewModel.setActiveLayer(layerId)
        advanceUntilIdle()
        
        viewModel.completeLocationAction(success, LocationRequestPurpose.CreatePoint)
        
        val state = viewModel.uiState.first { it.featureEditorTarget is FeatureEditorTarget.NewPoint }
        assertTrue(state.featureEditorOpen)
        assertTrue(state.featureEditorTarget is FeatureEditorTarget.NewPoint)
    }
}

class FakeCurrentLocationProvider : CurrentLocationProvider {
    var result: LocationResult = LocationResult.Error("Not set")
    override suspend fun getCurrentLocation(): LocationResult = result
}
