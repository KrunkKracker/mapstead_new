package com.jumastappworks.mapstead.ui.mapping

import com.jumastappworks.mapstead.data.mapping.BasemapProvider
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference

/**
 * Shared component for resilient basemap loading.
 * Ensures exactly one authoritative callback (success or terminal) per attempt.
 */
class BasemapStyleLoader(
    private val basemapProvider: BasemapProvider,
    private val scope: CoroutineScope,
    private val onStyleLoaded: (Style, BasemapLoadAttempt) -> Unit,
    private val onStyleTerminated: (BasemapTerminalReason, BasemapLoadAttempt) -> Unit,
    private val onStaleStyleApplied: (BasemapLoadAttempt) -> Unit = {}
) {
    private enum class OutcomeState { ACTIVE, SUCCEEDED, TERMINATED }

    private data class AttemptGate(
        val attempt: BasemapLoadAttempt,
        val state: OutcomeState = OutcomeState.ACTIVE,
        val terminalReason: BasemapTerminalReason? = null
    )

    private val gate = AtomicReference<AttemptGate?>(null)
    private var lastFailureListener: MapView.OnDidFailLoadingMapListener? = null
    private var timeoutJob: Job? = null

    /**
     * Loads a basemap style with a timeout.
     */
    fun loadStyle(
        mapView: MapView,
        map: MapLibreMap,
        attempt: BasemapLoadAttempt,
        timeoutMs: Long = 15000L
    ) {
        // 1. Atomically supersede any previous active attempt
        val prevGate = gate.getAndSet(AttemptGate(attempt))
        if (prevGate != null && prevGate.state == OutcomeState.ACTIVE) {
            onStyleTerminated(BasemapTerminalReason.SUPERSEDED, prevGate.attempt)
        }
        
        cleanupListeners(mapView)

        // 2. Create attempt-scoped failure listener
        val failureListener = MapView.OnDidFailLoadingMapListener { _ ->
            if (tryTerminate(attempt, BasemapTerminalReason.PROVIDER_FAILURE)) {
                cleanupListeners(mapView)
                onStyleTerminated(BasemapTerminalReason.PROVIDER_FAILURE, attempt)
            }
        }
        lastFailureListener = failureListener
        mapView.addOnDidFailLoadingMapListener(failureListener)

        // 3. Setup loader-level timeout
        timeoutJob = scope.launch {
            delay(timeoutMs)
            if (tryTerminate(attempt, BasemapTerminalReason.TIMEOUT)) {
                cleanupListeners(mapView)
                onStyleTerminated(BasemapTerminalReason.TIMEOUT, attempt)
            }
        }

        // 4. Resolve authoritative URL
        val url = basemapProvider.buildStyleUrl(attempt.sourceId)

        // 5. Request native style application
        map.setStyle(url) { style ->
            if (trySucceed(attempt)) {
                cleanupListeners(mapView)
                onStyleLoaded(style, attempt)
            } else {
                // Stale completion (superseded, timed out, or failed)
                onStaleStyleApplied(attempt)
            }
        }
    }

    private fun tryTerminate(attempt: BasemapLoadAttempt, reason: BasemapTerminalReason): Boolean {
        while (true) {
            val current = gate.get() ?: return false
            if (current.attempt != attempt || current.state != OutcomeState.ACTIVE) return false
            val next = current.copy(state = OutcomeState.TERMINATED, terminalReason = reason)
            if (gate.compareAndSet(current, next)) return true
        }
    }

    private fun trySucceed(attempt: BasemapLoadAttempt): Boolean {
        while (true) {
            val current = gate.get() ?: return false
            if (current.attempt != attempt || current.state != OutcomeState.ACTIVE) return false
            val next = current.copy(state = OutcomeState.SUCCEEDED)
            if (gate.compareAndSet(current, next)) return true
        }
    }

    private fun cleanupListeners(mapView: MapView) {
        lastFailureListener?.let { mapView.removeOnDidFailLoadingMapListener(it) }
        lastFailureListener = null
        timeoutJob?.cancel()
        timeoutJob = null
    }

    /**
     * Disposes the loader, marking any active attempt as DISPOSED.
     */
    fun dispose(mapView: MapView) {
        val current = gate.get()
        if (current != null && current.state == OutcomeState.ACTIVE) {
            if (tryTerminate(current.attempt, BasemapTerminalReason.DISPOSED)) {
                onStyleTerminated(BasemapTerminalReason.DISPOSED, current.attempt)
            }
        }
        cleanupListeners(mapView)
        gate.set(null)
    }
}
