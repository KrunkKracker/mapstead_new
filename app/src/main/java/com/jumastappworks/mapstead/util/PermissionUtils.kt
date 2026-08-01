package com.jumastappworks.mapstead.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity

sealed interface PermissionStatus {
    data object NotRequested : PermissionStatus
    data object Granted : PermissionStatus
    data object DeniedRetryable : PermissionStatus
    data object DeniedPermanently : PermissionStatus
}

fun Context.findComponentActivity(): ComponentActivity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is ComponentActivity) return context
        context = context.baseContext
    }
    return null
}

object PermissionUtils {

    /**
     * Determines the permission status based on current grants and rationale state.
     * @param shouldShowRationale null if the activity context is missing/unknown.
     */
    fun determineLocationPermissionStatus(
        isGranted: Boolean,
        hasBeenRequested: Boolean,
        shouldShowRationale: Boolean?
    ): PermissionStatus {
        if (isGranted) return PermissionStatus.Granted
        if (!hasBeenRequested) return PermissionStatus.NotRequested
        
        return when (shouldShowRationale) {
            true -> PermissionStatus.DeniedRetryable
            false -> PermissionStatus.DeniedPermanently
            null -> PermissionStatus.DeniedRetryable // Conservative fallback
        }
    }

    fun openAppSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }
}
