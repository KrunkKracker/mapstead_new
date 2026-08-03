package com.jumastappworks.mapstead.ui.mapping

import androidx.lifecycle.SavedStateHandle
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
    private val userPrefsFlow = MutableStateFlow(UserPreferences())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { userPrefsRepo.userPreferencesFlow } returns userPrefsFlow
        
        every { mapRepo.getLayersForPlan(any()) } returns flowOf(emptyList())
        every { attachmentRepo.getAttachmentsForMapFeature(any(), any()) } returns flowOf(emptyList())
        every { infraRepo.observeActiveItem(any(), any()) } returns flowOf(null)
        
        viewModel = MapViewModel(mapRepo, attachmentRepo, infraRepo, propRepo, resolver, locationProvider, basemapProvider, userPrefsRepo, namingService, context, savedState)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `selecting existing feature lands on details with beginner labels`() = runTest {
        val propId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        val feature = MapFeatureEntity(
            id = featureId, propertyId = propId, planId = UUID.randomUUID(), layerId = UUID.randomUUID(),
            geometryType = "LINESTRING", geometryJson = "{\"type\":\"LineString\",\"coordinates\":[[0,0],[1,1]]}",
            coordinateSpace = "GEOGRAPHIC", styleJson = "{}", accuracySource = "MANUAL", label = "Test Route"
        )
        
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.setProperty(propId)
        viewModel.selectPersistedFeature(feature)
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertTrue(state.featureEditorOpen)
        assertFalse(state.isEditingFeature)
        assertFalse(state.isNewUnsavedFeature)
        
        val detail = state.featureDetailState as? FeatureDetailUiState.Ready
        assertNotNull("FeatureDetailState should not be null", detail)
        assertEquals("Drawn Route", detail?.geometryLabel)
    }

    @Test
    fun `tapping Edit transitions from details to editor`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        val feature = MapFeatureEntity(id = UUID.randomUUID(), propertyId = UUID.randomUUID(), planId = UUID.randomUUID(), layerId = UUID.randomUUID(), geometryType = "POINT", geometryJson = "{\"type\":\"Point\",\"coordinates\":[0,0]}", coordinateSpace = "GEOGRAPHIC", styleJson = "{}", accuracySource = "MANUAL", label = "Test Point")
        
        viewModel.selectPersistedFeature(feature)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isEditingFeature)
        
        viewModel.onEditFeatureClick()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isEditingFeature)
    }

    @Test
    fun `save existing feature returns to details`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        val propId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = UUID.randomUUID(), layerId = UUID.randomUUID(), geometryType = "POINT", geometryJson = "{\"type\":\"Point\",\"coordinates\":[0,0]}", coordinateSpace = "GEOGRAPHIC", styleJson = "{}", accuracySource = "MANUAL", label = "Test Point")
        
        coEvery { mapRepo.saveFeatureWithOptionalItem(any(), any()) } returns Unit
        
        viewModel.setProperty(propId)
        viewModel.selectPersistedFeature(feature)
        viewModel.onEditFeatureClick()
        advanceUntilIdle()
        
        viewModel.saveFeature(feature)
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.isEditingFeature)
        assertTrue(viewModel.uiState.value.featureEditorOpen)
    }

    @Test
    fun `delete failure shows error and preserves detail state`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = UUID.randomUUID(), geometryType = "POINT", geometryJson = "{\"type\":\"Point\",\"coordinates\":[0,0]}", coordinateSpace = "GEOGRAPHIC", styleJson = "{}", accuracySource = "MANUAL", label = "Test Point")
        
        coEvery { mapRepo.softDeleteFeatureWithAttachments(propId, planId, featureId) } returns AttachmentDeleteState.Error(com.jumastappworks.mapstead.R.string.error_delete_failed)
        
        viewModel.openMapContext(propId, planId, "token")
        viewModel.selectPersistedFeature(feature)
        advanceUntilIdle()
        
        viewModel.deleteFeature(featureId)
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.featureEditorOpen)
        assertNotNull(viewModel.uiState.value.deleteFeatureErrorRes)
    }

    @Test
    fun `linked infrastructure item is loaded property-scoped`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        val propId = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        val feature = MapFeatureEntity(id = UUID.randomUUID(), propertyId = propId, planId = UUID.randomUUID(), layerId = UUID.randomUUID(), geometryType = "POINT", geometryJson = "{\"type\":\"Point\",\"coordinates\":[0,0]}", coordinateSpace = "GEOGRAPHIC", styleJson = "{}", accuracySource = "MANUAL", label = "Test Point", infrastructureItemId = itemId)
        val item = InfrastructureItemEntity(id = itemId, propertyId = propId, name = "Linked Item", category = "Utility", status = "Active")
        
        every { infraRepo.observeActiveItem(propId, itemId) } returns flowOf(item)
        
        viewModel.setProperty(propId)
        viewModel.selectPersistedFeature(feature)
        advanceUntilIdle()
        
        val detail = viewModel.uiState.value.featureDetailState as? FeatureDetailUiState.Ready
        assertEquals("Linked Item", detail?.linkedItem?.name)
    }
}
