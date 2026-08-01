package com.jumastappworks.mapstead.data.repository

import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.db.dao.MaintenanceDao
import com.jumastappworks.mapstead.data.db.entities.MaintenanceRecordEntity
import com.jumastappworks.mapstead.data.work.ReminderScheduler
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class MaintenanceRepositoryReliabilityTest {

    private val database = mockk<MapsteadDatabase>(relaxed = true)
    private val dao = mockk<MaintenanceDao>(relaxed = true)
    private val scheduler = mockk<ReminderScheduler>(relaxed = true)
    private lateinit var repository: MaintenanceRepository

    private val propertyId = UUID.randomUUID()
    private val recordId = UUID.randomUUID()

    @Before
    fun setup() {
        every { database.maintenanceDao() } returns dao
        every { database.runInTransaction(any<Runnable>()) } answers {
            val runnable = it.invocation.args[0] as Runnable
            runnable.run()
        }
        repository = MaintenanceRepository(database, scheduler)
    }

    @Test
    fun `completeMaintenanceRecord succeeds despite WorkManager failure`() = runTest {
        val record = MaintenanceRecordEntity(
            id = recordId,
            propertyId = propertyId,
            title = "Test",
            category = "Cat",
            serviceDate = LocalDate.now(),
            status = "Scheduled"
        )
        
        val linkedReminder = com.jumastappworks.mapstead.data.db.entities.ReminderEntity(
            id = UUID.randomUUID(),
            propertyId = propertyId,
            maintenanceRecordId = recordId,
            title = "R1",
            dueDate = LocalDate.now(),
            enabled = true
        )
        
        every { dao.getRecordByIdOnceSync(recordId) } returns record
        every { dao.getRemindersForRecordOnceSync(recordId) } returns listOf(linkedReminder)
        every { dao.updateRecordSync(any()) } just Runs
        every { dao.completeReminderSync(any(), any(), any()) } just Runs
        
        // Simulate WM failure
        every { scheduler.cancelReminder(any()) } throws RuntimeException("WM Error")

        val result = repository.completeMaintenanceRecord(propertyId, recordId, LocalDate.now())

        assertTrue("Result should be SuccessWithSchedulingWarning", 
            result is MaintenanceWriteResult.SuccessWithSchedulingWarning)
        
        verify { dao.updateRecordSync(any()) }
    }

    @Test
    fun `deleteRecordForProperty rejects cross property access`() = runTest {
        val otherPropertyId = UUID.randomUUID()
        val record = MaintenanceRecordEntity(
            id = recordId,
            propertyId = propertyId,
            title = "Test",
            category = "Cat",
            serviceDate = LocalDate.now(),
            status = "Scheduled"
        )

        coEvery { dao.getRecordByIdOnce(recordId) } returns record

        val result = repository.deleteRecordForProperty(otherPropertyId, recordId)

        assertTrue("Result should be OwnershipMismatch", result is MaintenanceWriteResult.OwnershipMismatch)
    }

    @Test
    fun `saveReminderForProperty falls back to disabled when scheduling fails`() = runTest {
        val reminder = com.jumastappworks.mapstead.data.db.entities.ReminderEntity(
            id = UUID.randomUUID(),
            propertyId = propertyId,
            title = "R1",
            dueDate = LocalDate.now(),
            enabled = true
        )

        coEvery { dao.getReminderByIdOnce(reminder.id) } returns reminder
        every { scheduler.scheduleReminder(any()) } throws RuntimeException("WM Error")

        val result = repository.saveReminderForProperty(propertyId, reminder)

        assertTrue("Result should be SavedDisabledAfterSchedulingFailure", result is MaintenanceWriteResult.SavedDisabledAfterSchedulingFailure)
        coVerify { dao.updateReminder(match { !it.enabled }) }
    }

    @Test
    fun `softDeleteReminderForProperty returns warning when cancellation fails`() = runTest {
        val reminderId = UUID.randomUUID()
        val reminder = com.jumastappworks.mapstead.data.db.entities.ReminderEntity(
            id = reminderId,
            propertyId = propertyId,
            title = "R1",
            dueDate = LocalDate.now(),
            enabled = true
        )

        coEvery { dao.getReminderByIdOnce(reminderId) } returns reminder
        every { scheduler.cancelReminder(any()) } throws RuntimeException("WM Error")

        val result = repository.softDeleteReminderForProperty(propertyId, reminderId)

        assertTrue("Result should be SuccessWithSchedulingWarning", result is MaintenanceWriteResult.SuccessWithSchedulingWarning)
        coVerify { dao.softDeleteReminder(reminderId, any(), any()) }
    }
}
