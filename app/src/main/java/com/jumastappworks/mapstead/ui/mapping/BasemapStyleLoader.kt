package com.jumastappworks.mapstead.ui.mapping

import com.jumastappworks.mapstead.data.mapping.BasemapProvider
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shared component for resilient basemap loading.
 * Ensures exactly one terminal callback per attempt.
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
    private var currentAttemptId: Long? = null

    fun loadStyle(
        mapView: MapView,
        map: MapLibreMap,
        attempt: BasemapLoadAttempt,
        timeoutMs: Long = 15000L
    ) {
        // 1. Supersede previous attempt
        cleanup(mapView)
        currentAttemptId = attempt.attemptId
        val isTerminal = AtomicBoolean(false)

        // 2. Create attempt-scoped failure listener
        val failureListener = MapView.OnDidFailLoadingMapListener { error ->
            if (currentAttemptId == attempt.attemptId && isTerminal.compareAndSet(false, true)) {
                cleanup(mapView)
                onStyleFailed(error ?: "Unknown MapLibre error", attempt)
            }
        }
        lastFailureListener = failureListener
        mapView.addOnDidFailLoadingMapListener(failureListener)

        // 3. Setup timeout
        timeoutJob = scope.launch {
            delay(timeoutMs)
            if (currentAttemptId == attempt.attemptId && isTerminal.compareAndSet(false, true)) {
                cleanup(mapView)
                onStyleFailed("Style load timeout", attempt)
            }
        }

        // 4. Resolve URL
        val url = basemapProvider.buildStyleUrl(attempt.sourceId)

        // 5. Request style
        map.setStyle(url) { style ->
            if (currentAttemptId == attempt.attemptId && isTerminal.compareAndSet(false, true)) {
                cleanup(mapView)
                onStyleLoaded(style, attempt)
            } else {
                // Stale completion - potentially reassert if this MapView is still active
                if (currentAttemptId != null) {
                    onStaleStyleApplied(attempt)
                }
            }
        }
    }

    private fun cleanup(mapView: MapView) {
        lastFailureListener?.let { mapView.removeOnDidFailLoadingMapListener(it) }
        lastFailureListener = null
        timeoutJob?.cancel()
        timeoutJob = null
    }

    fun dispose(mapView: MapView) {
        cleanup(mapView)
        currentAttemptId = null
    }
}
