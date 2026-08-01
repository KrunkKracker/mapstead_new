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
        val result = controller.handleSuccess(attempt!!)
        assertEquals(SecondaryValidationResult.ACCEPTED, result)
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
        val backupAttempt = controller.handleFailure(BasemapTerminalReason.TIMEOUT, primaryAttempt, BasemapId.STREETS)
        
        assertNotNull("Should have started backup", backupAttempt)
        assertNotEquals("Backup should have different ID", primaryAttempt.attemptId, backupAttempt!!.attemptId)
        assertEquals(BasemapSourceId.OPEN_FREE_MAP_LIBERTY, controller.requestedSourceId)
        assertEquals(SecondaryMapStatus.LOADING_BACKUP, controller.currentStatus)
    }

    @Test
    fun `Late primary completion after backup start is rejected and triggers repair`() {
        val controller = SecondaryBasemapController(renderSessionId, provider)
        val streetsDef = BasemapDefinition(BasemapSourceId.MAPTILER_STREETS, BasemapProviderType.MAPTILER, BasemapRole.PRIMARY, "url", 0, 0, true, BasemapId.STREETS, BasemapSourceId.OPEN_FREE_MAP_LIBERTY)
        val libertyDef = BasemapDefinition(BasemapSourceId.OPEN_FREE_MAP_LIBERTY, BasemapProviderType.OPEN_FREE_MAP, BasemapRole.BACKUP, "url", 0, 0, true)
        
        every { provider.getPrimaryBasemaps() } returns listOf(streetsDef)
        every { provider.getDefinition(BasemapSourceId.MAPTILER_STREETS) } returns streetsDef
        every { provider.getDefinition(BasemapSourceId.OPEN_FREE_MAP_LIBERTY) } returns libertyDef
        every { provider.resolveDefaultBackup(BasemapId.STREETS) } returns BasemapSourceId.OPEN_FREE_MAP_LIBERTY

        val primaryAttempt = controller.startLoad(BasemapId.STREETS)!!
        controller.handleFailure(BasemapTerminalReason.TIMEOUT, primaryAttempt, BasemapId.STREETS)
        
        // requestedSourceId is now LIBERTY
        assertEquals(BasemapSourceId.OPEN_FREE_MAP_LIBERTY, controller.requestedSourceId)
        
        // Late primary success
        val result = controller.handleSuccess(primaryAttempt)
        assertEquals(SecondaryValidationResult.ID_MISMATCH, result)
        assertEquals("Accepted source must NOT change", null, controller.acceptedSourceId)
    }

    @Test
    fun `Repair loop prevention in secondary controller`() {
        val controller = SecondaryBasemapController(renderSessionId, provider)
        val streetsDef = BasemapDefinition(BasemapSourceId.MAPTILER_STREETS, BasemapProviderType.MAPTILER, BasemapRole.PRIMARY, "url", 0, 0, true, BasemapId.STREETS)
        every { provider.getPrimaryBasemaps() } returns listOf(streetsDef)
        every { provider.getDefinition(BasemapSourceId.MAPTILER_STREETS) } returns streetsDef

        val attempt1 = controller.startLoad(BasemapId.STREETS)!!
        
        // Simulate stale success by reusing ID but bypassing internal state
        // We'll just call triggerRepair via a private method test or just handleStaleStyleApplied
        val staleAttempt = attempt1.copy(attemptId = 999) 
        
        val repair1 = controller.handleStaleStyleApplied(staleAttempt)
        assertNotNull("Should issue first repair", repair1)
        
        val repair2 = controller.handleStaleStyleApplied(staleAttempt)
        assertNull("Should NOT issue second repair for same stale event", repair2)
    }

    @Test
    fun `Accepted source alone controls attribution`() {
        val controller = SecondaryBasemapController(renderSessionId, provider)
        val streetsDef = BasemapDefinition(BasemapSourceId.MAPTILER_STREETS, BasemapProviderType.MAPTILER, BasemapRole.PRIMARY, "url", 0, 0, true, BasemapId.STREETS)
        every { provider.getPrimaryBasemaps() } returns listOf(streetsDef)
        every { provider.getDefinition(BasemapSourceId.MAPTILER_STREETS) } returns streetsDef

        val attempt = controller.startLoad(BasemapId.STREETS)!!
        
        assertEquals("Attribution source should be null while loading", null, controller.acceptedSourceId)
        
        controller.handleSuccess(attempt)
        assertEquals("Attribution source should update after success", BasemapSourceId.MAPTILER_STREETS, controller.acceptedSourceId)
    }
}
