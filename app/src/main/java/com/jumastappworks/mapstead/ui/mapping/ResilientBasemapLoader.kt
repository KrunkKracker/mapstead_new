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

data class SecondaryControllerDebugState(
    val currentStatus: SecondaryMapStatus,
    val requestedSourceId: BasemapSourceId?,
    val acceptedSourceId: BasemapSourceId?,
    val terminalReasons: Map<BasemapAttemptKey, BasemapTerminalReason>,
    val repairEpochs: Map<BasemapSourceId, BasemapRepairEpochState>,
    val isDisposed: Boolean
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
    var isDisposed by mutableStateOf(false)
        private set
    
    private var currentAttempt: BasemapLoadAttempt? = null
    private var attemptCounter by mutableLongStateOf(0L)
    private var semanticGeneration by mutableLongStateOf(0L)
    private var fallbackAttempted by mutableStateOf(false)
    
    private val terminalAttempts = mutableMapOf<BasemapAttemptKey, BasemapTerminalReason>()
    private val repairEpochs = mutableMapOf<SecondaryRepairEpochKey, BasemapRepairEpochState>()

    fun startLoad(preferredBasemapId: BasemapId): BasemapLoadAttempt? {
        if (isDisposed) return null
        semanticGeneration++
        fallbackAttempted = false
        terminalAttempts.clear()
        repairEpochs.clear()

        val primary = basemapProvider.getPrimaryBasemaps().find { it.preferredId == preferredBasemapId }
        val sourceId: BasemapSourceId
        val reason: BasemapLoadAttemptReason
        val role: BasemapRole
        if (primary != null) {
            sourceId = primary.sourceId
            reason = BasemapLoadAttemptReason.INITIAL
            role = BasemapRole.PRIMARY
            currentStatus = SecondaryMapStatus.LOADING_PRIMARY
        } else {
            // Phase 2.2h5R9C: Direct backup-only handling
            sourceId = basemapProvider.resolveDefaultBackup(preferredBasemapId)
            role = BasemapRole.BACKUP
            reason = BasemapLoadAttemptReason.BACKUP
            currentStatus = SecondaryMapStatus.LOADING_BACKUP
        }
        
        attemptCounter++
        requestedSourceId = sourceId
        val attempt = createAttempt(sourceId, reason, role)
        currentAttempt = attempt
        return attempt
    }

    fun handleSuccess(attempt: BasemapLoadAttempt): SecondaryControllerAction {
        if (isDisposed) return SecondaryControllerAction.Ignored
        
        val validation = validate(attempt)
        if (validation != SecondaryValidationResult.ACCEPTED) {
            return triggerRepairDecision(attempt, validation)
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
        if (isDisposed) return SecondaryControllerAction.Ignored
        
        // Validation must be checked BEFORE marking the attempt as terminal to avoid self-rejection
        val validation = validate(attempt)
        
        val key = attempt.toKey()
        terminalAttempts.putIfAbsent(key, reason)
        
        val isRepair = attempt.reason == BasemapLoadAttemptReason.REPAIR
        val repairKey = if (isRepair) SecondaryRepairEpochKey(renderSessionId, attempt.semanticGeneration, attempt.sourceId) else null

        if (validation != SecondaryValidationResult.ACCEPTED) {
             if (repairKey != null && attempt.semanticGeneration == semanticGeneration) {
                 repairEpochs[repairKey] = BasemapRepairEpochState.EXHAUSTED
             }
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
                    val backupAttempt = createAttempt(backupId, BasemapLoadAttemptReason.BACKUP, BasemapRole.BACKUP)
                    currentAttempt = backupAttempt
                    return if (backupAttempt != null) SecondaryControllerAction.LoadAttempt(backupAttempt) else SecondaryControllerAction.Failed
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
        if (isDisposed || attempt.renderSessionId != renderSessionId) return SecondaryControllerAction.Ignored
        return triggerRepair(attempt)
    }

    fun dispose() {
        if (isDisposed) return
        
        // Phase 2.2h5R9: Terminal truth for disposal
        if (currentStatus == SecondaryMapStatus.LOADING_PRIMARY || currentStatus == SecondaryMapStatus.LOADING_BACKUP) {
            currentAttempt?.let { attempt ->
                val key = attempt.toKey()
                terminalAttempts.putIfAbsent(key, BasemapTerminalReason.DISPOSED)
            }
        }
        
        isDisposed = true
        currentAttempt = null
        requestedSourceId = null
        // Preservation of acceptedSourceId and status for terminal truth
        if (currentStatus != SecondaryMapStatus.LOADED_PRIMARY && currentStatus != SecondaryMapStatus.LOADED_BACKUP) {
            currentStatus = SecondaryMapStatus.IDLE
        }
    }

    fun getDebugState(): SecondaryControllerDebugState {
        return SecondaryControllerDebugState(
            currentStatus = currentStatus,
            requestedSourceId = requestedSourceId,
            acceptedSourceId = acceptedSourceId,
            terminalReasons = terminalAttempts.toMap(),
            repairEpochs = repairEpochs.mapKeys { it.key.authoritativeSourceId },
            isDisposed = isDisposed
        )
    }

    private fun triggerRepairDecision(attempt: BasemapLoadAttempt, validation: SecondaryValidationResult): SecondaryControllerAction {
        // Only trigger repair for current live session failures that may have affected native state
        val eligible = when (validation) {
            SecondaryValidationResult.ACCEPTED -> false // Should not happen
            SecondaryValidationResult.STALE_SESSION -> false
            SecondaryValidationResult.GENERATION_MISMATCH -> false
            SecondaryValidationResult.TERMINAL_ATTEMPT -> {
                // If it succeeded but we previously marked it as terminal (e.g. TIMEOUT),
                // we should repair to ensure the current authoritative style is actually applied
                // and wasn't overwritten by the late successful application of the terminal style.
                attempt.renderSessionId == renderSessionId && attempt.semanticGeneration == semanticGeneration
            }
            SecondaryValidationResult.ID_MISMATCH -> {
                // Same session but old attempt ID. Authority might be different now.
                attempt.renderSessionId == renderSessionId && attempt.semanticGeneration == semanticGeneration
            }
            SecondaryValidationResult.SOURCE_MISMATCH,
            SecondaryValidationResult.STATUS_MISMATCH,
            SecondaryValidationResult.DEFINITION_MISMATCH,
            SecondaryValidationResult.PROVIDER_MISMATCH,
            SecondaryValidationResult.ROLE_MISMATCH -> {
                attempt.renderSessionId == renderSessionId && attempt.semanticGeneration == semanticGeneration
            }
        }
        
        return if (eligible) triggerRepair(attempt) else SecondaryControllerAction.Ignored
    }

    private fun triggerRepair(attempt: BasemapLoadAttempt): SecondaryControllerAction {
        val authSource = requestedSourceId ?: acceptedSourceId ?: return SecondaryControllerAction.Ignored
        val def = basemapProvider.getDefinition(authSource) ?: return SecondaryControllerAction.Ignored
        val repairKey = SecondaryRepairEpochKey(renderSessionId, semanticGeneration, authSource)
        
        if (repairEpochs[repairKey] != null) return SecondaryControllerAction.Ignored
        
        repairEpochs[repairKey] = BasemapRepairEpochState.IN_FLIGHT
        if (repairEpochs.size > 20) repairEpochs.remove(repairEpochs.keys.first())
        
        attemptCounter++
        val repairAttempt = createAttempt(authSource, BasemapLoadAttemptReason.REPAIR, def.role)
        currentAttempt = repairAttempt
        requestedSourceId = authSource
        currentStatus = if (def.role == BasemapRole.PRIMARY) SecondaryMapStatus.LOADING_PRIMARY else SecondaryMapStatus.LOADING_BACKUP
        
        return if (repairAttempt != null) SecondaryControllerAction.LoadAttempt(repairAttempt) else SecondaryControllerAction.Ignored
    }

    private fun validate(attempt: BasemapLoadAttempt): SecondaryValidationResult {
        val key = attempt.toKey()
        val terminalReason = terminalAttempts[key]
        if (terminalReason != null && terminalReason != BasemapTerminalReason.SUPERSEDED) {
            return SecondaryValidationResult.TERMINAL_ATTEMPT
        }
        
        if (attempt.renderSessionId != renderSessionId) return SecondaryValidationResult.STALE_SESSION
        if (attempt.semanticGeneration != semanticGeneration) return SecondaryValidationResult.GENERATION_MISMATCH
        
        val current = currentAttempt
        if (current == null ||
            attempt.attemptId != current.attemptId ||
            attempt.sourceId != current.sourceId ||
            attempt.provider != current.provider ||
            attempt.role != current.role ||
            attempt.reason != current.reason ||
            attempt.capturedSequence != current.capturedSequence
        ) {
            return SecondaryValidationResult.ID_MISMATCH
        }
        
        if (attempt.sourceId != requestedSourceId) return SecondaryValidationResult.SOURCE_MISMATCH
        
        val expectedStatus = if (attempt.role == BasemapRole.PRIMARY) SecondaryMapStatus.LOADING_PRIMARY else SecondaryMapStatus.LOADING_BACKUP
        if (currentStatus != expectedStatus) return SecondaryValidationResult.STATUS_MISMATCH

        val def = basemapProvider.getDefinition(attempt.sourceId)
        if (def == null) return SecondaryValidationResult.DEFINITION_MISMATCH
        if (def.provider != attempt.provider) return SecondaryValidationResult.PROVIDER_MISMATCH
        if (def.role != attempt.role) return SecondaryValidationResult.ROLE_MISMATCH
        
        return SecondaryValidationResult.ACCEPTED
    }

    private fun createAttempt(sourceId: BasemapSourceId, reason: BasemapLoadAttemptReason, role: BasemapRole): BasemapLoadAttempt? {
        val def = basemapProvider.getDefinition(sourceId) ?: return null
        return BasemapLoadAttempt(
            semanticGeneration = semanticGeneration,
            attemptId = attemptCounter,
            renderSessionId = renderSessionId,
            sourceId = sourceId,
            provider = def.provider,
            role = role,
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
    
    var activeAttempt by remember(renderSessionId) { mutableStateOf<BasemapLoadAttempt?>(null) }

    val loader = remember(renderSessionId) {
        BasemapStyleLoader(
            basemapProvider = basemapProvider,
            scope = scope,
            onStyleLoaded = { _, attempt ->
                if (!controller.isDisposed) {
                    val action = controller.handleSuccess(attempt)
                    if (action is SecondaryControllerAction.LoadAttempt) {
                        activeAttempt = action.attempt
                    }
                }
            },
            onStyleTerminated = { reason, attempt ->
                if (!controller.isDisposed) {
                    val action = controller.handleTerminated(reason, attempt, preferredBasemapId)
                    if (action is SecondaryControllerAction.LoadAttempt) {
                        activeAttempt = action.attempt
                    }
                }
            },
            onStaleStyleApplied = { attempt ->
                if (!controller.isDisposed) {
                    val action = controller.handleStaleStyleApplied(attempt)
                    if (action is SecondaryControllerAction.LoadAttempt) {
                        activeAttempt = action.attempt
                    }
                }
            }
        )
    }

    LaunchedEffect(renderSessionId, preferredBasemapId, retryNonce) {
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
            controller.dispose()
            loader.dispose(mapView)
        }
    }
}
