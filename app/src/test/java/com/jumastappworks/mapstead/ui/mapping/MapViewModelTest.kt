package com.jumastappworks.mapstead.ui.mapping

import androidx.lifecycle.SavedStateHandle
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.attachments.AttachmentDeleteState
import com.jumastappworks.mapstead.data.db.entities.LayerEntity
import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.data.db.entities.PlanEntity
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.data.prefs.UserPreferences
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import com.jumastappworks.mapstead.data.repository.*
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    private lateinit var viewModel: MapViewModel
    private val testDispatcher = UnconfinedTestDispatcher()
    private val mapRepo = mockk<MapRepository>(relaxed = true)
    private val attachmentRepo = mockk<AttachmentRepository>(relaxed = true)
    private val infrastructureRepo = mockk<InfrastructureRepository>(relaxed = true)
    private val propertyRepo = mockk<PropertyRepository>(relaxed = true)
    private val contextResolver = mockk<MapFeatureContextResolver>(relaxed = true)
    private val locationProvider = mockk<CurrentLocationProvider>(relaxed = true)
    private val basemapProvider = mockk<BasemapProvider>(relaxed = true)
    private val userPrefsRepo = mockk<UserPreferencesRepository>(relaxed = true)
    private val namingService = mockk<FeatureNamingService>(relaxed = true)
    private val context = mockk<android.content.Context>(relaxed = true)
    private val savedStateHandle = SavedStateHandle()
    
    private val featuresFlow = MutableStateFlow<List<MapFeatureEntity>>(emptyList())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { userPrefsRepo.userPreferencesFlow } returns flowOf(UserPreferences())
        every { context.getString(any()) } returns "Mock String"
        every { mapRepo.getFeaturesForLayer(any()) } returns featuresFlow
        
        // Mock basemap provider for general tests
        every { basemapProvider.getDefaultBasemapId() } returns BasemapId.STREETS
        every { basemapProvider.getPrimaryBasemaps() } returns emptyList() // Force fallback or idle for general tests
        every { basemapProvider.resolveDefaultBackup(any()) } returns BasemapSourceId.OPEN_FREE_MAP_LIBERTY
        
        viewModel = MapViewModel(
            mapRepo, attachmentRepo, infrastructureRepo, propertyRepo,
            contextResolver, locationProvider, basemapProvider, userPrefsRepo,
            namingService, context, savedStateHandle
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun setupContext(propId: UUID, planId: UUID, layers: List<LayerEntity>) {
        val property = PropertyEntity(id = propId, name = "P", propertyType = "T", latitude = 0.0, longitude = 0.0)
        val plan = PlanEntity(id = planId, propertyId = propId, name = "Plan", planType = "T", backgroundType = "M", centerLatitude = 0.0, centerLongitude = 0.0, zoom = 15.0)
        every { propertyRepo.getAllProperties() } returns flowOf(listOf(property))
        coEvery { propertyRepo.getPropertyById(propId) } returns property
        every { mapRepo.getPlansForProperty(propId) } returns flowOf(listOf(plan))
        every { mapRepo.getLayersForPlan(planId) } returns flowOf(layers)
        coEvery { mapRepo.getPlanById(planId) } returns plan
        
        // Start collection and wait for it to process initial state
        val job = CoroutineScope(testDispatcher).launch { viewModel.uiState.collect {} }
        
        viewModel.setProperty(propId)
        viewModel.selectPlan(planId)
        if (layers.isNotEmpty()) viewModel.setActiveLayer(layers.first().id)
        
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Ensure flows have processed everything
        repeat(5) { testDispatcher.scheduler.runCurrent() }
        
        job.cancel()
    }

    @Test
    fun `initial state is correct`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertNull(state.propertyId)
        assertFalse(state.layerPanelOpen)
        assertEquals(MapEditingMode.Select, state.editingMode)
    }

    @Test
    fun `select feature updates state`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect() }
        val featureId = UUID.randomUUID()
        val feature = MapFeatureEntity(id = featureId, propertyId = UUID.randomUUID(), planId = UUID.randomUUID(), layerId = UUID.randomUUID(), geometryType = "POINT", geometryJson = "{}", coordinateSpace = "G", styleJson = "{}", accuracySource = "M")
        
        viewModel.selectPersistedFeature(feature)
        advanceUntilIdle()
        
        assertEquals(feature, viewModel.uiState.value.selectedFeature)
        assertTrue(viewModel.uiState.value.featureEditorOpen)
    }

    @Test
    fun `toggle layer panel updates state`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.setLayerPanelOpen(true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.layerPanelOpen)
        
        viewModel.setLayerPanelOpen(false)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.layerPanelOpen)
    }

    @Test
    fun `plan selection reloads layers`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layers = listOf(LayerEntity(id = UUID.randomUUID(), propertyId = propId, planId = planId, name = "L", category = "C"))
        
        setupContext(propId, planId, layers)
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
        
        assertEquals(layers, viewModel.uiState.value.layers)
    }

    @Test
    fun `drawing-workflow mutual exclusion works`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layer = LayerEntity(id = UUID.randomUUID(), propertyId = propId, planId = planId, name = "L", category = "C")
        setupContext(propId, planId, listOf(layer))
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.setActiveLayer(layer.id)
        advanceUntilIdle()
        
        assertTrue(viewModel.beginAddPoint())
        advanceUntilIdle()
        
        assertFalse("Cannot start another workflow while one is active", viewModel.beginAddLine())
    }

    @Test
    fun `discard action triggers confirmation dialog`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layer = LayerEntity(id = UUID.randomUUID(), propertyId = propId, planId = planId, name = "L", category = "C")
        setupContext(propId, planId, listOf(layer))
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.setActiveLayer(layer.id)
        advanceUntilIdle()

        viewModel.beginAddPoint()
        viewModel.addPointAt(0.0, 0.0)
        advanceUntilIdle()
        
        viewModel.dismissFeatureEditor()
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.showDiscardEditDialog)
    }

    @Test
    fun `delete feature success clears selection`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        setupContext(propId, planId, emptyList())
        backgroundScope.launch { viewModel.uiState.collect() }
        
        val featureId = UUID.randomUUID()
        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = UUID.randomUUID(), geometryType = "POINT", geometryJson = "{}", coordinateSpace = "G", styleJson = "{}", accuracySource = "M")
        
        viewModel.selectPersistedFeature(feature)
        advanceUntilIdle()
        
        coEvery { mapRepo.softDeleteFeatureWithAttachments(any(), any(), any()) } returns AttachmentDeleteState.Deleted
        viewModel.deleteFeature(featureId)
        advanceUntilIdle()
        
        assertNull(viewModel.uiState.value.selectedFeature)
        assertFalse(viewModel.uiState.value.featureEditorOpen)
    }

    @Test
    fun `add area workflow starts with active layer`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layer = LayerEntity(id = UUID.randomUUID(), propertyId = propId, planId = planId, name = "L", category = "C")
        setupContext(propId, planId, listOf(layer))
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.setActiveLayer(layer.id)
        advanceUntilIdle()
        
        assertTrue(viewModel.beginAddPolygon())
        advanceUntilIdle()
        assertEquals(MapEditingMode.AddPolygon, viewModel.uiState.value.editingMode)
    }

    @Test
    fun `persisted point move eligibility requires unlocked layer`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C", isLocked = true)
        
        val featureId = UUID.randomUUID()
        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = layerId, geometryType = "POINT", geometryJson = "{\"type\":\"Point\",\"coordinates\":[0,0]}", coordinateSpace = "G", styleJson = "{}", accuracySource = "M")
        
        // Use local flows
        val localLayersFlow = MutableStateFlow<List<LayerEntity>>(emptyList())
        val localFeaturesFlow = MutableStateFlow<List<MapFeatureEntity>>(emptyList())
        
        every { mapRepo.getLayersForPlan(planId) } returns localLayersFlow
        every { mapRepo.getFeaturesForLayer(layerId) } returns localFeaturesFlow
        
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.setProperty(propId)
        viewModel.selectPlan(planId)
        advanceUntilIdle()

        localLayersFlow.value = listOf(layer)
        advanceUntilIdle()
        
        viewModel.setActiveLayer(layerId)
        advanceUntilIdle()

        localFeaturesFlow.value = listOf(feature)
        advanceUntilIdle()

        // Wait for features to propagate to visible features
        var attempts = 0
        while ((viewModel.uiState.value.visibleFeatures.isEmpty() || viewModel.uiState.value.layers.isEmpty()) && attempts < 100) {
            testDispatcher.scheduler.runCurrent()
            advanceUntilIdle()
            attempts++
        }

        assertTrue("Layers must be populated", viewModel.uiState.value.layers.isNotEmpty())
        assertTrue("Feature should be visible", viewModel.uiState.value.visibleFeatures.any { it.id == featureId })

        viewModel.beginMovePoint(featureId)
        advanceUntilIdle()
        
        assertEquals(R.string.layer_is_locked, viewModel.uiState.value.featureOperationErrorRes)
        assertFalse(viewModel.uiState.value.isPointMoveActive)
    }

    @Test
    fun `keep editing preserves point move state`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C")
        setupContext(propId, planId, listOf(layer))
        backgroundScope.launch { viewModel.uiState.collect() }
        
        val featureId = UUID.randomUUID()
        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = layerId, geometryType = "POINT", geometryJson = "{\"type\":\"Point\",\"coordinates\":[0,0]}", coordinateSpace = "G", styleJson = "{}", accuracySource = "M")
        
        featuresFlow.value = listOf(feature)
        advanceUntilIdle()

        viewModel.beginMovePoint(featureId)
        viewModel.proposePointMove(1.0, 1.0)
        advanceUntilIdle()
        
        viewModel.dismissDiscardDialog()
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.isPointMoveActive)
        assertEquals(1.0, viewModel.uiState.value.pointMoveState!!.proposedLongitude!!, 1e-6)
    }

    @Test
    fun `context change disables save in line edit`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C")
        setupContext(propId, planId, listOf(layer))
        backgroundScope.launch { viewModel.uiState.collect() }
        
        val featureId = UUID.randomUUID()
        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = layerId, geometryType = "LINESTRING", geometryJson = "{\"type\":\"LineString\",\"coordinates\":[[0,0],[1,1]]}", coordinateSpace = "G", styleJson = "{}", accuracySource = "M")
        
        featuresFlow.value = listOf(feature)
        advanceUntilIdle()

        viewModel.beginPersistedShapeEdit(featureId)
        advanceUntilIdle()
        
        assertEquals(MapEditingMode.EditLine, viewModel.uiState.value.editingMode)
        
        // Switch property
        viewModel.setProperty(UUID.randomUUID(), force = true)
        advanceUntilIdle()
        
        assertEquals(MapEditingMode.Select, viewModel.uiState.value.editingMode)
    }

    @Test
    fun `guided route startup works through viewmodel`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layer = LayerEntity(id = UUID.randomUUID(), propertyId = propId, planId = planId, name = "L", category = "C")
        setupContext(propId, planId, listOf(layer))
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.setActiveLayer(layer.id)
        advanceUntilIdle()

        val preset = GuidedMapPresets.ROUTES.first()
        viewModel.setGuidedPreset(preset)
        advanceUntilIdle()
        
        assertEquals(MapEditingMode.AddLine, viewModel.uiState.value.editingMode)
        assertNotNull(viewModel.uiState.value.guidedSession)
    }

    @Test
    fun `guided area startup works through viewmodel`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layer = LayerEntity(id = UUID.randomUUID(), propertyId = propId, planId = planId, name = "L", category = "C")
        setupContext(propId, planId, listOf(layer))
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.setActiveLayer(layer.id)
        advanceUntilIdle()

        val preset = GuidedMapPresets.AREAS.first { it.id != GuidedMapPresetId.PROPERTY_BOUNDARY }
        viewModel.setGuidedPreset(preset)
        advanceUntilIdle()
        
        assertEquals(MapEditingMode.AddPolygon, viewModel.uiState.value.editingMode)
    }

    @Test
    fun `guided boundary acknowledgment workflow works`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layer = LayerEntity(id = UUID.randomUUID(), propertyId = propId, planId = planId, name = "L", category = "C")
        setupContext(propId, planId, listOf(layer))
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.setActiveLayer(layer.id)
        advanceUntilIdle()

        every { userPrefsRepo.userPreferencesFlow } returns flowOf(UserPreferences(boundaryDisclaimerAcknowledged = false))
        
        val preset = GuidedMapPresets.AREAS.find { it.id == GuidedMapPresetId.PROPERTY_BOUNDARY }!!
        viewModel.setGuidedPreset(preset)
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.showBoundaryAcknowledgment)
        assertEquals(MapEditingMode.Select, viewModel.uiState.value.editingMode)
        
        viewModel.acknowledgeBoundary()
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.showBoundaryAcknowledgment)
        assertEquals(MapEditingMode.AddPolygon, viewModel.uiState.value.editingMode)
    }

    @Test
    fun `starter layer eligibility waits for authoritative idle state`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val property = PropertyEntity(id = propId, name = "P", propertyType = "T", latitude = 0.0, longitude = 0.0)
        val plan = PlanEntity(id = planId, propertyId = propId, name = "Plan", planType = "T", backgroundType = "M", centerLatitude = 0.0, centerLongitude = 0.0, zoom = 15.0)
        val layersFlow = MutableStateFlow<List<LayerEntity>>(emptyList())
        
        every { propertyRepo.getAllProperties() } returns flowOf(listOf(property))
        every { mapRepo.getPlansForProperty(propId) } returns flowOf(listOf(plan))
        every { mapRepo.getLayersForPlan(planId) } returns layersFlow
        coEvery { mapRepo.getPlanById(planId) } returns plan
        
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.setProperty(propId)
        viewModel.selectPlan(planId)
        advanceUntilIdle()
        
        assertFalse("Should not be eligible with no layers", viewModel.uiState.value.starterLayersEligible)
        
        layersFlow.value = listOf(LayerEntity(id = UUID.randomUUID(), propertyId = propId, planId = planId, name = "Other", category = "Other"))
        viewModel.setActiveLayer(layersFlow.value.first().id)
        advanceUntilIdle()
        
        assertTrue("Should be eligible now", viewModel.uiState.value.starterLayersEligible)
    }

    @Test
    fun `guided gps outcome classification works`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layer = LayerEntity(id = UUID.randomUUID(), propertyId = propId, planId = planId, name = "L", category = "C")
        setupContext(propId, planId, listOf(layer))
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.setActiveLayer(layer.id)
        advanceUntilIdle()

        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Success(1.0, 1.0, 50.0f, System.currentTimeMillis(), LocationResult.Success.Source.Fresh, true)
        
        viewModel.requestLocation(LocationRequestPurpose.CreatePoint)
        advanceUntilIdle()
        
        assertEquals(LocationIssueType.PoorAccuracy, viewModel.uiState.value.locationIssue?.type)
    }

    @Test
    fun `location rationale cancellation clears guided state`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layer = LayerEntity(id = UUID.randomUUID(), propertyId = propId, planId = planId, name = "L", category = "C")
        setupContext(propId, planId, listOf(layer))
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.setActiveLayer(layer.id)
        advanceUntilIdle()
        
        val preset = GuidedMapPresets.LOCATIONS.first()
        viewModel.setGuidedPreset(preset)
        viewModel.selectGuidedLocationMethod(PlacementMethod.MY_LOCATION)
        advanceUntilIdle()
        
        assertNotNull(viewModel.uiState.value.guidedSession)
        
        viewModel.cancelPermissionRationale()
        advanceUntilIdle()
        
        assertNull(viewModel.uiState.value.guidedSession)
    }

    @Test
    fun `starter layer creation recovery works after preference failure`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layer = LayerEntity(id = UUID.randomUUID(), propertyId = propId, planId = planId, name = "L", category = "C")
        setupContext(propId, planId, listOf(layer))
        backgroundScope.launch { viewModel.uiState.collect() }
        
        coEvery { mapRepo.ensureStarterLayers(any(), any(), any(), any()) } throws RuntimeException("Fail")
        viewModel.createStarterLayers(true, true, true, true)
        advanceUntilIdle()
        
        assertEquals(R.string.save_failed, viewModel.uiState.value.starterLayerErrorRes)
        
        coEvery { mapRepo.ensureStarterLayers(any(), any(), any(), any()) } returns emptyMap()
        viewModel.createStarterLayers(true, true, true, true)
        advanceUntilIdle()
        
        assertNull(viewModel.uiState.value.starterLayerErrorRes)
        assertTrue(viewModel.uiState.value.starterLayersCreated)
    }

    @Test
    fun `guided gps cancel clears purpose and session`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layer = LayerEntity(id = UUID.randomUUID(), propertyId = propId, planId = planId, name = "L", category = "C")
        setupContext(propId, planId, listOf(layer))
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.setActiveLayer(layer.id)
        advanceUntilIdle()
        
        coEvery { locationProvider.getCurrentLocation() } coAnswers {
            kotlinx.coroutines.delay(2000)
            LocationResult.Timeout
        }

        viewModel.setGuidedPreset(GuidedMapPresets.LOCATIONS.first())
        viewModel.selectGuidedLocationMethod(PlacementMethod.MY_LOCATION)
        advanceUntilIdle()
        
        assertEquals(LocationRequestPurpose.CreatePoint, viewModel.uiState.value.pendingLocationPurpose)
        
        viewModel.cancelGuidedLocationPlacement()
        advanceUntilIdle()
        
        assertNull(viewModel.uiState.value.pendingLocationPurpose)
        assertNull(viewModel.uiState.value.guidedSession)
    }

    @Test
    fun `locate only cancel does not clear unrelated session`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layer = LayerEntity(id = UUID.randomUUID(), propertyId = propId, planId = planId, name = "L", category = "C")
        setupContext(propId, planId, listOf(layer))
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.setActiveLayer(layer.id)
        advanceUntilIdle()

        viewModel.setGuidedPreset(GuidedMapPresets.LOCATIONS.first())
        viewModel.selectGuidedLocationMethod(PlacementMethod.TAP_MAP)
        advanceUntilIdle()
        
        coEvery { locationProvider.getCurrentLocation() } coAnswers {
            kotlinx.coroutines.delay(2000)
            LocationResult.Timeout
        }

        viewModel.requestLocation(LocationRequestPurpose.LocateOnly)
        advanceUntilIdle()
        
        viewModel.cancelLocationIssue()
        advanceUntilIdle()
        
        assertNotNull("Guided session should remain", viewModel.uiState.value.guidedSession)
    }

    @Test
    fun `availability blocks Add to Map during active search`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layer = LayerEntity(id = UUID.randomUUID(), propertyId = propId, planId = planId, name = "L", category = "C")
        setupContext(propId, planId, listOf(layer))
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.setActiveLayer(layer.id)
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.addToMapAvailability.isAvailable)
        
        viewModel.setSearchActive(true)
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.addToMapAvailability.isAvailable)
    }

    @Test
    fun `availability blocks Add to Map when layer is locked`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layer = LayerEntity(id = UUID.randomUUID(), propertyId = propId, planId = planId, name = "L", category = "C", isLocked = true)
        setupContext(propId, planId, listOf(layer))
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.setActiveLayer(layer.id)
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.addToMapAvailability.isAvailable)
    }

    @Test
    fun `requestOpenGuidedAddMenu honors availability`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        backgroundScope.launch { viewModel.uiState.collect {} }
        setupContext(propId, planId, emptyList())
        
        // Wait for setup to fully propagate through multiple levels of combine
        advanceUntilIdle()
        testDispatcher.scheduler.runCurrent()
        
        viewModel.requestOpenGuidedAddMenu()
        advanceUntilIdle()
        testDispatcher.scheduler.runCurrent()
        
        assertFalse("Menu should not show without layers", viewModel.uiState.value.showGuidedAddMenu)
        assertNotNull("Error should be set", viewModel.uiState.value.mapErrorRes)
    }

    @Test
    fun `preset selection rechecks availability`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        backgroundScope.launch { viewModel.uiState.collect {} }
        setupContext(propId, planId, emptyList())
        advanceUntilIdle()
        testDispatcher.scheduler.runCurrent()
        
        viewModel.setGuidedPreset(GuidedMapPresets.LOCATIONS.first())
        advanceUntilIdle()
        testDispatcher.scheduler.runCurrent()
        
        assertNull(viewModel.uiState.value.pendingGuidedPreset)
        assertNotNull("Error should be set", viewModel.uiState.value.mapErrorRes)
    }

    @Test
    fun `availability blocks Add to Map when feature details open`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layer = LayerEntity(id = UUID.randomUUID(), propertyId = propId, planId = planId, name = "L", category = "C")
        setupContext(propId, planId, listOf(layer))
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.setActiveLayer(layer.id)
        advanceUntilIdle()
        
        val feature = MapFeatureEntity(id = UUID.randomUUID(), propertyId = propId, planId = planId, layerId = layer.id, geometryType = "POINT", geometryJson = "{}", coordinateSpace = "G", styleJson = "{}", accuracySource = "M")
        viewModel.selectPersistedFeature(feature)
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.addToMapAvailability.isAvailable)
    }

    @Test
    fun `successful location creation clears purpose`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layer = LayerEntity(id = UUID.randomUUID(), propertyId = propId, planId = planId, name = "L", category = "C")
        setupContext(propId, planId, listOf(layer))
        
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.setActiveLayer(layer.id)
        advanceUntilIdle()

        coEvery { locationProvider.getCurrentLocation() } coAnswers {
            kotlinx.coroutines.delay(1000)
            LocationResult.Success(45.0, -75.0, 5.0f, System.currentTimeMillis(), LocationResult.Success.Source.Fresh, true)
        }

        viewModel.requestLocation(LocationRequestPurpose.CreatePoint)
        
        // Wait for the chain of combines to propagate
        testDispatcher.scheduler.advanceTimeBy(100)
        repeat(20) { testDispatcher.scheduler.runCurrent() }
        
        assertEquals("Purpose should be set while locating", LocationRequestPurpose.CreatePoint, viewModel.uiState.value.pendingLocationPurpose)

        testDispatcher.scheduler.advanceTimeBy(1000)
        advanceUntilIdle()
        
        assertNull("Purpose should be cleared after success", viewModel.uiState.value.pendingLocationPurpose)
    }

    @Test
    fun `location batch recomputes on showLocationDetails change`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.showLocationDetails)
        
        viewModel.showLocationDetails(true)
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.showLocationDetails)
    }

    @Test
    fun `location batch recomputes on hasRequestedLocationOnce change`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.hasRequestedLocationOnce)
        
        viewModel.setHasRequestedLocationOnce(true)
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.hasRequestedLocationOnce)
    }

    @Test
    fun `recombination does not duplicate one-time location work`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layer = LayerEntity(id = UUID.randomUUID(), propertyId = propId, planId = planId, name = "L", category = "C")
        setupContext(propId, planId, listOf(layer))
        backgroundScope.launch { viewModel.uiState.collect() }
        viewModel.setActiveLayer(layer.id)
        advanceUntilIdle()

        coEvery { locationProvider.getCurrentLocation() } returns LocationResult.Success(1.0, 1.0, 5.0f, System.currentTimeMillis(), LocationResult.Success.Source.Fresh, true)
        
        // Trigger location request
        viewModel.requestLocation(LocationRequestPurpose.CreatePoint)
        advanceUntilIdle()
        
        // Verify location request happened once
        coVerify(exactly = 1) { locationProvider.getCurrentLocation() }
        
        // Trigger unrelated recombination (e.g. show details)
        viewModel.showLocationDetails(true)
        advanceUntilIdle()
        
        // Verify no second location request occurred
        coVerify(exactly = 1) { locationProvider.getCurrentLocation() }
    }
}
