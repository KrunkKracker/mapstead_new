package com.jumastappworks.mapstead.ui.mapping

import androidx.lifecycle.SavedStateHandle
import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.data.prefs.MeasurementSystem
import com.jumastappworks.mapstead.data.prefs.UserPreferences
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import com.jumastappworks.mapstead.data.repository.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class SystemItemDraftLifecycleTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mapRepo = mockk<MapRepository>(relaxed = true)
    private val attachmentRepo = mockk<AttachmentRepository>(relaxed = true)
    private val infraRepo = mockk<InfrastructureRepository>(relaxed = true)
    private val propRepo = mockk<PropertyRepository>(relaxed = true)
    private val resolver = mockk<MapFeatureContextResolver>(relaxed = true)
    private val locationProvider = mockk<CurrentLocationProvider>(relaxed = true)
    private val basemapProvider = mockk<com.jumastappworks.mapstead.data.mapping.BasemapProvider>(relaxed = true)
    private val userPrefs = mockk<UserPreferencesRepository>(relaxed = true)
    private val namingService = mockk<com.jumastappworks.mapstead.data.mapping.FeatureNamingService>(relaxed = true)
    private val context = mockk<android.content.Context>(relaxed = true)
    private val savedStateHandle = SavedStateHandle()

    private lateinit var viewModel: MapViewModel
    private val propertyId = UUID.randomUUID()
    private val planId = UUID.randomUUID()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        val layerId = UUID.randomUUID()
        val layer = com.jumastappworks.mapstead.data.db.entities.LayerEntity(
            id = layerId, propertyId = propertyId, planId = planId, name = "Test Layer", category = "Structure"
        )
        every { mapRepo.getLayersForPlan(planId) } returns flowOf(listOf(layer))
        coEvery { mapRepo.getLayerById(layerId) } returns layer
        
        every { userPrefs.userPreferencesFlow } returns flowOf(
            UserPreferences(
                selectedBasemapId = BasemapId.STREETS,
                measurementSystem = MeasurementSystem.IMPERIAL
            )
        )
        viewModel = MapViewModel(mapRepo, attachmentRepo, infraRepo, propRepo, resolver, locationProvider, basemapProvider, userPrefs, namingService, context, savedStateHandle)
        viewModel.setProperty(propertyId)
        viewModel.selectPlan(planId)
        viewModel.setActiveLayer(layerId)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `creating a draft does not insert a database record`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }
        advanceUntilIdle()
        
        viewModel.beginAddPoint()
        advanceUntilIdle()
        viewModel.addPointAt(0.0, 0.0)
        advanceUntilIdle()
        
        viewModel.prepareSystemItemDraft(PendingSystemItemInput("New Pump", "Utility", null, false, "Instructions"))
        advanceUntilIdle()
        
        coVerify(exactly = 0) { infraRepo.insertItem(any()) }
        assertNotNull(viewModel.uiState.value.systemItemDraft)
    }

    @Test
    fun `canceling the guided creation clears the draft`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }
        advanceUntilIdle()
        
        viewModel.beginAddPoint()
        advanceUntilIdle()
        viewModel.addPointAt(0.0, 0.0)
        advanceUntilIdle()

        viewModel.prepareSystemItemDraft(PendingSystemItemInput("Draft", "Utility", null, false, ""))
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.systemItemDraft)

        viewModel.cancelGuidedCreation()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.systemItemDraft)
    }

    @Test
    fun `switching properties prompts discard and clearing draft on confirm`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }
        advanceUntilIdle()
        
        viewModel.beginAddPoint()
        advanceUntilIdle()
        viewModel.addPointAt(0.0, 0.0)
        advanceUntilIdle()

        viewModel.prepareSystemItemDraft(PendingSystemItemInput("Draft", "Utility", null, false, ""))
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.systemItemDraft)

        viewModel.setProperty(UUID.randomUUID())
        advanceUntilIdle()
        assertTrue("Should show discard dialog", viewModel.uiState.value.showDiscardEditDialog)

        viewModel.confirmDiscardEdit()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.systemItemDraft)
    }

    @Test
    fun `selected existing item clears the manual draft`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }
        advanceUntilIdle()
        
        // Manual point (non-guided)
        viewModel.beginAddPoint()
        advanceUntilIdle()
        viewModel.addPointAt(0.0, 0.0)
        advanceUntilIdle()

        viewModel.prepareSystemItemDraft(PendingSystemItemInput("Draft", "Utility", null, false, ""))
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.systemItemDraft)

        // Select existing
        viewModel.setLinkSelection(SystemItemLinkSelection.Existing(UUID.randomUUID()))
        advanceUntilIdle()
        
        // For non-guided new features, it should clear.
        assertNull(viewModel.uiState.value.systemItemDraft)
    }

    @Test
    fun `saveFeature creates draft item if it matches the selected id`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }
        advanceUntilIdle()
        
        viewModel.beginAddPoint()
        advanceUntilIdle()
        viewModel.addPointAt(0.0, 0.0)
        advanceUntilIdle()
        val featureId = viewModel.uiState.value.sessionFeatureId!!

        val draftId = viewModel.prepareSystemItemDraft(PendingSystemItemInput("Manual Pump", "Utility", null, true, "Call 911"))
        advanceUntilIdle()
        
        val feature = MapFeatureEntity(
            id = featureId, propertyId = propertyId, planId = planId, layerId = viewModel.uiState.value.activeLayerId!!,
            infrastructureItemId = draftId, 
            geometryType = "POINT", geometryJson = "{}", coordinateSpace = "G", styleJson = "{}", accuracySource = "M", label = "Manual Pump"
        )

        viewModel.saveFeature(feature)
        advanceUntilIdle()

        coVerify { 
            mapRepo.saveFeatureWithOptionalItem(
                match { it.infrastructureItemId == draftId },
                match { it.id == draftId && it.name == "Manual Pump" }
            )
        }
        assertNull(viewModel.uiState.value.systemItemDraft)
    }
}
