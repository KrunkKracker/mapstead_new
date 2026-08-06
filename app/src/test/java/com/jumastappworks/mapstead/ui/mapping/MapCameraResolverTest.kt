package com.jumastappworks.mapstead.ui.mapping

import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.data.db.entities.PlanEntity
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import org.junit.Assert.*
import org.junit.Test
import java.util.*

class MapCameraResolverTest {

    @Test
    fun `restoration request takes highest priority`() {
        val plan = PlanEntity(
            id = UUID.randomUUID(), propertyId = UUID.randomUUID(), name = "P", planType = "M", backgroundType = "M",
            centerLatitude = 10.0, centerLongitude = 20.0, zoom = 15.0
        )
        val restoration = CameraRestorationRequest(
            planId = plan.id, latitude = 50.0, longitude = 60.0, zoom = 18.0, bearing = 45.0
        )
        
        val resolution = MapCameraResolver.resolveInitialCamera(plan, null, emptyList(), restoration)
        val result = resolution.focus
        
        assertTrue(result is MapCameraFocus.Point)
        val point = result as MapCameraFocus.Point
        assertEquals(50.0, point.latitude, 0.0001)
        assertEquals(60.0, point.longitude, 0.0001)
        assertEquals(18f, point.zoom)
        assertEquals(45.0, point.bearing, 0.0001)
        assertEquals(CameraSource.SAVED_PLAN_CAMERA, resolution.source)
    }

    @Test
    fun `restoration for different plan is ignored`() {
        val plan = PlanEntity(
            id = UUID.randomUUID(), propertyId = UUID.randomUUID(), name = "P", planType = "M", backgroundType = "M",
            centerLatitude = 10.0, centerLongitude = 20.0, zoom = 15.0
        )
        val wrongRestoration = CameraRestorationRequest(
            planId = UUID.randomUUID(), latitude = 50.0, longitude = 60.0, zoom = 18.0, bearing = 0.0
        )
        
        val resolution = MapCameraResolver.resolveInitialCamera(plan, null, emptyList(), wrongRestoration)
        val point = resolution.focus as MapCameraFocus.Point
        assertEquals(10.0, point.latitude, 0.0001)
    }

    @Test
    fun `world-view restoration is rejected if better context exists`() {
        val planId = UUID.randomUUID()
        val plan = PlanEntity(id = planId, propertyId = UUID.randomUUID(), name = "P", planType = "M", backgroundType = "M")
        val restoration = CameraRestorationRequest(planId = planId, latitude = 0.0, longitude = 0.0, zoom = 0.5, bearing = 0.0)
        val property = PropertyEntity(id = plan.propertyId, name = "P", propertyType = "H", latitude = 45.0, longitude = -90.0)

        val resolution = MapCameraResolver.resolveInitialCamera(plan, property, emptyList(), restoration)
        
        assertEquals(CameraSource.PROPERTY_COORDINATES, resolution.source)
        val point = resolution.focus as MapCameraFocus.Point
        assertEquals(45.0, point.latitude, 0.0001)
    }

    @Test
    fun `plan center takes priority over property center`() {
        val plan = PlanEntity(
            propertyId = UUID.randomUUID(), name = "P", planType = "M", backgroundType = "M",
            centerLatitude = 10.0, centerLongitude = 20.0, zoom = 15.0
        )
        val property = PropertyEntity(id = UUID.randomUUID(), name = "Prop", propertyType = "H", latitude = 30.0, longitude = 40.0)
        
        val resolution = MapCameraResolver.resolveInitialCamera(plan, property, emptyList())
        val point = resolution.focus as MapCameraFocus.Point
        assertEquals(10.0, point.latitude, 0.0001)
        assertEquals(CameraSource.SAVED_PLAN_CAMERA, resolution.source)
    }

    @Test
    fun `property center takes priority over feature bounds`() {
        val plan = PlanEntity(propertyId = UUID.randomUUID(), name = "P", planType = "M", backgroundType = "M")
        val property = PropertyEntity(id = UUID.randomUUID(), name = "Prop", propertyType = "H", latitude = 30.0, longitude = 40.0)
        val features = listOf(
            MapFeatureEntity(
                id = UUID.randomUUID(), propertyId = plan.propertyId, planId = plan.id, layerId = UUID.randomUUID(),
                geometryType = "POINT", geometryJson = "{\"type\":\"Point\",\"coordinates\":[10,5]}", accuracySource = "M",
                coordinateSpace = "G", styleJson = "{}"
            )
        )
        
        val resolution = MapCameraResolver.resolveInitialCamera(plan, property, features)
        val point = resolution.focus as MapCameraFocus.Point
        assertEquals(30.0, point.latitude, 0.0001)
        assertEquals(CameraSource.PROPERTY_COORDINATES, resolution.source)
    }

    @Test
    fun `feature bounds used when no centers exist`() {
        val plan = PlanEntity(propertyId = UUID.randomUUID(), name = "P", planType = "M", backgroundType = "M")
        val features = listOf(
            MapFeatureEntity(
                id = UUID.randomUUID(), propertyId = plan.propertyId, planId = plan.id, layerId = UUID.randomUUID(),
                geometryType = "POINT", geometryJson = "{\"type\":\"Point\",\"coordinates\":[10,5]}", accuracySource = "M",
                coordinateSpace = "G", styleJson = "{}"
            ),
            MapFeatureEntity(
                id = UUID.randomUUID(), propertyId = plan.propertyId, planId = plan.id, layerId = UUID.randomUUID(),
                geometryType = "POINT", geometryJson = "{\"type\":\"Point\",\"coordinates\":[20,15]}", accuracySource = "M",
                coordinateSpace = "G", styleJson = "{}"
            )
        )
        
        val resolution = MapCameraResolver.resolveInitialCamera(plan, null, features)
        
        assertTrue(resolution.focus is MapCameraFocus.Bounds)
        assertEquals(CameraSource.FEATURE_BOUNDS, resolution.source)
    }

    @Test
    fun `fallback used when everything is empty`() {
        val resolution = MapCameraResolver.resolveInitialCamera(null, null, emptyList())
        val point = resolution.focus as MapCameraFocus.Point
        assertEquals(39.8283, point.latitude, 0.0001)
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
        val point = resolution.focus as MapCameraFocus.Point
        assertEquals(45.0, point.latitude, 0.0001)
    }
}
