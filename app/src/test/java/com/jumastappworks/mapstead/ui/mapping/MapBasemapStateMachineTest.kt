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
    fun `Readiness Architecture - Preferences then MapView`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        
        userPrefsFlow.value = userPrefsFlow.value.copy(selectedBasemapId = BasemapId.TOPO)
        advanceUntilIdle()
        
        val session = UUID.randomUUID()
        viewModel.onMapReady(session)
        advanceUntilIdle()
        
        assertEquals(BasemapSourceId.MAPTILER_TOPO, viewModel.uiState.value.currentAttempt?.sourceId)
        job.cancel()
    }

    @Test
    fun `MapView ready before preferences`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        
        val session = UUID.randomUUID()
        viewModel.onMapReady(session)
        advanceUntilIdle()
        
        assertEquals(BasemapSourceId.MAPTILER_STREETS, viewModel.uiState.value.currentAttempt?.sourceId)
        
        userPrefsFlow.value = userPrefsFlow.value.copy(selectedBasemapId = BasemapId.BASE)
        advanceUntilIdle()
        
        assertEquals(BasemapSourceId.MAPTILER_BASE, viewModel.uiState.value.currentAttempt?.sourceId)
        job.cancel()
    }

    @Test
    fun `Stored non-Streets preference never briefly requests Streets`() = runTest(testDispatcher) {
        userPrefsFlow.value = userPrefsFlow.value.copy(selectedBasemapId = BasemapId.TOPO)
        viewModel = MapViewModel(mapRepo, attachmentRepo, infraRepo, propRepo, resolver, locationProvider, basemapProvider, userPrefs, namingService, context, savedState)
        
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val session = UUID.randomUUID()
        viewModel.onMapReady(session)
        advanceUntilIdle()
        
        assertEquals("Should have requested TOPO immediately without Streets flash", BasemapSourceId.MAPTILER_TOPO, viewModel.uiState.value.currentAttempt?.sourceId)
        job.cancel()
    }

    @Test
    fun `MapView recreation rebinds correctly`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
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
        assertEquals(BasemapLoadAttemptReason.RECREATION, attempt2.reason)
        job.cancel()
    }

    @Test
    fun `Recreation while FAILED does not auto-retry`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.onMapReady(UUID.randomUUID())
        advanceUntilIdle()
        
        val primary = viewModel.uiState.value.currentAttempt!!
        viewModel.handleBasemapLoadTerminated(BasemapTerminalReason.PROVIDER_FAILURE, primary)
        advanceUntilIdle()
        
        val backup = viewModel.uiState.value.currentAttempt!!
        viewModel.handleBasemapLoadTerminated(BasemapTerminalReason.PROVIDER_FAILURE, backup)
        advanceUntilIdle()
        
        assertEquals(BasemapLoadStatus.FAILED, viewModel.uiState.value.basemapStatus)
        
        val session2 = UUID.randomUUID()
        viewModel.onMapReady(session2)
        advanceUntilIdle()
        
        assertEquals("Should REMAIN in FAILED state after recreation", BasemapLoadStatus.FAILED, viewModel.uiState.value.basemapStatus)
        job.cancel()
    }

    @Test
    fun `Strict Validation - Rejects mismatching session`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.onMapReady(UUID.randomUUID())
        advanceUntilIdle()
        viewModel.requestBasemap(BasemapId.BASE)
        advanceUntilIdle()
        val attempt = viewModel.uiState.value.currentAttempt!!
        
        val badSessionAttempt = attempt.copy(renderSessionId = UUID.randomUUID())
        val result = viewModel.handleBasemapLoadSuccess(badSessionAttempt)
        assertFalse(result.accepted)
        assertEquals(BasemapLoadRejectionReason.STALE_SESSION, result.rejectionReason)
        job.cancel()
    }

    @Test
    fun `Terminal Closure - Timeout prevents success`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.onMapReady(UUID.randomUUID())
        advanceUntilIdle()
        viewModel.requestBasemap(BasemapId.BASE)
        advanceUntilIdle()
        val attempt = viewModel.uiState.value.currentAttempt!!
        
        viewModel.handleBasemapLoadTerminated(BasemapTerminalReason.TIMEOUT, attempt)
        advanceUntilIdle()
        
        val result = viewModel.handleBasemapLoadSuccess(attempt)
        assertFalse("Terminal attempt must be rejected", result.accepted)
        job.cancel()
    }

    @Test
    fun `Repair Loop Prevention - Epoch based`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
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
        // Deliver another stale callback for STREETS
        viewModel.handleStaleStyleApplied(attempt1)
        advanceUntilIdle()
        assertEquals("Should not repeat repair while one is in flight", repairId, viewModel.uiState.value.currentAttempt?.attemptId)
        
        // Deliver late completion from the repair itself
        viewModel.handleStaleStyleApplied(repairAttempt)
        advanceUntilIdle()
        assertEquals("Should not repeat repair from the repair itself", repairId, viewModel.uiState.value.currentAttempt?.attemptId)
        job.cancel()
    }

    @Test
    fun `Normal success does not pre-exhaust repair epoch`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.onMapReady(UUID.randomUUID())
        advanceUntilIdle()
        
        viewModel.requestBasemap(BasemapId.STREETS)
        advanceUntilIdle()
        val attemptA = viewModel.uiState.value.currentAttempt!!
        
        viewModel.requestBasemap(BasemapId.BASE)
        advanceUntilIdle()
        val attemptB = viewModel.uiState.value.currentAttempt!!
        
        // B succeeds normally (REASON.INITIAL)
        viewModel.handleBasemapLoadSuccess(attemptB)
        advanceUntilIdle()
        
        // A completes late (STALE). Should trigger repair C because B didn't exhaust the epoch.
        viewModel.handleStaleStyleApplied(attemptA)
        advanceUntilIdle()
        
        val repairAttempt = viewModel.uiState.value.currentAttempt!!
        assertEquals(BasemapLoadAttemptReason.REPAIR, repairAttempt.reason)
        assertEquals(BasemapSourceId.MAPTILER_BASE, repairAttempt.sourceId)
        job.cancel()
    }

    @Test
    fun `In-memory selection survives persistence failure`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        coEvery { userPrefs.updateSelectedBasemap(any()) } throws RuntimeException("Persistence Error")
        
        viewModel.onMapReady(UUID.randomUUID())
        advanceUntilIdle()
        
        viewModel.requestBasemap(BasemapId.BASE)
        advanceUntilIdle()
        
        assertEquals("Memory state should update immediately", BasemapId.BASE, viewModel.uiState.value.preferredBasemapId)
        assertEquals("Should start load for BASE", BasemapSourceId.MAPTILER_BASE, viewModel.uiState.value.currentAttempt?.sourceId)
        job.cancel()
    }

    @Test
    fun `Temporary backup selection is not persisted`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.onMapReady(UUID.randomUUID())
        advanceUntilIdle()
        
        viewModel.requestBackupBasemap(BasemapSourceId.OPEN_FREE_MAP_LIBERTY)
        advanceUntilIdle()
        
        assertEquals(BasemapSourceId.OPEN_FREE_MAP_LIBERTY, viewModel.uiState.value.currentAttempt?.sourceId)
        coVerify(exactly = 0) { userPrefs.updateSelectedBasemap(any()) }
        job.cancel()
    }

    @Test
    fun `Camera Snapshot restoration equality`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val session = UUID.randomUUID()
        viewModel.onMapReady(session)
        advanceUntilIdle()
        
        viewModel.requestBasemap(BasemapId.STREETS)
        val attempt = viewModel.uiState.value.currentAttempt!!
        
        viewModel.captureCameraSnapshot(10.0, 20.0, 15.0, 45.0, 0.0, attempt)
        
        val snapshot = viewModel.getCameraSnapshot(attempt)
        assertNotNull(snapshot)
        assertEquals(10.0, snapshot!!.latitude, 1e-9)
        assertEquals(20.0, snapshot.longitude, 1e-9)
        assertEquals(15.0, snapshot.zoom, 1e-9)
        assertEquals(45.0, snapshot.bearing, 1e-9)
        
        // Accepted and consumed
        viewModel.consumeCameraSnapshot(attempt)
        assertNull(viewModel.getCameraSnapshot(attempt))
        job.cancel()
    }

    @Test
    fun `Superseded attempt removes snapshot`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.onMapReady(UUID.randomUUID())
        advanceUntilIdle()
        
        viewModel.requestBasemap(BasemapId.STREETS)
        val attempt1 = viewModel.uiState.value.currentAttempt!!
        viewModel.captureCameraSnapshot(1.0, 1.0, 1.0, 0.0, 0.0, attempt1)
        
        viewModel.requestBasemap(BasemapId.BASE)
        val attempt2 = viewModel.uiState.value.currentAttempt!!
        
        assertNull("Snapshot for superseded attempt should be removed", viewModel.getCameraSnapshot(attempt1))
        job.cancel()
    }

    @Test
    fun `Terminal reason preserved putIfAbsent`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.onMapReady(UUID.randomUUID())
        advanceUntilIdle()
        
        val attempt = viewModel.uiState.value.currentAttempt!!
        viewModel.handleBasemapLoadTerminated(BasemapTerminalReason.TIMEOUT, attempt)
        advanceUntilIdle()
        
        // Delivering another termination reason for same attempt. Should NOT overwrite TIMEOUT.
        viewModel.handleBasemapLoadTerminated(BasemapTerminalReason.SUPERSEDED, attempt)
        advanceUntilIdle()
        
        val result = viewModel.handleBasemapLoadSuccess(attempt)
        assertFalse(result.accepted)
        // Rejection reason should still be TERMINAL_ATTEMPT (validating against TIMEOUT)
        assertEquals(BasemapLoadRejectionReason.TERMINAL_ATTEMPT, result.rejectionReason)
        job.cancel()
    }

    @Test
    fun `Full attempt identity mismatch rejected`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.onMapReady(UUID.randomUUID())
        advanceUntilIdle()
        
        viewModel.requestBasemap(BasemapId.BASE)
        val attempt = viewModel.uiState.value.currentAttempt!!
        
        // Mismatch captured sequence
        val result = viewModel.handleBasemapLoadSuccess(attempt.copy(capturedSequence = 999))
        assertFalse(result.accepted)
        job.cancel()
    }

    @Test
    fun `Wrong provider or role rejected`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.onMapReady(UUID.randomUUID())
        advanceUntilIdle()
        
        val attempt = viewModel.uiState.value.currentAttempt!!
        
        // Wrong provider
        val result1 = viewModel.handleBasemapLoadSuccess(attempt.copy(provider = BasemapProviderType.OPEN_FREE_MAP))
        assertFalse(result1.accepted)
        
        // Wrong role
        val result2 = viewModel.handleBasemapLoadSuccess(attempt.copy(role = BasemapRole.BACKUP))
        assertFalse(result2.accepted)
        job.cancel()
    }
}
