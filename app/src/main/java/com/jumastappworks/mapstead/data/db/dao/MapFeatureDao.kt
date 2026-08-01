package com.jumastappworks.mapstead.data.db.dao

import androidx.room.*
import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
interface MapFeatureDao {
    @Query("SELECT * FROM map_features WHERE layerId = :layerId AND deletedAt IS NULL")
    fun getFeaturesForLayer(layerId: UUID): Flow<List<MapFeatureEntity>>

    @Query("SELECT * FROM map_features WHERE propertyId = :propertyId AND deletedAt IS NULL")
    fun getFeaturesForProperty(propertyId: UUID): Flow<List<MapFeatureEntity>>

    @Query("SELECT * FROM map_features WHERE infrastructureItemId = :itemId AND deletedAt IS NULL")
    fun getFeaturesForItem(itemId: UUID): Flow<List<MapFeatureEntity>>

    @Query("SELECT * FROM map_features WHERE planId = :planId AND deletedAt IS NULL")
    fun getFeaturesForPlan(planId: UUID): Flow<List<MapFeatureEntity>>

    @Query("SELECT * FROM map_features WHERE id = :id AND deletedAt IS NULL")
    suspend fun getFeatureById(id: UUID): MapFeatureEntity?

    @Query("SELECT * FROM map_features WHERE propertyId = :propertyId AND infrastructureItemId = :itemId AND deletedAt IS NULL ORDER BY updatedAt DESC")
    suspend fun getFeaturesForItemOnce(propertyId: UUID, itemId: UUID): List<MapFeatureEntity>

    @Insert
    suspend fun insertFeature(feature: MapFeatureEntity): Long

    @Update
    suspend fun updateFeature(feature: MapFeatureEntity)

    @Upsert
    suspend fun upsertFeature(feature: MapFeatureEntity)

    @Query("UPDATE map_features SET deletedAt = :deletedAt, updatedAt = :updatedAt, revision = revision + 1 WHERE id = :id AND propertyId = :propertyId AND planId = :planId AND deletedAt IS NULL")
    suspend fun softDeletePropertyPlanFeature(propertyId: UUID, planId: UUID, id: UUID, deletedAt: Instant = Instant.now(), updatedAt: Instant = Instant.now()): Int

    @Query("SELECT * FROM map_features ORDER BY createdAt ASC")
    suspend fun getAllFeaturesOnce(): List<MapFeatureEntity>

    @Query("DELETE FROM map_features")
    suspend fun clearAll()
}
