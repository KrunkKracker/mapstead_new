package com.jumastappworks.mapstead.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.ui.mapping.BasemapAttributionOverlay
import com.jumastappworks.mapstead.ui.mapping.ResilientBasemapLoader
import com.jumastappworks.mapstead.ui.mapping.SecondaryMapStatus
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

@Composable
fun PropertyLocationPickerMap(
    modifier: Modifier = Modifier,
    initialLat: Double?,
    initialLng: Double?,
    initialZoom: Double?,
    basemapProvider: BasemapProvider,
    onCameraMoved: (lat: Double, lng: Double, zoom: Double) -> Unit,
    onConfirm: (lat: Double, lng: Double) -> Unit
) {
    val lat = initialLat ?: 39.8283
    val lng = initialLng ?: -98.5795
    val zoom = initialZoom ?: if (initialLat != null) 17.0 else 4.0
    
    var currentCameraTarget by remember { mutableStateOf(LatLng(lat, lng)) }
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapViewInstance by remember { mutableStateOf<org.maplibre.android.maps.MapView?>(null) }
    var activeSourceId by remember { mutableStateOf<BasemapSourceId?>(null) }
    var currentStatus by remember { mutableStateOf(SecondaryMapStatus.IDLE) }
    var retryNonce by remember { mutableIntStateOf(0) }

    Box(modifier = modifier) {
        LifecycleManagedMapView(
            modifier = Modifier.fillMaxSize(),
            onMapReady = { map ->
                mapLibreMap = map
                
                map.moveCamera(CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(lat, lng))
                        .zoom(zoom)
                        .build()
                ))

                val idleListener = MapLibreMap.OnCameraIdleListener {
                    val pos = map.cameraPosition
                    val target = pos.target
                    if (target != null) {
                        currentCameraTarget = target
                        onCameraMoved(target.latitude, target.longitude, pos.zoom)
                    }
                }
                map.addOnCameraIdleListener(idleListener)
            },
            onMapViewCreated = { mv -> mapViewInstance = mv }
        )

        mapViewInstance?.let { mv: org.maplibre.android.maps.MapView ->
            ResilientBasemapLoader(
                mapView = mv,
                map = mapLibreMap,
                basemapProvider = basemapProvider,
                preferredBasemapId = basemapProvider.getDefaultBasemapId(),
                retryNonce = retryNonce,
                onStatusChanged = { status, sid -> 
                    currentStatus = status
                    activeSourceId = sid
                }
            )
        }

        BasemapAttributionOverlay(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 88.dp, start = 8.dp),
            sourceId = activeSourceId,
            basemapProvider = basemapProvider
        )

        if (currentStatus == SecondaryMapStatus.FAILED) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp, start = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.failed_to_load_basemap),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    TextButton(
                        onClick = { retryNonce++ },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        } else if (currentStatus == SecondaryMapStatus.LOADING_BACKUP || currentStatus == SecondaryMapStatus.LOADED_BACKUP) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp, start = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.basemap_fallback_active),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    if (currentStatus == SecondaryMapStatus.LOADED_BACKUP) {
                        TextButton(onClick = { retryNonce++ }) {
                            Text(stringResource(R.string.retry_primary_map), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        if (currentStatus == SecondaryMapStatus.LOADING_PRIMARY || currentStatus == SecondaryMapStatus.LOADING_BACKUP) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(32.dp),
                strokeWidth = 2.dp
            )
        }

        // Fixed Pin in center
        Icon(
            Icons.Default.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(48.dp)
                .align(Alignment.Center)
                .offset(y = (-24).dp)
        )

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .clip(RoundedCornerShape(8.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
        ) {
            Text(
                stringResource(R.string.setup_map_selection_hint),
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }

        Button(
            onClick = {
                onConfirm(currentCameraTarget.latitude, currentCameraTarget.longitude)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp)
                .heightIn(min = 56.dp)
        ) {
            Text(stringResource(R.string.setup_confirm_location))
        }
    }
}
