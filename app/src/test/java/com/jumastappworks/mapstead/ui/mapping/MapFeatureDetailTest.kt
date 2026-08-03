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

    private lateinit var viewModel: MapViewModel
    private val testDispatcher = UnconfinedTestDispatcher()
    private val userPrefsFlow = MutableStateFlow(UserPreferences(measurementSystem = MeasurementSystem.IMPERIAL))

    @Before
    fun setup() {
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
    }

    @Test
    fun `selecting existing feature lands on details with beginner labels`() = runTest {
        val job = backgroundScope.launch { viewModel.uiState.collect {} }
        val propId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        val feature = MapFeatureEntity(
            id = featureId, propertyId = propId, planId = UUID.randomUUID(), layerId = UUID.randomUUID(),
            geometryType = "LINESTRING", geometryJson = "{\"type\":\"LineString\",\"coordinates\":[[0,0],[1,1]]}",
            coordinateSpace = "GEOGRAPHIC", styleJson = "{}", accuracySource = "MANUAL", label = "Test Route"
        )
        
        viewModel.setProperty(propId)
        viewModel.selectPersistedFeature(feature)
        
        val readyState = viewModel.uiState.mapNotNull { it.featureDetailState }.filterIsInstance<FeatureDetailUiState.Ready>().first()
        
        assertEquals("Drawn Route", readyState.geometryLabel)
        assertFalse(viewModel.uiState.value.isEditingFeature)
        job.cancel()
    }

    @Test
    fun `point coordinates are derived safely`() = runTest {
        val job = backgroundScope.launch { viewModel.uiState.collect {} }
        val feature = MapFeatureEntity(
            id = UUID.randomUUID(), propertyId = UUID.randomUUID(), planId = UUID.randomUUID(), layerId = UUID.randomUUID(),
            geometryType = "POINT", geometryJson = "{\"type\":\"Point\",\"coordinates\":[-122.0, 37.0]}",
            coordinateSpace = "GEOGRAPHIC", styleJson = "{}", accuracySource = "MANUAL", label = "Test Point"
        )
        
        viewModel.setProperty(feature.propertyId)
        viewModel.selectPersistedFeature(feature)
        
        val readyState = viewModel.uiState.mapNotNull { it.featureDetailState }.filterIsInstance<FeatureDetailUiState.Ready>().first()
        assertEquals("37.000000, -122.000000", readyState.pointCoordinates)
        job.cancel()
    }

    @Test
    fun `accuracy source is normalized`() = runTest {
        val job = backgroundScope.launch { viewModel.uiState.collect {} }
        val feature = MapFeatureEntity(
            id = UUID.randomUUID(), propertyId = UUID.randomUUID(), planId = UUID.randomUUID(), layerId = UUID.randomUUID(),
            geometryType = "POINT", geometryJson = "{\"type\":\"Point\",\"coordinates\":[0,0]}",
            coordinateSpace = "GEOGRAPHIC", styleJson = "{}", accuracySource = "MANUAL", label = "Test Point"
        )
        
        viewModel.setProperty(feature.propertyId)
        viewModel.selectPersistedFeature(feature)
        
        val readyState = viewModel.uiState.mapNotNull { it.featureDetailState }.filterIsInstance<FeatureDetailUiState.Ready>().first()
        assertEquals(R.string.accuracy_source_user_estimated, readyState.accuracySummary.sourceRes)
        job.cancel()
    }

    @Test
    fun `linked record Unavailable state is used for missing items`() = runTest {
        val job = backgroundScope.launch { viewModel.uiState.collect {} }
        val propId = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        val feature = MapFeatureEntity(
            id = UUID.randomUUID(), propertyId = propId, planId = UUID.randomUUID(), layerId = UUID.randomUUID(),
            geometryType = "POINT", geometryJson = "{\"type\":\"Point\",\"coordinates\":[0,0]}",
            coordinateSpace = "GEOGRAPHIC", styleJson = "{}", accuracySource = "MANUAL", label = "Test Point",
            infrastructureItemId = itemId
        )
        
        every { infraRepo.observeActiveItem(propId, itemId) } returns flowOf(null)
        
        viewModel.setProperty(propId)
        viewModel.selectPersistedFeature(feature)
        
        val readyState = viewModel.uiState.mapNotNull { it.featureDetailState }.filterIsInstance<FeatureDetailUiState.Ready>().first()
        assertTrue(readyState.linkedRecord is LinkedRecordState.Unavailable)
        assertEquals(itemId, (readyState.linkedRecord as LinkedRecordState.Unavailable).itemId)
        job.cancel()
    }

    @Test
    fun `delete failure preserves selection and exposes error`() = runTest {
        val job = backgroundScope.launch { viewModel.uiState.collect {} }
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = UUID.randomUUID(), geometryType = "POINT", geometryJson = "{\"type\":\"Point\",\"coordinates\":[0,0]}", coordinateSpace = "GEOGRAPHIC", styleJson = "{}", accuracySource = "MANUAL", label = "Test Point")
        
        coEvery { mapRepo.softDeleteFeatureWithAttachments(propId, planId, featureId) } returns AttachmentDeleteState.Error(R.string.error_delete_failed)
        
        viewModel.openMapContext(propId, planId, "token")
        viewModel.selectPersistedFeature(feature)
        
        viewModel.deleteFeature(featureId)
        
        val state = viewModel.uiState.filter { it.deleteFeatureErrorRes != null }.first()
        assertEquals(R.string.error_delete_failed, state.deleteFeatureErrorRes)
        
        viewModel.clearDeleteFeatureError()
        val finalState = viewModel.uiState.filter { it.deleteFeatureErrorRes == null }.first()
        assertNull(finalState.deleteFeatureErrorRes)
        job.cancel()
    }
}
