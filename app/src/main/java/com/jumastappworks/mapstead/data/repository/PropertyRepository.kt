package com.jumastappworks.mapstead.data.repository

import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface PropertyRepository {
    fun getAllProperties(): Flow<List<PropertyEntity>>
    fun getArchivedProperties(): Flow<List<PropertyEntity>>
    suspend fun getPropertyById(id: UUID): PropertyEntity?
    suspend fun insertProperty(property: PropertyEntity)
    suspend fun updateProperty(property: PropertyEntity)
    suspend fun softDeleteProperty(id: UUID)
    suspend fun hardDeleteProperty(property: PropertyEntity)
    suspend fun archiveProperty(id: UUID)
    suspend fun restoreProperty(id: UUID)
    suspend fun insertPropertyWithDefaultMap(property: PropertyEntity, mapName: String): UUID
    suspend fun updatePropertyLocationWithOptionalFirstMap(propertyId: UUID, latitude: Double, longitude: Double, createFirstMap: Boolean): Result<Unit>
}
