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
class PointMoveDragTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mapRepo = mockk<MapRepository>(relaxed = true)
    private val attachmentRepo = mockk<AttachmentRepository>(relaxed = true)
    private val infraRepo = mockk<InfrastructureRepository>(relaxed = true)
    private val propRepo = mockk<PropertyRepository>(relaxed = true)
    private val resolver = mockk<MapFeatureContextResolver>(relaxed = true)
    private val locationProvider = mockk<CurrentLocationProvider>(relaxed = true)
    private val basemapProvider = mockk<BasemapProvider>(relaxed = true)
    private val userPrefs = mockk<UserPreferencesRepository>(relaxed = true)
    private val namingService = mockk<FeatureNamingService>(relaxed = true)
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
    fun `point drag proposal updates state continuously`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        
        val featureId = UUID.randomUUID()
        val feature = MapFeatureEntity(
            id = featureId, propertyId = testPropertyId, planId = testPlanId, layerId = testLayerId,
            geometryType = "POINT", geometryJson = "{\"type\":\"Point\",\"coordinates\":[0,0]}",
            coordinateSpace = "G", styleJson = "{}", accuracySource = "M"
        )
        every { mapRepo.getFeaturesForLayer(testLayerId) } returns flowOf(listOf(feature))
        advanceUntilIdle()
        
        viewModel.beginMovePoint(featureId)
        advanceUntilIdle()
        
        // Initial state should be original location
        assertNotNull(viewModel.uiState.value.pointMoveState)
        assertFalse("Save should be disabled before move", viewModel.uiState.value.canSavePointMove)
        
        viewModel.proposePointMove(10.0, 10.0, isDragging = true)
        advanceUntilIdle()
        assertEquals(10.0, viewModel.uiState.value.pointMoveState?.proposedLongitude)
        assertEquals(10.0, viewModel.uiState.value.pointMoveState?.proposedLatitude)
        assertTrue("isDragging should be true", viewModel.uiState.value.pointMoveState?.isDragging ?: false)
        assertTrue("Save should be enabled after move", viewModel.uiState.value.canSavePointMove)
        
        viewModel.proposePointMove(20.0, 20.0, isDragging = true)
        advanceUntilIdle()
        assertEquals(20.0, viewModel.uiState.value.pointMoveState?.proposedLongitude)
        assertEquals(20.0, viewModel.uiState.value.pointMoveState?.proposedLatitude)
        
        viewModel.finishPointMoveDrag()
        advanceUntilIdle()
        assertFalse("isDragging should be false after finish", viewModel.uiState.value.pointMoveState?.isDragging ?: true)
    }

    @Test
    fun `returning to original location disables save`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        
        val featureId = UUID.randomUUID()
        val feature = MapFeatureEntity(
            id = featureId, propertyId = testPropertyId, planId = testPlanId, layerId = testLayerId,
            geometryType = "POINT", geometryJson = "{\"type\":\"Point\",\"coordinates\":[0,0]}",
            coordinateSpace = "G", styleJson = "{}", accuracySource = "M"
        )
        every { mapRepo.getFeaturesForLayer(testLayerId) } returns flowOf(listOf(feature))
        advanceUntilIdle()
        
        viewModel.beginMovePoint(featureId)
        advanceUntilIdle()
        
        viewModel.proposePointMove(1.0, 1.0)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canSavePointMove)
        
        // Return to original
        viewModel.proposePointMove(0.0, 0.0)
        advanceUntilIdle()
        assertFalse("Save should be disabled at original location", viewModel.uiState.value.canSavePointMove)
    }

    @Test
    fun `point tap proposal updates state`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        
        val featureId = UUID.randomUUID()
        val feature = MapFeatureEntity(
            id = featureId, propertyId = testPropertyId, planId = testPlanId, layerId = testLayerId,
            geometryType = "POINT", geometryJson = "{\"type\":\"Point\",\"coordinates\":[0,0]}",
            coordinateSpace = "G", styleJson = "{}", accuracySource = "M"
        )
        every { mapRepo.getFeaturesForLayer(testLayerId) } returns flowOf(listOf(feature))
        advanceUntilIdle()
        
        viewModel.beginMovePoint(featureId)
        advanceUntilIdle()
        
        // Simulating map tap
        viewModel.proposePointMove(-122.0, 45.0)
        advanceUntilIdle()
        
        assertEquals(-122.0, viewModel.uiState.value.pointMoveState?.proposedLongitude)
        assertEquals(45.0, viewModel.uiState.value.pointMoveState?.proposedLatitude)
        assertTrue("Save should be enabled after tap", viewModel.uiState.value.canSavePointMove)
    }

    @Test
    fun `point cancel restores original location`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        
        val featureId = UUID.randomUUID()
        val feature = MapFeatureEntity(
            id = featureId, propertyId = testPropertyId, planId = testPlanId, layerId = testLayerId,
            geometryType = "POINT", geometryJson = "{\"type\":\"Point\",\"coordinates\":[0,0]}",
            coordinateSpace = "G", styleJson = "{}", accuracySource = "M"
        )
        coEvery { mapRepo.getFeatureById(featureId) } returns feature
        every { mapRepo.getFeaturesForLayer(testLayerId) } returns flowOf(listOf(feature))
        advanceUntilIdle()
        
        viewModel.beginMovePoint(featureId)
        advanceUntilIdle()
        
        viewModel.proposePointMove(99.0, 99.0)
        advanceUntilIdle()
        
        viewModel.cancelPointMove()
        advanceUntilIdle()
        
        assertNull("Move state must be cleared", viewModel.uiState.value.pointMoveState)
        coVerify(exactly = 0) { mapRepo.updateFeature(any()) }
    }

    @Test
    fun `point save persists the proposed location`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        
        val featureId = UUID.randomUUID()
        val feature = MapFeatureEntity(
            id = featureId, propertyId = testPropertyId, planId = testPlanId, layerId = testLayerId,
            geometryType = "POINT", geometryJson = "{\"type\":\"Point\",\"coordinates\":[0,0]}",
            coordinateSpace = "G", styleJson = "{}", accuracySource = "M"
        )
        coEvery { mapRepo.getFeatureById(featureId) } returns feature
        every { mapRepo.getFeaturesForLayer(testLayerId) } returns flowOf(listOf(feature))
        advanceUntilIdle()
        
        viewModel.beginMovePoint(featureId)
        advanceUntilIdle()
        
        viewModel.proposePointMove(-100.0, 40.0)
        advanceUntilIdle()
        
        viewModel.confirmPointMove()
        advanceUntilIdle()
        
        coVerify { mapRepo.updateFeature(match { 
            it.id == featureId && it.geometryJson.contains("-100.0") && it.geometryJson.contains("40.0") 
        }) }
    }
}
