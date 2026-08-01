package com.jumastappworks.mapstead.data.db.dao

import androidx.room.*
import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
interface InfrastructureDao {
    @Query("SELECT * FROM infrastructure_items WHERE propertyId = :propertyId AND deletedAt IS NULL")
    fun getItemsForProperty(propertyId: UUID): Flow<List<InfrastructureItemEntity>>

    @Query("SELECT * FROM infrastructure_items WHERE id = :id AND deletedAt IS NULL")
    suspend fun getItemById(id: UUID): InfrastructureItemEntity?

    @Query("SELECT * FROM infrastructure_items WHERE isEmergencyItem = 1 AND propertyId = :propertyId AND deletedAt IS NULL")
    fun getEmergencyItems(propertyId: UUID): Flow<List<InfrastructureItemEntity>>

    @Insert
    suspend fun insertItem(item: InfrastructureItemEntity): Long

    @Query("""
        UPDATE infrastructure_items 
        SET name = :name, 
            category = :category, 
            subtype = :subtype, 
            status = :status,
            manufacturer = :manufacturer,
            model = :model,
            serialNumber = :serialNumber,
            serviceProvider = :serviceProvider,
            phoneNumber = :phoneNumber,
            website = :website,
            instructions = :instructions,
            emergencyInstructions = :emergencyInstructions,
            notes = :notes,
            isEmergencyItem = :isEmergencyItem,
            updatedAt = :updatedAt,
            revision = revision + 1
        WHERE id = :id AND propertyId = :propertyId AND deletedAt IS NULL
    """)
    suspend fun updateActivePropertyItem(
        id: UUID,
        propertyId: UUID,
        name: String,
        category: String,
        subtype: String?,
        status: String,
        manufacturer: String?,
        model: String?,
        serialNumber: String?,
        serviceProvider: String?,
        phoneNumber: String?,
        website: String?,
        instructions: String?,
        emergencyInstructions: String?,
        notes: String?,
        isEmergencyItem: Boolean,
        updatedAt: Instant = Instant.now()
    ): Int

    @Upsert
    suspend fun upsertItem(item: InfrastructureItemEntity)

    @Query("UPDATE infrastructure_items SET deletedAt = :deletedAt, updatedAt = :updatedAt, revision = revision + 1 WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDeleteItem(id: UUID, deletedAt: Instant = Instant.now(), updatedAt: Instant = Instant.now())

    @Query("UPDATE infrastructure_items SET deletedAt = :deletedAt, updatedAt = :updatedAt, revision = revision + 1 WHERE id = :id AND propertyId = :propertyId AND deletedAt IS NULL")
    suspend fun softDeletePropertyItem(propertyId: UUID, id: UUID, deletedAt: Instant = Instant.now(), updatedAt: Instant = Instant.now()): Int

    @Query("SELECT * FROM infrastructure_items WHERE id = :id AND propertyId = :propertyId AND deletedAt IS NULL")
    suspend fun getActiveItemForProperty(propertyId: UUID, id: UUID): InfrastructureItemEntity?

    @Query("SELECT * FROM infrastructure_items WHERE propertyId = :propertyId AND parentItemId = :itemId AND deletedAt IS NULL")
    fun getChildrenForItem(propertyId: UUID, itemId: UUID): Flow<List<InfrastructureItemEntity>>

    @Query("UPDATE infrastructure_items SET parentItemId = :parentId, updatedAt = :updatedAt, revision = revision + 1 WHERE id = :itemId AND propertyId = :propertyId")
    suspend fun updateParent(propertyId: UUID, itemId: UUID, parentId: UUID?, updatedAt: Instant = Instant.now()): Int

    @Query("SELECT * FROM infrastructure_items ORDER BY createdAt ASC")
    suspend fun getAllItemsOnce(): List<InfrastructureItemEntity>

    @Query("DELETE FROM infrastructure_items")
    suspend fun clearAll()
}
