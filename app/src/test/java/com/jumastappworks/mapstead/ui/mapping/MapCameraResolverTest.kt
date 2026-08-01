package com.jumastappworks.mapstead.ui.mapping

import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.data.db.entities.PlanEntity
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import org.junit.Assert.*
import org.junit.Test
import java.util.*

class MapCameraResolverTest {

    @Test
    fun `plan center takes highest priority`() {
        val plan = PlanEntity(
            propertyId = UUID.randomUUID(), name = "P", planType = "M", backgroundType = "M",
            centerLatitude = 10.0, centerLongitude = 20.0, zoom = 15.0
        )
        val property = PropertyEntity(id = UUID.randomUUID(), name = "Prop", propertyType = "H", latitude = 30.0, longitude = 40.0)
        
        val resolution = MapCameraResolver.resolveInitialCamera(plan, property, emptyList())
        val result = resolution.focus
        
        assertTrue(result is MapCameraFocus.Point)
        val point = result as MapCameraFocus.Point
        assertEquals(10.0, point.latitude, 0.0001)
        assertEquals(20.0, point.longitude, 0.0001)
        assertEquals(15f, point.zoom)
        assertEquals(CameraSource.SAVED_PLAN_CAMERA, resolution.source)
    }

    @Test
    fun `property center takes second priority`() {
        val plan = PlanEntity(propertyId = UUID.randomUUID(), name = "P", planType = "M", backgroundType = "M")
        val property = PropertyEntity(id = UUID.randomUUID(), name = "Prop", propertyType = "H", latitude = 30.0, longitude = 40.0)
        
        val resolution = MapCameraResolver.resolveInitialCamera(plan, property, emptyList())
        val result = resolution.focus
        
        assertTrue(result is MapCameraFocus.Point)
        val point = result as MapCameraFocus.Point
        assertEquals(30.0, point.latitude, 0.0001)
        assertEquals(40.0, point.longitude, 0.0001)
        assertEquals(17f, point.zoom)
        assertEquals(CameraSource.PROPERTY_COORDINATES, resolution.source)
    }

    @Test
    fun `feature bounds used when no centers exist`() {
        val plan = PlanEntity(propertyId = UUID.randomUUID(), name = "P", planType = "M", backgroundType = "M")
        val features = listOf(
            MapFeatureEntity(
                id = UUID.randomUUID(), propertyId = plan.propertyId, planId = plan.id, layerId = UUID.randomUUID(),
                geometryType = "POINT", geometryJson = "{\"type\":\"Point\",\"coordinates\":[10,5]}", accuracySource = "Manual",
                coordinateSpace = "LOCAL", styleJson = "{}"
            ),
            MapFeatureEntity(
                id = UUID.randomUUID(), propertyId = plan.propertyId, planId = plan.id, layerId = UUID.randomUUID(),
                geometryType = "POINT", geometryJson = "{\"type\":\"Point\",\"coordinates\":[20,15]}", accuracySource = "Manual",
                coordinateSpace = "LOCAL", styleJson = "{}"
            )
        )
        
        val resolution = MapCameraResolver.resolveInitialCamera(plan, null, features)
        val result = resolution.focus
        
        assertTrue(result is MapCameraFocus.Bounds)
        val bounds = result as MapCameraFocus.Bounds
        assertEquals(10.0, bounds.sw.first, 0.0001) // Longitude
        assertEquals(5.0, bounds.sw.second, 0.0001) // Latitude
        assertEquals(20.0, bounds.ne.first, 0.0001)
        assertEquals(15.0, bounds.ne.second, 0.0001)
        assertEquals(CameraSource.FEATURE_BOUNDS, resolution.source)
    }

    @Test
    fun `fallback used when everything is empty`() {
        val resolution = MapCameraResolver.resolveInitialCamera(null, null, emptyList())
        val result = resolution.focus
        
        assertTrue(result is MapCameraFocus.Point)
        val point = result as MapCameraFocus.Point
        assertEquals(39.8283, point.latitude, 0.0001)
        assertEquals(-98.5795, point.longitude, 0.0001)
        assertEquals(4f, point.zoom)
        assertEquals(CameraSource.SAFE_FALLBACK, resolution.source)
    }

    @Test
    fun `default world camera is repaired when property context exists`() {
        val badPlan = PlanEntity(
            propertyId = UUID.randomUUID(), name = "Bad", planType = "M", backgroundType = "M",
            centerLatitude = 0.0, centerLongitude = 0.0, zoom = 0.0
        )
        val goodProperty = PropertyEntity(id = badPlan.propertyId, name = "P", propertyType = "H", latitude = 45.0, longitude = -90.0)
        
        val resolution = MapCameraResolver.resolveInitialCamera(badPlan, goodProperty, emptyList())
        
        assertEquals(CameraSource.REPAIRED_DEFAULT_CAMERA, resolution.source)
        assertTrue(resolution.focus is MapCameraFocus.Point)
        assertEquals(45.0, (resolution.focus as MapCameraFocus.Point).latitude, 0.0001)
    }
}
