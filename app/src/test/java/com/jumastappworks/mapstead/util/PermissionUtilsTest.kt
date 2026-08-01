package com.jumastappworks.mapstead.util

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.ComponentActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class PermissionUtilsTest {

    @Test
    fun `determineLocationPermissionStatus logic with null rationale`() {
        // Initial state
        assertEquals(
            PermissionStatus.NotRequested,
            PermissionUtils.determineLocationPermissionStatus(
                isGranted = false,
                hasBeenRequested = false,
                shouldShowRationale = false
            )
        )

        // Denied Retryable (shouldShowRationale = true)
        assertEquals(
            PermissionStatus.DeniedRetryable,
            PermissionUtils.determineLocationPermissionStatus(
                isGranted = false,
                hasBeenRequested = true,
                shouldShowRationale = true
            )
        )

        // Denied Permanent (shouldShowRationale = false)
        assertEquals(
            PermissionStatus.DeniedPermanently,
            PermissionUtils.determineLocationPermissionStatus(
                isGranted = false,
                hasBeenRequested = true,
                shouldShowRationale = false
            )
        )

        // Null rationale -> Conservative fallback to Retryable
        assertEquals(
            PermissionStatus.DeniedRetryable,
            PermissionUtils.determineLocationPermissionStatus(
                isGranted = false,
                hasBeenRequested = true,
                shouldShowRationale = null
            )
        )
    }

    @Test
    fun `findComponentActivity unwraps context wrappers`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).get()
        
        // Single wrapper
        val wrappedOnce = ContextWrapper(activity)
        assertEquals(activity, wrappedOnce.findComponentActivity())

        // Multiple wrappers
        val wrappedThrice = ContextWrapper(ContextWrapper(ContextWrapper(activity)))
        assertEquals(activity, wrappedThrice.findComponentActivity())
    }

    @Test
    fun `findComponentActivity returns null for non-activity context`() {
        val context = RuntimeEnvironment.getApplication()
        assertNull(context.findComponentActivity())
    }

    @Test
    fun `openAppSettings launches intent successfully`() {
        val context = RuntimeEnvironment.getApplication()
        val result = PermissionUtils.openAppSettings(context)
        
        assertTrue(result)
        
        val shadowApp = shadowOf(RuntimeEnvironment.getApplication())
        val nextIntent = shadowApp.nextStartedActivity
        assertNotNull(nextIntent)
        assertEquals(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, nextIntent.action)
        // Check that data starts with "package:"
        assertTrue(nextIntent.data.toString().startsWith("package:"))
    }
}
