package com.jumastappworks.mapstead.ui.mapping

import android.graphics.PointF
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.geometry.LatLng
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

sealed interface ShapeEditHitTarget {
    data class Vertex(val index: Int) : ShapeEditHitTarget
    data class Midpoint(val insertionIndex: Int, val longitude: Double, val latitude: Double) : ShapeEditHitTarget
}

object ShapeEditHitResolver {
    fun resolveNearest(
        touchPoint: PointF,
        vertices: List<Feature>,
        midpoints: List<Feature>,
        map: MapLibreMap
    ): ShapeEditHitTarget? {
        val validVertices = vertices.filter { 
            it.getNumberProperty("index") != null 
        }

        if (validVertices.isNotEmpty()) {
            val nearestVertex = validVertices.minByOrNull { feat ->
                calculateDistance(touchPoint, feat, map)
            }
            nearestVertex?.let {
                val index = it.getNumberProperty("index").toInt()
                return ShapeEditHitTarget.Vertex(index)
            }
        }

        val validMidpoints = midpoints.filter { 
            val index = it.getNumberProperty("index")?.toInt()
            val lng = it.getNumberProperty("lng")?.toDouble()
            val lat = it.getNumberProperty("lat")?.toDouble()
            index != null && isValidCoordinate(lng, lat)
        }

        if (validMidpoints.isNotEmpty()) {
            val nearestMidpoint = validMidpoints.minByOrNull { feat ->
                calculateDistance(touchPoint, feat, map)
            }
            nearestMidpoint?.let {
                val index = it.getNumberProperty("index").toInt()
                val lng = it.getNumberProperty("lng").toDouble()
                val lat = it.getNumberProperty("lat").toDouble()
                return ShapeEditHitTarget.Midpoint(index, lng, lat)
            }
        }

        return null
    }

    private fun isValidCoordinate(lng: Double?, lat: Double?): Boolean {
        if (lng == null || lat == null) return false
        if (!lng.isFinite() || !lat.isFinite()) return false
        return lng >= -180.0 && lng <= 180.0 && lat >= -90.0 && lat <= 90.0
    }

    private fun calculateDistance(touchPoint: PointF, feature: Feature, map: MapLibreMap): Double {
        val geom = feature.geometry() as? Point ?: return Double.MAX_VALUE
        val screenPoint = map.projection.toScreenLocation(LatLng(geom.latitude(), geom.longitude()))
        return Math.hypot((screenPoint.x - touchPoint.x).toDouble(), (screenPoint.y - touchPoint.y).toDouble())
    }
}
