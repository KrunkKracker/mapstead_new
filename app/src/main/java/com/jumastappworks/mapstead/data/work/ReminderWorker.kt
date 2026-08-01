package com.jumastappworks.mapstead.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.jumastappworks.mapstead.data.repository.MaintenanceRepository
import java.time.LocalDate
import java.util.UUID

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notificationHelper: NotificationHelper,
    private val maintenanceRepository: com.jumastappworks.mapstead.data.repository.MaintenanceRepository,
    private val reminderScheduler: ReminderScheduler
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val reminderIdStr = inputData.getString("reminder_id") ?: return Result.failure()
        val reminderId = try { UUID.fromString(reminderIdStr) } catch (e: Exception) { return Result.failure() }

        val reminder = maintenanceRepository.getReminderByIdOnce(reminderId)
            ?: return Result.success()

        if (!reminder.enabled || reminder.deletedAt != null || reminder.completedAt != null) {
            return Result.success()
        }
        
        val today = LocalDate.now()
        if (reminder.dueDate.isAfter(today)) {
            // Authoritative rescheduling for moved future dates
            reminderScheduler.scheduleReminder(reminder)
            return Result.success()
        }

        // Authoritative data from DB
        notificationHelper.showMaintenanceNotification(
            title = reminder.title,
            message = reminder.description ?: "A maintenance task is due.",
            propertyId = reminder.propertyId.toString(),
            itemId = reminder.infrastructureItemId?.toString(),
            recordId = reminder.maintenanceRecordId?.toString(),
            reminderId = reminder.id.toString()
        )
        
        return Result.success()
    }
}
