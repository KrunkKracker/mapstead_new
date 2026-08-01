package com.jumastappworks.mapstead.data.mapping

import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SampleDataLoader @Inject constructor(
    private val database: MapsteadDatabase,
    private val userPrefs: UserPreferencesRepository
) {
    companion object {
        val DEMO_PROPERTY_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
        val WATER_MAIN_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val ELECTRICAL_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
    }

    fun isDemoDataInstalled(): Flow<Boolean> {
        return database.propertyDao().getAllProperties().map { properties ->
            properties.any { it.id == DEMO_PROPERTY_ID }
        }
    }

    suspend fun loadSampleData() {
        val existingProperty = database.propertyDao().getPropertyById(DEMO_PROPERTY_ID)
        if (existingProperty == null) {
            database.propertyDao().insertProperty(
                PropertyEntity(
                    id = DEMO_PROPERTY_ID,
                    name = "Acme Fictional Farm", // clearly fictional data
                    propertyType = "Farm",
                    addressLine1 = "123 Fictional Lane",
                    city = "Fictional Ville",
                    stateOrRegion = "MT",
                    postalCode = "99999",
                    countryCode = "US",
                    latitude = 45.0,
                    longitude = -110.0,
                    description = "Fictional demo property."
                )
            )
        }

        // Only select demo data if there is no current real selection
        val prefs = userPrefs.userPreferencesFlow.first()
        val currentSelection = prefs.selectedPropertyId
        val selectDemo = if (currentSelection.isNullOrBlank() || currentSelection == DEMO_PROPERTY_ID.toString()) {
            true
        } else {
            // Check if selected property actually exists and is a real one
            try {
                val p = database.propertyDao().getPropertyById(UUID.fromString(currentSelection))
                p == null // If it doesn't exist, we can select demo
            } catch (e: Exception) {
                true
            }
        }

        if (selectDemo) {
            userPrefs.updateSelectedProperty(DEMO_PROPERTY_ID.toString())
        }

        // Insert items only if they don't already exist
        val existingWater = database.infrastructureDao().getItemById(WATER_MAIN_ID)
        if (existingWater == null) {
            database.infrastructureDao().insertItem(
                InfrastructureItemEntity(
                    id = WATER_MAIN_ID,
                    propertyId = DEMO_PROPERTY_ID,
                    parentItemId = null,
                    name = "Water Main Shutoff",
                    category = "Utility",
                    subtype = "Water",
                    status = "Active",
                    isEmergencyItem = true,
                    emergencyInstructions = "Turn valve 90 degrees clockwise to shut off water."
                )
            )
        }

        val existingElectrical = database.infrastructureDao().getItemById(ELECTRICAL_ID)
        if (existingElectrical == null) {
            database.infrastructureDao().insertItem(
                InfrastructureItemEntity(
                    id = ELECTRICAL_ID,
                    propertyId = DEMO_PROPERTY_ID,
                    parentItemId = null,
                    name = "Electrical Disconnect",
                    category = "Utility",
                    subtype = "Electric",
                    status = "Active",
                    isEmergencyItem = true,
                    emergencyInstructions = "Pull main lever down."
                )
            )
        }
    }

    suspend fun clearDemoData() {
        val prefs = userPrefs.userPreferencesFlow.first()
        if (prefs.selectedPropertyId == DEMO_PROPERTY_ID.toString()) {
            userPrefs.updateSelectedProperty(null)
        }
        database.propertyDao().getPropertyById(DEMO_PROPERTY_ID)?.let {
            database.propertyDao().hardDeleteProperty(it)
        }
        // Cascade delete on foreign key propertyId deletes items automatically, but let's be safe:
        database.infrastructureDao().getItemById(WATER_MAIN_ID)?.let {
            // softDeleteItem is the only delete method, we can use it or foreign key cascade handles hard delete
        }
    }
}
