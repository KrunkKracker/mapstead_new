package com.jumastappworks.mapstead.util

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import com.jumastappworks.mapstead.data.prefs.MeasurementSystem

class GeometryTests {

    @Test
    fun testValidateCoordinate() {
        assertTrue(GeometryUtils.validateCoordinate(0.0, 0.0))
        assertTrue(GeometryUtils.validateCoordinate(180.0, 90.0))
        assertTrue(GeometryUtils.validateCoordinate(-180.0, -90.0))
        assertFalse(GeometryUtils.validateCoordinate(180.1, 0.0))
        assertFalse(GeometryUtils.validateCoordinate(0.0, 90.1))
        assertFalse(GeometryUtils.validateCoordinate(Double.NaN, 0.0))
        assertFalse(GeometryUtils.validateCoordinate(0.0, Double.POSITIVE_INFINITY))
    }

    @Test
    fun testValidateLineGeometry() {
        val valid = listOf(Pair(0.0, 0.0), Pair(1.0, 1.0))
        assertTrue(GeometryUtils.validateLineGeometry(valid))

        val tooShort = listOf(Pair(0.0, 0.0))
        assertFalse(GeometryUtils.validateLineGeometry(tooShort))

        val duplicates = listOf(Pair(0.0, 0.0), Pair(0.0, 0.0))
        assertFalse(GeometryUtils.validateLineGeometry(duplicates))

        val consecutiveDuplicates = listOf(Pair(0.0, 0.0), Pair(0.0, 0.0), Pair(1.0, 1.0))
        assertFalse(GeometryUtils.validateLineGeometry(consecutiveDuplicates))
        
        val validWithDuplicatesFarther = listOf(Pair(0.0, 0.0), Pair(1.0, 1.0), Pair(0.0, 0.0))
        assertTrue(GeometryUtils.validateLineGeometry(validWithDuplicatesFarther))

        val outOfBounds = listOf(Pair(0.0, 0.0), Pair(200.0, 0.0))
        assertFalse(GeometryUtils.validateLineGeometry(outOfBounds))
    }

    @Test
    fun testCalculatePathLength() {
        val line = listOf(Pair(0.0, 0.0), Pair(0.0, 1.0))
        val length = GeometryUtils.calculatePathLength(line)
        assertTrue(length > 110000 && length < 112000)
    }

    @Test
    fun testLineStringMidpointLogic() {
        val p1 = Pair(0.0, 0.0)
        val p2 = Pair(10.0, 10.0)
        val midLng = (p1.first + p2.first) / 2.0
        val midLat = (p1.second + p2.second) / 2.0
        assertEquals(5.0, midLng, 0.000001)
        assertEquals(5.0, midLat, 0.000001)
    }

    @Test
    fun testValidTriangle() {
        val vertices = listOf(Pair(0.0, 0.0), Pair(1.0, 0.0), Pair(0.0, 1.0))
        val result = GeometryUtils.validatePolygonGeometry(vertices)
        assertTrue(result is PolygonValidationResult.Valid)
    }

    @Test
    fun testValidSquare() {
        val vertices = listOf(Pair(0.0, 0.0), Pair(1.0, 0.0), Pair(1.0, 1.0), Pair(0.0, 1.0))
        val result = GeometryUtils.validatePolygonGeometry(vertices)
        assertTrue(result is PolygonValidationResult.Valid)
    }

    @Test
    fun testTooFewVertices() {
        val vertices = listOf(Pair(0.0, 0.0), Pair(1.0, 0.0))
        val result = GeometryUtils.validatePolygonGeometry(vertices)
        assertTrue(result is PolygonValidationResult.Invalid && result.reason == PolygonValidationReason.TooFewVertices)
    }

    @Test
    fun testInvalidLongitude() {
        val vertices = listOf(Pair(181.0, 0.0), Pair(1.0, 0.0), Pair(0.0, 1.0))
        val result = GeometryUtils.validatePolygonGeometry(vertices)
        assertTrue(result is PolygonValidationResult.Invalid && result.reason == PolygonValidationReason.InvalidCoordinate)
    }

    @Test
    fun testInvalidLatitude() {
        val vertices = listOf(Pair(0.0, 91.0), Pair(1.0, 0.0), Pair(0.0, 1.0))
        val result = GeometryUtils.validatePolygonGeometry(vertices)
        assertTrue(result is PolygonValidationResult.Invalid && result.reason == PolygonValidationReason.InvalidCoordinate)
    }

