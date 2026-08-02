package com.jumastappworks.mapstead

import org.junit.Assert.*
import org.junit.Test

class BuildConfigConsistencyTest {

    @Test
    fun `MAPTILER_CONFIGURED matches API Key blankness`() {
        val key = BuildConfig.MAPTILER_API_KEY
        val configured = BuildConfig.MAPTILER_CONFIGURED
        
        if (key.isBlank() || key == "PLACEHOLDER") {
            assertFalse("MAPTILER_CONFIGURED must be false when key is blank or placeholder", configured)
        } else {
            assertTrue("MAPTILER_CONFIGURED must be true when key is provided", configured)
        }

        // Phase 2.2h5R9C: Explicit No-Key Verification via Gradle Property
        if (System.getProperty("mapstead.expectNoKey") == "true") {
            assertTrue("BuildConfig.MAPTILER_API_KEY must be blank when expectNoKey is true", key.isBlank())
            assertFalse("BuildConfig.MAPTILER_CONFIGURED must be false when expectNoKey is true", configured)
        }
    }
}
