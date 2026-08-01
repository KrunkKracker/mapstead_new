package com.jumastappworks.mapstead.ui.mapping

import com.jumastappworks.mapstead.data.mapping.BasemapProvider
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shared component for resilient basemap loading.
 * Ensures exactly one terminal loader-callback per attempt.
 */
class BasemapStyleLoader(
    private val basemapProvider: BasemapProvider,
    private val scope: CoroutineScope,
    private val onStyleLoaded: (Style, BasemapLoadAttempt) -> Unit,
    private val onStyleFailed: (String, BasemapLoadAttempt) -> Unit,
    private val onStaleStyleApplied: (BasemapLoadAttempt) -> Unit = {}
) {
    private var lastFailureListener: MapView.OnDidFailLoadingMapListener? = null
    private var timeoutJob: Job? = null
    private var activeAttemptId: Long? = null

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
        // 1. Supersede any previous in-flight loader activity
        cleanup(mapView)
        activeAttemptId = attempt.attemptId
        val isLoaderTerminal = AtomicBoolean(false)

        // 2. Create attempt-scoped failure listener
        val failureListener = MapView.OnDidFailLoadingMapListener { error ->
            if (activeAttemptId == attempt.attemptId && isLoaderTerminal.compareAndSet(false, true)) {
                cleanup(mapView)
                onStyleFailed(error ?: "Unknown MapLibre error", attempt)
            }
        }
        lastFailureListener = failureListener
        mapView.addOnDidFailLoadingMapListener(failureListener)

        // 3. Setup loader-level timeout
        timeoutJob = scope.launch {
            delay(timeoutMs)
            if (activeAttemptId == attempt.attemptId && isLoaderTerminal.compareAndSet(false, true)) {
                cleanup(mapView)
                onStyleFailed("Style load timeout", attempt)
            }
        }

        // 4. Resolve authoritative URL
        val url = basemapProvider.buildStyleUrl(attempt.sourceId)

        // 5. Request native style application
        map.setStyle(url) { style ->
            // Check if this specific call still "owns" the loader state
            if (activeAttemptId == attempt.attemptId && isLoaderTerminal.compareAndSet(false, true)) {
                cleanup(mapView)
                onStyleLoaded(style, attempt)
            } else {
                // Stale native completion (either superseded or already timed out/failed)
                // Report so the ViewModel can decide on repair if it affected the active MapView
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
     * Disposes the loader, cancelling any active jobs and listeners.
     */
    fun dispose(mapView: MapView) {
        cleanup(mapView)
        activeAttemptId = null
    }
}
