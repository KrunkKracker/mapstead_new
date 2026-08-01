package com.jumastappworks.mapstead.ui.mapping

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CameraValidationTest {

    @Test
    fun `coordinate validation logic`() {
        assertTrue(CameraValidation.isValid(45.0, -110.0, 10.0, 0.0))
        assertFalse(CameraValidation.isValid(91.0, 0.0, 10.0, 0.0))
        assertFalse(CameraValidation.isValid(0.0, 181.0, 10.0, 0.0))
        assertFalse(CameraValidation.isValid(Double.NaN, 0.0, 10.0, 0.0))
    }

    @Test
    fun `zoom range validation`() {
        assertTrue(CameraValidation.isValid(0.0, 0.0, 0.0, 0.0))
        assertTrue(CameraValidation.isValid(0.0, 0.0, 22.0, 0.0))
        assertFalse(CameraValidation.isValid(0.0, 0.0, -1.0, 0.0))
        assertFalse(CameraValidation.isValid(0.0, 0.0, 23.0, 0.0))
    }

    @Test
    fun `bearing normalization`() {
        assertEquals(0.0, CameraValidation.normalizeBearing(0.0), 1e-10)
        assertEquals(180.0, CameraValidation.normalizeBearing(180.0), 1e-10)
        assertEquals(0.0, CameraValidation.normalizeBearing(360.0), 1e-10)
        assertEquals(90.0, CameraValidation.normalizeBearing(450.0), 1e-10)
        assertEquals(270.0, CameraValidation.normalizeBearing(-90.0), 1e-10)
        assertEquals(0.0, CameraValidation.normalizeBearing(Double.NaN), 1e-10)
        assertEquals(0.0, CameraValidation.normalizeBearing(Double.POSITIVE_INFINITY), 1e-10)
    }

    @Test
    fun `circular bearing difference`() {
        assertEquals(10.0, CameraValidation.circularBearingDifference(0.0, 10.0), 1e-10)
        assertEquals(10.0, CameraValidation.circularBearingDifference(355.0, 5.0), 1e-10)
        assertEquals(170.0, CameraValidation.circularBearingDifference(10.0, 180.0), 1e-10)
        assertEquals(10.0, CameraValidation.circularBearingDifference(5.0, 355.0), 1e-10)
    }

    @Test
    fun `meaningful change detection`() {
        val pid = UUID.randomUUID()
        
        // No change
        assertFalse(CameraValidation.isMeaningfulChange(28.0, -82.0, 15.0, 0.0, 28.0, -82.0, 15.0, 0.0, pid, pid))
        
        // Small change (below epsilon)
        assertFalse(CameraValidation.isMeaningfulChange(28.0, -82.0, 15.0, 0.0, 28.0000001, -82.0, 15.0, 0.0, pid, pid))
        
        // Meaningful lat change
        assertTrue(CameraValidation.isMeaningfulChange(28.0, -82.0, 15.0, 0.0, 28.001, -82.0, 15.0, 0.0, pid, pid))
        
        // Meaningful zoom change
        assertTrue(CameraValidation.isMeaningfulChange(28.0, -82.0, 15.0, 0.0, 28.0, -82.0, 15.1, 0.0, pid, pid))
        
        // Meaningful bearing change
        assertTrue(CameraValidation.isMeaningfulChange(28.0, -82.0, 15.0, 359.0, 28.0, -82.0, 15.0, 1.0, pid, pid))
        
        // Plan change is always meaningful
        assertTrue(CameraValidation.isMeaningfulChange(28.0, -82.0, 15.0, 0.0, 28.0, -82.0, 15.0, 0.0, pid, UUID.randomUUID()))
    }
}
