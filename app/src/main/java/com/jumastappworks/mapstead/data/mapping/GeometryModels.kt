package com.jumastappworks.mapstead.data.mapping

import kotlinx.serialization.Serializable

@Serializable
sealed class GeometryData {
    @Serializable
    data class Point(val latitude: Double, val longitude: Double) : GeometryData()

    @Serializable
    data class Polyline(val points: List<Point>) : GeometryData()

    @Serializable
    data class Polygon(val rings: List<List<Point>>) : GeometryData()

    @Serializable
    data class Rectangle(val southWest: Point, val northEast: Point) : GeometryData()

    @Serializable
    data class Circle(val center: Point, val radiusInMeters: Double) : GeometryData()
}

@Serializable
data class MapFeatureProperties(
    val strokeColor: String? = null,
    val fillColor: String? = null,
    val strokeWidth: Float? = null,
    val labelText: String? = null,
    val labelSize: Float? = null,
    val accuracySource: String? = null, // Approximate, Phone GPS, Measured, etc.
    val notes: String? = null,
    val category: String? = null
)
