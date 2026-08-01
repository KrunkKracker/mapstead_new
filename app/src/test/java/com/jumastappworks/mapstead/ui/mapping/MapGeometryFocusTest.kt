package com.jumastappworks.mapstead.ui.mapping

import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import org.junit.Assert.*
import org.junit.Test
import java.util.*

class MapGeometryFocusTest {

    @Test
    fun `resolveFocus returns Point focus for valid point`() {
        val feature = createFeature("POINT", "{\"type\":\"Point\",\"coordinates\":[-122.0, 37.0]}")
        val focus = MapGeometryFocusResolver.resolveFocus(feature)
        assertTrue(focus is MapCameraFocus.Point)
        val pointFocus = focus as MapCameraFocus.Point
        assertEquals(37.0, pointFocus.latitude, 0.0001)
        assertEquals(-122.0, pointFocus.longitude, 0.0001)
    }

    @Test
    fun `resolveFocus returns null for invalid point coordinates`() {
        val feature = createFeature("POINT", "{\"type\":\"Point\",\"coordinates\":[-200.0, 37.0]}")
        assertNull(MapGeometryFocusResolver.resolveFocus(feature))
    }

    @Test
    fun `resolveFocus returns null for NaN coordinates`() {
        // JSON NaN is often represented as null or literal in some parsers, 
        // but here we check the resolver's robustness.
        val feature = createFeature("POINT", "{\"type\":\"Point\",\"coordinates\":[null, 37.0]}")
        assertNull(MapGeometryFocusResolver.resolveFocus(feature))
    }

    @Test
    fun `resolveFocus returns Bounds for valid linestring`() {
        val feature = createFeature("LINESTRING", "{\"type\":\"LineString\",\"coordinates\":[[-122.0, 37.0], [-121.0, 38.0]]}")
        val focus = MapGeometryFocusResolver.resolveFocus(feature)
        assertTrue(focus is MapCameraFocus.Bounds)
        val boundsFocus = focus as MapCameraFocus.Bounds
        assertEquals(-122.0, boundsFocus.sw.first, 0.0001)
        assertEquals(37.0, boundsFocus.sw.second, 0.0001)
        assertEquals(-121.0, boundsFocus.ne.first, 0.0001)
        assertEquals(38.0, boundsFocus.ne.second, 0.0001)
    }

    @Test
    fun `resolveFocus returns null for one-point linestring`() {
        val feature = createFeature("LINESTRING", "{\"type\":\"LineString\",\"coordinates\":[[-122.0, 37.0]]}")
        assertNull(MapGeometryFocusResolver.resolveFocus(feature))
    }

    @Test
    fun `resolveFocus returns null for degenerate linestring`() {
        val feature = createFeature("LINESTRING", "{\"type\":\"LineString\",\"coordinates\":[[-122.0, 37.0], [-122.0, 37.0]]}")
        assertNull(MapGeometryFocusResolver.resolveFocus(feature))
    }

    @Test
    fun `resolveFocus returns null for malformed linestring coordinates`() {
        val feature = createFeature("LINESTRING", "{\"type\":\"LineString\",\"coordinates\":[[-122,37], [NaN, 38]]}")
        assertNull(MapGeometryFocusResolver.resolveFocus(feature))
    }

    @Test
    fun `resolveFocus returns Bounds for valid polygon`() {
        val feature = createFeature("POLYGON", "{\"type\":\"Polygon\",\"coordinates\":[[[-122.0, 37.0], [-121.0, 37.0], [-121.0, 38.0], [-122.0, 37.0]]]}")
        val focus = MapGeometryFocusResolver.resolveFocus(feature)
        assertTrue(focus is MapCameraFocus.Bounds)
    }

    @Test
    fun `resolveFocus returns null for polygon with holes`() {
        val feature = createFeature("POLYGON", "{\"type\":\"Polygon\",\"coordinates\":[[[-122,37],[-120,37],[-120,39],[-122,37]], [[-121,37.5],[-120.5,37.5],[-120.5,38],[-121,37.5]]]}")
        assertNull(MapGeometryFocusResolver.resolveFocus(feature))
    }

    @Test
    fun `resolveFocus returns null for unclosed polygon`() {
        val feature = createFeature("POLYGON", "{\"type\":\"Polygon\",\"coordinates\":[[[-122,37],[-121,37],[-121,38]]]}")
        assertNull(MapGeometryFocusResolver.resolveFocus(feature))
    }

    @Test
    fun `resolveFocus returns null for too-few-vertices polygon`() {
        val feature = createFeature("POLYGON", "{\"type\":\"Polygon\",\"coordinates\":[[[-122,37],[-122,37],[-122,37]] ]}")
        assertNull(MapGeometryFocusResolver.resolveFocus(feature))
    }

    @Test
    fun `resolveFocus returns null for mismatched GeoJSON type`() {
        val feature = createFeature("POINT", "{\"type\":\"LineString\",\"coordinates\":[[-122.0, 37.0], [-121.0, 38.0]]}")
        assertNull(MapGeometryFocusResolver.resolveFocus(feature))
    }

    @Test
    fun `resolveFocus returns null for malformed JSON`() {
        val feature = createFeature("POINT", "{ malformed }")
        assertNull(MapGeometryFocusResolver.resolveFocus(feature))
    }

    @Test
    fun `resolveFocus returns null for unsupported geometry`() {
        val feature = createFeature("MULTIPOINT", "{\"type\":\"MultiPoint\",\"coordinates\":[[-122,37]]}")
        assertNull(MapGeometryFocusResolver.resolveFocus(feature))
    }

    @Test
    fun `isValidCoordinate correctly identifies valid bounds`() {
        // This indirectly tests the private isValidCoordinate through public resolveFocus
        val validPoint = createFeature("POINT", "{\"type\":\"Point\",\"coordinates\":[-180.0, -90.0]}")
        assertNotNull(MapGeometryFocusResolver.resolveFocus(validPoint))
        
        val invalidPoint = createFeature("POINT", "{\"type\":\"Point\",\"coordinates\":[180.1, 90.1]}")
        assertNull(MapGeometryFocusResolver.resolveFocus(invalidPoint))
    }

    private fun createFeature(type: String, json: String) = MapFeatureEntity(
        id = UUID.randomUUID(), propertyId = UUID.randomUUID(), planId = UUID.randomUUID(), layerId = UUID.randomUUID(),
        geometryType = type, geometryJson = json, coordinateSpace = "GEOGRAPHIC", styleJson = "{}", accuracySource = "MANUAL"
    )
}
