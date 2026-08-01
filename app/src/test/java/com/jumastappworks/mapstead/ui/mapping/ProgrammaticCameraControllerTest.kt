package com.jumastappworks.mapstead.ui.mapping

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ProgrammaticCameraControllerTest {

    @Test
    fun `Programmatic idle suppresses interaction`() {
        val controller = ProgrammaticCameraController()
        
        // Start a programmatic move
        controller.beginProgrammaticMove()
        assertTrue(controller.isActive())
        
        // Consume idle
        val suppressed = controller.consumeProgrammaticIdle()
        assertTrue("Should have suppressed interaction logic", suppressed)
        assertFalse("Should no longer be active after consumption", controller.isActive())
    }

    @Test
    fun `Latest session supersedes older session safely`() {
        val controller = ProgrammaticCameraController()
        
        val session1 = controller.beginProgrammaticMove()
        val session2 = controller.beginProgrammaticMove()
        
        assertTrue(controller.isActive())
        assertNotEquals("Sessions should be unique", session1.sessionId, session2.sessionId)
        assertTrue("Generation should increment", session2.generation > session1.generation)
        
        // Idle should consume everything because it's session-aware but we use a latest-wins policy
        // with exactly one active session at a time in our current design.
        assertTrue(controller.consumeProgrammaticIdle())
        assertFalse(controller.isActive())
    }

    @Test
    fun `Customer gesture cancels obsolete suppression`() {
        val controller = ProgrammaticCameraController()
        
        controller.beginProgrammaticMove()
        assertTrue(controller.isActive())
        
        // Move started by gesture (reason 3)
        controller.onCameraMoveStarted(3)
        assertFalse("Suppression should be cancelled by user gesture", controller.isActive())
        assertFalse(controller.consumeProgrammaticIdle())
    }

    @Test
    fun `API animation does not cancel suppression`() {
        val controller = ProgrammaticCameraController()
        
        controller.beginProgrammaticMove()
        
        // API animation (reason 1 or 2)
        controller.onCameraMoveStarted(1)
        assertTrue("API animation should NOT cancel suppression", controller.isActive())
    }

    @Test
    fun `Missing idle does not suppress later customer interaction indefinitely`() {
        val controller = ProgrammaticCameraController()
        
        controller.beginProgrammaticMove()
        
        // Imagine MapLibre never fires idle, but user starts a gesture
        controller.onCameraMoveStarted(3)
        
        assertFalse("Next interaction should NOT be suppressed", controller.isActive())
    }

    @Test
    fun `Real pan immediately after programmatic move persists`() {
        val controller = ProgrammaticCameraController()
        
        // 1. Programmatic move finishes
        controller.beginProgrammaticMove()
        assertTrue(controller.consumeProgrammaticIdle())
        
        // 2. User pans
        assertFalse("Next idle should not be suppressed", controller.consumeProgrammaticIdle())
    }

    @Test
    fun `Disposal clears all suppression`() {
        val controller = ProgrammaticCameraController()
        controller.beginProgrammaticMove()
        
        controller.clearForMapDisposal()
        assertFalse(controller.isActive())
    }
}
