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
    fun `Customer gesture cancels obsolete suppression`() {
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
        
        // Move started by gesture (REASON_API_GESTURE = 1)
        controller.onCameraMoveStarted(MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE)
        assertFalse("Suppression should be cancelled by user gesture", controller.isActive())
        
        val result = controller.consumeProgrammaticIdle(10.0, 10.0, 10.0, 0.0, 0.0, renderSessionId)
        assertEquals(ProgrammaticIdleResult.NO_PENDING_SESSION, result)
    }

    @Test
    fun `API animation does not cancel suppression`() {
        val controller = ProgrammaticCameraController()
        
        controller.beginProgrammaticMove(renderSessionId, 10.0, 10.0, 10.0, 0.0, 0.0, 1, ProgrammaticCameraMovementType.RESTORATION)
        
        // API animation (REASON_API_ANIMATION = 3)
        controller.onCameraMoveStarted(MapLibreMap.OnCameraMoveStartedListener.REASON_API_ANIMATION)
        assertTrue("API animation should NOT cancel suppression", controller.isActive())
    }

    @Test
    fun `Wrong render session cannot consume movement`() {
        val controller = ProgrammaticCameraController()
        controller.beginProgrammaticMove(renderSessionId, 10.0, 10.0, 10.0, 0.0, 0.0, 1, ProgrammaticCameraMovementType.RESTORATION)
        
        val result = controller.consumeProgrammaticIdle(10.0, 10.0, 10.0, 0.0, 0.0, UUID.randomUUID())
        assertEquals(ProgrammaticIdleResult.WRONG_RENDER_SESSION, result)
        assertTrue(controller.isActive())
    }

    @Test
    fun `Real gesture idle reaches interaction logic`() {
        val controller = ProgrammaticCameraController()
        
        // 1. Programmatic move completes
        controller.beginProgrammaticMove(renderSessionId, 10.0, 10.0, 10.0, 0.0, 0.0, 1, ProgrammaticCameraMovementType.RESTORATION)
        controller.consumeProgrammaticIdle(10.0, 10.0, 10.0, 0.0, 0.0, renderSessionId)
        
        // 2. User starts gesture
        controller.onCameraMoveStarted(MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE)
        
        // 3. User idle arrives
        val result = controller.consumeProgrammaticIdle(15.0, 15.0, 12.0, 90.0, 0.0, renderSessionId)
        assertEquals("User idle should not be suppressed by previous completed move", ProgrammaticIdleResult.NO_PENDING_SESSION, result)
    }
}
