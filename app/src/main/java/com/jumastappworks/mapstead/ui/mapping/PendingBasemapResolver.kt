package com.jumastappworks.mapstead.ui.mapping

import com.jumastappworks.mapstead.data.mapping.*
import java.util.UUID

data class PendingBasemapRequest(
    val preferredBasemapId: BasemapId,
    val semanticGeneration: Long,
    val sourceId: BasemapSourceId,
    val role: BasemapRole,
    val reason: BasemapLoadAttemptReason
)

sealed interface PendingConsumptionResult {
    data class IssuePending(val request: PendingBasemapRequest) : PendingConsumptionResult
    data class ReissueCurrentAuthority(val preferredBasemapId: BasemapId) : PendingConsumptionResult
    object DefinitionUnavailable : PendingConsumptionResult
    object NoLiveSession : PendingConsumptionResult
}

object PendingBasemapResolver {
    fun resolve(
        pending: PendingBasemapRequest?,
        currentGeneration: Long,
        currentPreferredId: BasemapId,
        sessionId: UUID?,
        basemapProvider: BasemapProvider
    ): PendingConsumptionResult {
        if (sessionId == null) return PendingConsumptionResult.NoLiveSession
        
        if (pending == null) return PendingConsumptionResult.NoLiveSession

        if (pending.semanticGeneration != currentGeneration) {
            return PendingConsumptionResult.ReissueCurrentAuthority(currentPreferredId)
        }

        val def = basemapProvider.getDefinition(pending.sourceId)
        return if (def != null) {
            PendingConsumptionResult.IssuePending(pending)
        } else {
            PendingConsumptionResult.DefinitionUnavailable
        }
    }
}
