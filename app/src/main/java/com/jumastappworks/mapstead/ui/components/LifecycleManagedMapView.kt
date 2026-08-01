package com.jumastappworks.mapstead.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

@Composable
fun LifecycleManagedMapView(
    modifier: Modifier = Modifier,
    onMapReady: (MapLibreMap) -> Unit,
    onMapViewCreated: (MapView) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnMapReady by rememberUpdatedState(onMapReady)
    val currentOnMapViewCreated by rememberUpdatedState(onMapViewCreated)
    
    // Ensure MapLibre is initialized before MapView construction
    remember { org.maplibre.android.MapLibre.getInstance(context) }

    val mapView = remember { MapView(context).also { currentOnMapViewCreated(it) } }
    
    // Track internal lifecycle to ensure idempotence
    val mapLifecycle = remember { 
        object {
            var created = false
            var started = false
            var resumed = false
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> if (!mapLifecycle.created) { mapView.onCreate(null); mapLifecycle.created = true }
                Lifecycle.Event.ON_START -> if (!mapLifecycle.started) { mapView.onStart(); mapLifecycle.started = true }
                Lifecycle.Event.ON_RESUME -> if (!mapLifecycle.resumed) { mapView.onResume(); mapLifecycle.resumed = true }
                Lifecycle.Event.ON_PAUSE -> if (mapLifecycle.resumed) { mapView.onPause(); mapLifecycle.resumed = false }
                Lifecycle.Event.ON_STOP -> if (mapLifecycle.started) { mapView.onStop(); mapLifecycle.started = false }
                Lifecycle.Event.ON_DESTROY -> {
                    // MapView destruction is handled in onDispose to be safe with Compose lifecycle
                }
                else -> {}
            }
        }

        // Catch up to current lifecycle state
        val currentState = lifecycleOwner.lifecycle.currentState
        if (currentState.isAtLeast(Lifecycle.State.CREATED) && !mapLifecycle.created) {
            mapView.onCreate(null)
            mapLifecycle.created = true
        }
        if (currentState.isAtLeast(Lifecycle.State.STARTED) && !mapLifecycle.started) {
            mapView.onStart()
            mapLifecycle.started = true
        }
        if (currentState.isAtLeast(Lifecycle.State.RESUMED) && !mapLifecycle.resumed) {
            mapView.onResume()
            mapLifecycle.resumed = true
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (mapLifecycle.resumed) { mapView.onPause(); mapLifecycle.resumed = false }
            if (mapLifecycle.started) { mapView.onStop(); mapLifecycle.started = false }
            if (mapLifecycle.created) { mapView.onDestroy(); mapLifecycle.created = false }
        }
    }

    AndroidView(
        factory = { 
            mapView.getMapAsync { map ->
                currentOnMapReady(map)
            }
            mapView
        },
        modifier = modifier
    )
}
