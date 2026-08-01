package com.jumastappworks.mapstead.ui.mapping

import java.util.UUID
import kotlin.math.abs

import org.maplibre.android.maps.MapLibreMap

/**
 * Manages session-matched suppression of camera interaction events.
 * Implements a "latest-session-wins" policy to ensure that programmatic movements
 * do not falsely trigger user-interaction or persistence logic.
 */
class ProgrammaticCameraController {
    private var currentSession: ProgrammaticCameraSession? = null

    /**
     * Begins a new programmatic movement session, superseding any existing one.
     */
    fun beginProgrammaticMove(
        renderSessionId: UUID,
        expectedLatitude: Double,
        expectedLongitude: Double,
        expectedZoom: Double,
        expectedBearing: Double,
        expectedTilt: Double,
        startSequence: Long,
        movementType: ProgrammaticCameraMovementType
    ): ProgrammaticCameraSession {
        val session = ProgrammaticCameraSession(
            sessionId = UUID.randomUUID(),
            renderSessionId = renderSessionId,
            expectedLatitude = expectedLatitude,
            expectedLongitude = expectedLongitude,
            expectedZoom = expectedZoom,
            expectedBearing = expectedBearing,
            expectedTilt = expectedTilt,
            startSequence = startSequence,
            movementType = movementType
        )
        currentSession = session
        return session
    }

    /**
     * Called when a camera move starts. If it was a customer gesture,
     * any active programmatic suppression is cancelled.
     */
    fun onCameraMoveStarted(reason: Int, renderSessionId: UUID) {
        if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
            currentSession?.let { session ->
                if (session.renderSessionId == renderSessionId) {
                    currentSession = null
                }
            }
        }
    }

    /**
     * Consumes the current programmatic suppression state if the observed camera
     * matches the expected fingerprint.
     */
    fun consumeProgrammaticIdle(
        observedLatitude: Double,
        observedLongitude: Double,
        observedZoom: Double,
        observedBearing: Double,
        observedTilt: Double,
        renderSessionId: UUID
    ): ProgrammaticIdleResult {
        val session = currentSession ?: return ProgrammaticIdleResult.NO_PENDING_SESSION
        
        if (session.renderSessionId != renderSessionId) {
            return ProgrammaticIdleResult.WRONG_RENDER_SESSION
        }

        val latMatch = abs(session.expectedLatitude - observedLatitude) < session.latTolerance
        val lngMatch = abs(session.expectedLongitude - observedLongitude) < session.lngTolerance
        val zoomMatch = abs(session.expectedZoom - observedZoom) < session.zoomTolerance
        
        // Normalize bearings for comparison
        val b1 = (session.expectedBearing % 360 + 360) % 360
        val b2 = (observedBearing % 360 + 360) % 360
        val diff = abs(b1 - b2)
        val bearingMatch = diff < session.bearingTolerance || diff > (360.0 - session.bearingTolerance)
        
        val tiltMatch = abs(session.expectedTilt - observedTilt) < session.tiltTolerance

        return if (latMatch && lngMatch && zoomMatch && bearingMatch && tiltMatch) {
            currentSession = null
            ProgrammaticIdleResult.MATCHED_CURRENT_SESSION
        } else {
            ProgrammaticIdleResult.CAMERA_DOES_NOT_MATCH
        }
    }

    /**
     * Explicitly cancels programmatic suppression due to user interaction.
     */
    fun cancelForCustomerGesture() {
        currentSession = null
    }

    /**
     * Checks if a programmatic movement session is currently active.
     */
    fun isActive(): Boolean {
        return currentSession != null
    }

    /**
     * Clears session state for a specific render session upon MapView disposal.
     */
    fun clearForMapDisposal(renderSessionId: UUID) {
        if (currentSession?.renderSessionId == renderSessionId) {
            currentSession = null
        }
    }
}
