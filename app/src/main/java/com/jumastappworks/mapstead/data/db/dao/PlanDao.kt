package com.jumastappworks.mapstead.data.db.dao

import androidx.room.*
import com.jumastappworks.mapstead.data.db.entities.PlanEntity
import com.jumastappworks.mapstead.data.db.entities.LayerEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
abstract class PlanDao {
    @Query("SELECT * FROM plans WHERE propertyId = :propertyId AND deletedAt IS NULL ORDER BY displayOrder ASC")
    abstract fun getPlansForProperty(propertyId: UUID): Flow<List<PlanEntity>>

    @Insert
    abstract suspend fun insertPlan(plan: PlanEntity): Long

    @Query("UPDATE plans SET centerLatitude = :lat, centerLongitude = :lng, zoom = :zoom, bearing = :bearing, updatedAt = :updatedAt, revision = revision + 1 WHERE id = :id")
    abstract suspend fun updateCamera(id: UUID, lat: Double, lng: Double, zoom: Double, bearing: Double, updatedAt: Instant)

    @Update
    abstract suspend fun updatePlan(plan: PlanEntity)

    @Upsert
    abstract suspend fun upsertPlan(plan: PlanEntity)

    @Query("UPDATE plans SET deletedAt = :deletedAt, updatedAt = :updatedAt, revision = revision + 1 WHERE id = :id")
    abstract suspend fun softDeletePlan(id: UUID, deletedAt: Instant = Instant.now(), updatedAt: Instant = Instant.now())

    @Insert
    abstract suspend fun insertLayer(layer: LayerEntity): Long

    @Transaction
    open suspend fun createPlanWithDefaultLayerTx(plan: PlanEntity, defaultLayer: LayerEntity): UUID {
        insertPlan(plan)
        insertLayer(defaultLayer)
        return plan.id
    }

    @Query("SELECT * FROM plans WHERE id = :id AND deletedAt IS NULL")
    abstract suspend fun getPlanByIdOnce(id: UUID): PlanEntity?

    @Query("SELECT * FROM plans ORDER BY createdAt ASC")
    abstract suspend fun getAllPlansOnce(): List<PlanEntity>

    @Query("DELETE FROM plans")
    abstract suspend fun clearAll()
    @Query("SELECT COUNT(*) FROM plans")
    abstract suspend fun getCount(): Int
}
