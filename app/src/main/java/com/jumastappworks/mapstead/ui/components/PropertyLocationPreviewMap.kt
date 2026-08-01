package com.jumastappworks.mapstead.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.ui.mapping.BasemapAttributionOverlay
import com.jumastappworks.mapstead.ui.mapping.ResilientBasemapLoader
import com.jumastappworks.mapstead.ui.mapping.SecondaryMapStatus
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng

@Composable
fun PropertyLocationPreviewMap(
    modifier: Modifier = Modifier,
    latitude: Double,
    longitude: Double,
    basemapProvider: BasemapProvider
) {
    var mapLibreMap by remember { mutableStateOf<org.maplibre.android.maps.MapLibreMap?>(null) }
    var mapViewInstance by remember { mutableStateOf<org.maplibre.android.maps.MapView?>(null) }
    var activeSourceId by remember { mutableStateOf<BasemapSourceId?>(null) }
    var currentStatus by remember { mutableStateOf(SecondaryMapStatus.IDLE) }

    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        LifecycleManagedMapView(
            modifier = Modifier.fillMaxSize(),
            onMapReady = { map ->
                mapLibreMap = map
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), 15.0))
                map.uiSettings.setAllGesturesEnabled(false)
            },
            onMapViewCreated = { mv -> mapViewInstance = mv }
        )

        mapViewInstance?.let { mv: org.maplibre.android.maps.MapView ->
            ResilientBasemapLoader(
                mapView = mv,
                map = mapLibreMap,
                basemapProvider = basemapProvider,
                preferredBasemapId = basemapProvider.getDefaultBasemapId(),
                onStatusChanged = { status, sid -> 
                    currentStatus = status
                    activeSourceId = sid
                }
            )
        }

        BasemapAttributionOverlay(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
            sourceId = activeSourceId,
            basemapProvider = basemapProvider
        )

        if (currentStatus == SecondaryMapStatus.LOADING_PRIMARY || currentStatus == SecondaryMapStatus.LOADING_BACKUP) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        }

        if (currentStatus == SecondaryMapStatus.FAILED) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.setup_map_preview_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