    @Test
    fun testNaNCoordinate() {
        val vertices = listOf(Pair(Double.NaN, 0.0), Pair(1.0, 0.0), Pair(0.0, 1.0))
        val result = GeometryUtils.validatePolygonGeometry(vertices)
        assertTrue(result is PolygonValidationResult.Invalid && result.reason == PolygonValidationReason.InvalidCoordinate)
    }

    @Test
    fun testInfinityCoordinate() {
        val vertices = listOf(Pair(Double.POSITIVE_INFINITY, 0.0), Pair(1.0, 0.0), Pair(0.0, 1.0))
        val result = GeometryUtils.validatePolygonGeometry(vertices)
        assertTrue(result is PolygonValidationResult.Invalid && result.reason == PolygonValidationReason.InvalidCoordinate)
    }

    @Test
    fun testConsecutiveDuplicate() {
        val vertices = listOf(Pair(0.0, 0.0), Pair(0.0, 0.0), Pair(1.0, 0.0), Pair(0.0, 1.0))
        val result = GeometryUtils.validatePolygonGeometry(vertices)
        assertTrue(result is PolygonValidationResult.Invalid && result.reason == PolygonValidationReason.ConsecutiveDuplicate)
    }

    @Test
    fun testNonconsecutiveDuplicate() {
        val vertices = listOf(Pair(0.0, 0.0), Pair(1.0, 0.0), Pair(1.0, 1.0), Pair(0.0, 1.0), Pair(0.0, 0.0))
        val result = GeometryUtils.validatePolygonGeometry(vertices)
        assertTrue(result is PolygonValidationResult.Invalid)
    }

    @Test
    fun testAllCollinearVertices() {
        val vertices = listOf(Pair(0.0, 0.0), Pair(1.0, 0.0), Pair(2.0, 0.0))
        val result = GeometryUtils.validatePolygonGeometry(vertices)
        assertTrue(result is PolygonValidationResult.Invalid && result.reason == PolygonValidationReason.ZeroArea)
    }

    @Test
    fun testZeroAreaPolygon() {
        val vertices = listOf(Pair(0.0, 0.0), Pair(1.0, 0.0), Pair(2.0, 0.0))
        val result = GeometryUtils.validatePolygonGeometry(vertices)
        assertTrue(result is PolygonValidationResult.Invalid && result.reason == PolygonValidationReason.ZeroArea)
    }

    @Test
    fun testBowTieIntersection() {
        val vertices = listOf(Pair(0.0, 0.0), Pair(2.0, 2.0), Pair(1.0, 0.0), Pair(0.0, 1.0))
        val result = GeometryUtils.validatePolygonGeometry(vertices)
        assertTrue(result is PolygonValidationResult.Invalid && result.reason == PolygonValidationReason.SelfIntersection)
    }

    @Test
    fun testNonadjacentEndpointTouch() {
        val vertices = listOf(Pair(0.0, 0.0), Pair(2.0, 0.0), Pair(1.0, 1.0), Pair(1.0, 0.0))
        val result = GeometryUtils.validatePolygonGeometry(vertices)
        assertTrue(result is PolygonValidationResult.Invalid && result.reason == PolygonValidationReason.SelfIntersection)
    }

    @Test
    fun testAdjacentEdgesSharingEndpoint() {
        val vertices = listOf(Pair(0.0, 0.0), Pair(1.0, 0.0), Pair(0.5, 1.0))
        val result = GeometryUtils.validatePolygonGeometry(vertices)
        assertTrue(result is PolygonValidationResult.Valid)
    }

    @Test
    fun testRingClosesExactlyOnce() {
        val vertices = listOf(Pair(0.0, 0.0), Pair(1.0, 0.0), Pair(1.0, 1.0))
        val json = GeometryUtils.buildPolygonGeoJson(vertices)
        assertTrue(json.contains("[[0.0,0.0],[1.0,0.0],[1.0,1.0],[0.0,0.0]]"))
    }

    @Test
    fun testLongitudeLatitudeOrder() {
        val vertices = listOf(Pair(10.0, 20.0), Pair(30.0, 40.0), Pair(50.0, 60.0))
        val json = GeometryUtils.buildPolygonGeoJson(vertices)
        assertTrue(json.contains("[10.0,20.0]"))
    }

