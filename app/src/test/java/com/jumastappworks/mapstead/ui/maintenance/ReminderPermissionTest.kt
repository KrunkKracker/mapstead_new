package com.jumastappworks.mapstead.ui.maintenance

import com.jumastappworks.mapstead.data.db.entities.ReminderEntity
import com.jumastappworks.mapstead.data.repository.MaintenanceRepository
import com.jumastappworks.mapstead.data.repository.MaintenanceWriteResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderPermissionTest {

    private val maintenanceRepository = mockk<MaintenanceRepository>(relaxed = true)
    private lateinit var viewModel: MaintenanceViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = MaintenanceViewModel(maintenanceRepository, mockk(), mockk(), mockk(), mockk())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Save Without Notifications saves enabled false`() = runTest {
        val propId = UUID.randomUUID()
        viewModel.startReminderEditing(propId, null)
        advanceUntilIdle()
        
        viewModel.updateReminderTitle("Test Reminder")
        viewModel.updateReminderEnabled(false) // This is what "Save Without Notifications" does in UI
        
        coEvery { maintenanceRepository.saveReminderForProperty(any(), any()) } returns MaintenanceWriteResult.Success(UUID.randomUUID())
        
        viewModel.saveReminder()
        advanceUntilIdle()
        
        coVerify { 
            maintenanceRepository.saveReminderForProperty(propId, match { !it.enabled }) 
        }
    }

    @Test
    fun `Untouched new reminder is not dirty`() = runTest {
        viewModel.startReminderEditing(UUID.randomUUID(), null)
        advanceUntilIdle()
        
        val state = viewModel.reminderEditorState.value as? ReminderEditorUiState.Ready
        assertFalse("Untouched new reminder should not be dirty", state?.isDirty() ?: true)
    }

    @Test
    fun `Changing due date makes reminder dirty`() = runTest {
        viewModel.startReminderEditing(UUID.randomUUID(), null)
        advanceUntilIdle()
        
        viewModel.updateReminderDueDate(LocalDate.now().plusDays(10))
        val state = viewModel.reminderEditorState.value as? ReminderEditorUiState.Ready
        assertTrue("Changed due date should make reminder dirty", state?.isDirty() ?: false)
    }
}
