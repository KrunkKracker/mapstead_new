package com.jumastappworks.mapstead.data.backup

sealed interface DriveError {
    data object AuthorizationExpired : DriveError
    data object NetworkUnavailable : DriveError
    data object NotFound : DriveError
    data object QuotaExceeded : DriveError
    data object PermissionDenied : DriveError
    data object InvalidResponse : DriveError
    data object Cancelled : DriveError
    data class Unknown(val safeMessage: String, val throwable: Throwable? = null) : DriveError
}

fun Throwable.toDriveError(): DriveError {
    val cause = this
    if (cause.message?.contains("401") == true) {
        return DriveError.AuthorizationExpired
    }
    if (cause is com.google.api.client.googleapis.json.GoogleJsonResponseException) {
        return when (cause.statusCode) {
            401 -> DriveError.AuthorizationExpired
            404 -> DriveError.NotFound
            403 -> {
                val hasQuota = cause.details?.errors?.any { it.reason?.contains("quota", ignoreCase = true) == true } ?: false
                if (hasQuota) DriveError.QuotaExceeded else DriveError.PermissionDenied
            }
            else -> DriveError.Unknown("Google Drive API Error: ${cause.statusCode}", cause)
        }
    }
    
    // Check causes recursively in case it is wrapped
    val actualCause = cause.cause
    if (actualCause != null && actualCause != cause) {
        val mapped = actualCause.toDriveError()
        if (mapped !is DriveError.Unknown) {
            return mapped
        }
    }
    
    if (cause is java.net.UnknownHostException || cause is java.io.IOException) {
        return DriveError.NetworkUnavailable
    }
    return DriveError.Unknown(cause.message ?: "Unknown error", cause)
}
