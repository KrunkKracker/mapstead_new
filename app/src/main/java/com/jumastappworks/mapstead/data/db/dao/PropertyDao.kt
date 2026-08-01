package com.jumastappworks.mapstead.data.db.dao

import androidx.room.*
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
interface PropertyDao {
    @Query("SELECT * FROM properties WHERE deletedAt IS NULL AND isArchived = 0 ORDER BY name ASC")
    fun getAllProperties(): Flow<List<PropertyEntity>>

    @Query("SELECT * FROM properties WHERE deletedAt IS NULL AND isArchived = 1 ORDER BY name ASC")
    fun getArchivedProperties(): Flow<List<PropertyEntity>>

    @Query("SELECT * FROM properties WHERE id = :id AND deletedAt IS NULL")
    suspend fun getPropertyById(id: UUID): PropertyEntity?

    @Insert
    suspend fun insertProperty(property: PropertyEntity): Long

    @Update
    suspend fun updateProperty(property: PropertyEntity)

    @Upsert
    suspend fun upsertProperty(property: PropertyEntity)

    @Query("UPDATE properties SET deletedAt = :deletedAt, updatedAt = :updatedAt, revision = revision + 1 WHERE id = :id")
    suspend fun softDeleteProperty(id: UUID, deletedAt: Instant = Instant.now(), updatedAt: Instant = Instant.now())

    @Delete
    suspend fun hardDeleteProperty(property: PropertyEntity)

    @Query("SELECT * FROM properties ORDER BY createdAt ASC")
    suspend fun getAllPropertiesOnce(): List<PropertyEntity>

    @Query("DELETE FROM properties")
    suspend fun clearAll()
    @Query("SELECT COUNT(*) FROM properties")
    suspend fun getCount(): Int
}
