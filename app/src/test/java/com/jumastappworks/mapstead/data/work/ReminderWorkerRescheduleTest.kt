package com.jumastappworks.mapstead.data.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.jumastappworks.mapstead.data.db.entities.ReminderEntity
import com.jumastappworks.mapstead.data.repository.MaintenanceRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class ReminderWorkerRescheduleTest {

    private val context = mockk<Context>(relaxed = true)
    private val params = mockk<WorkerParameters>(relaxed = true)
    private val notificationHelper = mockk<NotificationHelper>(relaxed = true)
    private val repository = mockk<MaintenanceRepository>(relaxed = true)
    private val scheduler = mockk<ReminderScheduler>(relaxed = true)

    private lateinit var worker: ReminderWorker

    @Before
    fun setup() {
        worker = ReminderWorker(context, params, notificationHelper, repository, scheduler)
        every { params.inputData.getString("reminder_id") } returns UUID.randomUUID().toString()
    }

    @Test
    fun `worker reschedules for future due date`() = runTest {
        val futureDate = LocalDate.now().plusDays(2)
        val reminder = ReminderEntity(
            id = UUID.randomUUID(),
            propertyId = UUID.randomUUID(),
            title = "Future",
            dueDate = futureDate,
            enabled = true
        )

        coEvery { repository.getReminderByIdOnce(any()) } returns reminder

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify { scheduler.scheduleReminder(reminder) }
        verify(exactly = 0) { notificationHelper.showMaintenanceNotification(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `worker notifies for due today date`() = runTest {
        val today = LocalDate.now()
        val reminder = ReminderEntity(
            id = UUID.randomUUID(),
            propertyId = UUID.randomUUID(),
            title = "Due Today",
            dueDate = today,
            enabled = true
        )

        coEvery { repository.getReminderByIdOnce(any()) } returns reminder

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify(exactly = 1) { notificationHelper.showMaintenanceNotification(any(), any(), any(), any(), any(), any()) }
    }
}
