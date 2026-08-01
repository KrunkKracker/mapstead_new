package com.jumastappworks.mapstead.util

import kotlinx.serialization.json.*
import kotlin.math.*

enum class PolygonValidationReason {
    TooFewVertices,
    InvalidCoordinate,
    ConsecutiveDuplicate,
    DuplicateVertices,
    ZeroArea,
    SelfIntersection,
    UnsupportedInteriorRing,
    InvalidRingClosure
}

sealed interface PolygonValidationResult {
    data object Valid : PolygonValidationResult
    data class Invalid(val reason: PolygonValidationReason) : PolygonValidationResult
}

sealed interface PolygonParseResult {
    data class Success(val vertices: List<Pair<Double, Double>>) : PolygonParseResult
    data class Error(val message: String) : PolygonParseResult
}

data class PolygonMidpoint(
    val insertionIndex: Int,
    val coordinate: Pair<Double, Double>
)

object GeometryUtils {
    private const val EARTH_RADIUS_METERS = 6371000.0

    fun calculateHaversineDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    fun calculatePathLength(coordinates: List<Pair<Double, Double>>): Double {
        var total = 0.0
        for (i in 0 until coordinates.size - 1) {
            val p1 = coordinates[i]
            val p2 = coordinates[i + 1]
            total += calculateHaversineDistance(p1.second, p1.first, p2.second, p2.first)
        }
        return total
    }

    fun formatDistance(meters: Double, system: com.jumastappworks.mapstead.data.prefs.MeasurementSystem = com.jumastappworks.mapstead.data.prefs.MeasurementSystem.IMPERIAL): String {
        return MeasurementFormatter.formatDistance(meters, system)
    }

    fun validateCoordinate(lng: Double, lat: Double): Boolean {
        return lng.isFinite() && lat.isFinite() && lng >= -180.0 && lng <= 180.0 && lat >= -90.0 && lat <= 90.0
    }

    fun validateLineGeometry(vertices: List<Pair<Double, Double>>): Boolean {
        if (vertices.size < 2) return false
        if (vertices.any { !validateCoordinate(it.first, it.second) }) return false
        for (i in 0 until vertices.size - 1) {
            if (areCoordinatesEqual(vertices[i], vertices[i + 1])) return false
        }
        
        var hasDistinct = false
        for (i in 0 until vertices.size) {
            for (j in i + 1 until vertices.size) {
                if (!areCoordinatesEqual(vertices[i], vertices[j])) {
                    hasDistinct = true
                    break
                }
            }
            if (hasDistinct) break
        }
        return hasDistinct
    }

    fun calculateSphericalArea(vertices: List<Pair<Double, Double>>): Double {
        if (vertices.size < 3) return 0.0
        var total = 0.0
        val r = EARTH_RADIUS_METERS
        
        for (i in vertices.indices) {
            val p1 = vertices[i]
            val p2 = vertices[(i + 1) % vertices.size]
            
            val lambda1 = Math.toRadians(p1.first)
            val lambda2 = Math.toRadians(p2.first)
            val phi1 = Math.toRadians(p1.second)
            val phi2 = Math.toRadians(p2.second)
            
            total += (lambda2 - lambda1) * (2 + sin(phi1) + sin(phi2))
        }
        
        return kotlin.math.abs(total * r * r / 2.0)
    }

    fun calculatePolygonPerimeter(vertices: List<Pair<Double, Double>>): Double {
        if (vertices.size < 2) return 0.0
        var total = 0.0
        for (i in vertices.indices) {
            val p1 = vertices[i]
            val p2 = vertices[(i + 1) % vertices.size]
            total += calculateHaversineDistance(p1.second, p1.first, p2.second, p2.first)
        }
        return total
    }

    fun formatArea(squareMeters: Double, system: com.jumastappworks.mapstead.data.prefs.MeasurementSystem = com.jumastappworks.mapstead.data.prefs.MeasurementSystem.IMPERIAL): String {
        return MeasurementFormatter.formatArea(squareMeters, system)
    }

