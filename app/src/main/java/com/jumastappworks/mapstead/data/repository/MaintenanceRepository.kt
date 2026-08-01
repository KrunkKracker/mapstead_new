package com.jumastappworks.mapstead.data.repository

import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.db.entities.MaintenanceRecordEntity
import com.jumastappworks.mapstead.data.db.entities.ReminderEntity
import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.data.work.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MaintenanceRepository @Inject constructor(
    private val database: MapsteadDatabase,
    private val reminderScheduler: ReminderScheduler
) {
    fun getRecordsForProperty(propertyId: UUID): Flow<List<MaintenanceRecordEntity>> =
        database.maintenanceDao().getRecordsForProperty(propertyId)

    fun getRecordsForItem(itemId: UUID): Flow<List<MaintenanceRecordEntity>> =
        database.maintenanceDao().getRecordsForItem(itemId)

    fun getRecordById(id: UUID): Flow<MaintenanceRecordEntity?> =
        database.maintenanceDao().getRecordById(id)

    suspend fun getRecordByIdOnce(id: UUID): MaintenanceRecordEntity? =
        database.maintenanceDao().getRecordByIdOnce(id)

    suspend fun getRecordForProperty(propertyId: UUID, recordId: UUID): MaintenanceRecordEntity? {
        val record = database.maintenanceDao().getRecordByIdOnce(recordId)
        return if (record?.propertyId == propertyId && record.deletedAt == null) record else null
    }

    suspend fun saveRecordForProperty(propertyId: UUID, record: MaintenanceRecordEntity): MaintenanceWriteResult {
        return try {
            if (record.propertyId != propertyId) return MaintenanceWriteResult.OwnershipMismatch
            
            // Validate linked item ownership
            record.infrastructureItemId?.let { itemId ->
                val item = database.infrastructureDao().getItemById(itemId)
                    ?: return MaintenanceWriteResult.InvalidLink
                if (item.propertyId != propertyId || item.deletedAt != null) {
                    return MaintenanceWriteResult.OwnershipMismatch
                }
            }

            val existing = database.maintenanceDao().getRecordByIdOnce(record.id)
            if (existing != null) {
                if (existing.propertyId != propertyId || existing.deletedAt != null) {
                    return MaintenanceWriteResult.OwnershipMismatch
                }
                val updated = record.copy(
                    createdAt = existing.createdAt,
                    updatedAt = Instant.now(),
                    revision = existing.revision + 1
                )
                database.maintenanceDao().updateRecord(updated)
            } else {
                database.maintenanceDao().insertRecord(
                    record.copy(
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                        revision = 1L
                    )
                )
            }
            MaintenanceWriteResult.Success(record.id)
        } catch (e: Exception) {
            MaintenanceWriteResult.Error(e.message ?: "Save failed")
        }
    }

    suspend fun deleteRecordForProperty(propertyId: UUID, recordId: UUID): MaintenanceWriteResult {
        return try {
            val existing = database.maintenanceDao().getRecordByIdOnceSync(recordId)
                ?: return MaintenanceWriteResult.NotFound
            if (existing.propertyId != propertyId) return MaintenanceWriteResult.OwnershipMismatch

            val now = Instant.now()
            val reminders = database.maintenanceDao().getRemindersForRecordOnceSync(recordId)
            
            database.runInTransaction {
                database.maintenanceDao().softDeleteRecordSync(recordId, now, now)
                reminders.forEach { reminder ->
                    database.maintenanceDao().softDeleteReminderSync(reminder.id, now, now)
                }
            }
            
            var schedulingWarningRes: Int? = null
            reminders.forEach { reminder ->
                try {
                    reminderScheduler.cancelReminder(reminder.id)
                } catch (e: Exception) {
                    schedulingWarningRes = com.jumastappworks.mapstead.R.string.maintenance_reschedule_scheduling_warning
                }
            }
            
            if (schedulingWarningRes != null) {
                MaintenanceWriteResult.SuccessWithSchedulingWarning(recordId, schedulingWarningRes!!)
            } else {
                MaintenanceWriteResult.Success(recordId)
            }
        } catch (e: Exception) {
            MaintenanceWriteResult.Error(e.message ?: "Delete failed")
        }
    }

    suspend fun completeMaintenanceRecord(
        propertyId: UUID,
        recordId: UUID,
        completionDate: java.time.LocalDate
    ): MaintenanceWriteResult {
        return try {
            val existing = database.maintenanceDao().getRecordByIdOnceSync(recordId)
                ?: return MaintenanceWriteResult.NotFound
            if (existing.propertyId != propertyId || existing.deletedAt != null) {
                return MaintenanceWriteResult.OwnershipMismatch
            }

            val now = Instant.now()
            val updated = existing.copy(
                status = "Completed",
                serviceDate = completionDate,
                updatedAt = now,
                revision = existing.revision + 1
            )
            
            val reminders = database.maintenanceDao().getRemindersForRecordOnceSync(recordId)

            database.runInTransaction {
                database.maintenanceDao().updateRecordSync(updated)
                reminders.forEach { reminder ->
                    database.maintenanceDao().completeReminderSync(reminder.id, now, now)
                }
            }

            var schedulingWarningRes: Int? = null
            reminders.forEach { reminder ->
                try {
                    reminderScheduler.cancelReminder(reminder.id)
                } catch (e: Exception) {
                    schedulingWarningRes = com.jumastappworks.mapstead.R.string.maintenance_reschedule_scheduling_warning
                }
            }
            
            if (schedulingWarningRes != null) {
                MaintenanceWriteResult.SuccessWithSchedulingWarning(recordId, schedulingWarningRes!!)
            } else {
                MaintenanceWriteResult.Success(recordId)
            }
        } catch (e: Exception) {
            MaintenanceWriteResult.Error(e.message ?: "Completion failed")
        }
    }

    suspend fun rescheduleRecordForProperty(
        propertyId: UUID,
        recordId: UUID,
        newServiceDate: java.time.LocalDate,
        newNextDueDate: java.time.LocalDate?
    ): MaintenanceWriteResult {
        return try {
            val existing = database.maintenanceDao().getRecordByIdOnce(recordId)
                ?: return MaintenanceWriteResult.NotFound
            if (existing.propertyId != propertyId || existing.deletedAt != null) return MaintenanceWriteResult.OwnershipMismatch

            // Validate item if exists
            existing.infrastructureItemId?.let { itemId ->
                val item = database.infrastructureDao().getItemById(itemId)
                    ?: return MaintenanceWriteResult.InvalidLink
                if (item.propertyId != propertyId || item.deletedAt != null) {
                    return MaintenanceWriteResult.OwnershipMismatch
                }
            }

            val newRecord = existing.copy(
                id = UUID.randomUUID(),
                serviceDate = newServiceDate,
                nextDueDate = newNextDueDate,
                status = "Scheduled",
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                revision = 1L,
                deletedAt = null
            )
            
            database.maintenanceDao().insertRecord(newRecord)
            MaintenanceWriteResult.Success(newRecord.id)
        } catch (e: Exception) {
            MaintenanceWriteResult.Error(e.message ?: "Reschedule failed")
        }
    }

    suspend fun getLinkedFeatureForRecord(propertyId: UUID, recordId: UUID): MapFeatureEntity? {
        val record = getRecordForProperty(propertyId, recordId) ?: return null
        val itemId = record.infrastructureItemId ?: return null
        return database.mapFeatureDao().getFeaturesForItemOnce(propertyId, itemId).firstOrNull()
    }

    fun getAllActiveReminders(): Flow<List<ReminderEntity>> =
        database.maintenanceDao().getAllActiveReminders()

    fun getRemindersForProperty(propertyId: UUID): Flow<List<ReminderEntity>> =
        database.maintenanceDao().getRemindersForProperty(propertyId)

    fun getRemindersForRecord(recordId: UUID): Flow<List<ReminderEntity>> =
        database.maintenanceDao().getRemindersForRecord(recordId)

    suspend fun getReminderByIdOnce(id: UUID): ReminderEntity? =
        database.maintenanceDao().getReminderByIdOnce(id)

    suspend fun getReminderForProperty(propertyId: UUID, reminderId: UUID): ReminderEntity? {
        val reminder = database.maintenanceDao().getReminderByIdOnce(reminderId)
        return if (reminder?.propertyId == propertyId && reminder.deletedAt == null) reminder else null
    }

    suspend fun saveReminderForProperty(propertyId: UUID, reminder: ReminderEntity): MaintenanceWriteResult {
        return try {
            if (reminder.propertyId != propertyId) return MaintenanceWriteResult.OwnershipMismatch

            // Validate links
            reminder.maintenanceRecordId?.let { rid ->
                val record = database.maintenanceDao().getRecordByIdOnce(rid)
                if (record == null || record.propertyId != propertyId || record.deletedAt != null) {
                    return MaintenanceWriteResult.Error("Invalid record link")
                }
            }
            reminder.infrastructureItemId?.let { iid ->
                val item = database.infrastructureDao().getItemById(iid)
                if (item == null || item.propertyId != propertyId || item.deletedAt != null) {
                    return MaintenanceWriteResult.Error("Invalid item link")
                }
            }

            val existing = database.maintenanceDao().getReminderByIdOnce(reminder.id)
            if (existing != null) {
                if (existing.propertyId != propertyId || existing.deletedAt != null) {
                    return MaintenanceWriteResult.OwnershipMismatch
                }
                val updated = reminder.copy(
                    createdAt = existing.createdAt,
                    updatedAt = Instant.now(),
                    revision = existing.revision + 1
                )
                database.maintenanceDao().updateReminder(updated)
            } else {
                database.maintenanceDao().insertReminder(
                    reminder.copy(
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                        revision = 1L
                    )
                )
            }

            if (reminder.enabled && reminder.completedAt == null && reminder.deletedAt == null) {
                try {
                    reminderScheduler.scheduleReminder(reminder)
                } catch (e: Exception) {
                    // Fallback to disabled
                    val savedReminder = database.maintenanceDao().getReminderByIdOnce(reminder.id)
                    if (savedReminder != null) {
                        val disabled = savedReminder.copy(
                            enabled = false,
                            updatedAt = Instant.now(),
                            revision = savedReminder.revision + 1
                        )
                        database.maintenanceDao().updateReminder(disabled)
                        return MaintenanceWriteResult.SavedDisabledAfterSchedulingFailure(
                            reminder.id,
                            com.jumastappworks.mapstead.R.string.reminder_saved_disabled_scheduling_failure
                        )
                    }
                }
            } else {
                try {
                    reminderScheduler.cancelReminder(reminder.id)
                } catch (e: Exception) {
                    return MaintenanceWriteResult.SuccessWithSchedulingWarning(
                        reminder.id,
                        com.jumastappworks.mapstead.R.string.maintenance_reschedule_scheduling_warning
                    )
                }
            }

            MaintenanceWriteResult.Success(reminder.id)
        } catch (e: Exception) {
            MaintenanceWriteResult.Error(e.message ?: "Save failed")
        }
    }

    suspend fun softDeleteReminderForProperty(propertyId: UUID, reminderId: UUID): MaintenanceWriteResult {
        return try {
            val existing = database.maintenanceDao().getReminderByIdOnce(reminderId)
                ?: return MaintenanceWriteResult.NotFound
            if (existing.propertyId != propertyId) return MaintenanceWriteResult.OwnershipMismatch

            database.maintenanceDao().softDeleteReminder(reminderId, Instant.now(), Instant.now())
            
            try {
                reminderScheduler.cancelReminder(reminderId)
            } catch (e: Exception) {
                return MaintenanceWriteResult.SuccessWithSchedulingWarning(
                    reminderId,
                    com.jumastappworks.mapstead.R.string.maintenance_reschedule_scheduling_warning
                )
            }
            MaintenanceWriteResult.Success(reminderId)
        } catch (e: Exception) {
            MaintenanceWriteResult.Error(e.message ?: "Delete failed")
        }
    }
}
