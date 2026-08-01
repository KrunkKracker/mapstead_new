package com.jumastappworks.mapstead.data.mapping

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationTracker @Inject constructor(
    @ApplicationContext private val context: Context
) : CurrentLocationProvider {

    private val fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): LocationResult {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        if (!hasFine && !hasCoarse) {
            return LocationResult.PermissionDenied
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val isGpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        val isNetworkEnabled = locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        if (!isGpsEnabled && !isNetworkEnabled) {
            return LocationResult.ProviderDisabled
        }

        return try {
            val location = withTimeoutOrNull(8000L) {
                fusedLocationProviderClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    null
                ).await()
            }
            if (location != null) {
                LocationResult.Success(
                    latitude = location.latitude, 
                    longitude = location.longitude, 
                    accuracyMeters = location.accuracy,
                    timestampMillis = location.time,
                    source = LocationResult.Success.Source.Fresh,
                    isPrecisePermission = hasFine
                )
            } else {
                val lastKnown = fusedLocationProviderClient.lastLocation.await()
                if (lastKnown != null) {
                    LocationResult.Success(
                        latitude = lastKnown.latitude, 
                        longitude = lastKnown.longitude, 
                        accuracyMeters = lastKnown.accuracy,
                        timestampMillis = lastKnown.time,
                        source = LocationResult.Success.Source.LastKnown,
                        isPrecisePermission = hasFine
                    )
                } else {
                    LocationResult.Timeout
                }
            }
        } catch (e: Exception) {
            LocationResult.Error(e.message ?: "Unknown location tracking error.")
        }
    }
}
