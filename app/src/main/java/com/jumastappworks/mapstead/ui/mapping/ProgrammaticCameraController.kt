package com.jumastappworks.mapstead.ui.mapping

import java.util.UUID

/**
 * Unique identifier for a programmatic camera movement session.
 */
data class ProgrammaticCameraSession(
    val sessionId: UUID,
    val generation: Long
)

/**
 * Manages session-based suppression of camera interaction events.
 * Implements a "latest-session-wins" policy to ensure that programmatic movements
 * do not falsely trigger user-interaction or persistence logic.
 */
class ProgrammaticCameraController {
    private var currentSession: ProgrammaticCameraSession? = null
    private var generation: Long = 0

    /**
     * Begins a new programmatic movement session, superseding any existing one.
     */
    fun beginProgrammaticMove(): ProgrammaticCameraSession {
        generation++
        val session = ProgrammaticCameraSession(UUID.randomUUID(), generation)
        currentSession = session
        return session
    }

    /**
     * Called when a camera move starts. If it was a customer gesture,
     * any active programmatic suppression is cancelled.
     * 
     * @param reason The move reason code from MapLibre.
     */
    fun onCameraMoveStarted(reason: Int) {
        // MapLibre move reasons: 
        // 1: REASON_API_ANIMATION
        // 2: REASON_DEVELOPER_ANIMATION
        // 3: REASON_GESTURE
        if (reason == 3) { // REASON_GESTURE
            cancelForCustomerGesture()
        }
    }

    /**
     * Consumes the current programmatic suppression state.
     * Returns true if a session was active and has been cleared.
     */
    fun consumeProgrammaticIdle(): Boolean {
        val active = currentSession != null
        currentSession = null
        return active
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
     * Clears all session state upon MapView disposal.
     */
    fun clearForMapDisposal() {
        currentSession = null
        generation = 0
    }
}
