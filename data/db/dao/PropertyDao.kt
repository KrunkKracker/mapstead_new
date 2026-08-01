package com.jumastappworks.mapstead.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface PropertyDao {
    @Query("SELECT * FROM properties WHERE deletedAt IS NULL ORDER BY name ASC")
    fun getAllProperties(): Flow<List<PropertyEntity>>

    @Query("SELECT * FROM properties WHERE id = :id AND deletedAt IS NULL")
    suspend fun getPropertyById(id: UUID): PropertyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProperty(property: PropertyEntity)

    @Update
    suspend fun updateProperty(property: PropertyEntity)

    @Query("UPDATE properties SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDeleteProperty(id: UUID, deletedAt: java.time.Instant = java.time.Instant.now())

    @Delete
    suspend fun hardDeleteProperty(property: PropertyEntity)
}
