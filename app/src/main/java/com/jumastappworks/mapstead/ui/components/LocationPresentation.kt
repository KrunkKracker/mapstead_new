package com.jumastappworks.mapstead.ui.components

import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.mapping.LocationResult

object LocationPresentation {
    fun getMessageRes(result: LocationResult): Int {
        return when (result) {
            is LocationResult.Success -> {
                if (result.source == LocationResult.Success.Source.Fresh) R.string.status_live
                else R.string.status_cached
            }
            LocationResult.PermissionDenied -> R.string.location_issue_permission_denied
            LocationResult.PermanentlyDenied -> R.string.location_issue_permission_permanently_denied
            LocationResult.ProviderDisabled -> R.string.location_issue_provider_disabled
            LocationResult.Timeout -> R.string.location_issue_timeout
            LocationResult.LocationUnavailable -> R.string.location_issue_unavailable
            is LocationResult.Error -> R.string.location_issue_generic
        }
    }
}
