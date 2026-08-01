package com.jumastappworks.mapstead.ui.mapping

import androidx.compose.runtime.*
import com.jumastappworks.mapstead.data.mapping.*
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import java.util.UUID

private data class SecondaryRepairEpochKey(
    val renderSessionId: UUID,
    val semanticGeneration: Long,
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
    
    private var currentAttempt: BasemapLoadAttempt? = null
    private var attemptCounter by mutableLongStateOf(0L)
    private var semanticGeneration by mutableLongStateOf(0L)
    private var fallbackAttempted by mutableStateOf(false)
    
    private val terminalAttempts = mutableMapOf<BasemapAttemptKey, BasemapTerminalReason>()
    private val repairEpochs = mutableMapOf<SecondaryRepairEpochKey, BasemapRepairEpochState>()

    fun startLoad(preferredBasemapId: BasemapId): BasemapLoadAttempt? {
        semanticGeneration++
        fallbackAttempted = false
        terminalAttempts.clear()
        repairEpochs.clear()

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
        return createAttempt(sourceId, BasemapLoadAttemptReason.INITIAL).also { currentAttempt = it }
    }

    fun handleSuccess(attempt: BasemapLoadAttempt): SecondaryControllerAction {
        val validation = validate(attempt)
        if (validation != SecondaryValidationResult.ACCEPTED) {
            return triggerRepair(attempt)
        }
        
        acceptedSourceId = attempt.sourceId
        requestedSourceId = null
        currentStatus = if (attempt.role == BasemapRole.PRIMARY) 
            SecondaryMapStatus.LOADED_PRIMARY else SecondaryMapStatus.LOADED_BACKUP
        
        if (attempt.reason == BasemapLoadAttemptReason.REPAIR) {
            val repairKey = SecondaryRepairEpochKey(renderSessionId, attempt.semanticGeneration, attempt.sourceId)
            repairEpochs[repairKey] = BasemapRepairEpochState.EXHAUSTED
        }

        return SecondaryControllerAction.Accepted
    }

    fun handleTerminated(reason: BasemapTerminalReason, attempt: BasemapLoadAttempt, preferredBasemapId: BasemapId): SecondaryControllerAction {
        val validation = validate(attempt)
        terminalAttempts[attempt.toKey()] = reason
        
        val isRepair = attempt.reason == BasemapLoadAttemptReason.REPAIR
        val repairKey = if (isRepair) SecondaryRepairEpochKey(renderSessionId, attempt.semanticGeneration, attempt.sourceId) else null

        if (validation != SecondaryValidationResult.ACCEPTED) {
            if (repairKey != null) repairEpochs[repairKey] = BasemapRepairEpochState.EXHAUSTED
            return SecondaryControllerAction.Ignored
        }
        
        try {
            if (reason == BasemapTerminalReason.TIMEOUT || reason == BasemapTerminalReason.PROVIDER_FAILURE) {
                if (attempt.role == BasemapRole.PRIMARY && !fallbackAttempted && attempt.reason != BasemapLoadAttemptReason.REPAIR) {
                    fallbackAttempted = true
                    val backupId = basemapProvider.resolveDefaultBackup(preferredBasemapId)
                    requestedSourceId = backupId
                    attemptCounter++
                    currentStatus = SecondaryMapStatus.LOADING_BACKUP
                    return createAttempt(backupId, BasemapLoadAttemptReason.BACKUP).let { 
                        currentAttempt = it
                        if (it != null) SecondaryControllerAction.LoadAttempt(it) else SecondaryControllerAction.Failed
                    }
                } else {
                    currentStatus = SecondaryMapStatus.FAILED
                    return SecondaryControllerAction.Failed
                }
            }
            return SecondaryControllerAction.Ignored
        } finally {
            if (repairKey != null) {
                repairEpochs[repairKey] = BasemapRepairEpochState.EXHAUSTED
            }
        }
    }

    fun handleStaleStyleApplied(attempt: BasemapLoadAttempt): SecondaryControllerAction {
        if (attempt.renderSessionId != renderSessionId) return SecondaryControllerAction.Ignored
        return triggerRepair(attempt)
    }

    private fun triggerRepair(attempt: BasemapLoadAttempt): SecondaryControllerAction {
        val authSource = requestedSourceId ?: acceptedSourceId ?: return SecondaryControllerAction.Ignored
        val repairKey = SecondaryRepairEpochKey(renderSessionId, semanticGeneration, authSource)
        
        if (repairEpochs[repairKey] != null) return SecondaryControllerAction.Ignored
        
        repairEpochs[repairKey] = BasemapRepairEpochState.IN_FLIGHT
        if (repairEpochs.size > 20) repairEpochs.remove(repairEpochs.keys.first())
        
        attemptCounter++
        val repairAttempt = createAttempt(authSource, BasemapLoadAttemptReason.REPAIR)
        currentAttempt = repairAttempt
        return if (repairAttempt != null) SecondaryControllerAction.LoadAttempt(repairAttempt) else SecondaryControllerAction.Ignored
    }

    private fun validate(attempt: BasemapLoadAttempt): SecondaryValidationResult {
        if (terminalAttempts.containsKey(attempt.toKey())) return SecondaryValidationResult.TERMINAL_ATTEMPT
        if (attempt.renderSessionId != renderSessionId) return SecondaryValidationResult.STALE_SESSION
        if (attempt.semanticGeneration != semanticGeneration) return SecondaryValidationResult.GENERATION_MISMATCH
        if (attempt.attemptId != attemptCounter) return SecondaryValidationResult.ID_MISMATCH
        if (attempt.sourceId != requestedSourceId) return SecondaryValidationResult.SOURCE_MISMATCH
        
        val expectedStatus = if (attempt.role == BasemapRole.PRIMARY) SecondaryMapStatus.LOADING_PRIMARY else SecondaryMapStatus.LOADING_BACKUP
        if (currentStatus != expectedStatus) return SecondaryValidationResult.STATUS_MISMATCH

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

    private fun BasemapLoadAttempt.toKey(): BasemapAttemptKey {
        return BasemapAttemptKey(semanticGeneration, attemptId, renderSessionId, sourceId)
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
                val action = controller.handleSuccess(attempt)
                if (action is SecondaryControllerAction.LoadAttempt) {
                    activeAttempt = action.attempt
                }
            },
            onStyleTerminated = { reason, attempt ->
                val action = controller.handleTerminated(reason, attempt, preferredBasemapId)
                if (action is SecondaryControllerAction.LoadAttempt) {
                    activeAttempt = action.attempt
                }
            },
            onStaleStyleApplied = { attempt ->
                val action = controller.handleStaleStyleApplied(attempt)
                if (action is SecondaryControllerAction.LoadAttempt) {
                    activeAttempt = action.attempt
                }
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