    fun checkSelfIntersection(vertices: List<Pair<Double, Double>>): Boolean {
        if (vertices.size < 4) return false
        val n = vertices.size
        
        for (i in 0 until n) {
            val p1 = vertices[i]
            val p2 = vertices[(i + 1) % n]
            
            for (j in i + 1 until n) {
                val p3 = vertices[j]
                val p4 = vertices[(j + 1) % n]
                
                // Skip adjacent segments
                if (i == j || i == (j + 1) % n || j == (i + 1) % n) continue
                
                if (doSegmentsIntersect(p1, p2, p3, p4)) return true
            }
        }
        return false
    }

    private fun doSegmentsIntersect(p1: Pair<Double, Double>, p2: Pair<Double, Double>, p3: Pair<Double, Double>, p4: Pair<Double, Double>): Boolean {
        fun crossProduct(a: Pair<Double, Double>, b: Pair<Double, Double>, c: Pair<Double, Double>): Double {
            val v = (b.first - a.first) * (c.second - a.second) - (b.second - a.second) * (c.first - a.first)
            return if (abs(v) < 1e-12) 0.0 else v
        }

        fun onSegment(p: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>): Boolean {
            return p.first >= min(a.first, b.first) - 1e-12 && p.first <= max(a.first, b.first) + 1e-12 &&
                   p.second >= min(a.second, b.second) - 1e-12 && p.second <= max(a.second, b.second) + 1e-12
        }

        val d1 = crossProduct(p3, p4, p1)
        val d2 = crossProduct(p3, p4, p2)
        val d3 = crossProduct(p1, p2, p3)
        val d4 = crossProduct(p1, p2, p4)

        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
            ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) return true

        if (d1 == 0.0 && onSegment(p1, p3, p4)) return true
        if (d2 == 0.0 && onSegment(p2, p3, p4)) return true
        if (d3 == 0.0 && onSegment(p3, p1, p2)) return true
        if (d4 == 0.0 && onSegment(p4, p1, p2)) return true

