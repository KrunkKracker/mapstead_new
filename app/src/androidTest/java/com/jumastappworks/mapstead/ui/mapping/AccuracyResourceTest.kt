package com.jumastappworks.mapstead.ui.mapping

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jumastappworks.mapstead.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class AccuracyResourceTest {

    @Test
    fun testAccuracyFormattingSucceeds() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        // Use a locale with dot decimal separator for predictable verification
        val config = context.resources.configuration
        config.setLocale(Locale.US)
        val localizedContext = context.createConfigurationContext(config)

        // 1. Verify that formatting with 12.34 produces "Accuracy: 12.3 m"
        val result = localizedContext.getString(R.string.accuracy, 12.34)
        assertEquals("Accuracy: 12.3 m", result)

        // 2. Verify that formatting with 0.0 succeeds
        val resultZero = localizedContext.getString(R.string.accuracy, 0.0)
        assertEquals("Accuracy: 0.0 m", resultZero)

        // 3. Verify that formatting with a large valid accuracy succeeds
        val resultLarge = localizedContext.getString(R.string.accuracy, 1234.567)
        assertEquals("Accuracy: 1234.6 m", resultLarge)
    }
}
