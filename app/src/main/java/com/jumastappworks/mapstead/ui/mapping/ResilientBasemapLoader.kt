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
 * Controller for secondary basemap surfaces (Picker, Preview).
 * Encapsulates validation, repair, and source truth separation.
 */
class SecondaryBasemapController(
    val renderSessionId: UUID,
    private val basemapProvider: BasemapProvider
) {
    var requestedSourceId by mutableStateOf<BasemapSourceId?>(null)
        private set
    var acceptedSourceId by mutableStateOf<BasemapSourceId?>(null)
        private set
    var currentStatus by mutableStateOf(SecondaryMapStatus.IDLE)
        private set
    
    private var attemptCounter by mutableLongStateOf(0L)
    private var semanticGeneration by mutableLongStateOf(0L)
    private var fallbackAttempted by mutableStateOf(false)
    
    private val terminalAttempts = mutableMapOf<Long, BasemapTerminalReason>()
    private val repairKeys = mutableSetOf<SecondaryRepairKey>()

    fun startLoad(preferredBasemapId: BasemapId): BasemapLoadAttempt? {
        semanticGeneration++
        fallbackAttempted = false
        terminalAttempts.clear()
        repairKeys.clear()

        val primary = basemapProvider.getPrimaryBasemaps().find { it.preferredId == preferredBasemapId }
        val sourceId: BasemapSourceId
        if (primary != null) {
            sourceId = primary.sourceId
            currentStatus = SecondaryMapStatus.LOADING_PRIMARY
        } else {
            fallbackAttempted = true
            sourceId = basemapProvider.resolveDefaultBackup(preferredBasemapId)
            currentStatus = SecondaryMapStatus.LOADING_BACKUP
        }
        
        attemptCounter++
        requestedSourceId = sourceId
        return createAttempt(sourceId, BasemapLoadAttemptReason.INITIAL)
    }

    fun handleSuccess(attempt: BasemapLoadAttempt): SecondaryValidationResult {
        val result = validate(attempt)
        if (result != SecondaryValidationResult.ACCEPTED) {
            triggerRepair(attempt)
            return result
        }
        
        acceptedSourceId = attempt.sourceId
        requestedSourceId = null
        currentStatus = if (attempt.role == BasemapRole.PRIMARY) 
            SecondaryMapStatus.LOADED_PRIMARY else SecondaryMapStatus.LOADED_BACKUP
        return result
    }

    fun handleFailure(reason: BasemapTerminalReason, attempt: BasemapLoadAttempt, preferredBasemapId: BasemapId): BasemapLoadAttempt? {
        val validation = validate(attempt)
        if (validation != SecondaryValidationResult.ACCEPTED) return null
        
        terminalAttempts[attempt.attemptId] = reason
        
        return if (attempt.role == BasemapRole.PRIMARY && !fallbackAttempted) {
            fallbackAttempted = true
            val backupId = basemapProvider.resolveDefaultBackup(preferredBasemapId)
            requestedSourceId = backupId
            attemptCounter++
            currentStatus = SecondaryMapStatus.LOADING_BACKUP
            createAttempt(backupId, BasemapLoadAttemptReason.BACKUP)
        } else {
            currentStatus = SecondaryMapStatus.FAILED
            null
        }
    }

    fun handleStaleStyleApplied(attempt: BasemapLoadAttempt): BasemapLoadAttempt? {
        if (attempt.renderSessionId != renderSessionId) return null
        return triggerRepair(attempt)
    }

    private fun triggerRepair(attempt: BasemapLoadAttempt): BasemapLoadAttempt? {
        val authSource = requestedSourceId ?: acceptedSourceId ?: return null
        val key = SecondaryRepairKey(attempt.attemptId, authSource)
        
        if (!repairKeys.contains(key)) {
            repairKeys.add(key)
            if (repairKeys.size > 20) repairKeys.remove(repairKeys.iterator().next())
            attemptCounter++
            return createAttempt(authSource, BasemapLoadAttemptReason.REPAIR)
        }
        return null
    }

    private fun validate(attempt: BasemapLoadAttempt): SecondaryValidationResult {
        if (attempt.renderSessionId != renderSessionId) return SecondaryValidationResult.STALE_SESSION
        if (attempt.semanticGeneration != semanticGeneration) return SecondaryValidationResult.GENERATION_MISMATCH
        if (attempt.attemptId != attemptCounter) return SecondaryValidationResult.ID_MISMATCH
        if (attempt.sourceId != requestedSourceId) return SecondaryValidationResult.SOURCE_MISMATCH
        
        val expectedStatus = if (attempt.role == BasemapRole.PRIMARY) SecondaryMapStatus.LOADING_PRIMARY else SecondaryMapStatus.LOADING_BACKUP
        if (currentStatus != expectedStatus) return SecondaryValidationResult.STATUS_MISMATCH

        if (terminalAttempts.containsKey(attempt.attemptId)) return SecondaryValidationResult.TERMINAL_ATTEMPT
        
        val def = basemapProvider.getDefinition(attempt.sourceId)
        if (def == null) return SecondaryValidationResult.DEFINITION_MISMATCH
        if (def.provider != attempt.provider) return SecondaryValidationResult.PROVIDER_MISMATCH
        if (def.role != attempt.role) return SecondaryValidationResult.ROLE_MISMATCH
        
        return SecondaryValidationResult.ACCEPTED
    }

    private fun createAttempt(sourceId: BasemapSourceId, reason: BasemapLoadAttemptReason): BasemapLoadAttempt? {
        val def = basemapProvider.getDefinition(sourceId) ?: return null
        return BasemapLoadAttempt(
            semanticGeneration = semanticGeneration,
            attemptId = attemptCounter,
            renderSessionId = renderSessionId,
            sourceId = sourceId,
            provider = def.provider,
            role = def.role,
            reason = reason,
            capturedSequence = 0L
        )
    }
}

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
    val controller = remember(renderSessionId) { SecondaryBasemapController(renderSessionId, basemapProvider) }
    
    var activeAttempt by remember { mutableStateOf<BasemapLoadAttempt?>(null) }

    val loader = remember(renderSessionId) {
        BasemapStyleLoader(
            basemapProvider = basemapProvider,
            scope = scope,
            onStyleLoaded = { _, attempt ->
                controller.handleSuccess(attempt)
            },
            onStyleTerminated = { reason, attempt ->
                activeAttempt = controller.handleFailure(reason, attempt, preferredBasemapId)
            },
            onStaleStyleApplied = { attempt ->
                controller.handleStaleStyleApplied(attempt)?.let { activeAttempt = it }
            }
        )
    }

    LaunchedEffect(preferredBasemapId, retryNonce) {
        activeAttempt = controller.startLoad(preferredBasemapId)
    }

    LaunchedEffect(activeAttempt, map) {
        val m = map ?: return@LaunchedEffect
        val attempt = activeAttempt ?: return@LaunchedEffect
        loader.loadStyle(mapView, m, attempt)
    }

    LaunchedEffect(controller.currentStatus, controller.acceptedSourceId) {
        onStatusChanged(controller.currentStatus, controller.acceptedSourceId)
    }

    DisposableEffect(renderSessionId) {
        onDispose {
            loader.dispose(mapView)
        }
    }
}
