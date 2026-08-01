package com.jumastappworks.mapstead

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class VersionDisplayTest {
    
    @Test
    fun `version values are correct`() {
        assertEquals("0.02", BuildConfig.VERSION_NAME)
        assertEquals(2, BuildConfig.VERSION_CODE)
    }
}