    @Test
    fun testStructuredGeoJsonPolygon() {
        val vertices = listOf(Pair(0.0, 0.0), Pair(1.0, 0.0), Pair(1.0, 1.0))
        val json = GeometryUtils.buildPolygonGeoJson(vertices)
        val element = Json.parseToJsonElement(json).jsonObject
        assertEquals("Polygon", element["type"]?.jsonPrimitive?.content)
        val coords = element["coordinates"]?.jsonArray
        assertEquals(1, coords?.size)
        val ring = coords?.get(0)?.jsonArray
        assertEquals(4, ring?.size)
    }

    @Test
    fun testParseValidPolygon() {
        val json = "{\"type\":\"Polygon\",\"coordinates\":[[[0.0,0.0],[1.0,0.0],[1.0,1.0],[0.0,0.0]]]}"
        val result = GeometryUtils.parsePolygonGeoJson(json)
        assertTrue(result is PolygonParseResult.Success)
        val vertices = (result as PolygonParseResult.Success).vertices
        assertEquals(3, vertices.size)
        assertEquals(Pair(0.0, 0.0), vertices[0])
    }

    @Test
    fun testRejectUnclosedOuterRing() {
        val json = "{\"type\":\"Polygon\",\"coordinates\":[[[0.0,0.0],[1.0,0.0],[1.0,1.0]]]}"
        val result = GeometryUtils.parsePolygonGeoJson(json)
        assertTrue(result is PolygonParseResult.Error)
    }

    @Test
    fun testRejectInteriorRing() {
        val json = "{\"type\":\"Polygon\",\"coordinates\":[[[0.0,0.0],[10.0,0.0],[10.0,10.0],[0.0,0.0]],[[2.0,2.0],[2.0,3.0],[3.0,3.0],[2.0,2.0]]]}"
        val result = GeometryUtils.parsePolygonGeoJson(json)
        assertTrue(result is PolygonParseResult.Error)
        assertTrue((result as PolygonParseResult.Error).message.contains("Interior rings"))
    }

    @Test
    fun testSphericalAreaReference() {
        val vertices = listOf(Pair(0.0, 0.0), Pair(1.0, 0.0), Pair(1.0, 1.0), Pair(0.0, 1.0))
        val area = GeometryUtils.calculateSphericalArea(vertices)
        // Reference area is approx 12,308,000,000 m^2 or 12,360,000,000 m^2 depending on model
        assertTrue("Area $area should be around 1.236e10", area > 12300000000.0 && area < 12400000000.0)
    }

    @Test
    fun testPerimeterIncludesClosingEdge() {
        val vertices = listOf(Pair(0.0, 0.0), Pair(1.0, 0.0), Pair(1.0, 1.0))
        val perimeter = GeometryUtils.calculatePolygonPerimeter(vertices)
        val d1 = GeometryUtils.calculateHaversineDistance(0.0, 0.0, 0.0, 1.0)
        val d2 = GeometryUtils.calculateHaversineDistance(0.0, 1.0, 1.0, 1.0)
        val d3 = GeometryUtils.calculateHaversineDistance(1.0, 1.0, 0.0, 0.0)
        assertEquals(d1 + d2 + d3, perimeter, 0.001)
    }

    @Test
    fun testSquareFeetFormatting() {
        val area = 100.0
        val formatted = GeometryUtils.formatArea(area, MeasurementSystem.IMPERIAL)
        assertTrue(formatted.contains("sq ft"))
    }

    @Test
    fun testAcreFormatting() {
        val area = 50000.0
        val formatted = GeometryUtils.formatArea(area, MeasurementSystem.IMPERIAL)
        assertTrue(formatted.contains("ac"))
    }

    @Test
    fun testCoordinateEpsilonEqualityCollision() {
        val p1 = Pair(0.0, 0.0)
        val p2 = Pair(1e-11, 1e-11)
        assertTrue("Coordinates within epsilon should be equal", GeometryUtils.areCoordinatesEqual(p1, p2))
        
        val p3 = Pair(1e-9, 1e-9)
        assertFalse("Coordinates outside epsilon should NOT be equal", GeometryUtils.areCoordinatesEqual(p1, p3))
    }

    @Test
    fun testStringConcatenationDuplicateDetectionReplacement() {
        val p1 = Pair(0.1 + 0.2, 0.3)
        val p2 = Pair(0.3, 0.3)
        assertNotEquals(p1.first.toString(), p2.first.toString())
        assertTrue("Epsilon-aware should detect they are essentially the same", GeometryUtils.areCoordinatesEqual(p1, p2, 1e-12))
    }

