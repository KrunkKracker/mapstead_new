package com.jumastappworks.mapstead.data.db.dao

import androidx.room.*
import com.jumastappworks.mapstead.data.db.entities.LayerEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
interface LayerDao {
    @Query("SELECT * FROM layers WHERE planId = :planId AND deletedAt IS NULL ORDER BY displayOrder ASC")
    fun getLayersForPlan(planId: UUID): Flow<List<LayerEntity>>

    @Insert
    suspend fun insertLayer(layer: LayerEntity): Long

    @Update
    suspend fun updateLayer(layer: LayerEntity)

    @Upsert
    suspend fun upsertLayer(layer: LayerEntity)

    @Query("UPDATE layers SET deletedAt = :deletedAt, updatedAt = :updatedAt, revision = revision + 1 WHERE id = :id")
    suspend fun softDeleteLayer(id: UUID, deletedAt: Instant = Instant.now(), updatedAt: Instant = Instant.now())

    @Query("SELECT * FROM layers WHERE id = :id AND deletedAt IS NULL")
    suspend fun getLayerById(id: UUID): LayerEntity?

    @Query("SELECT * FROM layers ORDER BY createdAt ASC")
    suspend fun getAllLayersOnce(): List<LayerEntity>

    @Query("DELETE FROM layers")
    suspend fun clearAll()
}
