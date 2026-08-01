package com.jumastappworks.mapstead.data.work

import android.content.Context
import androidx.work.*
import com.jumastappworks.mapstead.data.db.entities.ReminderEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    fun scheduleReminder(reminder: ReminderEntity) {
        val zoneId = ZoneId.systemDefault()
        val dueMillis = reminder.dueDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val currentMillis = System.currentTimeMillis()
        
        val delay = (dueMillis - currentMillis).coerceAtLeast(1000L)

        val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(
                "reminder_id" to reminder.id.toString()
            ))
            .build()

        workManager.enqueueUniqueWork(
            "mapstead-maintenance-reminder-${reminder.id}",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun cancelReminder(reminderId: UUID) {
        workManager.cancelUniqueWork("mapstead-maintenance-reminder-$reminderId")
    }
}
