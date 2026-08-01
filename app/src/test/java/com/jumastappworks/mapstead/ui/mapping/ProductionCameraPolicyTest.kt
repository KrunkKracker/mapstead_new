package com.jumastappworks.mapstead.ui.mapping

import org.junit.Assert.*
import org.junit.Test

class ProductionCameraPolicyTest {

    @Test
    fun `Production policy correctly classifies idle results`() {
        val controller = ProgrammaticCameraController()
        
        assertTrue("NO_PENDING_SESSION should persist", 
            controller.shouldPersistCamera(ProgrammaticIdleResult.NO_PENDING_SESSION))
            
        assertFalse("MATCHED_CURRENT_SESSION should NOT persist", 
            controller.shouldPersistCamera(ProgrammaticIdleResult.MATCHED_CURRENT_SESSION))
            
        assertFalse("CAMERA_DOES_NOT_MATCH should NOT persist", 
            controller.shouldPersistCamera(ProgrammaticIdleResult.CAMERA_DOES_NOT_MATCH))
            
        assertFalse("WRONG_RENDER_SESSION should NOT persist", 
            controller.shouldPersistCamera(ProgrammaticIdleResult.WRONG_RENDER_SESSION))
    }
}
