package com.jumastappworks.mapstead.ui.mapping

import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import com.jumastappworks.mapstead.data.db.entities.LayerEntity
import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MapSearchEngineTest {

    private val propertyId = UUID.randomUUID()
    private val planId = UUID.randomUUID()
    private val layerId = UUID.randomUUID()

    private val layer = LayerEntity(id = layerId, propertyId = propertyId, planId = planId, name = "Layer 1", category = "Utility")

    @Test
    fun testNormalizeWhitespace() {
        assertEquals("test query", MapSearchEngine.normalize("  TEST   query  "))
    }

    @Test
    fun testFilterMismatchedProperty() {
        val otherPropId = UUID.randomUUID()
        val feature = MapFeatureEntity(id = UUID.randomUUID(), propertyId = otherPropId, planId = planId, layerId = layerId, geometryType = "POINT", geometryJson = "{}", coordinateSpace = "G", styleJson = "{}", accuracySource = "M", label = "Target")
        
        val results = MapSearchEngine.filterAndRank("target", propertyId, planId, listOf(feature), listOf(layer), emptyList())
        assertTrue(results.isEmpty())
    }

    @Test
    fun testFilterMismatchedPlan() {
        val otherPlanId = UUID.randomUUID()
        val feature = MapFeatureEntity(id = UUID.randomUUID(), propertyId = propertyId, planId = otherPlanId, layerId = layerId, geometryType = "POINT", geometryJson = "{}", coordinateSpace = "G", styleJson = "{}", accuracySource = "M", label = "Target")
        
        val results = MapSearchEngine.filterAndRank("target", propertyId, planId, listOf(feature), listOf(layer), emptyList())
        assertTrue(results.isEmpty())
    }

    @Test
    fun testSystemItemOwnershipValidation() {
        val itemId = UUID.randomUUID()
        val otherPropId = UUID.randomUUID()
        
        // System item belongs to ANOTHER property
        val infra = InfrastructureItemEntity(id = itemId, propertyId = otherPropId, name = "Mismatched Pump", category = "HVAC", status = "Active")
        val feature = MapFeatureEntity(id = UUID.randomUUID(), propertyId = propertyId, planId = planId, layerId = layerId, infrastructureItemId = itemId, geometryType = "POINT", geometryJson = "{}", coordinateSpace = "G", styleJson = "{}", accuracySource = "M", label = "My Feature")
        
        val results = MapSearchEngine.filterAndRank("pump", propertyId, planId, listOf(feature), listOf(layer), listOf(infra))
        
        // Should not match "pump" because the item is ignored due to ownership mismatch
        assertTrue("Should not match mismatched system item", results.isEmpty())
        
        val results2 = MapSearchEngine.filterAndRank("feature", propertyId, planId, listOf(feature), listOf(layer), listOf(infra))
        assertEquals(1, results2.size)
        assertNull("System item info should be null for mismatched owner", results2[0].systemItemName)
    }

    @Test
    fun testDeletedSystemItemExcluded() {
        val itemId = UUID.randomUUID()
        val infra = InfrastructureItemEntity(id = itemId, propertyId = propertyId, name = "Old Pump", category = "HVAC", status = "Active", deletedAt = java.time.Instant.now())
        val feature = MapFeatureEntity(id = UUID.randomUUID(), propertyId = propertyId, planId = planId, layerId = layerId, infrastructureItemId = itemId, geometryType = "POINT", geometryJson = "{}", coordinateSpace = "G", styleJson = "{}", accuracySource = "M")
        
        val results = MapSearchEngine.filterAndRank("pump", propertyId, planId, listOf(feature), listOf(layer), listOf(infra))
        assertTrue(results.isEmpty())
    }

    @Test
    fun testRankingPriority() {
        val features = listOf(
            MapFeatureEntity(id = UUID.randomUUID(), propertyId = propertyId, planId = planId, layerId = layerId, geometryType = "POINT", geometryJson = "{}", coordinateSpace = "G", styleJson = "{}", accuracySource = "M", label = "Generator 1"), // Prefix
            MapFeatureEntity(id = UUID.randomUUID(), propertyId = propertyId, planId = planId, layerId = layerId, geometryType = "POINT", geometryJson = "{}", coordinateSpace = "G", styleJson = "{}", accuracySource = "M", label = "Generator"),   // Exact
            MapFeatureEntity(id = UUID.randomUUID(), propertyId = propertyId, planId = planId, layerId = layerId, geometryType = "POINT", geometryJson = "{}", coordinateSpace = "G", styleJson = "{}", accuracySource = "M", label = "A Generator") // Contains
        )
        
        val results = MapSearchEngine.filterAndRank("generator", propertyId, planId, features, listOf(layer), emptyList())
        assertEquals(3, results.size)
        assertEquals("Generator", results[0].featureLabel)
        assertEquals("Generator 1", results[1].featureLabel)
        assertEquals("A Generator", results[2].featureLabel)
    }
}
