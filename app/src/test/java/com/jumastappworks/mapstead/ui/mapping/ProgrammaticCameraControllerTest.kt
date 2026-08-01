package com.jumastappworks.mapstead.ui.mapping

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import org.maplibre.android.maps.MapLibreMap

class ProgrammaticCameraControllerTest {

    private val renderSessionId = UUID.randomUUID()

    @Test
    fun `Programmatic idle suppresses interaction and matching fingerprint is consumed`() {
        val controller = ProgrammaticCameraController()
        
        // 1. Begin movement A
        controller.beginProgrammaticMove(
            renderSessionId = renderSessionId,
            expectedLatitude = 10.0,
            expectedLongitude = 20.0,
            expectedZoom = 15.0,
            expectedBearing = 0.0,
            expectedTilt = 0.0,
            startSequence = 1,
            movementType = ProgrammaticCameraMovementType.RESTORATION
        )
        assertTrue(controller.isActive())
        
        // 2. Deliver idle with matching camera
        val result = controller.consumeProgrammaticIdle(
            observedLatitude = 10.0,
            observedLongitude = 20.0,
            observedZoom = 15.0,
            observedBearing = 0.0,
            observedTilt = 0.0,
            renderSessionId = renderSessionId
        )
        
        assertEquals(ProgrammaticIdleResult.MATCHED_CURRENT_SESSION, result)
        assertFalse("Should no longer be active after matching consumption", controller.isActive())
    }

    @Test
    fun `Latest session supersedes older session and old idle cannot consume new session`() {
        val controller = ProgrammaticCameraController()
        
        // 1. Begin movement A
        controller.beginProgrammaticMove(
            renderSessionId = renderSessionId,
            expectedLatitude = 10.0,
            expectedLongitude = 10.0,
            expectedZoom = 10.0,
            expectedBearing = 0.0,
            expectedTilt = 0.0,
            startSequence = 1,
            movementType = ProgrammaticCameraMovementType.INITIAL_FOCUS
        )
        
        // 2. Begin movement B (supersedes A)
        controller.beginProgrammaticMove(
            renderSessionId = renderSessionId,
            expectedLatitude = 20.0,
            expectedLongitude = 20.0,
            expectedZoom = 20.0,
            expectedBearing = 0.0,
            expectedTilt = 0.0,
            startSequence = 2,
            movementType = ProgrammaticCameraMovementType.RESTORATION
        )
        
        // 3. Deliver idle from A (late idle)
        val resultA = controller.consumeProgrammaticIdle(
            observedLatitude = 10.0,
            observedLongitude = 10.0,
            observedZoom = 10.0,
            observedBearing = 0.0,
            observedTilt = 0.0,
            renderSessionId = renderSessionId
        )
        
        assertEquals("Old idle should not match current session B", ProgrammaticIdleResult.CAMERA_DOES_NOT_MATCH, resultA)
        assertTrue("Session B should remain pending", controller.isActive())
        
        // 4. Deliver idle from B
        val resultB = controller.consumeProgrammaticIdle(
            observedLatitude = 20.0,
            observedLongitude = 20.0,
            observedZoom = 20.0,
            observedBearing = 0.0,
            observedTilt = 0.0,
            renderSessionId = renderSessionId
        )
        
        assertEquals(ProgrammaticIdleResult.MATCHED_CURRENT_SESSION, resultB)
        assertFalse(controller.isActive())
    }

    @Test
    fun `Customer gesture cancels suppression only for matching session`() {
        val controller = ProgrammaticCameraController()
        
        controller.beginProgrammaticMove(
            renderSessionId = renderSessionId,
            expectedLatitude = 10.0,
            expectedLongitude = 10.0,
            expectedZoom = 10.0,
            expectedBearing = 0.0,
            expectedTilt = 0.0,
            startSequence = 1,
            movementType = ProgrammaticCameraMovementType.MY_LOCATION
        )
        assertTrue(controller.isActive())
        
        // Move started by gesture in WRONG session
        controller.onCameraMoveStarted(MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE, UUID.randomUUID())
        assertTrue("Gesture in different session should NOT cancel suppression", controller.isActive())
        
        // Move started by gesture in CORRECT session
        controller.onCameraMoveStarted(MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE, renderSessionId)
        assertFalse("Gesture in correct session should cancel suppression", controller.isActive())
    }

    @Test
    fun `Disposal clears correct render session`() {
        val controller = ProgrammaticCameraController()
        controller.beginProgrammaticMove(renderSessionId, 10.0, 10.0, 10.0, 0.0, 0.0, 1, ProgrammaticCameraMovementType.RESTORATION)
        
        // Disposal of wrong session
        controller.clearForMapDisposal(UUID.randomUUID())
        assertTrue("Disposing wrong session should not clear active one", controller.isActive())
        
        // Disposal of correct session
        controller.clearForMapDisposal(renderSessionId)
        assertFalse("Disposing correct session should clear it", controller.isActive())
    }

    @Test
    fun `Bearing wraparound tolerance handling`() {
        val controller = ProgrammaticCameraController()
        
        // Expected 359.9
        controller.beginProgrammaticMove(renderSessionId, 10.0, 10.0, 10.0, 359.9, 0.0, 1, ProgrammaticCameraMovementType.RESTORATION)
        
        // Observed 0.1 (diff is 0.2 across 360 wrap)
        val result = controller.consumeProgrammaticIdle(10.0, 10.0, 10.0, 0.1, 0.0, renderSessionId)
        assertEquals(ProgrammaticIdleResult.MATCHED_CURRENT_SESSION, result)
    }

    @Test
    fun `Real gesture idle reaches interaction logic`() {
        val controller = ProgrammaticCameraController()
        
        // 1. Programmatic move completes
        controller.beginProgrammaticMove(renderSessionId, 10.0, 10.0, 10.0, 0.0, 0.0, 1, ProgrammaticCameraMovementType.RESTORATION)
        controller.consumeProgrammaticIdle(10.0, 10.0, 10.0, 0.0, 0.0, renderSessionId)
        
        // 2. User idle arrives (no pending session)
        val result = controller.consumeProgrammaticIdle(15.0, 15.0, 12.0, 90.0, 0.0, renderSessionId)
        assertEquals(ProgrammaticIdleResult.NO_PENDING_SESSION, result)
    }
}
