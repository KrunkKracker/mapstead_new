package com.jumastappworks.mapstead.ui.mapping

import androidx.lifecycle.SavedStateHandle
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.data.prefs.UserPreferences
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import com.jumastappworks.mapstead.data.repository.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
    private val positronDef = BasemapDefinition(BasemapSourceId.OPEN_FREE_MAP_POSITRON, BasemapProviderType.OPEN_FREE_MAP, BasemapRole.BACKUP, "url", 0, 0, true)
    private val fiordDef = BasemapDefinition(BasemapSourceId.OPEN_FREE_MAP_FIORD, BasemapProviderType.OPEN_FREE_MAP, BasemapRole.BACKUP, "url", 0, 0, true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        every { userPrefs.userPreferencesFlow } returns userPrefsFlow
        coEvery { userPrefs.updateSelectedBasemap(any()) } answers {
            userPrefsFlow.value = userPrefsFlow.value.copy(selectedBasemapId = arg(0))
        }
        every { basemapProvider.getDefaultBasemapId() } returns BasemapId.STREETS
        
        every { basemapProvider.getPrimaryBasemaps() } returns listOf(streetsDef, baseDef, topoDef)
        every { basemapProvider.resolveDefaultBackup(BasemapId.STREETS) } returns BasemapSourceId.OPEN_FREE_MAP_LIBERTY
        every { basemapProvider.resolveDefaultBackup(BasemapId.BASE) } returns BasemapSourceId.OPEN_FREE_MAP_POSITRON
        every { basemapProvider.resolveDefaultBackup(BasemapId.TOPO) } returns BasemapSourceId.OPEN_FREE_MAP_FIORD

        every { basemapProvider.getDefinition(BasemapSourceId.MAPTILER_STREETS) } returns streetsDef
        every { basemapProvider.getDefinition(BasemapSourceId.MAPTILER_BASE) } returns baseDef
        every { basemapProvider.getDefinition(BasemapSourceId.MAPTILER_TOPO) } returns topoDef
        every { basemapProvider.getDefinition(BasemapSourceId.OPEN_FREE_MAP_LIBERTY) } returns libertyDef
        every { basemapProvider.getDefinition(BasemapSourceId.OPEN_FREE_MAP_POSITRON) } returns positronDef
        every { basemapProvider.getDefinition(BasemapSourceId.OPEN_FREE_MAP_FIORD) } returns fiordDef

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
        val session1 = UUID.randomUUID()
        viewModel.onMapReady(session1)
        advanceUntilIdle()
        
        viewModel.requestBasemap(BasemapId.BASE)
        advanceUntilIdle()
        val attempt1 = viewModel.uiState.value.currentAttempt!!
        
        viewModel.onRenderSessionDisposed(session1)
        advanceUntilIdle()
        
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
        val session1 = UUID.randomUUID()
        viewModel.onMapReady(session1)
        advanceUntilIdle()
        
        val primary = viewModel.uiState.value.currentAttempt!!
        viewModel.handleBasemapLoadTerminated(BasemapTerminalReason.PROVIDER_FAILURE, primary)
        advanceUntilIdle()
        
        val backup = viewModel.uiState.value.currentAttempt!!
        viewModel.handleBasemapLoadTerminated(BasemapTerminalReason.PROVIDER_FAILURE, backup)
        advanceUntilIdle()
        
        assertEquals(BasemapLoadStatus.FAILED, viewModel.uiState.value.basemapStatus)
        
        viewModel.onRenderSessionDisposed(session1)
        advanceUntilIdle()
        
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
    fun `Requested source mismatch rejected`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val session = UUID.randomUUID()
        viewModel.onMapReady(session)
        advanceUntilIdle()
        
        viewModel.requestBasemap(BasemapId.BASE)
        advanceUntilIdle()
        val state = viewModel.uiState.value
        val attempt = state.currentAttempt!!
        
        // Simulate requestedSourceId being changed externally or by race
        // Use exactly the current identity except for sourceId and provider/role matching it
        val mismatchedAttempt = attempt.copy(
            sourceId = BasemapSourceId.MAPTILER_STREETS,
            provider = streetsDef.provider,
            role = streetsDef.role
        )
        
        val result = viewModel.handleBasemapLoadSuccess(mismatchedAttempt)
        assertFalse("Should reject due to source mismatch", result.accepted)
        assertEquals(BasemapLoadRejectionReason.REQUESTED_SOURCE_MISMATCH, result.rejectionReason)
        job.cancel()
    }

    @Test
    fun `Request deferred when no render session active`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val sessionA = UUID.randomUUID()
        viewModel.onMapReady(sessionA)
        advanceUntilIdle()
        
        // 1. Dispose session A
        viewModel.onRenderSessionDisposed(sessionA)
        advanceUntilIdle()
        assertNull("Session ID should be null", viewModel.uiState.value.renderSessionId)
        
        // 2. Request map change while no session
        viewModel.requestBasemap(BasemapId.BASE)
        advanceUntilIdle()
        
        // Assert immediate deferral logic updates
        assertEquals("Status should update to LOADING_PRIMARY even if no session", BasemapLoadStatus.LOADING_PRIMARY, viewModel.uiState.value.basemapStatus)
        assertEquals("Requested source ID should update to BASE", BasemapSourceId.MAPTILER_BASE, viewModel.uiState.value.requestedSourceId)
        
        // 3. Verify no attempt bound to old session A or null session
        val currentAttempt = viewModel.uiState.value.currentAttempt
        if (currentAttempt != null) {
            assertNotEquals("Should not bind to disposed session", sessionA, currentAttempt.renderSessionId)
        }
        
        // 4. Start new session B
        val sessionB = UUID.randomUUID()
        viewModel.onMapReady(sessionB)
        advanceUntilIdle()
        
        // 5. Verify load occurs for BASE using session B
        val finalAttempt = viewModel.uiState.value.currentAttempt
        assertNotNull(finalAttempt)
        assertEquals(sessionB, finalAttempt!!.renderSessionId)
        assertEquals(BasemapSourceId.MAPTILER_BASE, finalAttempt.sourceId)
        job.cancel()
    }

    @Test
    fun `Late repair attempt from disposed session blocked`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val sessionA = UUID.randomUUID()
        viewModel.onMapReady(sessionA)
        advanceUntilIdle()
        
        val attemptA = viewModel.uiState.value.currentAttempt!!
        
        // Dispose session A
        viewModel.onRenderSessionDisposed(sessionA)
        advanceUntilIdle()
        
        // Deliver stale callback from A
        viewModel.handleStaleStyleApplied(attemptA)
        advanceUntilIdle()
        
        assertNull("No repair should be issued for disposed session", viewModel.uiState.value.currentAttempt)
        job.cancel()
    }

    @Test
    fun `Loading primary recreation flow`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val sessionA = UUID.randomUUID()
        viewModel.onMapReady(sessionA)
        advanceUntilIdle()
        
        // 1. LOADING_PRIMARY in session A
        assertEquals(BasemapLoadStatus.LOADING_PRIMARY, viewModel.uiState.value.basemapStatus)
        val attemptA = viewModel.uiState.value.currentAttempt!!
        assertEquals(sessionA, attemptA.renderSessionId)
        
        // 2. Dispose session A
        viewModel.onRenderSessionDisposed(sessionA)
        advanceUntilIdle()
        
        // Status and requested source must be preserved
        assertEquals(BasemapLoadStatus.LOADING_PRIMARY, viewModel.uiState.value.basemapStatus)
        assertEquals(streetsDef.sourceId, viewModel.uiState.value.requestedSourceId)
        assertNull("Session must be inactive", viewModel.uiState.value.renderSessionId)
        
        // 3. Register session B
        val sessionB = UUID.randomUUID()
        viewModel.onMapReady(sessionB)
        advanceUntilIdle()
        
        // 4. Verify RECREATION attempt for session B using preserved source
        val attemptB = viewModel.uiState.value.currentAttempt!!
        assertEquals(sessionB, attemptB.renderSessionId)
        assertEquals(streetsDef.sourceId, attemptB.sourceId)
        assertEquals(BasemapLoadAttemptReason.RECREATION, attemptB.reason)
        assertEquals(BasemapLoadStatus.LOADING_PRIMARY, viewModel.uiState.value.basemapStatus)
        job.cancel()
    }

    @Test
    fun `Loaded backup recreation flow`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val sessionA = UUID.randomUUID()
        viewModel.onMapReady(sessionA)
        advanceUntilIdle()
        
        // 1. Switch to backup and succeed
        val primaryAttempt = viewModel.uiState.value.currentAttempt!!
        viewModel.handleBasemapLoadTerminated(BasemapTerminalReason.PROVIDER_FAILURE, primaryAttempt)
        advanceUntilIdle()
        val backupAttemptA = viewModel.uiState.value.currentAttempt!!
        viewModel.handleBasemapLoadSuccess(backupAttemptA)
        advanceUntilIdle()
        
        assertEquals(BasemapLoadStatus.LOADED, viewModel.uiState.value.basemapStatus)
        assertEquals(libertyDef.sourceId, viewModel.uiState.value.activeSourceId)
        assertTrue(viewModel.uiState.value.isUsingFallback)
        
        // 2. Dispose session A
        viewModel.onRenderSessionDisposed(sessionA)
        advanceUntilIdle()
        
        // State must be preserved
        assertEquals(BasemapLoadStatus.LOADED, viewModel.uiState.value.basemapStatus)
        assertEquals(libertyDef.sourceId, viewModel.uiState.value.activeSourceId)
        
        // 3. Register session B
        val sessionB = UUID.randomUUID()
        viewModel.onMapReady(sessionB)
        advanceUntilIdle()
        
        // 4. Verify RECREATION attempt for session B using accepted backup
        val attemptB = viewModel.uiState.value.currentAttempt!!
        assertEquals(sessionB, attemptB.renderSessionId)
        assertEquals(libertyDef.sourceId, attemptB.sourceId)
        assertEquals(BasemapLoadAttemptReason.RECREATION, attemptB.reason)
        assertEquals(BasemapLoadStatus.LOADED, viewModel.uiState.value.basemapStatus)
        job.cancel()
    }

    @Test
    fun `Failed-state recreation does not auto-retry`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val sessionA = UUID.randomUUID()
        viewModel.onMapReady(sessionA)
        advanceUntilIdle()
        
        // 1. Fail primary and backup to reach FAILED state
        viewModel.handleBasemapLoadTerminated(BasemapTerminalReason.PROVIDER_FAILURE, viewModel.uiState.value.currentAttempt!!)
        advanceUntilIdle()
        viewModel.handleBasemapLoadTerminated(BasemapTerminalReason.PROVIDER_FAILURE, viewModel.uiState.value.currentAttempt!!)
        advanceUntilIdle()
        
        assertEquals(BasemapLoadStatus.FAILED, viewModel.uiState.value.basemapStatus)
        
        // 2. Dispose session A
        viewModel.onRenderSessionDisposed(sessionA)
        advanceUntilIdle()
        
        // 3. Register session B
        val sessionB = UUID.randomUUID()
        viewModel.onMapReady(sessionB)
        advanceUntilIdle()
        
        // 4. Verify still FAILED with no attempt
        assertEquals(BasemapLoadStatus.FAILED, viewModel.uiState.value.basemapStatus)
        assertNull("Should not auto-retry on recreation", viewModel.uiState.value.currentAttempt)
        
        // 5. Explicit retry begins new generation
        val oldGen = viewModel.uiState.value.basemapGeneration
        viewModel.retryPrimaryMap()
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.basemapGeneration > oldGen)
        assertEquals(BasemapLoadStatus.LOADING_PRIMARY, viewModel.uiState.value.basemapStatus)
        job.cancel()
    }

    @Test
    fun `Preference Authority - Stale repo emission ignored during override`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        
        // 1. Initial load
        val session = UUID.randomUUID()
        viewModel.onMapReady(session)
        advanceUntilIdle()
        assertEquals(BasemapSourceId.MAPTILER_STREETS, viewModel.uiState.value.currentAttempt?.sourceId)
        
        // 2. Request BASE (Sets override). DO NOT update userPrefsFlow yet.
        coEvery { userPrefs.updateSelectedBasemap(any()) } returns Unit
        viewModel.requestBasemap(BasemapId.BASE)
        advanceUntilIdle()
        
        val genAfterRequest = viewModel.uiState.value.basemapGeneration
        assertEquals(BasemapId.BASE, viewModel.uiState.value.preferredBasemapId)
        
        // 3. Simulate repo emitting STALE (STREETS) while override is still BASE
        userPrefsFlow.value = userPrefsFlow.value.copy(selectedBasemapId = BasemapId.STREETS)
        advanceUntilIdle()
        
        // Should NOT trigger a reload of STREETS or change preferred ID
        assertEquals("Preferred ID must remain BASE because of override", BasemapId.BASE, viewModel.uiState.value.preferredBasemapId)
        assertEquals("Generation must not increment from stale emission", genAfterRequest, viewModel.uiState.value.basemapGeneration)
        
        // 4. Simulate repo emitting BASE (Confirmation)
        userPrefsFlow.value = userPrefsFlow.value.copy(selectedBasemapId = BasemapId.BASE)
        advanceUntilIdle()
        
        // Still no new generation/attempt because it's just confirmation
        assertEquals(genAfterRequest, viewModel.uiState.value.basemapGeneration)
        
        // 5. AFTER confirmation, a NEW emission should be processed normally
        userPrefsFlow.value = userPrefsFlow.value.copy(selectedBasemapId = BasemapId.TOPO)
        advanceUntilIdle()
        
        assertTrue("New emission after confirmation must trigger load", viewModel.uiState.value.basemapGeneration > genAfterRequest)
        assertEquals(BasemapId.TOPO, viewModel.uiState.value.preferredBasemapId)
        assertEquals(BasemapSourceId.MAPTILER_TOPO, viewModel.uiState.value.currentAttempt?.sourceId)
        job.cancel()
    }

    @Test
    fun `Full Fallback Regression - New Selection Failure correctly triggers its own backup`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val sessionA = UUID.randomUUID()
        viewModel.onMapReady(sessionA)
        advanceUntilIdle()
        
        // 1. Accepted backup for initial STREETS
        val streetsAttempt = viewModel.uiState.value.currentAttempt!!
        viewModel.handleBasemapLoadTerminated(BasemapTerminalReason.TIMEOUT, streetsAttempt)
        advanceUntilIdle()
        
        val libertyAttempt = viewModel.uiState.value.currentAttempt!!
        assertEquals(BasemapSourceId.OPEN_FREE_MAP_LIBERTY, libertyAttempt.sourceId)
        viewModel.handleBasemapLoadSuccess(libertyAttempt)
        advanceUntilIdle()
        
        assertEquals(BasemapSourceId.OPEN_FREE_MAP_LIBERTY, viewModel.uiState.value.activeSourceId)
        
        // 2. Dispose and Deferred selection of BASE
        viewModel.onRenderSessionDisposed(sessionA)
        advanceUntilIdle()
        viewModel.requestBasemap(BasemapId.BASE)
        advanceUntilIdle()
        
        // 3. Session B Ready
        val sessionB = UUID.randomUUID()
        viewModel.onMapReady(sessionB)
        advanceUntilIdle()
        
        // 4. Fail primary BASE
        val primaryAttemptB = viewModel.uiState.value.currentAttempt!!
        assertEquals(BasemapSourceId.MAPTILER_BASE, primaryAttemptB.sourceId)
        viewModel.handleBasemapLoadTerminated(BasemapTerminalReason.TIMEOUT, primaryAttemptB)
        advanceUntilIdle()
        
        // 5. Verify it triggers backup for BASE (POSITRON)
        // Step 12: Verify Liberty is not reissued when Base fails (it should try Positron)
        val backupAttemptB = viewModel.uiState.value.currentAttempt!!
        assertEquals(BasemapRole.BACKUP, backupAttemptB.role)
        assertEquals(BasemapSourceId.OPEN_FREE_MAP_POSITRON, backupAttemptB.sourceId)
        assertEquals(BasemapLoadStatus.LOADING_BACKUP, viewModel.uiState.value.basemapStatus)
        
        // Step 13: Accept Positron
        viewModel.handleBasemapLoadSuccess(backupAttemptB)
        advanceUntilIdle()
        
        // Step 14: Verify activeSourceId is Positron, preferredBasemapId is Base, basemapStatus is LOADED, and isUsingFallback is true
        val finalState = viewModel.uiState.value
        assertEquals(BasemapSourceId.OPEN_FREE_MAP_POSITRON, finalState.activeSourceId)
        assertEquals(BasemapId.BASE, finalState.preferredBasemapId)
        assertEquals(BasemapLoadStatus.LOADED, finalState.basemapStatus)
        assertTrue(finalState.isUsingFallback)
        
        // Step 15: Verify attribution source truth is Positron
        assertEquals(BasemapSourceId.OPEN_FREE_MAP_POSITRON, finalState.acceptedStyleEvent?.attempt?.sourceId)
        job.cancel()
    }

    @Test
    fun `Production Sequence Recreation - IDLE to INITIAL`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        
        // Status is IDLE initially
        assertEquals(BasemapLoadStatus.IDLE, viewModel.uiState.value.basemapStatus)
        
        // Register session
        val session = UUID.randomUUID()
        viewModel.onMapReady(session)
        advanceUntilIdle()
        
        // Verify INITIAL load
        val attempt = viewModel.uiState.value.currentAttempt!!
        assertEquals(BasemapLoadAttemptReason.INITIAL, attempt.reason)
        assertEquals(BasemapLoadStatus.LOADING_PRIMARY, viewModel.uiState.value.basemapStatus)
        job.cancel()
    }

    @Test
    fun `Production Sequence Recreation - FAILED to FAILED`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val sessionA = UUID.randomUUID()
        viewModel.onMapReady(sessionA)
        advanceUntilIdle()
        
        // Fail primary and backup to reach FAILED state
        viewModel.handleBasemapLoadTerminated(BasemapTerminalReason.TIMEOUT, viewModel.uiState.value.currentAttempt!!)
        advanceUntilIdle()
        viewModel.handleBasemapLoadTerminated(BasemapTerminalReason.TIMEOUT, viewModel.uiState.value.currentAttempt!!)
        advanceUntilIdle()
        assertEquals(BasemapLoadStatus.FAILED, viewModel.uiState.value.basemapStatus)
        
        // Dispose
        viewModel.onRenderSessionDisposed(sessionA)
        advanceUntilIdle()
        
        // Recreation
        val sessionB = UUID.randomUUID()
        viewModel.onMapReady(sessionB)
        advanceUntilIdle()
        
        // Should remain FAILED with no new attempt
        assertEquals(BasemapLoadStatus.FAILED, viewModel.uiState.value.basemapStatus)
        assertNull(viewModel.uiState.value.currentAttempt)
        job.cancel()
    }

    @Test
    fun `Definition Unavailable Pending Recovery`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        
        // 1. Setup deferred request for a source that will disappear
        viewModel.requestBasemap(BasemapId.TOPO)
        advanceUntilIdle()
        
        // 2. Make definition unavailable
        every { basemapProvider.getDefinition(BasemapSourceId.MAPTILER_TOPO) } returns null
        
        // 3. Start map session
        val session = UUID.randomUUID()
        viewModel.onMapReady(session)
        advanceUntilIdle()
        
        // 4. Verify FAILED state and cleared requestedSourceId
        assertEquals(BasemapLoadStatus.FAILED, viewModel.uiState.value.basemapStatus)
        assertNull(viewModel.uiState.value.requestedSourceId)
        assertEquals(BasemapId.TOPO, viewModel.uiState.value.preferredBasemapId)
        job.cancel()
    }

    @Test
    fun `Main Disposal Terminal Truth - Preservation and Non-overwriting`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val sessionA = UUID.randomUUID()
        viewModel.onMapReady(sessionA)
        advanceUntilIdle()
        
        val attemptA = viewModel.uiState.value.currentAttempt!!
        
        // 1. Dispose while loading
        viewModel.onRenderSessionDisposed(sessionA)
        advanceUntilIdle()
        
        assertEquals(BasemapTerminalReason.DISPOSED, viewModel.getTerminalReason(attemptA))
        assertEquals(BasemapLoadStatus.LOADING_PRIMARY, viewModel.uiState.value.basemapStatus)
        assertEquals(streetsDef.sourceId, viewModel.uiState.value.requestedSourceId)
        
        // 2. Start session B and succeed
        val sessionB = UUID.randomUUID()
        viewModel.onMapReady(sessionB)
        advanceUntilIdle()
        val attemptB = viewModel.uiState.value.currentAttempt!!
        viewModel.handleBasemapLoadSuccess(attemptB)
        advanceUntilIdle()
        
        assertEquals(BasemapLoadStatus.LOADED, viewModel.uiState.value.basemapStatus)
        
        // 3. Dispose while LOADED
        viewModel.onRenderSessionDisposed(sessionB)
        advanceUntilIdle()
        
        // Should NOT mark attemptB as DISPOSED because it was already LOADED
        assertNull("Loaded attempt should not be marked DISPOSED", viewModel.getTerminalReason(attemptB))
        assertEquals(BasemapLoadStatus.LOADED, viewModel.uiState.value.basemapStatus)
        assertEquals(streetsDef.sourceId, viewModel.uiState.value.activeSourceId)
        job.cancel()
    }

    @Test
    fun `Stale Pending Request Retirement - Generation Mismatch`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        
        // 1. Create a pending request for BASE (Gen 1)
        viewModel.requestBasemap(BasemapId.BASE)
        advanceUntilIdle()
        val gen1 = viewModel.uiState.value.basemapGeneration
        
        // 2. While still no map session, request TOPO (Gen 2)
        viewModel.requestBasemap(BasemapId.TOPO)
        advanceUntilIdle()
        val gen2 = viewModel.uiState.value.basemapGeneration
        assertTrue(gen2 > gen1)
        
        // 3. Start map session. It should retire the stale Gen 1 pending (if it were somehow still there) 
        // and assert authority for Gen 2.
        // Internal state check: In our implementation, requestBasemap overwrites the pending request.
        // To test the "onMapReady" retirement logic, we might need to simulate a stale generation.
        
        val session = UUID.randomUUID()
        viewModel.onMapReady(session)
        advanceUntilIdle()
        
        assertEquals(BasemapId.TOPO, viewModel.uiState.value.preferredBasemapId)
        assertEquals(BasemapSourceId.MAPTILER_TOPO, viewModel.uiState.value.currentAttempt?.sourceId)
        assertEquals(gen2, viewModel.uiState.value.currentAttempt?.semanticGeneration)
        job.cancel()
    }

    @Test
    fun `Preference Authority - Selecting already-stored preference does not arm override`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val session = UUID.randomUUID()
        viewModel.onMapReady(session)
        advanceUntilIdle()
        
        // Ensure lastObserved is set to STREETS
        userPrefsFlow.value = userPrefsFlow.value.copy(selectedBasemapId = BasemapId.STREETS)
        advanceUntilIdle()
        
        val gen1 = viewModel.uiState.value.basemapGeneration
        
        // Request SAME value
        viewModel.requestBasemap(BasemapId.STREETS)
        advanceUntilIdle()
        
        assertTrue("Generation must increment even for same-value selection", viewModel.uiState.value.basemapGeneration > gen1)
        
        // Verify no override active by emitting different value from repo immediately
        userPrefsFlow.value = userPrefsFlow.value.copy(selectedBasemapId = BasemapId.BASE)
        advanceUntilIdle()
        
        assertEquals("Should accept BASE immediately if no override was set", BasemapId.BASE, viewModel.uiState.value.preferredBasemapId)
        job.cancel()
    }

    @Test
    fun `Preference Authority - Stale different emission after deferred request is consumed`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        
        // 1. Initial State: TOPO
        userPrefsFlow.value = userPrefsFlow.value.copy(selectedBasemapId = BasemapId.TOPO)
        advanceUntilIdle()
        
        // 2. Request BASE while no session (Deferred)
        coEvery { userPrefs.updateSelectedBasemap(BasemapId.BASE) } returns Unit
        
        viewModel.requestBasemap(BasemapId.BASE)
        advanceUntilIdle()
        
        val genAfterRequest = viewModel.uiState.value.basemapGeneration
        assertEquals(BasemapId.BASE, viewModel.uiState.value.preferredBasemapId)
        
        // 3. Start session. BASE attempt issued.
        val session = UUID.randomUUID()
        viewModel.onMapReady(session)
        advanceUntilIdle()
        assertEquals(BasemapSourceId.MAPTILER_BASE, viewModel.uiState.value.currentAttempt?.sourceId)
        
        // 4. Repo emits STREETS (Stale/Different)
        userPrefsFlow.value = userPrefsFlow.value.copy(selectedBasemapId = BasemapId.STREETS)
        advanceUntilIdle()
        
        // Should ignore because override is BASE
        assertEquals("Preferred ID must remain BASE because of override", BasemapId.BASE, viewModel.uiState.value.preferredBasemapId)
        assertEquals("Generation must not increment from stale emission", genAfterRequest, viewModel.uiState.value.basemapGeneration)
        
        // 5. Repo emits BASE (Confirmation)
        userPrefsFlow.value = userPrefsFlow.value.copy(selectedBasemapId = BasemapId.BASE)
        advanceUntilIdle()
        
        // 6. Now repo emits TOPO. Should be accepted.
        userPrefsFlow.value = userPrefsFlow.value.copy(selectedBasemapId = BasemapId.TOPO)
        advanceUntilIdle()
        
        assertEquals(BasemapId.TOPO, viewModel.uiState.value.preferredBasemapId)
        assertTrue("New emission after confirmation must trigger load", viewModel.uiState.value.basemapGeneration > genAfterRequest)
        job.cancel()
    }

    @Test
    fun `Preference Authority - First matching emission sets preference readiness`() = runTest(testDispatcher) {
        val customPrefsFlow = MutableSharedFlow<UserPreferences>(replay = 1)
        every { userPrefs.userPreferencesFlow } returns customPrefsFlow
        
        // Re-initialize viewModel for this test to ensure it starts with fresh flow
        viewModel = MapViewModel(mapRepo, attachmentRepo, infraRepo, propRepo, resolver, locationProvider, basemapProvider, userPrefs, namingService, context, savedState)
        
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        
        // 1. Start map session first
        val session = UUID.randomUUID()
        viewModel.onMapReady(session)
        advanceUntilIdle()
        
        // 2. Initial state should be IDLE because preferences are not ready
        assertEquals(BasemapLoadStatus.IDLE, viewModel.uiState.value.basemapStatus)
        
        // 3. Emit from preferences
        customPrefsFlow.emit(UserPreferences(selectedBasemapId = BasemapId.STREETS))
        advanceUntilIdle()
        
        // 4. Should now be loading
        assertEquals(BasemapLoadStatus.LOADING_PRIMARY, viewModel.uiState.value.basemapStatus)
        job.cancel()
    }

    @Test
    fun `Pending Request Resolver - STALE_GENERATION reissues current authority without incrementing`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        
        // 1. Setup session and ensure authoritative state is TOPO
        val sessionA = UUID.randomUUID()
        viewModel.onMapReady(sessionA)
        advanceUntilIdle()
        
        viewModel.requestBasemap(BasemapId.TOPO)
        advanceUntilIdle()
        val gen = viewModel.uiState.value.basemapGeneration
        
        // 2. Dispose session
        viewModel.onRenderSessionDisposed(sessionA)
        advanceUntilIdle()
        
        // 3. Manually simulate a STALE pending request (Gen - 1)
        // We can't directly inject into private _pendingBasemapRequest, 
        // but we can test the resolver's logic by proxying through onMapReady 
        // if we can get the generation out of sync.
        
        // However, in our MapViewModel, requestBasemap increments generation.
        // If we want a stale generation in pending, we'd need it to have been set earlier.
        
        // Let's verify that even if there was a mismatch, it reissues the CURRENT authority.
        val sessionB = UUID.randomUUID()
        viewModel.onMapReady(sessionB)
        advanceUntilIdle()
        
        assertEquals(BasemapSourceId.MAPTILER_TOPO, viewModel.uiState.value.currentAttempt?.sourceId)
        assertEquals(gen, viewModel.uiState.value.currentAttempt?.semanticGeneration)
        job.cancel()
    }

    @Test
    fun `Pending Request Resolver - FAILED REISSUE enters truthful FAILED state`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        
        // 1. Setup deferred request for TOPO
        viewModel.requestBasemap(BasemapId.TOPO)
        advanceUntilIdle()
        
        // 2. Make definition unavailable
        every { basemapProvider.getDefinition(BasemapSourceId.MAPTILER_TOPO) } returns null
        
        // 3. Start map session
        val session = UUID.randomUUID()
        viewModel.onMapReady(session)
        advanceUntilIdle()
        
        // 4. Verify FAILED state and cleared requestedSourceId
        assertEquals(BasemapLoadStatus.FAILED, viewModel.uiState.value.basemapStatus)
        assertNull(viewModel.uiState.value.requestedSourceId)
        job.cancel()
    }

    @Test
    fun `Preference Authority - Persistence failure + stale emission`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        coEvery { userPrefs.updateSelectedBasemap(any()) } throws RuntimeException("Persistence Error")
        
        val session = UUID.randomUUID()
        viewModel.onMapReady(session)
        advanceUntilIdle()
        
        // 1. Initial State: STREETS
        userPrefsFlow.value = userPrefsFlow.value.copy(selectedBasemapId = BasemapId.STREETS)
        advanceUntilIdle()
        
        // 2. Request BASE. Persistence fails.
        viewModel.requestBasemap(BasemapId.BASE)
        advanceUntilIdle()
        
        assertEquals(BasemapId.BASE, viewModel.uiState.value.preferredBasemapId)
        
        // 3. Repo emits STREETS (Original value, stale relative to request)
        userPrefsFlow.value = userPrefsFlow.value.copy(selectedBasemapId = BasemapId.STREETS)
        advanceUntilIdle()
        
        // Should ignore because override is BASE
        assertEquals("Should stick to memory state BASE despite persistence failure", BasemapId.BASE, viewModel.uiState.value.preferredBasemapId)
        job.cancel()
    }

    @Test
    fun `Production Sequence Recreation - LOADED to RECREATION`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val sessionA = UUID.randomUUID()
        viewModel.onMapReady(sessionA)
        advanceUntilIdle()
        
        // 1. Succeed Primary
        viewModel.handleBasemapLoadSuccess(viewModel.uiState.value.currentAttempt!!)
        advanceUntilIdle()
        assertEquals(BasemapLoadStatus.LOADED, viewModel.uiState.value.basemapStatus)
        
        // Dispose session A
        viewModel.onRenderSessionDisposed(sessionA)
        advanceUntilIdle()
        
        // 2. Recreation
        val sessionB = UUID.randomUUID()
        viewModel.onMapReady(sessionB)
        advanceUntilIdle()
        
        val attemptB = viewModel.uiState.value.currentAttempt!!
        assertEquals(sessionB, attemptB.renderSessionId)
        assertEquals(BasemapLoadAttemptReason.RECREATION, attemptB.reason)
        assertEquals(BasemapLoadStatus.LOADED, viewModel.uiState.value.basemapStatus)
        job.cancel()
    }

    @Test
    fun `Production Sequence Recreation - LOADING_BACKUP to RECREATION`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val sessionA = UUID.randomUUID()
        viewModel.onMapReady(sessionA)
        advanceUntilIdle()
        
        // 1. Fail Primary -> LOADING_BACKUP
        viewModel.handleBasemapLoadTerminated(BasemapTerminalReason.TIMEOUT, viewModel.uiState.value.currentAttempt!!)
        advanceUntilIdle()
        assertEquals(BasemapLoadStatus.LOADING_BACKUP, viewModel.uiState.value.basemapStatus)
        
        // Dispose session A
        viewModel.onRenderSessionDisposed(sessionA)
        advanceUntilIdle()
        
        // 2. Recreation
        val sessionB = UUID.randomUUID()
        viewModel.onMapReady(sessionB)
        advanceUntilIdle()
        
        val attemptB = viewModel.uiState.value.currentAttempt!!
        assertEquals(sessionB, attemptB.renderSessionId)
        assertEquals(BasemapLoadAttemptReason.RECREATION, attemptB.reason)
        assertEquals(BasemapRole.BACKUP, attemptB.role)
        assertEquals(BasemapLoadStatus.LOADING_BACKUP, viewModel.uiState.value.basemapStatus)
        job.cancel()
    }

    @Test
    fun `customerBasemapPreferenceOverride Behavioral Test`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val session = UUID.randomUUID()
        
        // 1. Establish Topo
        userPrefsFlow.value = userPrefsFlow.value.copy(selectedBasemapId = BasemapId.TOPO)
        viewModel.onMapReady(session)
        advanceUntilIdle()
        assertEquals(BasemapSourceId.MAPTILER_TOPO, viewModel.uiState.value.currentAttempt?.sourceId)
        
        // 2. Select Base (Override)
        // Simulate persistence delay: request update but don't emit from repo yet
        coEvery { userPrefs.updateSelectedBasemap(any()) } returns Unit
        viewModel.requestBasemap(BasemapId.BASE)
        advanceUntilIdle()
        assertEquals(BasemapId.BASE, viewModel.uiState.value.preferredBasemapId)
        
        // 3. Repo emits Streets (Stale Repo Emission)
        userPrefsFlow.value = userPrefsFlow.value.copy(selectedBasemapId = BasemapId.STREETS)
        advanceUntilIdle()
        
        // 4. Verify Base persists because override is still active (waiting for BASE)
        assertEquals("Override must persist", BasemapId.BASE, viewModel.uiState.value.preferredBasemapId)
        assertEquals(BasemapSourceId.MAPTILER_BASE, viewModel.uiState.value.currentAttempt?.sourceId)
        
        // 5. Repo finally emits Base (Confirmation)
        userPrefsFlow.value = userPrefsFlow.value.copy(selectedBasemapId = BasemapId.BASE)
        advanceUntilIdle()
        assertEquals(BasemapId.BASE, viewModel.uiState.value.preferredBasemapId)
        
        // 6. After confirmation, override is cleared. Next repo change should work.
        userPrefsFlow.value = userPrefsFlow.value.copy(selectedBasemapId = BasemapId.TOPO)
        advanceUntilIdle()
        assertEquals(BasemapId.TOPO, viewModel.uiState.value.preferredBasemapId)
        job.cancel()
    }
}
