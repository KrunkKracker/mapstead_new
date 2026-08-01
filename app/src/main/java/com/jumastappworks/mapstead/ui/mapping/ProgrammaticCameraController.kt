package com.jumastappworks.mapstead.ui.mapping

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages token-based suppression of camera interaction events.
 * This ensures that programmatic camera movements (e.g. style restoration, initial focus)
 * do not falsely trigger user-interaction or persistence logic.
 * 
 * Safely supports overlapping programmatic moves using unique tokens.
 */
class ProgrammaticCameraController {
    private val activeTokens = ConcurrentHashMap.newKeySet<UUID>()

    /**
     * Issues a new unique suppression token.
     */
    fun issueToken(): UUID {
        val token = UUID.randomUUID()
        activeTokens.add(token)
        return token
    }

    /**
     * Consumes a matching suppression token.
     * Returns true if the token was active and has been consumed.
     */
    fun consume(token: UUID?): Boolean {
        if (token == null) return false
        return activeTokens.remove(token)
    }

    /**
     * Checks if any programmatic movement is currently active.
     */
    fun isActive(): Boolean {
        return activeTokens.isNotEmpty()
    }

    /**
     * Checks if the supplied token is currently active.
     */
    fun isTokenActive(token: UUID?): Boolean {
        return token != null && activeTokens.contains(token)
    }

    /**
     * Clears all active suppression tokens.
     * Use with caution to avoid race conditions.
     */
    fun clear() {
        activeTokens.clear()
    }
}
