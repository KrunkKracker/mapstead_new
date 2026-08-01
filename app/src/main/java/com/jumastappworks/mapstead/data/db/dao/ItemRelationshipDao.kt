package com.jumastappworks.mapstead.data.db.dao

import androidx.room.*
import com.jumastappworks.mapstead.data.db.entities.ItemRelationshipEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
interface ItemRelationshipDao {
    @Query("SELECT * FROM item_relationships WHERE deletedAt IS NULL")
    fun getAllRelationships(): Flow<List<ItemRelationshipEntity>>

    @Query("SELECT * FROM item_relationships WHERE propertyId = :propertyId AND deletedAt IS NULL")
    fun getRelationshipsForProperty(propertyId: UUID): Flow<List<ItemRelationshipEntity>>

    @Query("SELECT * FROM item_relationships WHERE propertyId = :propertyId AND (sourceItemId = :itemId OR targetItemId = :itemId) AND deletedAt IS NULL")
    fun getRelationshipsForItem(propertyId: UUID, itemId: UUID): Flow<List<ItemRelationshipEntity>>

    @Query("SELECT * FROM item_relationships WHERE propertyId = :propertyId AND sourceItemId = :itemId AND deletedAt IS NULL")
    fun getOutgoingRelationshipsForItem(propertyId: UUID, itemId: UUID): Flow<List<ItemRelationshipEntity>>

    @Query("SELECT * FROM item_relationships WHERE propertyId = :propertyId AND targetItemId = :itemId AND deletedAt IS NULL")
    fun getIncomingRelationshipsForItem(propertyId: UUID, itemId: UUID): Flow<List<ItemRelationshipEntity>>

    @Query("SELECT * FROM item_relationships WHERE id = :id AND propertyId = :propertyId AND deletedAt IS NULL")
    suspend fun getRelationshipById(propertyId: UUID, id: UUID): ItemRelationshipEntity?

    @Query("SELECT * FROM item_relationships WHERE propertyId = :propertyId AND sourceItemId = :sourceId AND targetItemId = :targetId AND relationshipType = :type AND deletedAt IS NULL")
    suspend fun findDirectionalDuplicate(propertyId: UUID, sourceId: UUID, targetId: UUID, type: String): ItemRelationshipEntity?

    @Query("SELECT * FROM item_relationships WHERE propertyId = :propertyId AND ((sourceItemId = :id1 AND targetItemId = :id2) OR (sourceItemId = :id2 AND targetItemId = :id1)) AND relationshipType = 'CONNECTED_TO' AND deletedAt IS NULL")
    suspend fun findSymmetricDuplicate(propertyId: UUID, id1: UUID, id2: UUID): ItemRelationshipEntity?

    @Query("SELECT * FROM item_relationships WHERE propertyId = :propertyId AND relationshipType = 'DEPENDS_ON' AND deletedAt IS NULL")
    suspend fun getActiveDependencies(propertyId: UUID): List<ItemRelationshipEntity>

    @Query("SELECT COUNT(*) FROM item_relationships WHERE propertyId = :propertyId AND (sourceItemId = :itemId OR targetItemId = :itemId) AND deletedAt IS NULL")
    suspend fun getCountForItem(propertyId: UUID, itemId: UUID): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRelationship(relationship: ItemRelationshipEntity): Long

    @Update
    suspend fun updateRelationship(relationship: ItemRelationshipEntity): Int

    @Delete
    suspend fun hardDeleteRelationship(relationship: ItemRelationshipEntity)

    @Query("UPDATE item_relationships SET deletedAt = :deletedAt, updatedAt = :updatedAt, revision = revision + 1 WHERE id = :id AND propertyId = :propertyId AND deletedAt IS NULL")
    suspend fun softDeleteRelationship(propertyId: UUID, id: UUID, deletedAt: Instant = Instant.now(), updatedAt: Instant = Instant.now()): Int

    @Query("SELECT * FROM item_relationships ORDER BY createdAt ASC")
    suspend fun getAllRelationshipsOnce(): List<ItemRelationshipEntity>

    @Query("DELETE FROM item_relationships")
    suspend fun clearAll()

    @Upsert
    suspend fun upsertRelationship(relationship: ItemRelationshipEntity)
}
