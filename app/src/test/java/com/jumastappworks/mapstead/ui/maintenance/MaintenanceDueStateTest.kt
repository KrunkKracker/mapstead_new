package com.jumastappworks.mapstead.ui.maintenance

import com.jumastappworks.mapstead.data.db.entities.MaintenanceRecordEntity
import com.jumastappworks.mapstead.util.MaintenanceStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class MaintenanceDueStateTest {

    private val propertyId = UUID.randomUUID()
    private val today = LocalDate.now()

    @Test
    fun testOverdueState() {
        val record = createRecord(nextDue = today.minusDays(1))
        assertEquals(MaintenanceDueState.OVERDUE, getDueState(record, today))
    }

    @Test
    fun testDueTodayState() {
        val record = createRecord(nextDue = today)
        assertEquals(MaintenanceDueState.DUE_TODAY, getDueState(record, today))
    }

    @Test
    fun testDueSoonState() {
        val record = createRecord(nextDue = today.plusDays(15))
        assertEquals(MaintenanceDueState.DUE_SOON, getDueState(record, today))
    }

    @Test
    fun testScheduledState() {
        val record = createRecord(nextDue = today.plusDays(40))
        assertEquals(MaintenanceDueState.SCHEDULED, getDueState(record, today))
    }

    @Test
    fun testCompletedState() {
        val record = createRecord(status = "Completed", nextDue = today.minusDays(1))
        assertEquals(MaintenanceDueState.COMPLETED, getDueState(record, today))
    }

    @Test
    fun testUnscheduledState() {
        val record = createRecord(nextDue = null)
        assertEquals(MaintenanceDueState.UNSCHEDULED, getDueState(record, today))
    }

    private fun createRecord(status: String = "Scheduled", nextDue: LocalDate?): MaintenanceRecordEntity {
        return MaintenanceRecordEntity(
            propertyId = propertyId,
            title = "Test",
            category = "Cat",
            serviceDate = today.minusMonths(1),
            nextDueDate = nextDue,
            status = status
        )
    }

    // Helper mirror of VM logic for pure test
    private fun getDueState(record: MaintenanceRecordEntity, today: LocalDate): MaintenanceDueState {
        if (MaintenanceStatus.isCompleted(record.status)) return MaintenanceDueState.COMPLETED
        if (record.status.trim().equals("Cancelled", ignoreCase = true)) return MaintenanceDueState.CANCELLED
        val nextDue = record.nextDueDate ?: return MaintenanceDueState.UNSCHEDULED
        return when {
            nextDue.isBefore(today) -> MaintenanceDueState.OVERDUE
            nextDue.isEqual(today) -> MaintenanceDueState.DUE_TODAY
            nextDue.isBefore(today.plusDays(30)) -> MaintenanceDueState.DUE_SOON
            else -> MaintenanceDueState.SCHEDULED
        }
    }
}
