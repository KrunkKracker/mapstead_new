package com.jumastappworks.mapstead.ui.mapping

import androidx.compose.runtime.*
import com.jumastappworks.mapstead.data.mapping.*
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import java.util.UUID

private data class SecondaryRepairKey(
    val staleAttemptId: Long,
    val authoritativeSourceId: BasemapSourceId
)

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
    retryNonce: Int = 0,
    onStatusChanged: (SecondaryMapStatus, BasemapSourceId?) -> Unit = { _, _ -> }
) {
    val scope = rememberCoroutineScope()
    val renderSessionId = remember(mapView) { UUID.randomUUID() }
    
    var currentSourceId by remember { mutableStateOf<BasemapSourceId?>(null) }
    var attemptCounter by remember { mutableLongStateOf(0L) }
    var semanticGeneration by remember { mutableLongStateOf(0L) }
    var fallbackAttempted by remember { mutableStateOf(false) }
    var currentStatus by remember { mutableStateOf(SecondaryMapStatus.IDLE) }
    
    val terminalAttempts = remember { mutableSetOf<Long>() }
    val repairKeys = remember { mutableSetOf<SecondaryRepairKey>() }

    val loader = remember(renderSessionId) {
        BasemapStyleLoader(
            basemapProvider = basemapProvider,
            scope = scope,
            onStyleLoaded = { _, attempt ->
                val def = basemapProvider.getDefinition(attempt.sourceId)
                if (attempt.renderSessionId == renderSessionId && 
                    attempt.semanticGeneration == semanticGeneration &&
                    attempt.attemptId == attemptCounter && 
                    attempt.sourceId == currentSourceId &&
                    !terminalAttempts.contains(attempt.attemptId) &&
                    def?.provider == attempt.provider &&
                    def?.role == attempt.role) {
                    
                    currentStatus = if (attempt.role == BasemapRole.PRIMARY) 
                        SecondaryMapStatus.LOADED_PRIMARY else SecondaryMapStatus.LOADED_BACKUP
                    onStatusChanged(currentStatus, attempt.sourceId)
                }
            },
            onStyleFailed = { _, attempt ->
                val def = basemapProvider.getDefinition(attempt.sourceId)
                if (attempt.renderSessionId == renderSessionId && 
                    attempt.semanticGeneration == semanticGeneration &&
                    attempt.attemptId == attemptCounter && 
                    !terminalAttempts.contains(attempt.attemptId) &&
                    def?.role == attempt.role) {
                    
                    terminalAttempts.add(attempt.attemptId)
                    
                    if (attempt.role == BasemapRole.PRIMARY && !fallbackAttempted) {
                        fallbackAttempted = true
                        val backupId = basemapProvider.resolveDefaultBackup(preferredBasemapId)
                        currentSourceId = backupId
                        attemptCounter++
                        currentStatus = SecondaryMapStatus.LOADING_BACKUP
                        onStatusChanged(currentStatus, currentSourceId)
                    } else {
                        currentStatus = SecondaryMapStatus.FAILED
                        onStatusChanged(currentStatus, null)
                    }
                }
            },
            onStaleStyleApplied = { attempt ->
                if (attempt.renderSessionId == renderSessionId) {
                    val authSource = currentSourceId
                    if (authSource != null) {
                        val key = SecondaryRepairKey(attempt.attemptId, authSource)
                        if (!repairKeys.contains(key)) {
                            repairKeys.add(key)
                            if (repairKeys.size > 20) repairKeys.remove(repairKeys.iterator().next())
                            attemptCounter++
                        }
                    }
                }
            }
        )
    }

    LaunchedEffect(preferredBasemapId, retryNonce) {
        val primary = basemapProvider.getPrimaryBasemaps().find { it.preferredId == preferredBasemapId }
        fallbackAttempted = false
        semanticGeneration++
        terminalAttempts.clear()
        repairKeys.clear()
        
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

    LaunchedEffect(currentSourceId, map, renderSessionId, attemptCounter) {
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
            reason = if (repairKeys.any { it.authoritativeSourceId == sid }) 
                BasemapLoadAttemptReason.REPAIR else if (fallbackAttempted && def.role == BasemapRole.BACKUP) 
                BasemapLoadAttemptReason.BACKUP else BasemapLoadAttemptReason.INITIAL,
            capturedSequence = 0L
        )
        
        loader.loadStyle(mapView, m, attempt)
    }

    DisposableEffect(renderSessionId) {
        onDispose {
            loader.dispose(mapView)
        }
    }
}
