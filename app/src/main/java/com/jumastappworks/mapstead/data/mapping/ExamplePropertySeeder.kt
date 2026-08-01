package com.jumastappworks.mapstead.data.mapping

import com.jumastappworks.mapstead.data.db.DatabaseTransactionRunner
import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.db.entities.*
import com.jumastappworks.mapstead.util.GeometryUtils
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class ExamplePropertySeeder @Inject constructor(
    private val database: MapsteadDatabase,
    private val transactionRunner: DatabaseTransactionRunner
) {
    companion object {
        val EXAMPLE_PROPERTY_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val EXAMPLE_PLAN_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
        
        val LAYER_STRUCTURES_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000003")
        val LAYER_UTILITIES_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000004")
        val LAYER_OUTDOOR_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000005")

        val WELL_FEATURE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000006")
        val PANEL_FEATURE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000007")
        val GATE_FEATURE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000008")
        
        val WELL_ITEM_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000009")
        val PANEL_ITEM_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000010")
        val GATE_ITEM_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000011")

        val WATER_LINE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000012")
        val FENCE_LINE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000013")
        val HOUSE_AREA_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000014")
        val BOUNDARY_AREA_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000015")
        
        val EXAMPLE_RELATIONSHIP_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000016")
        val EXAMPLE_MAINTENANCE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000017")
        val EXAMPLE_REMINDER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000018")
    }

    suspend fun isExampleInstalled(): Boolean {
        return database.propertyDao().getPropertyById(EXAMPLE_PROPERTY_ID) != null
    }

    suspend fun removeExample() {
        transactionRunner.run {
            // Hard delete the entire graph using the property ID and fixed child IDs
            // Foreign key cascades handle most, but we want to be explicit or ensure order
            val property = database.propertyDao().getAllPropertiesOnce().find { it.id == EXAMPLE_PROPERTY_ID }
            if (property != null) {
                database.propertyDao().hardDeleteProperty(property)
            }
        }
    }

    suspend fun seedExample() {
        transactionRunner.run {
            // 1. Clean up any partial state
            val existing = database.propertyDao().getAllPropertiesOnce().find { it.id == EXAMPLE_PROPERTY_ID }
            if (existing != null) {
                database.propertyDao().hardDeleteProperty(existing)
            }

            // 2. Create Property
            val now = Instant.now()
            database.propertyDao().insertProperty(PropertyEntity(
                id = EXAMPLE_PROPERTY_ID,
                name = "Example Property — Safe to Explore",
                propertyType = "Farm",
                addressLine1 = "123 Green Pasture Lane",
                city = "Meadowville",
                stateOrRegion = "OR",
                postalCode = "97000",
                acreage = 15.5,
                description = "This is a demonstration property showing Mapstead's capabilities. All data is fictional.",
                latitude = 44.0521,
                longitude = -123.0868,
                createdAt = now,
                updatedAt = now
            ))

            // 3. Create Plan
            database.planDao().insertPlan(PlanEntity(
                id = EXAMPLE_PLAN_ID,
                propertyId = EXAMPLE_PROPERTY_ID,
                name = "Main Layout",
                planType = "EXTERIOR_MAP",
                backgroundType = "MAP",
                centerLatitude = 44.0521,
                centerLongitude = -123.0868,
                zoom = 17.5,
                createdAt = now,
                updatedAt = now
            ))

            // 4. Create Layers
            database.layerDao().insertLayer(LayerEntity(id = LAYER_STRUCTURES_ID, propertyId = EXAMPLE_PROPERTY_ID, planId = EXAMPLE_PLAN_ID, name = "Buildings & Boundaries", category = "Structure", displayOrder = 0, createdAt = now, updatedAt = now))
            database.layerDao().insertLayer(LayerEntity(id = LAYER_UTILITIES_ID, propertyId = EXAMPLE_PROPERTY_ID, planId = EXAMPLE_PLAN_ID, name = "Utilities", category = "Utility", displayOrder = 1, createdAt = now, updatedAt = now))
            database.layerDao().insertLayer(LayerEntity(id = LAYER_OUTDOOR_ID, propertyId = EXAMPLE_PROPERTY_ID, planId = EXAMPLE_PLAN_ID, name = "Outdoor Features", category = "Landscape", displayOrder = 2, createdAt = now, updatedAt = now))

            // 5. Create System Items
            database.infrastructureDao().insertItem(InfrastructureItemEntity(id = WELL_ITEM_ID, propertyId = EXAMPLE_PROPERTY_ID, name = "Deep Well Pump", category = "Utility", subtype = "Water", status = "Active", manufacturer = "Goulds", model = "5GS05", serialNumber = "SN-998877", notes = "Primary water source for the property.", createdAt = now, updatedAt = now))
            database.infrastructureDao().insertItem(InfrastructureItemEntity(id = PANEL_ITEM_ID, propertyId = EXAMPLE_PROPERTY_ID, name = "Main Electrical Panel", category = "Utility", subtype = "Electric", status = "Active", manufacturer = "Square D", notes = "Located on the north exterior wall of the house.", createdAt = now, updatedAt = now))
            database.infrastructureDao().insertItem(InfrastructureItemEntity(id = GATE_ITEM_ID, propertyId = EXAMPLE_PROPERTY_ID, name = "Front Security Gate", category = "Access", status = "Active", notes = "Automated gate with keypad.", createdAt = now, updatedAt = now))

            // 6. Create Features
            database.mapFeatureDao().insertFeature(MapFeatureEntity(id = WELL_FEATURE_ID, propertyId = EXAMPLE_PROPERTY_ID, planId = EXAMPLE_PLAN_ID, layerId = LAYER_UTILITIES_ID, infrastructureItemId = WELL_ITEM_ID, geometryType = "POINT", geometryJson = GeometryUtils.buildPointGeoJson(-123.0865, 44.0523), coordinateSpace = "GEOGRAPHIC", styleJson = "{\"preset_style\":\"utility_point\"}", label = "Well", accuracySource = "Manual", createdAt = now, updatedAt = now))
            database.mapFeatureDao().insertFeature(MapFeatureEntity(id = PANEL_FEATURE_ID, propertyId = EXAMPLE_PROPERTY_ID, planId = EXAMPLE_PLAN_ID, layerId = LAYER_UTILITIES_ID, infrastructureItemId = PANEL_ITEM_ID, geometryType = "POINT", geometryJson = GeometryUtils.buildPointGeoJson(-123.0868, 44.0520), coordinateSpace = "GEOGRAPHIC", styleJson = "{\"preset_style\":\"utility_point\"}", label = "Electrical Panel", accuracySource = "Manual", createdAt = now, updatedAt = now))
            database.mapFeatureDao().insertFeature(MapFeatureEntity(id = GATE_FEATURE_ID, propertyId = EXAMPLE_PROPERTY_ID, planId = EXAMPLE_PLAN_ID, layerId = LAYER_STRUCTURES_ID, infrastructureItemId = GATE_ITEM_ID, geometryType = "POINT", geometryJson = GeometryUtils.buildPointGeoJson(-123.0872, 44.0528), coordinateSpace = "GEOGRAPHIC", styleJson = "{\"preset_style\":\"gate\"}", label = "Front Gate", accuracySource = "Manual", createdAt = now, updatedAt = now))

            val waterLineCoords = listOf(Pair(-123.0865, 44.0523), Pair(-123.0868, 44.0520))
            database.mapFeatureDao().insertFeature(MapFeatureEntity(id = WATER_LINE_ID, propertyId = EXAMPLE_PROPERTY_ID, planId = EXAMPLE_PLAN_ID, layerId = LAYER_UTILITIES_ID, geometryType = "LINESTRING", geometryJson = GeometryUtils.buildLineStringGeoJson(waterLineCoords), coordinateSpace = "GEOGRAPHIC", styleJson = "{\"preset_style\":\"utility_route\"}", label = "Main Water Line", accuracySource = "Manual", createdAt = now, updatedAt = now))

            val fenceCoords = listOf(Pair(-123.0870, 44.0525), Pair(-123.0860, 44.0525))
            database.mapFeatureDao().insertFeature(MapFeatureEntity(id = FENCE_LINE_ID, propertyId = EXAMPLE_PROPERTY_ID, planId = EXAMPLE_PLAN_ID, layerId = LAYER_STRUCTURES_ID, geometryType = "LINESTRING", geometryJson = GeometryUtils.buildLineStringGeoJson(fenceCoords), coordinateSpace = "GEOGRAPHIC", styleJson = "{\"preset_style\":\"fence\"}", label = "North Fence", accuracySource = "Manual", createdAt = now, updatedAt = now))

            val houseCoords = listOf(Pair(-123.0869, 44.0520), Pair(-123.0867, 44.0520), Pair(-123.0867, 44.0518), Pair(-123.0869, 44.0518))
            database.mapFeatureDao().insertFeature(MapFeatureEntity(id = HOUSE_AREA_ID, propertyId = EXAMPLE_PROPERTY_ID, planId = EXAMPLE_PLAN_ID, layerId = LAYER_STRUCTURES_ID, geometryType = "POLYGON", geometryJson = GeometryUtils.buildPolygonGeoJson(houseCoords), coordinateSpace = "GEOGRAPHIC", styleJson = "{\"preset_style\":\"building\"}", label = "Main House", accuracySource = "Manual", createdAt = now, updatedAt = now))

            val boundCoords = listOf(Pair(-123.0875, 44.0530), Pair(-123.0855, 44.0530), Pair(-123.0855, 44.0510), Pair(-123.0875, 44.0510))
            database.mapFeatureDao().insertFeature(MapFeatureEntity(id = BOUNDARY_AREA_ID, propertyId = EXAMPLE_PROPERTY_ID, planId = EXAMPLE_PLAN_ID, layerId = LAYER_STRUCTURES_ID, geometryType = "POLYGON", geometryJson = GeometryUtils.buildPolygonGeoJson(boundCoords), coordinateSpace = "GEOGRAPHIC", styleJson = "{\"preset_style\":\"property_boundary\"}", label = "Property Boundary", accuracySource = "Manual", createdAt = now, updatedAt = now))

            // 7. Relationships
            database.itemRelationshipDao().insertRelationship(ItemRelationshipEntity(id = EXAMPLE_RELATIONSHIP_ID, propertyId = EXAMPLE_PROPERTY_ID, sourceItemId = WELL_ITEM_ID, targetItemId = PANEL_ITEM_ID, relationshipType = "DEPENDS_ON", createdAt = now, updatedAt = now))

            // 8. Maintenance & Reminders
            database.maintenanceDao().insertRecord(MaintenanceRecordEntity(id = EXAMPLE_MAINTENANCE_ID, propertyId = EXAMPLE_PROPERTY_ID, infrastructureItemId = WELL_ITEM_ID, title = "Filter Replacement", category = "Maintenance", serviceDate = LocalDate.now().minusMonths(1), nextDueDate = LocalDate.now().plusMonths(5), status = "Completed", provider = "Pure Water Co.", cost = 85.0, createdAt = now, updatedAt = now))
            
            database.maintenanceDao().insertReminder(ReminderEntity(id = EXAMPLE_REMINDER_ID, propertyId = EXAMPLE_PROPERTY_ID, maintenanceRecordId = EXAMPLE_MAINTENANCE_ID, infrastructureItemId = WELL_ITEM_ID, title = "Check Well Filter", description = "Standard example reminder (notifications disabled).", dueDate = LocalDate.now().plusMonths(5), enabled = false, createdAt = now, updatedAt = now))
        }
    }
}
