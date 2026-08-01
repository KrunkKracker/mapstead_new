package com.jumastappworks.mapstead.ui.mapping

import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.util.GeometryUtils
import com.jumastappworks.mapstead.util.PolygonParseResult
import com.jumastappworks.mapstead.util.PolygonValidationResult
import kotlinx.serialization.json.*

object MapGeometryFocusResolver {

    fun resolveFocus(feature: MapFeatureEntity): MapCameraFocus? {
        return try {
            val element = Json.parseToJsonElement(feature.geometryJson).jsonObject
            val type = element["type"]?.jsonPrimitive?.content
            if (type == null || !type.equals(feature.geometryType, ignoreCase = true)) {
                return null
            }

            when (feature.geometryType.uppercase()) {
                "POINT" -> resolvePointFocus(element)
                "LINESTRING" -> resolveLineBoundsFocus(element)
                "POLYGON" -> resolvePolygonBoundsFocus(feature.geometryJson, element)
                else -> null
            }
        } catch (e: Exception) { null }
    }

    private fun resolvePointFocus(element: JsonObject): MapCameraFocus? {
        val coords = element["coordinates"]?.jsonArray ?: return null
        if (coords.size < 2) return null
        val lng = coords[0].jsonPrimitive.doubleOrNull ?: return null
        val lat = coords[1].jsonPrimitive.doubleOrNull ?: return null
        
        return if (isValidCoordinate(lng, lat)) {
            MapCameraFocus.Point(lat, lng)
        } else null
    }

    private fun resolveLineBoundsFocus(element: JsonObject): MapCameraFocus? {
        val coords = element["coordinates"]?.jsonArray ?: return null
        if (coords.size < 2) return null
        
        val vertices = mutableListOf<Pair<Double, Double>>()
        coords.forEach { coord ->
            val arr = coord.jsonArray
            val lng = arr[0].jsonPrimitive.doubleOrNull ?: return null
            val lat = arr[1].jsonPrimitive.doubleOrNull ?: return null
            if (!isValidCoordinate(lng, lat)) return null
            vertices.add(Pair(lng, lat))
        }

        if (vertices.distinct().size < 2) {
            return null
        }

        return calculateBoundsFocus(vertices)
    }

    private fun resolvePolygonBoundsFocus(json: String, element: JsonObject): MapCameraFocus? {
        // Confirm coordinates structure for Polygon (array of rings)
        val rings = element["coordinates"]?.jsonArray ?: return null
        if (rings.isEmpty()) return null
        
        // Interior ring check - Mapstead currently supports exterior ring only for focus
        if (rings.size > 1) return null

        val parse = GeometryUtils.parsePolygonGeoJson(json)
        if (parse !is PolygonParseResult.Success) return null
        
        val vertices = parse.vertices
        // Strict coordinate check for all vertices
        vertices.forEach { (lng, lat) ->
            if (!isValidCoordinate(lng, lat)) return null
        }

        // Validate via GeometryUtils (closed, size, no self-intersection if possible)
        val validation = GeometryUtils.validatePolygonGeometry(vertices)
        if (validation !is PolygonValidationResult.Valid) return null

        return calculateBoundsFocus(vertices)
    }

    private fun calculateBoundsFocus(vertices: List<Pair<Double, Double>>): MapCameraFocus? {
        if (vertices.isEmpty()) return null
        
        var minLng = Double.MAX_VALUE; var maxLng = -Double.MAX_VALUE
        var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
        
        vertices.forEach { (lng, lat) ->
            if (lng < minLng) minLng = lng
            if (lng > maxLng) maxLng = lng
            if (lat < minLat) minLat = lat
            if (lat > maxLat) maxLat = lat
        }
        
        if (minLng == maxLng && minLat == maxLat) {
            return MapCameraFocus.Point(minLat, minLng)
        }

        return MapCameraFocus.Bounds(
            sw = Pair(minLng, minLat),
            ne = Pair(maxLng, maxLat)
        )
    }

    private fun isValidCoordinate(lng: Double, lat: Double): Boolean =
        lng.isFinite() && lat.isFinite() && lng >= -180.0 && lng <= 180.0 && lat >= -90.0 && lat <= 90.0
}