    @Test
    fun testFeetMilesPerimeterFormatting() {
        val d1 = 500.0 / 3.28084
        assertEquals("500 ft", GeometryUtils.formatDistance(d1, MeasurementSystem.IMPERIAL))
        val d2 = 2000.0
        assertEquals("1.2 mi", GeometryUtils.formatDistance(d2, MeasurementSystem.IMPERIAL))
    }

    @Test
    fun testPolygonMidpointGeneration() {
        val vertices = listOf(Pair(0.0, 0.0), Pair(2.0, 0.0), Pair(2.0, 2.0), Pair(0.0, 2.0))
        val midpoints = GeometryUtils.polygonMidpoints(vertices)
        
        assertEquals("Should have one midpoint per edge", vertices.size, midpoints.size)
        
        // Edge 0: (0,0) -> (2,0) => (1,0)
        assertEquals(Pair(1.0, 0.0), midpoints[0].coordinate)
        assertEquals(1, midpoints[0].insertionIndex)
        
        // Edge 3 (closing): (0,2) -> (0,0) => (0,1)
        assertEquals(Pair(0.0, 1.0), midpoints[3].coordinate)
        assertEquals(4, midpoints[3].insertionIndex)
    }

    @Test
    fun testMidpointGenerationEmptyForInsufficientVertices() {
        val vertices = listOf(Pair(0.0, 0.0), Pair(1.0, 1.0))
        val midpoints = GeometryUtils.polygonMidpoints(vertices)
        assertTrue(midpoints.isEmpty())
    }

    @Test
    fun testInsertAtEveryEdgePreservesVertexOrder() {
        val vertices = listOf(Pair(0.0, 0.0), Pair(2.0, 0.0), Pair(2.0, 2.0))
        // 0 -> 1 -> 2 -> (0)
        // Insert at index 1 (between 0 and 1)
        val v1 = vertices.toMutableList()
        v1.add(1, Pair(1.0, 0.0))
        assertEquals(listOf(Pair(0.0, 0.0), Pair(1.0, 0.0), Pair(2.0, 0.0), Pair(2.0, 2.0)), v1)
        
        // Insert at index 3 (between 2 and 0)
        val v2 = vertices.toMutableList()
        v2.add(3, Pair(1.0, 1.0))
        assertEquals(listOf(Pair(0.0, 0.0), Pair(2.0, 0.0), Pair(2.0, 2.0), Pair(1.0, 1.0)), v2)
    }

    @Test
    fun testPolygonAreaPrecision() {
        // Very thin polygon
        val vertices = listOf(Pair(0.0, 0.0), Pair(0.00001, 0.0), Pair(0.00001, 1.0), Pair(0.0, 1.0))
        val area = GeometryUtils.calculateSphericalArea(vertices)
        assertTrue(area > 0)
    }

    @Test
    fun testInvalidCoordinateExtreme() {
        assertFalse(GeometryUtils.validateCoordinate(1000.0, 0.0))
        assertFalse(GeometryUtils.validateCoordinate(0.0, -100.0))
    }

    @Test
    fun testAreCoordinatesEqualCustomEpsilon() {
        val p1 = Pair(1.0, 1.0)
        val p2 = Pair(1.1, 1.1)
        assertTrue(GeometryUtils.areCoordinatesEqual(p1, p2, 0.2))
        assertFalse(GeometryUtils.areCoordinatesEqual(p1, p2, 0.05))
    }

    @Test
    fun testPolygonMidpointsPreserveEdgeOrder() {
        val vertices = listOf(Pair(0.0, 0.0), Pair(1.0, 0.0), Pair(1.0, 1.0), Pair(0.0, 1.0))
        val midpoints = GeometryUtils.polygonMidpoints(vertices)
        assertEquals(1, midpoints[0].insertionIndex)
        assertEquals(2, midpoints[1].insertionIndex)
        assertEquals(3, midpoints[2].insertionIndex)
        assertEquals(4, midpoints[3].insertionIndex)
    }

    @Test
    fun testValidateLineGeometryEmpty() {
        assertFalse(GeometryUtils.validateLineGeometry(emptyList()))
    }

    @Test
    fun testHaversineSamePoint() {
        assertEquals(0.0, GeometryUtils.calculateHaversineDistance(10.0, 20.0, 10.0, 20.0), 0.0)
    }

    @Test
    fun testHaversineAntipodes() {
        val d = GeometryUtils.calculateHaversineDistance(90.0, 0.0, -90.0, 0.0)
        assertTrue(abs(d - 20037508.0) < 500000.0) 
    }

