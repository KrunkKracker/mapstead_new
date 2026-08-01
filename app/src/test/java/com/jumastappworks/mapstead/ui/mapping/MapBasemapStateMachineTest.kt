package com.jumastappworks.mapstead.ui.mapping

import androidx.lifecycle.SavedStateHandle
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.data.prefs.UserPreferences
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import com.jumastappworks.mapstead.data.repository.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MapBasemapStateMachineTest {

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
    private val savedState = SavedStateHandle()

    private lateinit var viewModel: MapViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val userPrefsFlow = MutableStateFlow(UserPreferences())
    
    private val streetsDef = BasemapDefinition(BasemapSourceId.MAPTILER_STREETS, BasemapProviderType.MAPTILER, BasemapRole.PRIMARY, "url", 0, 0, true, BasemapId.STREETS, BasemapSourceId.OPEN_FREE_MAP_LIBERTY)
    private val baseDef = BasemapDefinition(BasemapSourceId.MAPTILER_BASE, BasemapProviderType.MAPTILER, BasemapRole.PRIMARY, "url", 0, 0, true, BasemapId.BASE, BasemapSourceId.OPEN_FREE_MAP_POSITRON)
    private val topoDef = BasemapDefinition(BasemapSourceId.MAPTILER_TOPO, BasemapProviderType.MAPTILER, BasemapRole.PRIMARY, "url", 0, 0, true, BasemapId.TOPO, BasemapSourceId.OPEN_FREE_MAP_FIORD)
    private val libertyDef = BasemapDefinition(BasemapSourceId.OPEN_FREE_MAP_LIBERTY, BasemapProviderType.OPEN_FREE_MAP, BasemapRole.BACKUP, "url", 0, 0, true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        every { userPrefs.userPreferencesFlow } returns userPrefsFlow
        coEvery { userPrefs.updateSelectedBasemap(any()) } answers {
            userPrefsFlow.value = userPrefsFlow.value.copy(selectedBasemapId = arg(0))
        }
        every { basemapProvider.getDefaultBasemapId() } returns BasemapId.STREETS
        
        every { basemapProvider.getPrimaryBasemaps() } returns listOf(streetsDef, baseDef, topoDef)
        every { basemapProvider.resolveDefaultBackup(any()) } returns BasemapSourceId.OPEN_FREE_MAP_LIBERTY
        every { basemapProvider.getDefinition(BasemapSourceId.MAPTILER_STREETS) } returns streetsDef
        every { basemapProvider.getDefinition(BasemapSourceId.MAPTILER_BASE) } returns baseDef
        every { basemapProvider.getDefinition(BasemapSourceId.MAPTILER_TOPO) } returns topoDef
        every { basemapProvider.getDefinition(BasemapSourceId.OPEN_FREE_MAP_LIBERTY) } returns libertyDef

        viewModel = MapViewModel(mapRepo, attachmentRepo, infraRepo, propRepo, resolver, locationProvider, basemapProvider, userPrefs, namingService, context, savedState)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Readiness Architecture - Preferences then MapView`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        
        userPrefsFlow.value = userPrefsFlow.value.copy(selectedBasemapId = BasemapId.TOPO)
        advanceUntilIdle()
        
        val session = UUID.randomUUID()
        viewModel.onMapReady(session)
        advanceUntilIdle()
        
        assertEquals(BasemapSourceId.MAPTILER_TOPO, viewModel.uiState.value.currentAttempt?.sourceId)
    }

    @Test
    fun `MapView recreation rebinds correctly`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.onMapReady(UUID.randomUUID())
        advanceUntilIdle()
        
        viewModel.requestBasemap(BasemapId.BASE)
        advanceUntilIdle()
        val attempt1 = viewModel.uiState.value.currentAttempt!!
        
        val session2 = UUID.randomUUID()
        viewModel.onMapReady(session2)
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertEquals(BasemapLoadStatus.LOADING_PRIMARY, state.basemapStatus)
        val attempt2 = state.currentAttempt!!
        assertNotEquals(attempt1.attemptId, attempt2.attemptId)
        assertEquals(session2, attempt2.renderSessionId)
    }

    @Test
    fun `Strict Validation - Rejects stale session`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.onMapReady(UUID.randomUUID())
        advanceUntilIdle()
        viewModel.requestBasemap(BasemapId.BASE)
        advanceUntilIdle()
        val attempt = viewModel.uiState.value.currentAttempt!!
        
        val result = viewModel.handleBasemapLoadSuccess(attempt.sourceId, attempt.copy(renderSessionId = UUID.randomUUID()))
        assertFalse(result.accepted)
        assertEquals(BasemapLoadRejectionReason.STALE_SESSION, result.rejectionReason)
    }

    @Test
    fun `Terminal Closure - Timeout prevents success`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.onMapReady(UUID.randomUUID())
        advanceUntilIdle()
        viewModel.requestBasemap(BasemapId.BASE)
        advanceUntilIdle()
        val attempt = viewModel.uiState.value.currentAttempt!!
        
        viewModel.handleBasemapLoadFailure("Timeout", attempt)
        advanceUntilIdle()
        
        val result = viewModel.handleBasemapLoadSuccess(BasemapSourceId.MAPTILER_BASE, attempt)
        assertFalse("Terminal attempt must be rejected", result.accepted)
    }

    @Test
    fun `Accepted Style Restoration - Event check`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.onMapReady(UUID.randomUUID())
        advanceUntilIdle()
        
        viewModel.requestBasemap(BasemapId.TOPO)
        advanceUntilIdle()
        val attempt = viewModel.uiState.value.currentAttempt!!
        
        val result = viewModel.handleBasemapLoadSuccess(BasemapSourceId.MAPTILER_TOPO, attempt)
        assertTrue("Load should be accepted: ${result.rejectionReason}", result.accepted)
        
        advanceUntilIdle()
        val event = viewModel.uiState.value.acceptedStyleEvent
        assertNotNull("Event should be emitted", event)
        assertEquals(attempt.attemptId, event!!.attempt.attemptId)
    }

    @Test
    fun `Repair Loop Prevention`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.onMapReady(UUID.randomUUID())
        advanceUntilIdle()
        
        viewModel.requestBasemap(BasemapId.STREETS)
        advanceUntilIdle()
        val attempt1 = viewModel.uiState.value.currentAttempt!!
        
        viewModel.requestBasemap(BasemapId.BASE)
        advanceUntilIdle()
        
        viewModel.handleStaleStyleApplied(attempt1)
        advanceUntilIdle()
        val repairAttempt = viewModel.uiState.value.currentAttempt!!
        assertEquals(BasemapLoadAttemptReason.REPAIR, repairAttempt.reason)
        
        val repairId = repairAttempt.attemptId
        viewModel.handleStaleStyleApplied(attempt1)
        advanceUntilIdle()
        assertEquals("Should not repeat repair", repairId, viewModel.uiState.value.currentAttempt?.attemptId)
    }

    @Test
    fun `Customer gesture suppresses restoration`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.onMapReady(UUID.randomUUID())
        advanceUntilIdle()
        
        viewModel.requestBasemap(BasemapId.BASE)
        advanceUntilIdle()
        val attempt = viewModel.uiState.value.currentAttempt!!
        
        viewModel.onCameraInteraction()
        advanceUntilIdle()
        
        viewModel.handleBasemapLoadSuccess(BasemapSourceId.MAPTILER_BASE, attempt)
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertNotNull("Event should be emitted", state.acceptedStyleEvent)
        assertNotEquals("Sequence mismatch", state.acceptedStyleEvent!!.attempt.capturedSequence, state.cameraInteractionSequence)
    }
}
