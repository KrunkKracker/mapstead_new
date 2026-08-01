package com.jumastappworks.mapstead.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

sealed interface NotificationPermissionStatus {
    data object NotRequired : NotificationPermissionStatus
    data object Granted : NotificationPermissionStatus
    data object NotRequested : NotificationPermissionStatus
    data object DeniedRetryable : NotificationPermissionStatus
    data object DeniedPermanently : NotificationPermissionStatus
}

object NotificationPermissionPolicy {

    /**
     * Determines the notification permission status.
     * @param shouldShowRationale null if the activity context is missing/unknown.
     */
    fun determineStatus(
        context: Context,
        hasBeenRequested: Boolean,
        shouldShowRationale: Boolean?,
        sdkInt: Int = Build.VERSION_CODES.TIRAMISU // Default to current for policy check if needed
    ): NotificationPermissionStatus {
        // Use provided sdkInt for testing or real Build.VERSION.SDK_INT
        val currentSdk = if (sdkInt == Build.VERSION_CODES.TIRAMISU && Build.VERSION.SDK_INT != 0) Build.VERSION.SDK_INT else sdkInt

        if (currentSdk < Build.VERSION_CODES.TIRAMISU) {
            return NotificationPermissionStatus.NotRequired
        }

        val isGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (isGranted) return NotificationPermissionStatus.Granted
        if (!hasBeenRequested) return NotificationPermissionStatus.NotRequested

        return when (shouldShowRationale) {
            true -> NotificationPermissionStatus.DeniedRetryable
            false -> NotificationPermissionStatus.DeniedPermanently
            null -> NotificationPermissionStatus.DeniedRetryable // Conservative fallback
        }
    }
}
