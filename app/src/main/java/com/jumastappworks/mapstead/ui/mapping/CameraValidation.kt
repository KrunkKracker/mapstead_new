package com.jumastappworks.mapstead.ui.mapping

import com.jumastappworks.mapstead.util.GeometryUtils
import kotlin.math.abs

object CameraValidation {

    const val MIN_ZOOM = 0.0
    const val MAX_ZOOM = 22.0
    const val DEFAULT_ZOOM = 17.0
    const val FALLBACK_BEARING = 0.0

    fun isValid(latitude: Double, longitude: Double, zoom: Double, bearing: Double): Boolean {
        return latitude.isFinite() && latitude in -90.0..90.0 &&
                longitude.isFinite() && longitude in -180.0..180.0 &&
                zoom.isFinite() && zoom in MIN_ZOOM..MAX_ZOOM &&
                bearing.isFinite()
    }

    fun normalizeBearing(bearing: Double): Double {
        if (!bearing.isFinite()) return FALLBACK_BEARING
        var normalized = bearing % 360.0
        if (normalized < 0) normalized += 360.0
        return normalized
    }

    fun isDefaultWorldView(latitude: Double, longitude: Double, zoom: Double): Boolean {
        // MapLibre default whole-world state is (0, 0, 0)
        return abs(latitude) < 0.0001 && abs(longitude) < 0.0001 && zoom < 1.0
    }

    fun circularBearingDifference(a: Double, b: Double): Double {
        val diff = abs(normalizeBearing(a) - normalizeBearing(b))
        return if (diff > 180.0) 360.0 - diff else diff
    }

    fun isMeaningfulChange(
        oldLat: Double?, oldLng: Double?, oldZoom: Double?, oldBearing: Double?,
        newLat: Double, newLng: Double, newZoom: Double, newBearing: Double,
        oldPlanId: java.util.UUID?, newPlanId: java.util.UUID?
    ): Boolean {
        if (oldPlanId != newPlanId) return true
        if (oldLat == null || oldLng == null || oldZoom == null || oldBearing == null) return true

        val latDiff = abs(newLat - oldLat)
        val lngDiff = abs(newLng - oldLng)
        val zoomDiff = abs(newZoom - oldZoom)
        val bearingDiff = circularBearingDifference(newBearing, oldBearing)

        return latDiff > 0.000001 || lngDiff > 0.000001 || zoomDiff > 0.01 || bearingDiff > 0.1
    }
}
