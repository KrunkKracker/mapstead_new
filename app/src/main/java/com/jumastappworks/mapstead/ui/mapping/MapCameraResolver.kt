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

    /**
     * Priority:
     * 1. Valid restoration for the current plan
     * 2. Valid saved Plan center
     * 3. Property coordinates
     * 4. Feature bounds
     * 5. Safe fallback
     */
    fun resolveInitialCamera(
        plan: PlanEntity?,
        property: PropertyEntity?,
        features: List<MapFeatureEntity>,
        restoration: CameraRestorationRequest? = null
    ): CameraResolution {
        // 1. Valid restoration for this plan
        if (restoration != null && plan != null && restoration.planId == plan.id) {
            if (CameraValidation.isValid(restoration.latitude, restoration.longitude, restoration.zoom, restoration.bearing)) {
                if (!CameraValidation.isDefaultWorldView(restoration.latitude, restoration.longitude, restoration.zoom)) {
                    return CameraResolution(
                        MapCameraFocus.Point(
                            latitude = restoration.latitude,
                            longitude = restoration.longitude,
                            zoom = restoration.zoom.toFloat(),
                            bearing = restoration.bearing
                        ),
                        CameraSource.SAVED_PLAN_CAMERA
                    )
                }
            }
        }

        // 2. Valid saved Plan center
        if (plan != null) {
            val lat = plan.centerLatitude
            val lng = plan.centerLongitude
            val zoom = plan.zoom ?: CameraValidation.DEFAULT_ZOOM
            val bearing = CameraValidation.normalizeBearing(plan.bearing ?: 0.0)
            
            if (lat != null && lng != null && CameraValidation.isValid(lat, lng, zoom, bearing)) {
                if (!CameraValidation.isDefaultWorldView(lat, lng, zoom)) {
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
        }

        // 3. Fallback to recenter logic (Property -> Features -> Fallback)
        val recenter = resolveRecenterCamera(property, features, null)
        
        // If we found a useful fallback but the original was a default world view, mark it as repaired
        if (plan != null && recenter.source != CameraSource.SAFE_FALLBACK) {
             val lat = plan.centerLatitude
             val lng = plan.centerLongitude
             val zoom = plan.zoom ?: 0.0
             if (lat != null && lng != null && CameraValidation.isDefaultWorldView(lat, lng, zoom)) {
                 return CameraResolution(recenter.focus, CameraSource.REPAIRED_DEFAULT_CAMERA)
             }
        }

        return recenter
    }

    fun resolveRecenterCamera(
        property: PropertyEntity?,
        features: List<MapFeatureEntity>,
        plan: PlanEntity?
    ): CameraResolution {
        // 1. Current property coordinates when valid
        if (property != null) {
            val lat = property.latitude
            val lng = property.longitude
            if (lat != null && lng != null && CameraValidation.isValid(lat, lng, CameraValidation.DEFAULT_ZOOM, 0.0) && !CameraValidation.isDefaultWorldView(lat, lng, CameraValidation.DEFAULT_ZOOM)) {
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

        // 2. Current plan feature bounds
        val bounds = calculateFeatureBounds(features)
        if (bounds != null) return CameraResolution(bounds, CameraSource.FEATURE_BOUNDS)

        // 3. Valid saved plan camera
        if (plan != null) {
            val lat = plan.centerLatitude
            val lng = plan.centerLongitude
            val zoom = plan.zoom ?: CameraValidation.DEFAULT_ZOOM
            val bearing = CameraValidation.normalizeBearing(plan.bearing ?: 0.0)
            if (lat != null && lng != null && CameraValidation.isValid(lat, lng, zoom, bearing) && !CameraValidation.isDefaultWorldView(lat, lng, zoom)) {
                return CameraResolution(
                    MapCameraFocus.Point(lat, lng, zoom.toFloat(), bearing),
                    CameraSource.SAVED_PLAN_CAMERA
                )
            }
        }

        // 4. Safe documented fallback (central US point)
        return CameraResolution(
            MapCameraFocus.Point(39.8283, -98.5795, 4f, 0.0),
            CameraSource.SAFE_FALLBACK
        )
    }

    fun calculateFeatureBounds(features: List<MapFeatureEntity>): MapCameraFocus.Bounds? {
        val activeFeatures = features.filter { it.deletedAt == null }
        if (activeFeatures.isEmpty()) return null
        
        val allVertices = activeFeatures.flatMap { feature ->
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
