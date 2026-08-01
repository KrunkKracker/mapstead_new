package com.jumastappworks.mapstead.data.db.dao

import androidx.room.*
import com.jumastappworks.mapstead.data.db.entities.MaintenanceRecordEntity
import com.jumastappworks.mapstead.data.db.entities.ReminderEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
interface MaintenanceDao {
    @Query("SELECT * FROM maintenance_records WHERE propertyId = :propertyId AND deletedAt IS NULL ORDER BY serviceDate DESC")
    fun getRecordsForProperty(propertyId: UUID): Flow<List<MaintenanceRecordEntity>>

    @Query("SELECT * FROM maintenance_records WHERE infrastructureItemId = :itemId AND deletedAt IS NULL ORDER BY serviceDate DESC")
    fun getRecordsForItem(itemId: UUID): Flow<List<MaintenanceRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecord(record: MaintenanceRecordEntity): Long

    @Update
    suspend fun updateRecord(record: MaintenanceRecordEntity)

    @Update
    fun updateRecordSync(record: MaintenanceRecordEntity)

    @Upsert
    suspend fun upsertRecord(record: MaintenanceRecordEntity)

    @Query("UPDATE maintenance_records SET deletedAt = :deletedAt, updatedAt = :updatedAt, revision = revision + 1 WHERE id = :id")
    suspend fun softDeleteRecord(id: UUID, deletedAt: Instant = Instant.now(), updatedAt: Instant = Instant.now())

    @Query("UPDATE maintenance_records SET deletedAt = :deletedAt, updatedAt = :updatedAt, revision = revision + 1 WHERE id = :id")
    fun softDeleteRecordSync(id: UUID, deletedAt: Instant, updatedAt: Instant)

    @Query("SELECT * FROM maintenance_records WHERE id = :id AND deletedAt IS NULL")
    fun getRecordById(id: UUID): Flow<MaintenanceRecordEntity?>

    @Query("SELECT * FROM maintenance_records WHERE id = :id")
    suspend fun getRecordByIdOnce(id: UUID): MaintenanceRecordEntity?

    @Query("SELECT * FROM maintenance_records WHERE id = :id")
    fun getRecordByIdOnceSync(id: UUID): MaintenanceRecordEntity?

    // Reminders
    @Query("SELECT * FROM reminders WHERE infrastructureItemId = :itemId AND completedAt IS NULL AND deletedAt IS NULL")
    fun getActiveRemindersForItem(itemId: UUID): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE completedAt IS NULL AND deletedAt IS NULL")
    fun getAllActiveReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE propertyId = :propertyId AND deletedAt IS NULL")
    fun getRemindersForProperty(propertyId: UUID): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id AND deletedAt IS NULL")
    suspend fun getReminderByIdOnce(id: UUID): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Update
    fun updateReminderSync(reminder: ReminderEntity)

    @Query("UPDATE reminders SET deletedAt = :deletedAt, updatedAt = :updatedAt, revision = revision + 1 WHERE id = :id")
    suspend fun softDeleteReminder(id: UUID, deletedAt: Instant = Instant.now(), updatedAt: Instant = Instant.now())

    @Query("UPDATE reminders SET deletedAt = :deletedAt, updatedAt = :updatedAt, revision = revision + 1 WHERE id = :id")
    fun softDeleteReminderSync(id: UUID, deletedAt: Instant, updatedAt: Instant)

    @Query("UPDATE reminders SET completedAt = :completedAt, updatedAt = :updatedAt, revision = revision + 1 WHERE id = :id")
    suspend fun completeReminder(id: UUID, completedAt: Instant = Instant.now(), updatedAt: Instant = Instant.now())

    @Query("UPDATE reminders SET completedAt = :completedAt, updatedAt = :updatedAt, revision = revision + 1 WHERE id = :id")
    fun completeReminderSync(id: UUID, completedAt: Instant, updatedAt: Instant)

    @Query("SELECT * FROM reminders WHERE maintenanceRecordId = :recordId AND deletedAt IS NULL")
    fun getRemindersForRecordOnceSync(recordId: UUID): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE maintenanceRecordId = :recordId AND deletedAt IS NULL")
    fun getRemindersForRecord(recordId: UUID): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM maintenance_records ORDER BY createdAt ASC")
    suspend fun getAllRecordsOnce(): List<MaintenanceRecordEntity>

    @Query("SELECT * FROM reminders ORDER BY createdAt ASC")
    suspend fun getAllRemindersOnce(): List<ReminderEntity>

    @Upsert
    suspend fun upsertReminder(reminder: ReminderEntity)

    @Query("DELETE FROM maintenance_records")
    suspend fun clearAllRecords()

    @Query("DELETE FROM reminders")
    suspend fun clearAllReminders()
}
