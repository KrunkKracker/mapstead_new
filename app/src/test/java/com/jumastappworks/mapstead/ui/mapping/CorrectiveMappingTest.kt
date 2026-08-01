package com.jumastappworks.mapstead.ui.mapping

import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.data.db.entities.PlanEntity
import com.jumastappworks.mapstead.data.db.entities.LayerEntity
import com.jumastappworks.mapstead.util.GeometryUtils
import org.junit.Assert.*
import org.junit.Test
import java.util.*

class CorrectiveMappingTest {

    @Test
    fun `coordinate order is longitude then latitude`() {
        // Florida coordinate regression test
        val lon = -82.35
        val lat = 28.12
        
        val pointGeoJson = GeometryUtils.buildPointGeoJson(lon, lat)
        assertTrue("GeoJSON should have longitude first", pointGeoJson.contains("-82.35"))
        assertTrue("GeoJSON should have latitude second", pointGeoJson.contains("28.12"))
        
        val parsed = GeometryUtils.parsePointGeometry(pointGeoJson)
        assertEquals(lon, parsed.first, 1e-10)
        assertEquals(lat, parsed.second, 1e-10)
    }

    @Test
    fun `MapCameraResolver ensures SW NE ordering for Florida scale`() {
        val pid = UUID.randomUUID()
        val plid = UUID.randomUUID()
        val lid = UUID.randomUUID()
        
        // Tampa area - reversed in input to test sorting
        val features = listOf(
            mockFeatureWithIds(pid, plid, lid, "POINT", "{\"type\":\"Point\",\"coordinates\":[-82.4, 28.1]}"),
            mockFeatureWithIds(pid, plid, lid, "POINT", "{\"type\":\"Point\",\"coordinates\":[-82.5, 28.0]}")
        )
        
        val plan = PlanEntity(id = plid, propertyId = pid, name = "Tampa Plan", planType = "M", backgroundType = "M")
        
        val resolution = MapCameraResolver.resolveInitialCamera(plan, null, features)
        val focus = resolution.focus
        assertTrue(focus is MapCameraFocus.Bounds)
        val bounds = focus as MapCameraFocus.Bounds
        
        // SW must be lower values
        assertEquals("SW Longitude", -82.5, bounds.sw.first, 1e-10)
        assertEquals("SW Latitude", 28.0, bounds.sw.second, 1e-10)
        assertEquals("NE Longitude", -82.4, bounds.ne.first, 1e-10)
        assertEquals("NE Latitude", 28.1, bounds.ne.second, 1e-10)
    }

    @Test
    fun `search ranking prioritizes exact matches and correct categories`() {
        val pid = UUID.randomUUID()
        val plid = UUID.randomUUID()
        val lid = UUID.randomUUID()
        
        val features = listOf(
            mockFeatureWithIds(pid, plid, lid, "POINT", "{}", label = "Water pump"),
            mockFeatureWithIds(pid, plid, lid, "POINT", "{}", label = "Pump"),
            mockFeatureWithIds(pid, plid, lid, "POINT", "{}", label = "Pump station")
        )
        
        val layers = listOf(LayerEntity(id = lid, propertyId = pid, planId = plid, name = "L", category = "Utility"))
        val results = MapSearchEngine.filterAndRank("pump", pid, plid, features, layers, emptyList())
        
        assertEquals(3, results.size)
        // 1. Exact match "Pump" (Score 1)
        assertEquals("Pump", results[0].featureLabel)
        // 2. Prefix match "Pump station" (Score 5)
        assertEquals("Pump station", results[1].featureLabel)
        // 3. Contains match "Water pump" (Score 9)
        assertEquals("Water pump", results[2].featureLabel)
    }

    @Test
    fun `search context validation excludes cross-property layers`() {
        val pid1 = UUID.randomUUID()
        val pid2 = UUID.randomUUID()
        val plid = UUID.randomUUID()
        val lid = UUID.randomUUID()
        
        val features = listOf(
            mockFeatureWithIds(pid1, plid, lid, "POINT", "{}", label = "Correct Property Item")
        )
        
        // Layer belongs to pid2, but feature and query say pid1
        val layers = listOf(LayerEntity(id = lid, propertyId = pid2, planId = plid, name = "L", category = "C"))
        
        val results = MapSearchEngine.filterAndRank("correct", pid1, plid, features, layers, emptyList())
        
        assertTrue("Should return no results due to layer property mismatch", results.isEmpty())
    }

    @Test
    fun `search ranking and deduplication are deterministic`() {
        val fid1 = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val fid2 = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val pid = UUID.randomUUID()
        val plid = UUID.randomUUID()
        val lid = UUID.randomUUID()
        
        val features = listOf(
            mockFeatureExplicitId(fid1, pid, plid, lid, "Pump A"),
            mockFeatureExplicitId(fid2, pid, plid, lid, "Pump B")
        )
        
        val results = MapSearchEngine.filterAndRank(
            query = "pump",
            currentPropertyId = pid,
            currentPlanId = plid,
            features = features,
            layers = listOf(LayerEntity(id = lid, propertyId = pid, planId = plid, name = "L", category = "C")),
            items = emptyList()
        )
        
        assertEquals(2, results.size)
        assertEquals("Pump A", results[0].featureLabel)
        assertEquals("Pump B", results[1].featureLabel)
    }

    private fun mockFeatureWithIds(
        pid: UUID, plid: UUID, lid: UUID, type: String, json: String, 
        label: String = "Test"
    ): MapFeatureEntity {
        return MapFeatureEntity(
            id = UUID.randomUUID(),
            propertyId = pid,
            planId = plid,
            layerId = lid,
            geometryType = type,
            geometryJson = json,
            coordinateSpace = "GEOGRAPHIC",
            styleJson = "{}",
            accuracySource = "MANUAL",
            label = label
        )
    }

    private fun mockFeatureExplicitId(id: UUID, pid: UUID, plid: UUID, lid: UUID, label: String): MapFeatureEntity {
        return MapFeatureEntity(
            id = id,
            propertyId = pid,
            planId = plid,
            layerId = lid,
            geometryType = "POINT",
            geometryJson = "{}",
            coordinateSpace = "GEOGRAPHIC",
            styleJson = "{}",
            accuracySource = "MANUAL",
            label = label
        )
    }
}
