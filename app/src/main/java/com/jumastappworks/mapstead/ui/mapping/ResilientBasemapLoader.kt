package com.jumastappworks.mapstead.ui.mapping

import androidx.compose.runtime.*
import com.jumastappworks.mapstead.data.mapping.*
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import java.util.UUID

/**
 * A Composable that manages resilient basemap loading for any MapView.
 * This is used for secondary maps (Picker, Preview).
 */
@Composable
fun ResilientBasemapLoader(
    mapView: MapView,
    map: MapLibreMap?,
    basemapProvider: BasemapProvider,
    preferredBasemapId: BasemapId,
    onStatusChanged: (SecondaryMapStatus, BasemapSourceId?) -> Unit = { _, _ -> }
) {
    val scope = rememberCoroutineScope()
    val renderSessionId = remember(mapView) { UUID.randomUUID() }
    
    var currentSourceId by remember { mutableStateOf<BasemapSourceId?>(null) }
    var attemptCounter by remember { mutableLongStateOf(0L) }
    var semanticGeneration by remember { mutableLongStateOf(0L) }
    var fallbackAttempted by remember { mutableStateOf(false) }
    var repairNonce by remember { mutableLongStateOf(0L) }
    var currentStatus by remember { mutableStateOf(SecondaryMapStatus.IDLE) }
    
    // Terminal state tracking per attempt ID
    val terminalAttempts = remember { mutableSetOf<Long>() }

    val loader = remember(renderSessionId) {
        BasemapStyleLoader(
            basemapProvider = basemapProvider,
            scope = scope,
            onStyleLoaded = { _, attempt ->
                if (attempt.renderSessionId == renderSessionId && 
                    attempt.attemptId == attemptCounter && 
                    !terminalAttempts.contains(attempt.attemptId)) {
                    
                    currentStatus = if (attempt.role == BasemapRole.PRIMARY) 
                        SecondaryMapStatus.LOADED_PRIMARY else SecondaryMapStatus.LOADED_BACKUP
                    onStatusChanged(currentStatus, attempt.sourceId)
                }
            },
            onStyleFailed = { _, attempt ->
                if (attempt.renderSessionId == renderSessionId && 
                    attempt.attemptId == attemptCounter && 
                    !terminalAttempts.contains(attempt.attemptId)) {
                    
                    terminalAttempts.add(attempt.attemptId)
                    
                    if (attempt.role == BasemapRole.PRIMARY && !fallbackAttempted) {
                        fallbackAttempted = true
                        val backupId = basemapProvider.resolveDefaultBackup(preferredBasemapId)
                        currentSourceId = backupId
                        attemptCounter++ // Unique ID for backup
                        currentStatus = SecondaryMapStatus.LOADING_BACKUP
                        onStatusChanged(currentStatus, currentSourceId)
                    } else {
                        currentStatus = SecondaryMapStatus.FAILED
                        onStatusChanged(currentStatus, null)
                    }
                }
            },
            onStaleStyleApplied = { attempt ->
                if (attempt.renderSessionId == renderSessionId && 
                    attempt.attemptId != attemptCounter &&
                    repairNonce == 0L) { // Bounded repair
                    
                    repairNonce++
                    attemptCounter++
                }
            }
        )
    }

    LaunchedEffect(preferredBasemapId) {
        val primary = basemapProvider.getPrimaryBasemaps().find { it.preferredId == preferredBasemapId }
        fallbackAttempted = false
        semanticGeneration++
        repairNonce = 0L
        terminalAttempts.clear()
        
        if (primary != null) {
            currentSourceId = primary.sourceId
            currentStatus = SecondaryMapStatus.LOADING_PRIMARY
        } else {
            fallbackAttempted = true
            currentSourceId = basemapProvider.resolveDefaultBackup(preferredBasemapId)
            currentStatus = SecondaryMapStatus.LOADING_BACKUP
        }
        attemptCounter++
        onStatusChanged(currentStatus, currentSourceId)
    }

    LaunchedEffect(currentSourceId, map, renderSessionId, attemptCounter, repairNonce) {
        val m = map ?: return@LaunchedEffect
        val sid = currentSourceId ?: return@LaunchedEffect
        val def = basemapProvider.getDefinition(sid) ?: return@LaunchedEffect
        
        val attempt = BasemapLoadAttempt(
            semanticGeneration = semanticGeneration,
            attemptId = attemptCounter,
            renderSessionId = renderSessionId,
            sourceId = sid,
            provider = def.provider,
            role = def.role,
            reason = if (repairNonce > 0L && attemptCounter > 1L) 
                BasemapLoadAttemptReason.REPAIR else if (fallbackAttempted && def.role == BasemapRole.BACKUP) 
                BasemapLoadAttemptReason.BACKUP else BasemapLoadAttemptReason.INITIAL
        )
        
        loader.loadStyle(mapView, m, attempt)
    }

    DisposableEffect(renderSessionId) {
        onDispose {
            loader.dispose(mapView)
        }
    }
}
