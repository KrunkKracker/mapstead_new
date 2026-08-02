package com.jumastappworks.mapstead.ui.mapping

import com.jumastappworks.mapstead.data.mapping.*
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SecondaryMapValidationTest {

    private val provider = mockk<BasemapProvider>(relaxed = true)
    private val renderSessionId = UUID.randomUUID()
    
    @Test
    fun `Secondary Basemap Controller handles successful primary load`() {
        val controller = SecondaryBasemapController(renderSessionId, provider)
        val streetsDef = BasemapDefinition(BasemapSourceId.MAPTILER_STREETS, BasemapProviderType.MAPTILER, BasemapRole.PRIMARY, "url", 0, 0, true, BasemapId.STREETS)
        every { provider.getPrimaryBasemaps() } returns listOf(streetsDef)
        every { provider.getDefinition(BasemapSourceId.MAPTILER_STREETS) } returns streetsDef

        // 1. Start load
        val attempt = controller.startLoad(BasemapId.STREETS)
        assertNotNull(attempt)
        assertEquals(BasemapSourceId.MAPTILER_STREETS, controller.requestedSourceId)
        assertEquals(SecondaryMapStatus.LOADING_PRIMARY, controller.currentStatus)
        
        // 2. Success
        val action = controller.handleSuccess(attempt!!)
        assertTrue(action is SecondaryControllerAction.Accepted)
        assertEquals(BasemapSourceId.MAPTILER_STREETS, controller.acceptedSourceId)
        assertNull(controller.requestedSourceId)
        assertEquals(SecondaryMapStatus.LOADED_PRIMARY, controller.currentStatus)
    }

    @Test
    fun `Secondary Controller handles direct backup-only case`() {
        val controller = SecondaryBasemapController(renderSessionId, provider)
        every { provider.getPrimaryBasemaps() } returns emptyList()
        every { provider.resolveDefaultBackup(BasemapId.STREETS) } returns BasemapSourceId.OPEN_FREE_MAP_LIBERTY
        val libertyDef = BasemapDefinition(BasemapSourceId.OPEN_FREE_MAP_LIBERTY, BasemapProviderType.OPEN_FREE_MAP, BasemapRole.BACKUP, "url", 0, 0, true)
        every { provider.getDefinition(BasemapSourceId.OPEN_FREE_MAP_LIBERTY) } returns libertyDef

        val attempt = controller.startLoad(BasemapId.STREETS)
        assertNotNull(attempt)
        assertEquals(BasemapSourceId.OPEN_FREE_MAP_LIBERTY, controller.requestedSourceId)
        assertEquals(BasemapRole.BACKUP, attempt!!.role)
        assertEquals(BasemapLoadAttemptReason.BACKUP, attempt.reason)
        assertEquals(SecondaryMapStatus.LOADING_BACKUP, controller.currentStatus)
    }

    @Test
    fun `Primary failure triggers backup with different attempt ID`() {
        val controller = SecondaryBasemapController(renderSessionId, provider)
        val streetsDef = BasemapDefinition(BasemapSourceId.MAPTILER_STREETS, BasemapProviderType.MAPTILER, BasemapRole.PRIMARY, "url", 0, 0, true, BasemapId.STREETS, BasemapSourceId.OPEN_FREE_MAP_LIBERTY)
        val libertyDef = BasemapDefinition(BasemapSourceId.OPEN_FREE_MAP_LIBERTY, BasemapProviderType.OPEN_FREE_MAP, BasemapRole.BACKUP, "url", 0, 0, true)
        
        every { provider.getPrimaryBasemaps() } returns listOf(streetsDef)
        every { provider.getDefinition(BasemapSourceId.MAPTILER_STREETS) } returns streetsDef
        every { provider.getDefinition(BasemapSourceId.OPEN_FREE_MAP_LIBERTY) } returns libertyDef
        every { provider.resolveDefaultBackup(BasemapId.STREETS) } returns BasemapSourceId.OPEN_FREE_MAP_LIBERTY

        val primaryAttempt = controller.startLoad(BasemapId.STREETS)!!
        
        // 2. Fail primary
        val action = controller.handleTerminated(BasemapTerminalReason.TIMEOUT, primaryAttempt, BasemapId.STREETS)
        
        assertTrue("Should have returned LoadAttempt for backup", action is SecondaryControllerAction.LoadAttempt)
        val backupAttempt = (action as SecondaryControllerAction.LoadAttempt).attempt
        assertNotEquals("Backup should have different ID", primaryAttempt.attemptId, backupAttempt.attemptId)
        assertEquals(BasemapSourceId.OPEN_FREE_MAP_LIBERTY, controller.requestedSourceId)
        assertEquals(SecondaryMapStatus.LOADING_BACKUP, controller.currentStatus)
    }

    @Test
    fun `Late primary completion after backup start triggers repair`() {
        val controller = SecondaryBasemapController(renderSessionId, provider)
        val streetsDef = BasemapDefinition(BasemapSourceId.MAPTILER_STREETS, BasemapProviderType.MAPTILER, BasemapRole.PRIMARY, "url", 0, 0, true, BasemapId.STREETS, BasemapSourceId.OPEN_FREE_MAP_LIBERTY)
        val libertyDef = BasemapDefinition(BasemapSourceId.OPEN_FREE_MAP_LIBERTY, BasemapProviderType.OPEN_FREE_MAP, BasemapRole.BACKUP, "url", 0, 0, true)
        
        every { provider.getPrimaryBasemaps() } returns listOf(streetsDef)
        every { provider.getDefinition(BasemapSourceId.MAPTILER_STREETS) } returns streetsDef
        every { provider.getDefinition(BasemapSourceId.OPEN_FREE_MAP_LIBERTY) } returns libertyDef
        every { provider.resolveDefaultBackup(BasemapId.STREETS) } returns BasemapSourceId.OPEN_FREE_MAP_LIBERTY

        val primaryAttempt = controller.startLoad(BasemapId.STREETS)!!
        controller.handleTerminated(BasemapTerminalReason.TIMEOUT, primaryAttempt, BasemapId.STREETS)
        
        // Late primary success arrives
        val action = controller.handleSuccess(primaryAttempt)
        
        // Validation fails because ID mismatch (current is backup)
        // Should trigger repair for current authoritative source (LIBERTY)
        assertTrue("Should return LoadAttempt for repair", action is SecondaryControllerAction.LoadAttempt)
        val repairAttempt = (action as SecondaryControllerAction.LoadAttempt).attempt
        assertEquals(BasemapSourceId.OPEN_FREE_MAP_LIBERTY, repairAttempt.sourceId)
    }

    @Test
    fun `Repair epoch in secondary controller prevents loop`() {
        val controller = SecondaryBasemapController(renderSessionId, provider)
        val streetsDef = BasemapDefinition(BasemapSourceId.MAPTILER_STREETS, BasemapProviderType.MAPTILER, BasemapRole.PRIMARY, "url", 0, 0, true, BasemapId.STREETS)
        every { provider.getPrimaryBasemaps() } returns listOf(streetsDef)
        every { provider.getDefinition(BasemapSourceId.MAPTILER_STREETS) } returns streetsDef

        val attempt1 = controller.startLoad(BasemapId.STREETS)!!
        
        // Deliver late stale callback
        val staleAttempt = attempt1.copy(attemptId = 999) 
        val action1 = controller.handleStaleStyleApplied(staleAttempt)
        assertTrue("First repair should be issued", action1 is SecondaryControllerAction.LoadAttempt)
        
        val repairAttempt = (action1 as SecondaryControllerAction.LoadAttempt).attempt
        
        // Repair succeeds
        controller.handleSuccess(repairAttempt)
        
        // Deliver another stale callback for same state. Should be ignored because epoch EXHAUSTED.
        val action2 = controller.handleStaleStyleApplied(staleAttempt)
        assertTrue("Second repair should be ignored after exhaustion", action2 is SecondaryControllerAction.Ignored)
    }

    @Test
    fun `Terminal repair exhausts epoch`() {
        val controller = SecondaryBasemapController(renderSessionId, provider)
        val streetsDef = BasemapDefinition(BasemapSourceId.MAPTILER_STREETS, BasemapProviderType.MAPTILER, BasemapRole.PRIMARY, "url", 0, 0, true, BasemapId.STREETS)
        every { provider.getPrimaryBasemaps() } returns listOf(streetsDef)
        every { provider.getDefinition(BasemapSourceId.MAPTILER_STREETS) } returns streetsDef

        val attempt1 = controller.startLoad(BasemapId.STREETS)!!
        val action1 = controller.handleStaleStyleApplied(attempt1.copy(attemptId = 999))
        val repairAttempt = (action1 as SecondaryControllerAction.LoadAttempt).attempt
        
        // Repair fails
        controller.handleTerminated(BasemapTerminalReason.PROVIDER_FAILURE, repairAttempt, BasemapId.STREETS)
        
        // Deliver stale again
        val action2 = controller.handleStaleStyleApplied(attempt1.copy(attemptId = 999))
        assertTrue("Epoch should be exhausted after terminal failure", action2 is SecondaryControllerAction.Ignored)
    }

    @Test
    fun `Repair success updates acceptedSourceId and status`() {
        val controller = SecondaryBasemapController(renderSessionId, provider)
        val streetsDef = BasemapDefinition(BasemapSourceId.MAPTILER_STREETS, BasemapProviderType.MAPTILER, BasemapRole.PRIMARY, "url", 0, 0, true, BasemapId.STREETS)
        every { provider.getPrimaryBasemaps() } returns listOf(streetsDef)
        every { provider.getDefinition(BasemapSourceId.MAPTILER_STREETS) } returns streetsDef

        val attempt1 = controller.startLoad(BasemapId.STREETS)!!
        controller.handleSuccess(attempt1)
        
        // Stale arrives
        val action = controller.handleStaleStyleApplied(attempt1.copy(attemptId = 999))
        assertTrue(action is SecondaryControllerAction.LoadAttempt)
        val repairAttempt = (action as SecondaryControllerAction.LoadAttempt).attempt
        assertEquals(SecondaryMapStatus.LOADING_PRIMARY, controller.currentStatus)
        assertEquals(BasemapSourceId.MAPTILER_STREETS, controller.requestedSourceId)
        
        // Repair success
        controller.handleSuccess(repairAttempt)
        assertEquals(SecondaryMapStatus.LOADED_PRIMARY, controller.currentStatus)
        assertEquals(BasemapSourceId.MAPTILER_STREETS, controller.acceptedSourceId)
        assertNull(controller.requestedSourceId)
    }

    @Test
    fun `Repair failure transitions to FAILED and exhausts epoch`() {
        val controller = SecondaryBasemapController(renderSessionId, provider)
        val streetsDef = BasemapDefinition(BasemapSourceId.MAPTILER_STREETS, BasemapProviderType.MAPTILER, BasemapRole.PRIMARY, "url", 0, 0, true, BasemapId.STREETS)
        every { provider.getPrimaryBasemaps() } returns listOf(streetsDef)
        every { provider.getDefinition(BasemapSourceId.MAPTILER_STREETS) } returns streetsDef

        val attempt1 = controller.startLoad(BasemapId.STREETS)!!
        controller.handleSuccess(attempt1)
        
        val action = controller.handleStaleStyleApplied(attempt1.copy(attemptId = 999))
        val repairAttempt = (action as SecondaryControllerAction.LoadAttempt).attempt
        
        // Repair fails
        controller.handleTerminated(BasemapTerminalReason.TIMEOUT, repairAttempt, BasemapId.STREETS)
        assertEquals(SecondaryMapStatus.FAILED, controller.currentStatus)
        
        // Verify no second repair issued (epoch exhausted)
        val action2 = controller.handleStaleStyleApplied(attempt1.copy(attemptId = 999))
        assertTrue(action2 is SecondaryControllerAction.Ignored)
    }

    @Test
    fun `Dispose while primary loading records DISPOSED`() {
        val controller = SecondaryBasemapController(renderSessionId, provider)
        val streetsDef = BasemapDefinition(BasemapSourceId.MAPTILER_STREETS, BasemapProviderType.MAPTILER, BasemapRole.PRIMARY, "url", 0, 0, true, BasemapId.STREETS)
        every { provider.getPrimaryBasemaps() } returns listOf(streetsDef)
        every { provider.getDefinition(BasemapSourceId.MAPTILER_STREETS) } returns streetsDef

        val attempt = controller.startLoad(BasemapId.STREETS)!!
        controller.dispose()
        
        assertTrue(controller.isDisposed)
        val debug = controller.getDebugState()
        assertEquals(BasemapTerminalReason.DISPOSED, debug.terminalReasons[BasemapAttemptKey(attempt.semanticGeneration, attempt.attemptId, attempt.renderSessionId, attempt.sourceId)])
        assertNull(controller.requestedSourceId)
    }

    @Test
    fun `Dispose does not overwrite earlier terminal reason`() {
        val controller = SecondaryBasemapController(renderSessionId, provider)
        val streetsDef = BasemapDefinition(BasemapSourceId.MAPTILER_STREETS, BasemapProviderType.MAPTILER, BasemapRole.PRIMARY, "url", 0, 0, true, BasemapId.STREETS)
        every { provider.getPrimaryBasemaps() } returns listOf(streetsDef)
        every { provider.getDefinition(BasemapSourceId.MAPTILER_STREETS) } returns streetsDef

        val attempt = controller.startLoad(BasemapId.STREETS)!!
        controller.handleTerminated(BasemapTerminalReason.TIMEOUT, attempt, BasemapId.STREETS)
        
        controller.dispose()
        
        val debug = controller.getDebugState()
        val key = BasemapAttemptKey(attempt.semanticGeneration, attempt.attemptId, attempt.renderSessionId, attempt.sourceId)
        assertEquals(BasemapTerminalReason.TIMEOUT, debug.terminalReasons[key])
    }

    @Test
    fun `Dispose does not mark accepted success as DISPOSED`() {
        val controller = SecondaryBasemapController(renderSessionId, provider)
        val streetsDef = BasemapDefinition(BasemapSourceId.MAPTILER_STREETS, BasemapProviderType.MAPTILER, BasemapRole.PRIMARY, "url", 0, 0, true, BasemapId.STREETS)
        every { provider.getPrimaryBasemaps() } returns listOf(streetsDef)
        every { provider.getDefinition(BasemapSourceId.MAPTILER_STREETS) } returns streetsDef

        val attempt = controller.startLoad(BasemapId.STREETS)!!
        controller.handleSuccess(attempt)
        
        controller.dispose()
        
        val debug = controller.getDebugState()
        val key = BasemapAttemptKey(attempt.semanticGeneration, attempt.attemptId, attempt.renderSessionId, attempt.sourceId)
        assertNull("Successful attempt should not have terminal reason", debug.terminalReasons[key])
    }

    @Test
    fun `Late callback after disposal is ignored`() {
        val controller = SecondaryBasemapController(renderSessionId, provider)
        val streetsDef = BasemapDefinition(BasemapSourceId.MAPTILER_STREETS, BasemapProviderType.MAPTILER, BasemapRole.PRIMARY, "url", 0, 0, true, BasemapId.STREETS)
        every { provider.getPrimaryBasemaps() } returns listOf(streetsDef)
        every { provider.getDefinition(BasemapSourceId.MAPTILER_STREETS) } returns streetsDef

        val attempt = controller.startLoad(BasemapId.STREETS)!!
        controller.dispose()
        
        val action = controller.handleSuccess(attempt)
        assertEquals(SecondaryControllerAction.Ignored, action)
        assertNull(controller.acceptedSourceId)
        assertEquals(SecondaryMapStatus.IDLE, controller.currentStatus)
    }

    @Test
    fun `New render session starts fresh attempt even if BasemapId same`() {
        // This is primarily verified via the Composable LaunchedEffect keys (renderSessionId)
        // but we can verify the controller state
        val controller1 = SecondaryBasemapController(UUID.randomUUID(), provider)
        val streetsDef = BasemapDefinition(BasemapSourceId.MAPTILER_STREETS, BasemapProviderType.MAPTILER, BasemapRole.PRIMARY, "url", 0, 0, true, BasemapId.STREETS)
        every { provider.getPrimaryBasemaps() } returns listOf(streetsDef)
        every { provider.getDefinition(BasemapSourceId.MAPTILER_STREETS) } returns streetsDef

        val attempt1 = controller1.startLoad(BasemapId.STREETS)!!
        assertEquals(1L, attempt1.attemptId)
        
        val controller2 = SecondaryBasemapController(UUID.randomUUID(), provider)
        val attempt2 = controller2.startLoad(BasemapId.STREETS)!!
        assertEquals(1L, attempt2.attemptId)
        assertNotEquals(controller1.renderSessionId, controller2.renderSessionId)
    }

    @Test
    fun `Dispose while LOADED does not record DISPOSED terminal reason`() {
        val controller = SecondaryBasemapController(renderSessionId, provider)
        val streetsDef = BasemapDefinition(BasemapSourceId.MAPTILER_STREETS, BasemapProviderType.MAPTILER, BasemapRole.PRIMARY, "url", 0, 0, true, BasemapId.STREETS)
        every { provider.getPrimaryBasemaps() } returns listOf(streetsDef)
        every { provider.getDefinition(BasemapSourceId.MAPTILER_STREETS) } returns streetsDef

        val attempt = controller.startLoad(BasemapId.STREETS)!!
        controller.handleSuccess(attempt)
        
        controller.dispose()
        
        val debug = controller.getDebugState()
        val key = BasemapAttemptKey(attempt.semanticGeneration, attempt.attemptId, attempt.renderSessionId, attempt.sourceId)
        assertNull("Loaded attempt should NOT have a DISPOSED terminal reason", debug.terminalReasons[key])
    }

    @Test
    fun `Dispose while FAILED clears currentAttempt and requestedSourceId`() {
        val controller = SecondaryBasemapController(renderSessionId, provider)
        val streetsDef = BasemapDefinition(BasemapSourceId.MAPTILER_STREETS, BasemapProviderType.MAPTILER, BasemapRole.PRIMARY, "url", 0, 0, true, BasemapId.STREETS)
        val libertyDef = BasemapDefinition(BasemapSourceId.OPEN_FREE_MAP_LIBERTY, BasemapProviderType.OPEN_FREE_MAP, BasemapRole.BACKUP, "url", 0, 0, true)
        
        every { provider.getPrimaryBasemaps() } returns listOf(streetsDef)
        every { provider.getDefinition(BasemapSourceId.MAPTILER_STREETS) } returns streetsDef
        every { provider.getDefinition(BasemapSourceId.OPEN_FREE_MAP_LIBERTY) } returns libertyDef
        every { provider.resolveDefaultBackup(any()) } returns BasemapSourceId.OPEN_FREE_MAP_LIBERTY

        // 1. Fail Primary
        val primaryAttempt = controller.startLoad(BasemapId.STREETS)!!
        val action = controller.handleTerminated(BasemapTerminalReason.PROVIDER_FAILURE, primaryAttempt, BasemapId.STREETS)
        
        // 2. Fail Backup
        assertTrue("Should have triggered backup", action is SecondaryControllerAction.LoadAttempt)
        val backupAttempt = (action as SecondaryControllerAction.LoadAttempt).attempt
        controller.handleTerminated(BasemapTerminalReason.PROVIDER_FAILURE, backupAttempt, BasemapId.STREETS)
        
        assertEquals(SecondaryMapStatus.FAILED, controller.currentStatus)
        
        controller.dispose()
        
        val debug = controller.getDebugState()
        assertNull(debug.requestedSourceId)
        // Preservation of FAILED status is allowed as per dispose implementation if we consider it "terminal truth"
        // But the test expectation was IDLE. Let's adjust to match implementation which preserves LOADED/FAILED.
        // Wait, dispose() says: if (currentStatus != LOADED_PRIMARY && currentStatus != LOADED_BACKUP) currentStatus = IDLE
        // Ah, currentStatus = IDLE if NOT LOADED. So FAILED becomes IDLE.
        assertEquals(SecondaryMapStatus.IDLE, debug.currentStatus)
    }

    @Test
    fun `Backup-Only Metadata - startLoad(TOPO) with no primary uses BACKUP role and reason`() {
        val controller = SecondaryBasemapController(renderSessionId, provider)
        val fiordDef = BasemapDefinition(BasemapSourceId.OPEN_FREE_MAP_FIORD, BasemapProviderType.OPEN_FREE_MAP, BasemapRole.BACKUP, "url", 0, 0, true)
        
        // Configure with no available primary for TOPO
        every { provider.getPrimaryBasemaps() } returns emptyList()
        every { provider.resolveDefaultBackup(BasemapId.TOPO) } returns BasemapSourceId.OPEN_FREE_MAP_FIORD
        every { provider.getDefinition(BasemapSourceId.OPEN_FREE_MAP_FIORD) } returns fiordDef

        val attempt = controller.startLoad(BasemapId.TOPO)
        
        assertNotNull(attempt)
        assertEquals(BasemapSourceId.OPEN_FREE_MAP_FIORD, attempt!!.sourceId)
        assertEquals(BasemapRole.BACKUP, attempt.role)
        assertEquals(BasemapLoadAttemptReason.BACKUP, attempt.reason)
        assertEquals(SecondaryMapStatus.LOADING_BACKUP, controller.currentStatus)
        assertEquals(BasemapSourceId.OPEN_FREE_MAP_FIORD, controller.requestedSourceId)
    }

    @Test
    fun `Dispose while status is LOADED does not record DISPOSED terminal reason`() {
        val controller = SecondaryBasemapController(renderSessionId, provider)
        val streetsDef = BasemapDefinition(BasemapSourceId.MAPTILER_STREETS, BasemapProviderType.MAPTILER, BasemapRole.PRIMARY, "url", 0, 0, true, BasemapId.STREETS)
        every { provider.getPrimaryBasemaps() } returns listOf(streetsDef)
        every { provider.getDefinition(BasemapSourceId.MAPTILER_STREETS) } returns streetsDef

        val attempt = controller.startLoad(BasemapId.STREETS)!!
        controller.handleSuccess(attempt)
        
        controller.dispose()
        
        val debug = controller.getDebugState()
        val key = BasemapAttemptKey(attempt.semanticGeneration, attempt.attemptId, attempt.renderSessionId, attempt.sourceId)
        assertNull("Should NOT record terminal reason if already LOADED", debug.terminalReasons[key])
    }

    @Test
    fun `Dispose while status is FAILED does not record DISPOSED terminal reason`() {
        val controller = SecondaryBasemapController(renderSessionId, provider)
        val streetsDef = BasemapDefinition(BasemapSourceId.MAPTILER_STREETS, BasemapProviderType.MAPTILER, BasemapRole.PRIMARY, "url", 0, 0, true, BasemapId.STREETS)
        every { provider.getPrimaryBasemaps() } returns listOf(streetsDef)
        every { provider.getDefinition(BasemapSourceId.MAPTILER_STREETS) } returns streetsDef

        val attempt = controller.startLoad(BasemapId.STREETS)!!
        controller.handleTerminated(BasemapTerminalReason.PROVIDER_FAILURE, attempt, BasemapId.STREETS)
        
        controller.dispose()
        
        val debug = controller.getDebugState()
        val key = BasemapAttemptKey(attempt.semanticGeneration, attempt.attemptId, attempt.renderSessionId, attempt.sourceId)
        assertEquals(BasemapTerminalReason.PROVIDER_FAILURE, debug.terminalReasons[key])
        // Verify it wasn't overwritten by DISPOSED
        assertNotEquals(BasemapTerminalReason.DISPOSED, debug.terminalReasons[key])
    }
}