    @Test
    fun testFormatAreaZero() {
        assertEquals("0 sq ft", GeometryUtils.formatArea(0.0, MeasurementSystem.IMPERIAL))
    }

    @Test
    fun testFormatDistanceZero() {
        assertEquals("0 ft", GeometryUtils.formatDistance(0.0, MeasurementSystem.IMPERIAL))
    }

    @Test
    fun testBuildPointGeoJson() {
        val json = GeometryUtils.buildPointGeoJson(1.2, 3.4)
        assertTrue(json.contains("\"Point\""))
        assertTrue(json.contains("[1.2,3.4]"))
    }

    @Test
    fun testParsePolygonEmpty() {
        val result = GeometryUtils.parsePolygonGeoJson("")
        assertTrue(result is PolygonParseResult.Error)
    }

    @Test
    fun testParsePolygonMalformed() {
        val result = GeometryUtils.parsePolygonGeoJson("{ invalid }")
        assertTrue(result is PolygonParseResult.Error)
    }

    @Test
    fun testParsePolygonNotPolygon() {
        val result = GeometryUtils.parsePolygonGeoJson("{\"type\":\"Point\",\"coordinates\":[0,0]}")
        assertTrue(result is PolygonParseResult.Error)
    }

    @Test
    fun testValidateCoordinateThresholds() {
        assertTrue(GeometryUtils.validateCoordinate(180.0, 90.0))
        assertTrue(GeometryUtils.validateCoordinate(-180.0, -90.0))
        assertFalse(GeometryUtils.validateCoordinate(180.0000001, 0.0))
        assertFalse(GeometryUtils.validateCoordinate(0.0, 90.0000001))
    }

    @Test
    fun testLargePolygonValidation() {
        val vertices = mutableListOf<Pair<Double, Double>>()
        repeat(100) {
            vertices.add(Pair(it.toDouble() / 100.0, it.toDouble() / 100.0))
        }
        // This is collinear, so zero area
        val result = GeometryUtils.validatePolygonGeometry(vertices)
        assertTrue(result is PolygonValidationResult.Invalid)
    }

    @Test
    fun testTriangleValidation() {
        val vertices = listOf(Pair(0.0, 0.0), Pair(0.001, 0.0), Pair(0.0, 0.001))
        val result = GeometryUtils.validatePolygonGeometry(vertices)
        assertTrue(result is PolygonValidationResult.Valid)
    }

    @Test
    fun testPolygonAreaSymmetry() {
        val v1 = listOf(Pair(0.0, 0.0), Pair(1.0, 0.0), Pair(1.0, 1.0))
        val v2 = listOf(Pair(1.0, 1.0), Pair(1.0, 0.0), Pair(0.0, 0.0))
        assertEquals(GeometryUtils.calculateSphericalArea(v1), GeometryUtils.calculateSphericalArea(v2), 1.0)
    }

    @Test
    fun testPolygonPerimeterSymmetry() {
        val v1 = listOf(Pair(0.0, 0.0), Pair(1.0, 0.0), Pair(1.0, 1.0))
        val v2 = listOf(Pair(1.0, 1.0), Pair(1.0, 0.0), Pair(0.0, 0.0))
        assertEquals(GeometryUtils.calculatePolygonPerimeter(v1), GeometryUtils.calculatePolygonPerimeter(v2), 0.1)
    }

    @Test
    fun testValidateCoordinateNegativeZero() {
        assertTrue(GeometryUtils.validateCoordinate(-0.0, -0.0))
    }

    @Test
    fun testValidateLineGeometryDuplicates() {
        val vertices = listOf(Pair(0.0, 0.0), Pair(0.0, 0.0))
        assertFalse(GeometryUtils.validateLineGeometry(vertices))
    }

    @Test
    fun testValidatePolygonGeometryDuplicates() {
        val vertices = listOf(Pair(0.0, 0.0), Pair(1.0, 1.0), Pair(0.0, 0.0))
        val result = GeometryUtils.validatePolygonGeometry(vertices)
        assertTrue(result is PolygonValidationResult.Invalid)
    }

    @Test
    fun testSphericalAreaHemisphere() {
        val vertices = listOf(Pair(0.0, 0.0), Pair(90.0, 0.0), Pair(0.0, 90.0))
        val area = GeometryUtils.calculateSphericalArea(vertices)
        // 1/16 of surface area
        assertTrue(area > 3e13 && area < 3.5e13)
    }

