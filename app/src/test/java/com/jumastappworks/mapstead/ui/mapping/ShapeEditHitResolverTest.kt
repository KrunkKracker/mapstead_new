package com.jumastappworks.mapstead.ui.mapping

import android.graphics.PointF
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Projection
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

class ShapeEditHitResolverTest {

    private val map = mockk<MapLibreMap>()
    private val projection = mockk<Projection>()

    @Test
    fun testVertexPriorityOverMidpoint() {
        every { map.projection } returns projection
        
        val vertexFeature = mockk<Feature>()
        every { vertexFeature.geometry() } returns Point.fromLngLat(1.0, 1.0)
        every { vertexFeature.getNumberProperty("index") } returns 2
        
        val midpointFeature = mockk<Feature>()
        every { midpointFeature.geometry() } returns Point.fromLngLat(1.0, 1.0)
        every { midpointFeature.getNumberProperty("index") } returns 3
        every { midpointFeature.getNumberProperty("lng") } returns 1.0
        every { midpointFeature.getNumberProperty("lat") } returns 1.0
        
        every { projection.toScreenLocation(any()) } returns PointF(100f, 100f)
        
        val target = ShapeEditHitResolver.resolveNearest(
            PointF(100f, 100f),
            listOf(vertexFeature),
            listOf(midpointFeature),
            map
        )
        
        assertTrue(target is ShapeEditHitTarget.Vertex)
        assertEquals(2, (target as ShapeEditHitTarget.Vertex).index)
    }

    @Test
    fun testNearestVertexSelected() {
        every { map.projection } returns projection
        
        val v1 = mockk<Feature>()
        every { v1.geometry() } returns Point.fromLngLat(1.0, 1.0)
        every { v1.getNumberProperty("index") } returns 1
        
        val v2 = mockk<Feature>()
        every { v2.geometry() } returns Point.fromLngLat(2.0, 2.0)
        every { v2.getNumberProperty("index") } returns 2
        
        every { projection.toScreenLocation(LatLng(1.0, 1.0)) } returns PointF(10f, 10f)
        every { projection.toScreenLocation(LatLng(2.0, 2.0)) } returns PointF(100f, 100f)
        
        val target = ShapeEditHitResolver.resolveNearest(
            PointF(15f, 15f),
            listOf(v1, v2),
            emptyList(),
            map
        )
        
        assertEquals(ShapeEditHitTarget.Vertex(1), target)
    }

    @Test
    fun testMidpointSelectedIfNoVertexHit() {
        every { map.projection } returns projection
        
        val m1 = mockk<Feature>()
        every { m1.geometry() } returns Point.fromLngLat(1.5, 1.5)
        every { m1.getNumberProperty("index") } returns 1
        every { m1.getNumberProperty("lng") } returns 1.5
        every { m1.getNumberProperty("lat") } returns 1.5
        
        every { projection.toScreenLocation(LatLng(1.5, 1.5)) } returns PointF(50f, 50f)
        
        val target = ShapeEditHitResolver.resolveNearest(
            PointF(50f, 50f),
            emptyList(),
            listOf(m1),
            map
        )
        
        assertEquals(ShapeEditHitTarget.Midpoint(1, 1.5, 1.5), target)
    }

    @Test
    fun testRejectsInvalidProperties() {
        val m1 = mockk<Feature>()
        every { m1.geometry() } returns Point.fromLngLat(1.5, 1.5)
        every { m1.getNumberProperty(any()) } returns null
        
        val target = ShapeEditHitResolver.resolveNearest(
            PointF(50f, 50f),
            emptyList(),
            listOf(m1),
            map
        )
        
        assertNull(target)
    }

    @Test
    fun testRejectsNaNCoordinates() {
        val m1 = mockk<Feature>()
        every { m1.geometry() } returns Point.fromLngLat(Double.NaN, 1.5)
        every { m1.getNumberProperty("index") } returns 1
        every { m1.getNumberProperty("lng") } returns Double.NaN
        every { m1.getNumberProperty("lat") } returns 1.5
        
        val target = ShapeEditHitResolver.resolveNearest(
            PointF(50f, 50f),
            emptyList(),
            listOf(m1),
            map
        )
        assertNull(target)
    }

    @Test
    fun testRejectsOutOfRangeCoordinates() {
        val m1 = mockk<Feature>()
        every { m1.geometry() } returns Point.fromLngLat(181.0, 1.5)
        every { m1.getNumberProperty("index") } returns 1
        every { m1.getNumberProperty("lng") } returns 181.0
        every { m1.getNumberProperty("lat") } returns 1.5
        
        val target = ShapeEditHitResolver.resolveNearest(
            PointF(50f, 50f),
            emptyList(),
            listOf(m1),
            map
        )
        assertNull(target)
    }

    @Test
    fun testMalformedNearestVertexDoesNotSuppressValidOne() {
        every { map.projection } returns projection
        
        val malformedV = mockk<Feature>()
        every { malformedV.geometry() } returns Point.fromLngLat(1.0, 1.0)
        every { malformedV.getNumberProperty("index") } returns null
        
        val validV = mockk<Feature>()
        every { validV.geometry() } returns Point.fromLngLat(2.0, 2.0)
        every { validV.getNumberProperty("index") } returns 2
        
        every { projection.toScreenLocation(LatLng(1.0, 1.0)) } returns PointF(10f, 10f)
        every { projection.toScreenLocation(LatLng(2.0, 2.0)) } returns PointF(100f, 100f)
        
        val target = ShapeEditHitResolver.resolveNearest(
            PointF(15f, 15f),
            listOf(malformedV, validV),
            emptyList(),
            map
        )
        
        assertEquals(ShapeEditHitTarget.Vertex(2), target)
    }
}
