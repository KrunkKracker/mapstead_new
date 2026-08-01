package com.jumastappworks.mapstead.util

import org.junit.Assert.*
import org.junit.Test

class PermissionStatusTest {

    @Test
    fun `determineLocationPermissionStatus logic branches`() {
        // Initial state
        assertEquals(
            PermissionStatus.NotRequested,
            PermissionUtils.determineLocationPermissionStatus(isGranted = false, hasBeenRequested = false, shouldShowRationale = false)
        )

        // Granted
        assertEquals(
            PermissionStatus.Granted,
            PermissionUtils.determineLocationPermissionStatus(isGranted = true, hasBeenRequested = true, shouldShowRationale = false)
        )

        // Denied but retryable (Rationale available)
        assertEquals(
            PermissionStatus.DeniedRetryable,
            PermissionUtils.determineLocationPermissionStatus(isGranted = false, hasBeenRequested = true, shouldShowRationale = true)
        )

        // Denied permanently (Requested before, no rationale now)
        assertEquals(
            PermissionStatus.DeniedPermanently,
            PermissionUtils.determineLocationPermissionStatus(isGranted = false, hasBeenRequested = true, shouldShowRationale = false)
        )
    }
}
