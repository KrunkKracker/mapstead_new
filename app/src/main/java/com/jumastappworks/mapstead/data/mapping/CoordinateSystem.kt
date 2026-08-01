package com.jumastappworks.mapstead.data.mapping

import kotlinx.serialization.Serializable

/**
 * Normalizes coordinates for Non-geospatial plans (Image, PDF, Canvas).
 * (0.0, 0.0) is Top-Left, (1.0, 1.0) is Bottom-Right.
 */
@Serializable
data class NormalizedPoint(val x: Float, val y: Float)

@Serializable
sealed class PlanGeometry {
    @Serializable
    data class Point(val position: NormalizedPoint) : PlanGeometry()

    @Serializable
    data class Polyline(val points: List<NormalizedPoint>) : PlanGeometry()

    @Serializable
    data class Polygon(val rings: List<List<NormalizedPoint>>) : PlanGeometry()
}
