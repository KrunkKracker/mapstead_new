package com.jumastappworks.mapstead.ui.mapping

import com.jumastappworks.mapstead.data.mapping.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class BasemapStyleLoaderTest {

    private val provider = mockk<BasemapProvider>(relaxed = true)
    private val mapView = mockk<MapView>(relaxed = true)
    private val map = mockk<MapLibreMap>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val scope = TestScope(testDispatcher)

    private val attemptA = BasemapLoadAttempt(0, 1, UUID.randomUUID(), BasemapSourceId.MAPTILER_STREETS, BasemapProviderType.MAPTILER, BasemapRole.PRIMARY, BasemapLoadAttemptReason.INITIAL, 0)
    private val attemptB = BasemapLoadAttempt(0, 2, attemptA.renderSessionId, BasemapSourceId.MAPTILER_BASE, BasemapProviderType.MAPTILER, BasemapRole.PRIMARY, BasemapLoadAttemptReason.INITIAL, 0)

    @Before
    fun setup() {
        every { provider.buildStyleUrl(any()) } returns "authoritative_url"
    }

    @Test
    fun `Success wins before timeout`() = scope.runTest {
        var successCalled = false
        var terminatedReason: BasemapTerminalReason? = null
        
        val loader = BasemapStyleLoader(
            provider, scope.backgroundScope,
            onStyleLoaded = { _, _ -> successCalled = true },
            onStyleTerminated = { reason, _ -> terminatedReason = reason }
        )

        val setStyleCallback = slot<Style.OnStyleLoaded>()
        every { map.setStyle(any<String>(), capture(setStyleCallback)) } just Runs

        loader.loadStyle(mapView, map, attemptA, timeoutMs = 1000L)
        
        // Simulate native success before timeout
        setStyleCallback.captured.onStyleLoaded(mockk())
        
        assertTrue(successCalled)
        assertNull(terminatedReason)
        
        // Fast forward past timeout
        advanceTimeBy(2000L)
        assertNull("Timeout should NOT fire after success", terminatedReason)
    }

    @Test
    fun `Timeout wins before success`() = scope.runTest {
        var successCalled = false
        var terminatedReason: BasemapTerminalReason? = null
        var staleCalled = false
        
        val loader = BasemapStyleLoader(
            provider, scope.backgroundScope,
            onStyleLoaded = { _, _ -> successCalled = true },
            onStyleTerminated = { reason, _ -> terminatedReason = reason },
            onStaleStyleApplied = { staleCalled = true }
        )

        val setStyleCallback = slot<Style.OnStyleLoaded>()
        every { map.setStyle(any<String>(), capture(setStyleCallback)) } just Runs

        loader.loadStyle(mapView, map, attemptA, timeoutMs = 1000L)
        
        // Fast forward past timeout
        advanceTimeBy(2000L)
        
        assertEquals(BasemapTerminalReason.TIMEOUT, terminatedReason)
        assertFalse(successCalled)
        
        // Simulate native success arriving late
        setStyleCallback.captured.onStyleLoaded(mockk())
        
        assertFalse("Success should NOT fire after timeout", successCalled)
        assertTrue("Should report as stale", staleCalled)
    }

    @Test
    fun `Starting B while A is active supersedes A`() = scope.runTest {
        val terminatedAttempts = mutableListOf<Pair<BasemapTerminalReason, BasemapLoadAttempt>>()
        
        val loader = BasemapStyleLoader(
            provider, scope.backgroundScope,
            onStyleLoaded = { _, _ -> },
            onStyleTerminated = { reason, attempt -> terminatedAttempts.add(reason to attempt) }
        )

        loader.loadStyle(mapView, map, attemptA)
        loader.loadStyle(mapView, map, attemptB)
        
        assertEquals(1, terminatedAttempts.size)
        assertEquals(BasemapTerminalReason.SUPERSEDED, terminatedAttempts[0].first)
        assertEquals(attemptA.attemptId, terminatedAttempts[0].second.attemptId)
    }

    @Test
    fun `Dispose while active marks as DISPOSED`() = scope.runTest {
        var terminatedReason: BasemapTerminalReason? = null
        
        val loader = BasemapStyleLoader(
            provider, scope.backgroundScope,
            onStyleLoaded = { _, _ -> },
            onStyleTerminated = { reason, _ -> terminatedReason = reason }
        )

        loader.loadStyle(mapView, map, attemptA)
        loader.dispose(mapView)
        
        assertEquals(BasemapTerminalReason.DISPOSED, terminatedReason)
    }

    @Test
    fun `Dispose after success emits no DISPOSED callback`() = scope.runTest {
        var terminatedReason: BasemapTerminalReason? = null
        
        val loader = BasemapStyleLoader(
            provider, scope.backgroundScope,
            onStyleLoaded = { _, _ -> },
            onStyleTerminated = { reason, _ -> terminatedReason = reason }
        )

        val setStyleCallback = slot<Style.OnStyleLoaded>()
        every { map.setStyle(any<String>(), capture(setStyleCallback)) } just Runs

        loader.loadStyle(mapView, map, attemptA)
        setStyleCallback.captured.onStyleLoaded(mockk())
        
        loader.dispose(mapView)
        assertNull("Dispose after success should not emit terminal callback", terminatedReason)
    }

    @Test
    fun `Provider failure wins before style success`() = scope.runTest {
        var successCalled = false
        var terminatedReason: BasemapTerminalReason? = null
        var staleCalled = false
        
        val loader = BasemapStyleLoader(
            provider, scope.backgroundScope,
            onStyleLoaded = { _, _ -> successCalled = true },
            onStyleTerminated = { reason, _ -> terminatedReason = reason },
            onStaleStyleApplied = { staleCalled = true }
        )

        val failureListener = slot<MapView.OnDidFailLoadingMapListener>()
        every { mapView.addOnDidFailLoadingMapListener(capture(failureListener)) } just Runs
        val setStyleCallback = slot<Style.OnStyleLoaded>()
        every { map.setStyle(any<String>(), capture(setStyleCallback)) } just Runs

        loader.loadStyle(mapView, map, attemptA)
        
        // 1. Provider failure
        failureListener.captured.onDidFailLoadingMap("error")
        assertEquals(BasemapTerminalReason.PROVIDER_FAILURE, terminatedReason)
        
        // 2. Late success
        setStyleCallback.captured.onStyleLoaded(mockk())
        assertFalse("Success should NOT fire after provider failure", successCalled)
        assertTrue("Should report as stale", staleCalled)
    }

    @Test
    fun `Repeated provider-failure callbacks emit one terminal result`() = scope.runTest {
        var terminalCount = 0
        val loader = BasemapStyleLoader(
            provider, scope.backgroundScope,
            onStyleLoaded = { _, _ -> },
            onStyleTerminated = { _, _ -> terminalCount++ }
        )

        val failureListener = slot<MapView.OnDidFailLoadingMapListener>()
        every { mapView.addOnDidFailLoadingMapListener(capture(failureListener)) } just Runs
        loader.loadStyle(mapView, map, attemptA)
        
        failureListener.captured.onDidFailLoadingMap("error1")
        failureListener.captured.onDidFailLoadingMap("error2")
        
        assertEquals("Only one terminal callback expected", 1, terminalCount)
    }

    @Test
    fun `Timeout followed by provider failure emits one terminal result`() = scope.runTest {
        var terminalCount = 0
        var lastReason: BasemapTerminalReason? = null
        val loader = BasemapStyleLoader(
            provider, scope.backgroundScope,
            onStyleLoaded = { _, _ -> },
            onStyleTerminated = { reason, _ -> terminalCount++; lastReason = reason }
        )

        val failureListener = slot<MapView.OnDidFailLoadingMapListener>()
        every { mapView.addOnDidFailLoadingMapListener(capture(failureListener)) } just Runs
        loader.loadStyle(mapView, map, attemptA, timeoutMs = 1000L)
        
        // 1. Timeout
        advanceTimeBy(2000L)
        assertEquals(BasemapTerminalReason.TIMEOUT, lastReason)
        
        // 2. Late provider failure
        failureListener.captured.onDidFailLoadingMap("error")
        assertEquals("Should not increment count", 1, terminalCount)
        assertEquals("Reason should remain TIMEOUT", BasemapTerminalReason.TIMEOUT, lastReason)
    }

    @Test
    fun `Starting B after A succeeded does not supersede A`() = scope.runTest {
        val terminatedAttempts = mutableListOf<BasemapLoadAttempt>()
        val loader = BasemapStyleLoader(
            provider, scope.backgroundScope,
            onStyleLoaded = { _, _ -> },
            onStyleTerminated = { _, attempt -> terminatedAttempts.add(attempt) }
        )

        val setStyleCallback = slot<Style.OnStyleLoaded>()
        every { map.setStyle(any<String>(), capture(setStyleCallback)) } just Runs

        loader.loadStyle(mapView, map, attemptA)
        setStyleCallback.captured.onStyleLoaded(mockk())
        
        loader.loadStyle(mapView, map, attemptB)
        
        // Only B is active, A was already completed. SUPERSEDED should NOT have been called for A.
        assertFalse("Attempt A should not be superseded because it already succeeded", 
            terminatedAttempts.any { it.attemptId == attemptA.attemptId })
    }

    @Test
    fun `Listeners and jobs are cancelled or removed appropriately`() = scope.runTest {
        val loader = BasemapStyleLoader(
            provider, scope.backgroundScope,
            onStyleLoaded = { _, _ -> },
            onStyleTerminated = { _, _ -> }
        )

        val setStyleCallback = slot<Style.OnStyleLoaded>()
        every { map.setStyle(any<String>(), capture(setStyleCallback)) } just Runs
        
        loader.loadStyle(mapView, map, attemptA)
        
        // 1. Native success
        setStyleCallback.captured.onStyleLoaded(mockk())
        
        // Verify failure listener removed
        verify { mapView.removeOnDidFailLoadingMapListener(any()) }
        
        // Verify timeout job cancelled (can't easily verify job cancellation directly here without exposing internals,
        // but we verify behavior in other tests)
    }
}
