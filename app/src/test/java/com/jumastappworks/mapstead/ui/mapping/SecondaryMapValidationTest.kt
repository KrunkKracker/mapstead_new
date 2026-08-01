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
    fun `Accepted source alone controls attribution`() {
        val controller = SecondaryBasemapController(renderSessionId, provider)
        val streetsDef = BasemapDefinition(BasemapSourceId.MAPTILER_STREETS, BasemapProviderType.MAPTILER, BasemapRole.PRIMARY, "url", 0, 0, true, BasemapId.STREETS)
        every { provider.getPrimaryBasemaps() } returns listOf(streetsDef)
        every { provider.getDefinition(BasemapSourceId.MAPTILER_STREETS) } returns streetsDef

        controller.startLoad(BasemapId.STREETS)
        assertEquals("Attribution source should be null while loading", null, controller.acceptedSourceId)
        
        val attempt = controller.startLoad(BasemapId.STREETS)!!
        controller.handleSuccess(attempt)
        assertEquals(BasemapSourceId.MAPTILER_STREETS, controller.acceptedSourceId)
    }
}
