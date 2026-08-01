package com.jumastappworks.mapstead.data.mapping

sealed interface LocationResult {
    data class Success(
        val latitude: Double, 
        val longitude: Double, 
        val accuracyMeters: Float,
        val timestampMillis: Long,
        val source: Source,
        val isPrecisePermission: Boolean
    ) : LocationResult {
        enum class Source { Fresh, LastKnown }
    }
    object PermissionDenied : LocationResult
    object PermanentlyDenied : LocationResult
    object LocationUnavailable : LocationResult
    object ProviderDisabled : LocationResult
    object Timeout : LocationResult
    data class Error(val message: String) : LocationResult
}

interface CurrentLocationProvider {
    suspend fun getCurrentLocation(): LocationResult
}
