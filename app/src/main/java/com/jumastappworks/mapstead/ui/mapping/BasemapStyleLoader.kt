package com.jumastappworks.mapstead.ui.mapping

import com.jumastappworks.mapstead.data.mapping.BasemapProvider
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference

/**
 * Shared component for resilient basemap loading.
 * Ensures exactly one terminal loader-callback per attempt.
 */
class BasemapStyleLoader(
    private val basemapProvider: BasemapProvider,
    private val scope: CoroutineScope,
    private val onStyleLoaded: (Style, BasemapLoadAttempt) -> Unit,
    private val onStyleTerminated: (BasemapTerminalReason, BasemapLoadAttempt) -> Unit,
    private val onStaleStyleApplied: (BasemapLoadAttempt) -> Unit = {}
) {
    private var lastFailureListener: MapView.OnDidFailLoadingMapListener? = null
    private var timeoutJob: Job? = null
    private var activeAttempt: BasemapLoadAttempt? = null

    /**
     * Loads a basemap style with a timeout.
     * 
     * @param mapView The native MapView for listener attachment.
     * @param map The MapLibreMap instance.
     * @param attempt The authoritative attempt identity.
     * @param timeoutMs Maximum time to wait before reporting failure.
     */
    fun loadStyle(
        mapView: MapView,
        map: MapLibreMap,
        attempt: BasemapLoadAttempt,
        timeoutMs: Long = 15000L
    ) {
        // 1. Terminate previous in-flight loader activity as SUPERSEDED
        activeAttempt?.let { prev ->
            onStyleTerminated(BasemapTerminalReason.SUPERSEDED, prev)
        }
        cleanup(mapView)
        
        activeAttempt = attempt
        val outcome = AtomicReference<BasemapTerminalReason?>(null)

        // 2. Create attempt-scoped failure listener
        val failureListener = MapView.OnDidFailLoadingMapListener { _ ->
            if (activeAttempt?.attemptId == attempt.attemptId && outcome.compareAndSet(null, BasemapTerminalReason.PROVIDER_FAILURE)) {
                cleanup(mapView)
                onStyleTerminated(BasemapTerminalReason.PROVIDER_FAILURE, attempt)
            }
        }
        lastFailureListener = failureListener
        mapView.addOnDidFailLoadingMapListener(failureListener)

        // 3. Setup loader-level timeout
        timeoutJob = scope.launch {
            delay(timeoutMs)
            if (activeAttempt?.attemptId == attempt.attemptId && outcome.compareAndSet(null, BasemapTerminalReason.TIMEOUT)) {
                cleanup(mapView)
                onStyleTerminated(BasemapTerminalReason.TIMEOUT, attempt)
            }
        }

        // 4. Resolve authoritative URL
        val url = basemapProvider.buildStyleUrl(attempt.sourceId)

        // 5. Request native style application
        map.setStyle(url) { style ->
            if (activeAttempt?.attemptId == attempt.attemptId && outcome.get() == null) {
                // Success: this call still owns the loader state and is not terminal
                cleanup(mapView)
                onStyleLoaded(style, attempt)
            } else {
                // Stale completion (superseded, timed out, or failed)
                onStaleStyleApplied(attempt)
            }
        }
    }

    private fun cleanup(mapView: MapView) {
        lastFailureListener?.let { mapView.removeOnDidFailLoadingMapListener(it) }
        lastFailureListener = null
        timeoutJob?.cancel()
        timeoutJob = null
    }

    /**
     * Disposes the loader, marking any active attempt as DISPOSED.
     */
    fun dispose(mapView: MapView) {
        activeAttempt?.let { attempt ->
            onStyleTerminated(BasemapTerminalReason.DISPOSED, attempt)
        }
        cleanup(mapView)
        activeAttempt = null
    }
}