        return false
    }

    fun areCoordinatesEqual(p1: Pair<Double, Double>, p2: Pair<Double, Double>, epsilon: Double = 1e-9): Boolean {
        return abs(p1.first - p2.first) < epsilon && abs(p1.second - p2.second) < epsilon
    }

    fun polygonMidpoints(vertices: List<Pair<Double, Double>>): List<PolygonMidpoint> {
        if (vertices.size < 3) return emptyList()
        val midpoints = mutableListOf<PolygonMidpoint>()
        for (i in vertices.indices) {
            val p1 = vertices[i]
            val p2 = vertices[(i + 1) % vertices.size]
            val midLng = (p1.first + p2.first) / 2.0
            val midLat = (p1.second + p2.second) / 2.0
            midpoints.add(PolygonMidpoint(i + 1, Pair(midLng, midLat)))
        }
        return midpoints
    }

    fun validatePolygonGeometry(vertices: List<Pair<Double, Double>>): PolygonValidationResult {
        if (vertices.size < 3) return PolygonValidationResult.Invalid(PolygonValidationReason.TooFewVertices)
        
        if (vertices.any { !validateCoordinate(it.first, it.second) }) {
            return PolygonValidationResult.Invalid(PolygonValidationReason.InvalidCoordinate)
        }

        for (i in 0 until vertices.size - 1) {
            if (areCoordinatesEqual(vertices[i], vertices[i + 1])) {
                return PolygonValidationResult.Invalid(PolygonValidationReason.ConsecutiveDuplicate)
            }
        }
        
        // Reject any repeated vertex to ensure simple polygon
        for (i in 0 until vertices.size) {
            for (j in i + 1 until vertices.size) {
                if (areCoordinatesEqual(vertices[i], vertices[j])) {
                    return PolygonValidationResult.Invalid(PolygonValidationReason.DuplicateVertices)
                }
            }
        }

        if (checkSelfIntersection(vertices)) {
            return PolygonValidationResult.Invalid(PolygonValidationReason.SelfIntersection)
        }

        val area = calculateSphericalArea(vertices)
        val areaEpsilon = 1e-7 
        if (area <= areaEpsilon) {
            return PolygonValidationResult.Invalid(PolygonValidationReason.ZeroArea)
        }

        return PolygonValidationResult.Valid
    }

    fun buildPointGeoJson(longitude: Double, latitude: Double): String {
        return buildJsonObject {
            put("type", "Point")
            put("coordinates", buildJsonArray {
                add(longitude)
                add(latitude)
            })
        }.toString()
    }

    fun buildLineStringGeoJson(vertices: List<Pair<Double, Double>>): String {
        return buildJsonObject {
            put("type", "LineString")
            put("coordinates", buildJsonArray {
                vertices.forEach { (lng, lat) ->
                    addJsonArray {
                        add(lng)
                        add(lat)
                    }
                }
            })
        }.toString()
    }

    fun buildPolygonGeoJson(vertices: List<Pair<Double, Double>>): String {
        return buildJsonObject {
            put("type", "Polygon")
            put("coordinates", buildJsonArray {
                addJsonArray {
                    vertices.forEach { (lng, lat) ->
                        addJsonArray {
                            add(lng)
                            add(lat)
                        }
                    }
                    if (vertices.isNotEmpty()) {
                        addJsonArray {
                            add(vertices[0].first)
                            add(vertices[0].second)
                        }
                    }
                }
            })
        }.toString()
    }

    fun parsePointGeometry(json: String): Pair<Double, Double> {
        val element = Json.parseToJsonElement(json).jsonObject
        val coords = element["coordinates"]?.jsonArray ?: throw IllegalArgumentException("No coordinates")
        return Pair(coords[0].jsonPrimitive.double, coords[1].jsonPrimitive.double)
    }

    fun parseLineStringGeometry(json: String): List<Pair<Double, Double>> {
        val element = Json.parseToJsonElement(json).jsonObject
        val coords = element["coordinates"]?.jsonArray ?: throw IllegalArgumentException("No coordinates")
        return coords.map { it.jsonArray }.map { Pair(it[0].jsonPrimitive.double, it[1].jsonPrimitive.double) }
    }

    fun parsePolygonGeoJson(json: String): PolygonParseResult {
        return try {
            val element = Json.parseToJsonElement(json).jsonObject
            if (element["type"]?.jsonPrimitive?.content != "Polygon") return PolygonParseResult.Error("Not a Polygon")
            val rings = element["coordinates"]?.jsonArray ?: return PolygonParseResult.Error("No coordinates")
            if (rings.isEmpty()) return PolygonParseResult.Error("No rings found")
            if (rings.size > 1) return PolygonParseResult.Error("Interior rings not supported")
            
            val outerRing = rings[0].jsonArray
            if (outerRing.size < 4) return PolygonParseResult.Error("Outer ring too short")
            
            val start = outerRing[0].jsonArray
            val end = outerRing[outerRing.size - 1].jsonArray
            if (abs(start[0].jsonPrimitive.double - end[0].jsonPrimitive.double) > 1e-10 || 
                abs(start[1].jsonPrimitive.double - end[1].jsonPrimitive.double) > 1e-10) {
                return PolygonParseResult.Error("Ring not closed")
            }

            val vertices = mutableListOf<Pair<Double, Double>>()
            for (i in 0 until outerRing.size - 1) {
                val pt = outerRing[i].jsonArray
                vertices.add(Pair(pt[0].jsonPrimitive.double, pt[1].jsonPrimitive.double))
            }
            PolygonParseResult.Success(vertices)
        } catch (e: Exception) {
            PolygonParseResult.Error("Parse failed: ${e.message}")
        }
    }

    fun parseFeatureGeometry(json: String, type: String): Result<List<Pair<Double, Double>>> {
        return try {
            when (type.uppercase()) {
                "POINT" -> Result.success(listOf(parsePointGeometry(json)))
                "LINESTRING" -> Result.success(parseLineStringGeometry(json))
                "POLYGON" -> {
                    when (val p = parsePolygonGeoJson(json)) {
                        is PolygonParseResult.Success -> Result.success(p.vertices)
                        is PolygonParseResult.Error -> Result.failure(Exception(p.message))
                    }
                }
                else -> Result.failure(Exception("Unknown type: $type"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
