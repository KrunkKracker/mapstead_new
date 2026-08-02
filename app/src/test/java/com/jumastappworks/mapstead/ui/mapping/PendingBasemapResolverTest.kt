package com.jumastappworks.mapstead.ui.mapping

import com.jumastappworks.mapstead.data.mapping.*
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PendingBasemapResolverTest {

    private val provider = mockk<BasemapProvider>()
    private val sessionId = UUID.randomUUID()

    @Test
    fun `resolve returns NoLiveSession when sessionId is null`() {
        val result = PendingBasemapResolver.resolve(
            pending = null,
            currentGeneration = 1L,
            currentPreferredId = BasemapId.STREETS,
            sessionId = null,
            basemapProvider = provider
        )
        assertEquals(PendingConsumptionResult.NoLiveSession, result)
    }

    @Test
    fun `resolve returns NoLiveSession when pending is null`() {
        val result = PendingBasemapResolver.resolve(
            pending = null,
            currentGeneration = 1L,
            currentPreferredId = BasemapId.STREETS,
            sessionId = sessionId,
            basemapProvider = provider
        )
        assertEquals(PendingConsumptionResult.NoLiveSession, result)
    }

    @Test
    fun `resolve returns ReissueCurrentAuthority when generation mismatch`() {
        val pending = PendingBasemapRequest(
            preferredBasemapId = BasemapId.BASE,
            semanticGeneration = 1L,
            sourceId = BasemapSourceId.MAPTILER_BASE,
            role = BasemapRole.PRIMARY,
            reason = BasemapLoadAttemptReason.INITIAL
        )
        
        val result = PendingBasemapResolver.resolve(
            pending = pending,
            currentGeneration = 2L,
            currentPreferredId = BasemapId.STREETS,
            sessionId = sessionId,
            basemapProvider = provider
        )
        
        assertTrue(result is PendingConsumptionResult.ReissueCurrentAuthority)
        assertEquals(BasemapId.STREETS, (result as PendingConsumptionResult.ReissueCurrentAuthority).preferredBasemapId)
    }

    @Test
    fun `resolve returns DefinitionUnavailable when source definition missing`() {
        val pending = PendingBasemapRequest(
            preferredBasemapId = BasemapId.BASE,
            semanticGeneration = 1L,
            sourceId = BasemapSourceId.MAPTILER_BASE,
            role = BasemapRole.PRIMARY,
            reason = BasemapLoadAttemptReason.INITIAL
        )
        
        every { provider.getDefinition(BasemapSourceId.MAPTILER_BASE) } returns null
        
        val result = PendingBasemapResolver.resolve(
            pending = pending,
            currentGeneration = 1L,
            currentPreferredId = BasemapId.BASE,
            sessionId = sessionId,
            basemapProvider = provider
        )
        
        assertEquals(PendingConsumptionResult.DefinitionUnavailable, result)
    }

    @Test
    fun `resolve returns IssuePending when valid`() {
        val pending = PendingBasemapRequest(
            preferredBasemapId = BasemapId.BASE,
            semanticGeneration = 1L,
            sourceId = BasemapSourceId.MAPTILER_BASE,
            role = BasemapRole.PRIMARY,
            reason = BasemapLoadAttemptReason.INITIAL
        )
        
        val def = mockk<BasemapDefinition>()
        every { provider.getDefinition(BasemapSourceId.MAPTILER_BASE) } returns def
        
        val result = PendingBasemapResolver.resolve(
            pending = pending,
            currentGeneration = 1L,
            currentPreferredId = BasemapId.BASE,
            sessionId = sessionId,
            basemapProvider = provider
        )
        
        assertTrue(result is PendingConsumptionResult.IssuePending)
        assertEquals(pending, (result as PendingConsumptionResult.IssuePending).request)
    }
}
