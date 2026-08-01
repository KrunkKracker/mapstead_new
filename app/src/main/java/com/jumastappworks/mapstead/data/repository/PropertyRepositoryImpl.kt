package com.jumastappworks.mapstead.data.repository

import androidx.room.withTransaction
import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.db.dao.PropertyDao
import com.jumastappworks.mapstead.data.db.entities.LayerEntity
import com.jumastappworks.mapstead.data.db.entities.PlanEntity
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PropertyRepositoryImpl @Inject constructor(
    private val propertyDao: PropertyDao,
    private val database: MapsteadDatabase
) : PropertyRepository {

    override fun getAllProperties(): Flow<List<PropertyEntity>> = propertyDao.getAllProperties()

    override fun getArchivedProperties(): Flow<List<PropertyEntity>> = propertyDao.getArchivedProperties()
    
    override suspend fun getPropertyById(id: UUID): PropertyEntity? = propertyDao.getPropertyById(id)
    
    override suspend fun insertProperty(property: PropertyEntity) {
        propertyDao.insertProperty(property.copy(
            updatedAt = Instant.now()
        ))
    }
    
    override suspend fun updateProperty(property: PropertyEntity) {
        propertyDao.updateProperty(property.copy(
            updatedAt = Instant.now(),
            revision = property.revision + 1
        ))
    }
    
    override suspend fun softDeleteProperty(id: UUID) {
        propertyDao.softDeleteProperty(id, Instant.now(), Instant.now())
    }

    override suspend fun hardDeleteProperty(property: PropertyEntity) = propertyDao.hardDeleteProperty(property)

    override suspend fun archiveProperty(id: UUID) {
        val property = propertyDao.getPropertyById(id) ?: return
        propertyDao.updateProperty(property.copy(
            isArchived = true,
            updatedAt = Instant.now(),
            revision = property.revision + 1
        ))
    }

    override suspend fun restoreProperty(id: UUID) {
        val property = propertyDao.getPropertyById(id) ?: return
        propertyDao.updateProperty(property.copy(
            isArchived = false,
            updatedAt = Instant.now(),
            revision = property.revision + 1
        ))
    }

    override suspend fun insertPropertyWithDefaultMap(property: PropertyEntity, mapName: String): UUID {
        database.withTransaction {
            val now = Instant.now()
            val existing = propertyDao.getPropertyById(property.id)
            val propToSave = if (existing != null) {
                existing.copy(
                    name = property.name,
                    propertyType = property.propertyType,
                    latitude = property.latitude,
                    longitude = property.longitude,
                    updatedAt = now,
                    revision = existing.revision + 1
                )
            } else {
                property.copy(createdAt = now, updatedAt = now, revision = 1L)
            }
            propertyDao.upsertProperty(propToSave)

            val lat = property.latitude
            val lng = property.longitude

            if (lat != null && lng != null) {
                val plans = database.planDao().getAllPlansOnce().filter { it.propertyId == property.id && it.deletedAt == null }
                if (plans.isEmpty()) {
                    val planId = UUID.randomUUID()
                    val plan = PlanEntity(
                        id = planId,
                        propertyId = property.id,
                        name = mapName,
                        planType = "Site Map",
                        backgroundType = "MAP",
                        centerLatitude = lat,
                        centerLongitude = lng,
                        zoom = 17.0,
                        bearing = 0.0,
                        createdAt = now,
                        updatedAt = now,
                        revision = 1L
                    )
                    database.planDao().insertPlan(plan)

                    val layerId = UUID.randomUUID()
                    val layer = LayerEntity(
                        id = layerId,
                        propertyId = property.id,
                        planId = planId,
                        name = "Primary Features",
                        category = "Structure",
                        isVisible = true,
                        isLocked = false,
                        displayOrder = 0,
                        opacity = 1.0f,
                        createdAt = now,
                        updatedAt = now,
                        revision = 1L
                    )
                    database.layerDao().insertLayer(layer)
                }
            }
        }
        return property.id
    }

    override suspend fun updatePropertyLocationWithOptionalFirstMap(
        propertyId: UUID,
        latitude: Double,
        longitude: Double,
        createFirstMap: Boolean
    ): Result<Unit> = runCatching {
        if (!latitude.isFinite() || latitude < -90.0 || latitude > 90.0 ||
            !longitude.isFinite() || longitude < -180.0 || longitude > 180.0) {
            throw IllegalArgumentException("Invalid coordinates")
        }

        database.withTransaction {
            val property = propertyDao.getPropertyById(propertyId) 
                ?: throw IllegalStateException("Property not found")
            
            val now = Instant.now()
            val updatedProp = property.copy(
                latitude = latitude,
                longitude = longitude,
                updatedAt = now,
                revision = property.revision + 1
            )
            propertyDao.updateProperty(updatedProp)

            if (createFirstMap) {
                val plans = database.planDao().getAllPlansOnce().filter { it.propertyId == propertyId && it.deletedAt == null }
                if (plans.isEmpty()) {
                    val planId = UUID.randomUUID()
                    val plan = PlanEntity(
                        id = planId,
                        propertyId = propertyId,
                        name = "Property Map",
                        planType = "Site Map",
                        backgroundType = "MAP",
                        centerLatitude = latitude,
                        centerLongitude = longitude,
                        zoom = 17.0,
                        bearing = 0.0,
                        createdAt = now,
                        updatedAt = now,
                        revision = 1L
                    )
                    database.planDao().insertPlan(plan)

                    val layerId = UUID.randomUUID()
                    val layer = LayerEntity(
                        id = layerId,
                        propertyId = propertyId,
                        planId = planId,
                        name = "Primary Features",
                        category = "Structure",
                        isVisible = true,
                        isLocked = false,
                        displayOrder = 0,
                        opacity = 1.0f,
                        createdAt = now,
                        updatedAt = now,
                        revision = 1L
                    )
                    database.layerDao().insertLayer(layer)
                }
            }
        }
    }
}
