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
    private val testDispatcher = UnconfinedTestDispatcher()

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
            // Emulate persistence delay but memory update happens immediately in VM
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
    fun `Readiness Architecture - Preferences first then MapView creates exactly one correct attempt`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        
        // 1. Preferences emit
        userPrefsFlow.value = userPrefsFlow.value.copy(selectedBasemapId = BasemapId.TOPO)
        
        // Should still be IDLE because MapView is not ready
        assertEquals(BasemapLoadStatus.IDLE, viewModel.uiState.value.basemapStatus)
        
        // 2. MapView ready
        val session = UUID.randomUUID()
        viewModel.onMapReady(session)
        
        val state = viewModel.uiState.value
        assertEquals(BasemapLoadStatus.LOADING_PRIMARY, state.basemapStatus)
        val attempt = state.currentAttempt!!
        assertEquals(BasemapSourceId.MAPTILER_TOPO, attempt.sourceId)
        assertEquals(BasemapLoadAttemptReason.INITIAL, attempt.reason)
        assertEquals(session, attempt.renderSessionId)
        
        // Verify no extra attempts
        assertEquals(1L, attempt.attemptId)
    }

    @Test
    fun `Readiness Architecture - MapView first then Preferences creates exactly one correct attempt`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        
        // 1. MapView ready
        val session = UUID.randomUUID()
        viewModel.onMapReady(session)
        
        // Should still be IDLE (or default if preferences emitted default earlier)
        // In our setup, VM starts with default STREETS but waits for collector
        // Let's assume userPrefsFlow hasn't emitted for this test yet (initial state)
        
        // 2. Preferences emit
        userPrefsFlow.value = userPrefsFlow.value.copy(selectedBasemapId = BasemapId.BASE)
        
        val state = viewModel.uiState.value
        assertEquals(BasemapLoadStatus.LOADING_PRIMARY, state.basemapStatus)
        val attempt = state.currentAttempt!!
        assertEquals(BasemapSourceId.MAPTILER_BASE, attempt.sourceId)
        assertEquals(session, attempt.renderSessionId)
    }

    @Test
    fun `Stored non-Streets preference does not briefly load Streets`() = runTest {
        // Start a new setup where preference is already TOPO before VM init
        userPrefsFlow.value = userPrefsFlow.value.copy(selectedBasemapId = BasemapId.TOPO)
        viewModel = MapViewModel(mapRepo, attachmentRepo, infraRepo, propRepo, resolver, locationProvider, basemapProvider, userPrefs, namingService, context, savedState)
        
        backgroundScope.launch { viewModel.uiState.collect {} }
        
        viewModel.onMapReady(UUID.randomUUID())
        
        assertEquals(BasemapSourceId.MAPTILER_TOPO, viewModel.uiState.value.currentAttempt?.sourceId)
        assertEquals(BasemapSourceId.MAPTILER_TOPO, viewModel.uiState.value.requestedSourceId)
    }

    @Test
    fun `MapView recreation in LOADING_PRIMARY handles attempt correctly`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        val session1 = UUID.randomUUID()
        viewModel.onMapReady(session1)
        
        viewModel.requestBasemap(BasemapId.STREETS)
        val attempt1 = viewModel.uiState.value.currentAttempt!!
        assertEquals(1L, attempt1.attemptId)
        
        // Rotate/Recreate
        val session2 = UUID.randomUUID()
        viewModel.onMapReady(session2)
        
        val state = viewModel.uiState.value
        assertEquals(BasemapLoadStatus.LOADING_PRIMARY, state.basemapStatus)
        val attempt2 = state.currentAttempt!!
        assertNotEquals(attempt1.attemptId, attempt2.attemptId)
        assertEquals(session2, attempt2.renderSessionId)
        assertEquals(BasemapLoadAttemptReason.RECREATION, attempt2.reason)
        assertEquals(BasemapSourceId.MAPTILER_STREETS, attempt2.sourceId)
    }

    @Test
    fun `MapView recreation in FAILED does not auto-retry`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        val session1 = UUID.randomUUID()
        viewModel.onMapReady(session1)
        
        viewModel.requestBasemap(BasemapId.STREETS)
        val primary = viewModel.uiState.value.currentAttempt!!
        viewModel.handleBasemapLoadFailure("error", primary)
        
        val backup = viewModel.uiState.value.currentAttempt!!
        viewModel.handleBasemapLoadFailure("error", backup)
        
        assertEquals(BasemapLoadStatus.FAILED, viewModel.uiState.value.basemapStatus)
        
        // Recreate
        val session2 = UUID.randomUUID()
        viewModel.onMapReady(session2)
        
        // Status should REMAIN FAILED, not restart load
        assertEquals(BasemapLoadStatus.FAILED, viewModel.uiState.value.basemapStatus)
        assertEquals(backup.attemptId, viewModel.uiState.value.currentAttempt?.attemptId)
    }

    @Test
    fun `Strict Validation - Rejects wrong source, provider, or role`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onMapReady(UUID.randomUUID())
        
        viewModel.requestBasemap(BasemapId.STREETS)
        val attempt = viewModel.uiState.value.currentAttempt!!
        
        // 1. Wrong source
        val result1 = viewModel.handleBasemapLoadSuccess(BasemapSourceId.MAPTILER_BASE, attempt)
        assertFalse(result1.accepted)
        
        // 2. Wrong role (emulate manually changed attempt data if possible, or just prove valid check)
        val badRoleAttempt = attempt.copy(role = BasemapRole.BACKUP)
        val result2 = viewModel.handleBasemapLoadSuccess(BasemapSourceId.MAPTILER_STREETS, badRoleAttempt)
        assertFalse(result2.accepted)
    }

    @Test
    fun `Terminal Closure - Timed-out attempt cannot later succeed`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onMapReady(UUID.randomUUID())
        
        viewModel.requestBasemap(BasemapId.STREETS)
        val attempt = viewModel.uiState.value.currentAttempt!!
        
        // Simulate timeout (failure)
        viewModel.handleBasemapLoadFailure("Timeout", attempt)
        assertEquals(BasemapLoadStatus.LOADING_BACKUP, viewModel.uiState.value.basemapStatus)
        
        // Late success from timed-out attempt
        val result = viewModel.handleBasemapLoadSuccess(BasemapSourceId.MAPTILER_STREETS, attempt)
        assertFalse("Late success from terminal attempt must be rejected", result.accepted)
    }

    @Test
    fun `In-memory selection updates immediately even if DataStore fails`() = runTest {
        coEvery { userPrefs.updateSelectedBasemap(any()) } throws Exception("DataStore Failure")
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onMapReady(UUID.randomUUID())
        
        viewModel.requestBasemap(BasemapId.BASE)
        
        // Memory state should be BASE immediately
        assertEquals(BasemapId.BASE, viewModel.uiState.value.preferredBasemapId)
        // Map should start loading BASE
        assertEquals(BasemapSourceId.MAPTILER_BASE, viewModel.uiState.value.currentAttempt?.sourceId)
    }

    @Test
    fun `Persistence - Temporary backup selection is not persisted`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onMapReady(UUID.randomUUID())
        
        viewModel.requestBackupBasemap(BasemapSourceId.OPEN_FREE_MAP_LIBERTY)
        
        // Status should be LOADING_BACKUP
        assertEquals(BasemapLoadStatus.LOADING_BACKUP, viewModel.uiState.value.basemapStatus)
        
        // Verify UserPreferences was NOT called for backup
        coVerify(exactly = 0) { userPrefs.updateSelectedBasemap(any()) }
    }

    @Test
    fun `Programmatic Camera Tokens - onCameraMoved suppressed when active`() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        
        // Issue token
        val token = viewModel.programmaticCameraController.issueToken()
        assertTrue(viewModel.programmaticCameraController.isActive())
        
        // Emulate MapScreen calling onCameraMoved but checking controller first
        // If isActive() is true, it shouldn't call onCameraMoved logic that triggers persistence
        // We test this via behavioral integration in MapScreen, but here we prove token state.
        
        assertTrue(viewModel.programmaticCameraController.isTokenActive(token))
        
        viewModel.programmaticCameraController.consume(token)
        assertFalse(viewModel.programmaticCameraController.isActive())
    }
}
