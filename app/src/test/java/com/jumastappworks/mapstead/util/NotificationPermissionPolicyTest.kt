package com.jumastappworks.mapstead.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NotificationPermissionPolicyTest {

    private val context = mockk<Context>()

    @Before
    fun setup() {
        mockkStatic(ContextCompat::class)
    }

    @After
    fun teardown() {
        unmockkStatic(ContextCompat::class)
    }

    @Test
    fun `not required on older android versions`() {
        assertEquals(
            NotificationPermissionStatus.NotRequired,
            NotificationPermissionPolicy.determineStatus(
                context, false, false, sdkInt = Build.VERSION_CODES.S_V2
            )
        )
    }

    @Test
    fun `granted status on android 13 plus`() {
        every { ContextCompat.checkSelfPermission(any(), Manifest.permission.POST_NOTIFICATIONS) } returns PackageManager.PERMISSION_GRANTED
        
        assertEquals(
            NotificationPermissionStatus.Granted,
            NotificationPermissionPolicy.determineStatus(
                context, true, false, sdkInt = Build.VERSION_CODES.TIRAMISU
            )
        )
    }

    @Test
    fun `denied permanently when requested before but no rationale now`() {
        every { ContextCompat.checkSelfPermission(any(), Manifest.permission.POST_NOTIFICATIONS) } returns PackageManager.PERMISSION_DENIED
        
        assertEquals(
            NotificationPermissionStatus.DeniedPermanently,
            NotificationPermissionPolicy.determineStatus(
                context, hasBeenRequested = true, shouldShowRationale = false, sdkInt = Build.VERSION_CODES.TIRAMISU
            )
        )
    }

    @Test
    fun `denied retryable when rationale should be shown`() {
        every { ContextCompat.checkSelfPermission(any(), Manifest.permission.POST_NOTIFICATIONS) } returns PackageManager.PERMISSION_DENIED
        
        assertEquals(
            NotificationPermissionStatus.DeniedRetryable,
            NotificationPermissionPolicy.determineStatus(
                context, hasBeenRequested = true, shouldShowRationale = true, sdkInt = Build.VERSION_CODES.TIRAMISU
            )
        )
    }

    @Test
    fun `denied retryable fallback when activity rationale is unknown`() {
        every { ContextCompat.checkSelfPermission(any(), Manifest.permission.POST_NOTIFICATIONS) } returns PackageManager.PERMISSION_DENIED
        
        assertEquals(
            NotificationPermissionStatus.DeniedRetryable,
            NotificationPermissionPolicy.determineStatus(
                context, hasBeenRequested = true, shouldShowRationale = null, sdkInt = Build.VERSION_CODES.TIRAMISU
            )
        )
    }

    @Test
    fun `not requested state`() {
        every { ContextCompat.checkSelfPermission(any(), Manifest.permission.POST_NOTIFICATIONS) } returns PackageManager.PERMISSION_DENIED
        
        assertEquals(
            NotificationPermissionStatus.NotRequested,
            NotificationPermissionPolicy.determineStatus(
                context, hasBeenRequested = false, shouldShowRationale = false, sdkInt = Build.VERSION_CODES.TIRAMISU
            )
        )
    }
}
