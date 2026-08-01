package com.jumastappworks.mapstead.ui.mapping

import com.jumastappworks.mapstead.data.db.entities.LayerEntity
import com.jumastappworks.mapstead.data.db.entities.PlanEntity
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.data.repository.MapRepository
import com.jumastappworks.mapstead.data.repository.PropertyRepository
import com.jumastappworks.mapstead.data.repository.AttachmentRepository
import com.jumastappworks.mapstead.data.attachments.*
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import androidx.lifecycle.SavedStateHandle
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class GuidedSheetRegressionTest {

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
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        
        mockkStatic(android.net.Uri::class)
        every { android.net.Uri.parse(any()) } returns mockk(relaxed = true)
        Dispatchers.setMain(testDispatcher)
        every { userPrefsRepo.userPreferencesFlow } returns flowOf(mockk(relaxed = true))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun TestScope.setupInitializedViewModel(): UUID {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val plan = PlanEntity(id = planId, propertyId = propId, name = "Plan", planType = "M", backgroundType = "M")
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "Layer", category = "Structure", displayOrder = 0)
        
        coEvery { mapRepo.getPlanById(planId) } returns plan
        coEvery { propRepo.getPropertyById(propId) } returns PropertyEntity(id = propId, name = "Prop", propertyType = "P")
        every { mapRepo.getPlansForProperty(propId) } returns flowOf(listOf(plan))
        every { mapRepo.getLayersForPlan(planId) } returns flowOf(listOf(layer))
        every { mapRepo.getFeaturesForLayer(any()) } returns flowOf(emptyList())
        every { mapRepo.getFeaturesForPlan(any()) } returns flowOf(emptyList())
        
        viewModel = MapViewModel(
            mapRepo, attachmentRepo, mockk(relaxed = true), propRepo,
            mapFeatureContextResolver, locProvider, basemapProvider,
            userPrefsRepo, featureNamingService, context, SavedStateHandle()
        )
        
        viewModel.openMapContext(propId, planId, "token")
        advanceUntilIdle()
        viewModel.setActiveLayer(layerId)
        advanceUntilIdle()
        return layerId
    }

    @Test
    fun `selectGuidedPresetAndCloseMenu closes the add menu`() = runTest {
        val layerId = setupInitializedViewModel()
        assertEquals(layerId, viewModel.uiState.value.activeLayerId)
        
        viewModel.setShowGuidedAddMenu(true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showGuidedAddMenu)
        
        val preset = GuidedMapPresets.ROUTES.find { it.id == GuidedMapPresetId.FENCE }!!
        viewModel.selectGuidedPresetAndCloseMenu(preset)
        advanceUntilIdle()
        
        assertFalse("Add menu should be closed authoritatively", viewModel.uiState.value.showGuidedAddMenu)
        assertNotNull("Guided session should be started", viewModel.uiState.value.guidedSession)
    }

    @Test
    fun `first map tap places point during guided creation`() = runTest {
        setupInitializedViewModel()
        
        val preset = GuidedMapPresets.LOCATIONS.find { it.id == GuidedMapPresetId.WELL }!!
        viewModel.selectGuidedPresetAndCloseMenu(preset)
        advanceUntilIdle()
        viewModel.selectGuidedLocationMethod(PlacementMethod.TAP_MAP)
        advanceUntilIdle()
        
        assertEquals(MapEditingMode.AddPoint, viewModel.uiState.value.editingMode)
        
        viewModel.addPointAt(10.0, 20.0)
        advanceUntilIdle()
        
        assertTrue("Feature editor should open after first tap", viewModel.uiState.value.featureEditorOpen)
        assertEquals(MapEditingMode.Select, viewModel.uiState.value.editingMode)
    }

    @Test
    fun `cancelGuidedCreation clears staged photo and deletes temp capture`() = runTest {
        setupInitializedViewModel()
        viewModel.setStagedPhoto("content://uri", "token123")
        
        viewModel.cancelGuidedCreation()
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.stagedPhoto is StagedCreationPhotoState.None)
        coVerify { attachmentRepo.deleteTempCameraCapture("token123") }
    }

    @Test
    fun `successful saveFeature clears staged photo state`() = runTest {
        setupInitializedViewModel()
        viewModel.setStagedPhoto("content://photo", "token")
        coEvery { attachmentRepo.importAttachment(any(), any(), any(), any(), any(), any()) } returns AttachmentWriteResult.Success(UUID.randomUUID())
        
        val feature = MapFeatureEntity(id = UUID.randomUUID(), propertyId = UUID.randomUUID(), planId = UUID.randomUUID(), layerId = UUID.randomUUID(), geometryType = "POINT", geometryJson = "{}", label = "Saved", coordinateSpace = "G", styleJson = "{}", accuracySource = "M")
        viewModel.saveFeature(feature)
        advanceUntilIdle()
        
        assertTrue("Staged photo should be cleared on success", viewModel.uiState.value.stagedPhoto is StagedCreationPhotoState.None)
    }

    @Test
    fun `feature save success but photo failure preserves retry state`() = runTest {
        setupInitializedViewModel()
        viewModel.setStagedPhoto("content://photo", "token")
        coEvery { attachmentRepo.importAttachment(any(), any(), any(), any(), any(), any()) } returns AttachmentWriteResult.CopyFailed
        
        val feature = MapFeatureEntity(id = UUID.randomUUID(), propertyId = UUID.randomUUID(), planId = UUID.randomUUID(), layerId = UUID.randomUUID(), geometryType = "POINT", geometryJson = "{}", label = "Saved", coordinateSpace = "G", styleJson = "{}", accuracySource = "M")
        viewModel.saveFeature(feature)
        advanceUntilIdle()
        
        assertTrue("Staged photo should be retained on failure", viewModel.uiState.value.stagedPhoto is StagedCreationPhotoState.Ready)
        assertTrue(viewModel.uiState.value.saveOutcome is GuidedSaveOutcome.FeatureSavedPhotoFailed)
    }

    @Test
    fun `MAP_ONLY photo creates no Infrastructure Item`() = runTest {
        setupInitializedViewModel()
        val preset = GuidedMapPresets.LOCATIONS.find { it.id == GuidedMapPresetId.TREE }!! // MAP_ONLY
        viewModel.selectGuidedPresetAndCloseMenu(preset)
        
        viewModel.addPointAt(0.0, 0.0)
        advanceUntilIdle()
        
        val feature = viewModel.uiState.value.featureEditorFeature!!
        viewModel.saveFeature(feature)
        advanceUntilIdle()
        
        coVerify(exactly = 0) { mapRepo.saveFeatureWithOptionalItem(any(), match { it != null }) }
    }

    @Test
    fun `retryFeaturePhoto does not resave the feature`() = runTest {
        setupInitializedViewModel()
        viewModel.setStagedPhoto("content://photo", "token")
        val propertyId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        
        viewModel.retryFeaturePhoto(propertyId, featureId)
        advanceUntilIdle()
        
        coVerify(exactly = 0) { mapRepo.saveFeatureWithOptionalItem(any(), any()) }
        coVerify(exactly = 1) { attachmentRepo.importAttachment(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `continueWithoutFeaturePhoto clears capture and deletes temp capture`() = runTest {
        setupInitializedViewModel()
        viewModel.setStagedPhoto("content://photo", "token")
        
        viewModel.continueWithoutFeaturePhoto(UUID.randomUUID())
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.stagedPhoto is StagedCreationPhotoState.None)
        coVerify { attachmentRepo.deleteTempCameraCapture("token") }
    }

    @Test
    fun `new workflow does not inherit previous photo`() = runTest {
        setupInitializedViewModel()
        viewModel.setStagedPhoto("content://photo", "token")
        
        // Cancel the first one
        viewModel.cancelGuidedCreation()
        advanceUntilIdle()
        
        // Start a new one
        val preset = GuidedMapPresets.LOCATIONS.first()
        viewModel.selectGuidedPresetAndCloseMenu(preset)
        advanceUntilIdle()
        
        assertTrue("New guided session should not have a staged photo from previous session", viewModel.uiState.value.stagedPhoto is StagedCreationPhotoState.None)
    }

    @Test
    fun `camera result Ready stages photo regardless of success boolean`() = runTest {
        setupInitializedViewModel()
        viewModel.setInFlightCapture("content://photo", "token")
        coEvery { attachmentRepo.inspectTempCameraCapture("token", any()) } returns TempCameraCaptureInspectionResult.Ready
        
        viewModel.handleCameraResult(false)
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.stagedPhoto is StagedCreationPhotoState.Ready)
    }
}
