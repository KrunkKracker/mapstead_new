package com.jumastappworks.mapstead.data.db.dao

import androidx.room.*
import com.jumastappworks.mapstead.data.db.entities.AttachmentEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE deletedAt IS NULL")
    fun getAllAttachments(): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE propertyId = :propertyId AND deletedAt IS NULL")
    fun getAttachmentsForProperty(propertyId: UUID): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE propertyId = :propertyId AND infrastructureItemId IS NULL AND maintenanceRecordId IS NULL AND mapFeatureId IS NULL AND deletedAt IS NULL")
    fun getPropertyLevelAttachments(propertyId: UUID): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE propertyId = :propertyId AND infrastructureItemId = :itemId AND deletedAt IS NULL")
    fun getAttachmentsForInfrastructureItem(propertyId: UUID, itemId: UUID): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE propertyId = :propertyId AND maintenanceRecordId = :recordId AND deletedAt IS NULL")
    fun getAttachmentsForMaintenanceRecord(propertyId: UUID, recordId: UUID): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE propertyId = :propertyId AND mapFeatureId = :featureId AND deletedAt IS NULL")
    fun getAttachmentsForMapFeature(propertyId: UUID, featureId: UUID): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE propertyId = :propertyId AND mapFeatureId = :featureId AND deletedAt IS NULL")
    suspend fun getAttachmentsForMapFeatureOnce(propertyId: UUID, featureId: UUID): List<AttachmentEntity>

    @Query("SELECT COUNT(*) FROM attachments WHERE propertyId = :propertyId AND deletedAt IS NULL")
    suspend fun getCountForProperty(propertyId: UUID): Int

    @Query("SELECT COUNT(*) FROM attachments WHERE infrastructureItemId = :itemId AND deletedAt IS NULL")
    suspend fun getCountForInfrastructureItem(itemId: UUID): Int

    @Query("SELECT COUNT(*) FROM attachments WHERE maintenanceRecordId = :recordId AND deletedAt IS NULL")
    suspend fun getCountForMaintenanceRecord(recordId: UUID): Int

    @Query("SELECT COUNT(*) FROM attachments WHERE propertyId = :propertyId AND mapFeatureId = :featureId AND deletedAt IS NULL")
    suspend fun getCountForMapFeature(propertyId: UUID, featureId: UUID): Int

    @Query("UPDATE attachments SET isCover = 0, updatedAt = :now, revision = revision + 1 WHERE propertyId = :propertyId AND mapFeatureId = :featureId AND isCover = 1 AND deletedAt IS NULL")
    suspend fun clearFeatureCover(propertyId: UUID, featureId: UUID, now: Instant = Instant.now()): Int

    @Query("UPDATE attachments SET isCover = 1, updatedAt = :now, revision = revision + 1 WHERE id = :attachmentId AND propertyId = :propertyId AND mapFeatureId = :featureId AND deletedAt IS NULL")
    suspend fun setFeatureCover(propertyId: UUID, featureId: UUID, attachmentId: UUID, now: Instant = Instant.now()): Int

    @Query("SELECT * FROM attachments WHERE id = :id AND deletedAt IS NULL")
    suspend fun getAttachmentById(id: UUID): AttachmentEntity?

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun getAttachmentByIdIncludingDeleted(id: UUID): AttachmentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAttachment(attachment: AttachmentEntity): Long

    @Update
    suspend fun updateAttachment(attachment: AttachmentEntity): Int

    @Query("UPDATE attachments SET deletedAt = :deletedAt, updatedAt = :updatedAt, revision = revision + 1 WHERE id = :id AND propertyId = :propertyId AND deletedAt IS NULL")
    suspend fun softDeletePropertyAttachment(propertyId: UUID, id: UUID, deletedAt: Instant = Instant.now(), updatedAt: Instant = Instant.now()): Int

    @Query("SELECT * FROM attachments ORDER BY createdAt ASC")
    suspend fun getAllAttachmentsOnceIncludingDeleted(): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE deletedAt IS NULL ORDER BY createdAt ASC")
    suspend fun getAllAttachmentsOnce(): List<AttachmentEntity>

    @Query("DELETE FROM attachments")
    suspend fun clearAll()

    @Upsert
    suspend fun upsertAttachment(attachment: AttachmentEntity)

    @Query("SELECT COUNT(*) FROM attachments")
    suspend fun getCount(): Int
}
