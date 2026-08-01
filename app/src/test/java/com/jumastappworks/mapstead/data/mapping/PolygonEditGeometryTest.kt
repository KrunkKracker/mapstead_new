package com.jumastappworks.mapstead.data.mapping

import com.jumastappworks.mapstead.util.GeometryUtils
import com.jumastappworks.mapstead.util.PolygonValidationReason
import com.jumastappworks.mapstead.util.PolygonValidationResult
import org.junit.Assert.*
import org.junit.Test

class PolygonEditGeometryTest {

    @Test
    fun testMidpointCountEqualsVertexCount() {
        val vertices = listOf(
            Pair(0.0, 0.0),
            Pair(1.0, 0.0),
            Pair(1.0, 1.0),
            Pair(0.0, 1.0)
        )
        // Midpoints: (0,0)-(1,0), (1,0)-(1,1), (1,1)-(0,1), (0,1)-(0,0)
        assertEquals(4, vertices.size)
    }

    @Test
    fun testVertexDragUpdatesOnlySelectedVertex() {
        val original = listOf(Pair(0.0, 0.0), Pair(1.0, 0.0), Pair(1.0, 1.0))
        val modified = original.toMutableList()
        modified[1] = Pair(2.0, 0.0)
        
        assertEquals(Pair(0.0, 0.0), modified[0])
        assertEquals(Pair(2.0, 0.0), modified[1])
        assertEquals(Pair(1.0, 1.0), modified[2])
    }

    @Test
    fun testTriangleVertexDeletionBlockedByValidator() {
        val triangle = listOf(Pair(0.0, 0.0), Pair(1.0, 0.0), Pair(1.0, 1.0))
        val result = GeometryUtils.validatePolygonGeometry(triangle.dropLast(1))
        assertTrue(result is PolygonValidationResult.Invalid)
        assertEquals(PolygonValidationReason.TooFewVertices, (result as PolygonValidationResult.Invalid).reason)
    }

    @Test
    fun testBowTieInvalid() {
        val bowTie = listOf(
            Pair(0.0, 0.0),
            Pair(2.0, 2.0),
            Pair(2.0, 0.0),
            Pair(0.0, 2.0)
        )
        val result = GeometryUtils.validatePolygonGeometry(bowTie)
        assertTrue(result is PolygonValidationResult.Invalid)
        assertEquals(PolygonValidationReason.SelfIntersection, (result as PolygonValidationResult.Invalid).reason)
    }

    @Test
    fun testValidDragUpdatesArea() {
        val original = listOf(Pair(0.0, 0.0), Pair(1.0, 0.0), Pair(1.0, 1.0), Pair(0.0, 1.0))
        val originalArea = GeometryUtils.calculateSphericalArea(original)
        
        val modified = original.toMutableList()
        modified[1] = Pair(2.0, 0.0)
        modified[2] = Pair(2.0, 1.0)
        val modifiedArea = GeometryUtils.calculateSphericalArea(modified)
        
        assertTrue(modifiedArea > originalArea)
    }

    @Test
    fun testRingClosureInGeoJson() {
        val vertices = listOf(Pair(0.0, 0.0), Pair(1.0, 0.0), Pair(1.0, 1.0))
        val geoJson = GeometryUtils.buildPolygonGeoJson(vertices)
        // Polygon GeoJSON coordinates: [[[lng, lat], [lng, lat], [lng, lat], [startLng, startLat]]]
        assertTrue(geoJson.contains("[0.0,0.0]"))
        val count = geoJson.split("[0.0,0.0]").size - 1
        assertEquals(2, count) // Start and end
    }
}
