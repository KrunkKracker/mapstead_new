package com.jumastappworks.mapstead

import com.jumastappworks.mapstead.data.mapping.CurrentLocationProvider
import com.jumastappworks.mapstead.data.mapping.LocationResult

class FakeCurrentLocationProvider : CurrentLocationProvider {
    var nextResult: LocationResult = LocationResult.Success(45.0, -110.0, 3.0f, System.currentTimeMillis(), LocationResult.Success.Source.Fresh, true)

    override suspend fun getCurrentLocation(): LocationResult {
        return nextResult
    }
}