    @Test
    fun testPerimeterWrapping() {
        val v = listOf(Pair(179.0, 0.0), Pair(-179.0, 0.0), Pair(180.0, 1.0))
        val p = GeometryUtils.calculatePolygonPerimeter(v)
        assertTrue(p > 0)
    }

    @Test
    fun testThinRectangleArea() {
        val v = listOf(Pair(0.0, 0.0), Pair(0.000001, 0.0), Pair(0.000001, 0.000001), Pair(0.0, 0.000001))
        val area = GeometryUtils.calculateSphericalArea(v)
        assertTrue(area > 0)
    }

    @Test
    fun testFormatDistanceLarge() {
        val d = 20000.0 // meters
        assertTrue(GeometryUtils.formatDistance(d, MeasurementSystem.IMPERIAL).contains("mi"))
    }

    @Test
    fun testFormatAreaLarge() {
        val a = 1000000.0 // sq meters
        assertTrue(GeometryUtils.formatArea(a, MeasurementSystem.IMPERIAL).contains("ac"))
    }

    @Test
    fun testHaversinePole() {
        val d = GeometryUtils.calculateHaversineDistance(90.0, 0.0, 90.0, 180.0)
        assertEquals(0.0, d, 0.01)
    }

    @Test
    fun testPolygonValidationReasonValues() {
        val reason = PolygonValidationReason.SelfIntersection
        assertEquals("SelfIntersection", reason.name)
    }

    @Test
    fun testPolygonParseResultSuccess() {
        val v = listOf(Pair(0.0, 0.0))
        val res = PolygonParseResult.Success(v)
        assertEquals(v, res.vertices)
    }

    @Test
    fun testPolygonMidpointCoordinateCorrectness() {
        val v = listOf(Pair(0.0, 0.0), Pair(10.0, 0.0), Pair(5.0, 10.0))
        val m = GeometryUtils.polygonMidpoints(v)
        // Midpoint 0: (0,0)-(10,0) -> (5,0)
        assertEquals(5.0, m[0].coordinate.first, 0.01)
        assertEquals(0.0, m[0].coordinate.second, 0.01)
    }
    
    @Test
    fun testValidationReasonProperty() {
        val res = PolygonValidationResult.Invalid(PolygonValidationReason.TooFewVertices)
        assertEquals(PolygonValidationReason.TooFewVertices, res.reason)
    }
    
    @Test
    fun testValidationResultValidProperty() {
        val res = PolygonValidationResult.Valid
        assertTrue(res is PolygonValidationResult)
    }

    @Test
    fun testFormatAreaPrecision() {
        val a = 123.456
        val s = GeometryUtils.formatArea(a, MeasurementSystem.IMPERIAL)
        assertTrue(s.contains("1,328.9"))
    }

    @Test
    fun testEarthRadiusValue() {
        // Just verify internal constant logic if accessible or behavior
        val d = GeometryUtils.calculateHaversineDistance(0.0, 0.0, 0.0, 1.0)
        assertTrue(d > 111000.0 && d < 112000.0)
    }

    @Test
    fun testPolygonMidpointInsertionIndexCorrectness() {
        val v = listOf(Pair(0.0, 0.0), Pair(1.0, 0.0), Pair(1.0, 1.0))
        val m = GeometryUtils.polygonMidpoints(v)
        assertEquals(1, m[0].insertionIndex)
        assertEquals(2, m[1].insertionIndex)
        assertEquals(3, m[2].insertionIndex)
    }

    @Test
    fun testValidateLineGeometryNullCoords() {
        // Not null, but check behavior with extreme values
        val v = listOf(Pair(Double.NEGATIVE_INFINITY, 0.0))
        assertFalse(GeometryUtils.validateLineGeometry(v))
    }

    @Test
    fun testBuildPolygonGeoJsonEmpty() {
        val json = GeometryUtils.buildPolygonGeoJson(emptyList())
        assertTrue(json.contains("[]"))
    }

    @Test
    fun testFormatDistanceLargeFeet() {
        val d = 1000.0 / 3.28084
        assertEquals("1,000 ft", GeometryUtils.formatDistance(d, MeasurementSystem.IMPERIAL))
    }

    @Test
    fun testFormatDistanceSmallMiles() {
        val d = 1609.35 // Slightly more than 1 mile
        assertEquals("1 mi", GeometryUtils.formatDistance(d, MeasurementSystem.IMPERIAL))
    }
}
