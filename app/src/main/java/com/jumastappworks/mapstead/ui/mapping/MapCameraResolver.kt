package com.jumastappworks.mapstead.ui.mapping

import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.data.db.entities.PlanEntity
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.util.GeometryUtils

enum class CameraSource {
    SAVED_PLAN_CAMERA,
    PROPERTY_COORDINATES,
    FEATURE_BOUNDS,
    SAFE_FALLBACK,
    REPAIRED_DEFAULT_CAMERA
}

data class CameraResolution(
    val focus: MapCameraFocus,
    val source: CameraSource
)

object MapCameraResolver {
    fun resolveInitialCamera(
        plan: PlanEntity?,
        property: PropertyEntity?,
        features: List<MapFeatureEntity>
    ): CameraResolution {
        // 1. Valid saved Plan center, zoom, and bearing
        if (plan != null) {
            val lat = plan.centerLatitude
            val lng = plan.centerLongitude
            val zoom = plan.zoom ?: CameraValidation.DEFAULT_ZOOM
            val bearing = CameraValidation.normalizeBearing(plan.bearing ?: 0.0)
            
            if (lat != null && lng != null && CameraValidation.isValid(lat, lng, zoom, bearing)) {
                // PART 3: Reject default-like whole-world views IF better context exists
                if (CameraValidation.isDefaultWorldView(lat, lng, zoom)) {
                    val fallback = resolveFallback(property, features)
                    if (fallback.source != CameraSource.SAFE_FALLBACK) {
                        return CameraResolution(fallback.focus, CameraSource.REPAIRED_DEFAULT_CAMERA)
                    }
                }

                return CameraResolution(
                    MapCameraFocus.Point(
                        latitude = lat,
                        longitude = lng,
                        zoom = zoom.toFloat(),
                        bearing = bearing
                    ),
                    CameraSource.SAVED_PLAN_CAMERA
                )
            }
        }

        return resolveFallback(property, features)
    }

    private fun resolveFallback(
        property: PropertyEntity?,
        features: List<MapFeatureEntity>
    ): CameraResolution {
        // 2. Valid Property coordinates
        if (property != null) {
            val lat = property.latitude
            val lng = property.longitude
            if (lat != null && lng != null && CameraValidation.isValid(lat, lng, CameraValidation.DEFAULT_ZOOM, 0.0)) {
                return CameraResolution(
                    MapCameraFocus.Point(
                        latitude = lat,
                        longitude = lng,
                        zoom = CameraValidation.DEFAULT_ZOOM.toFloat(),
                        bearing = 0.0
                    ),
                    CameraSource.PROPERTY_COORDINATES
                )
            }
        }

        // 3. Bounds of active persisted features belonging to the current Plan
        val bounds = calculateFeatureBounds(features)
        if (bounds != null) return CameraResolution(bounds, CameraSource.FEATURE_BOUNDS)

        // 4. Safe documented fallback (central US point)
        return CameraResolution(
            MapCameraFocus.Point(39.8283, -98.5795, 4f, 0.0),
            CameraSource.SAFE_FALLBACK
        )
    }

    fun calculateFeatureBounds(features: List<MapFeatureEntity>): MapCameraFocus.Bounds? {
        if (features.isEmpty()) return null
        
        val allVertices = features.flatMap { feature ->
            val result = GeometryUtils.parseFeatureGeometry(feature.geometryJson, feature.geometryType)
            result.getOrNull() ?: emptyList()
        }.filter { (lng, lat) -> 
            GeometryUtils.validateCoordinate(lng, lat)
        }
        
        if (allVertices.isEmpty()) return null
        
        val minLat = allVertices.minOf { it.second }
        val maxLat = allVertices.maxOf { it.second }
        val minLng = allVertices.minOf { it.first }
        val maxLng = allVertices.maxOf { it.first }
        
        return MapCameraFocus.Bounds(
            sw = Pair(minLng, minLat),
            ne = Pair(maxLng, maxLat)
        )
    }
}
