package com.jumastappworks.mapstead.ui.maintenance

import com.jumastappworks.mapstead.data.db.entities.MaintenanceRecordEntity
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.repository.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MaintenanceViewModelTest {

    private val maintenanceRepo = mockk<MaintenanceRepository>(relaxed = true)
    private val propertyRepo = mockk<PropertyRepository>(relaxed = true)
    private val infraRepo = mockk<InfrastructureRepository>(relaxed = true)
    private val mapRepo = mockk<MapRepository>(relaxed = true)
    private val attachmentRepo = mockk<AttachmentRepository>(relaxed = true)

    private lateinit var viewModel: MaintenanceViewModel

    private val propertyId = UUID.randomUUID()
    private val property = PropertyEntity(id = propertyId, name = "Test Property", propertyType = "Home")

    private val recordsFlow = MutableStateFlow<List<MaintenanceRecordEntity>>(emptyList())
    private val remindersFlow = MutableStateFlow<List<com.jumastappworks.mapstead.data.db.entities.ReminderEntity>>(emptyList())
    private val itemsFlow = MutableStateFlow<List<com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity>>(emptyList())
    private val attachmentsFlow = MutableStateFlow<List<com.jumastappworks.mapstead.data.db.entities.AttachmentEntity>>(emptyList())

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        
        coEvery { propertyRepo.getPropertyById(propertyId) } returns property
        every { maintenanceRepo.getRecordsForProperty(propertyId) } returns recordsFlow
        every { maintenanceRepo.getRemindersForProperty(propertyId) } returns remindersFlow
        every { infraRepo.getItemsForProperty(propertyId) } returns itemsFlow
        every { attachmentRepo.getAttachmentsForProperty(propertyId) } returns attachmentsFlow

        viewModel = MaintenanceViewModel(maintenanceRepo, propertyRepo, infraRepo, mapRepo, attachmentRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun testInitialLoadingState() = runTest {
        assertTrue(viewModel.uiState.value is MaintenanceUiState.Loading)
    }

    @Test
    fun testReadyStateAfterPropertySet() = runTest {
        backgroundScope.launch { viewModel.uiState.collect() }
        
        viewModel.setPropertyId(propertyId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("State should be Ready", state is MaintenanceUiState.Ready)
        val ready = state as MaintenanceUiState.Ready
        assertEquals("Test Property", ready.property.name)
        assertEquals(MaintenanceFilter.All, ready.selectedFilter)
    }

    @Test
    fun testFilteringLogic() = runTest {
        backgroundScope.launch { viewModel.uiState.collect() }
        
        val today = LocalDate.now()
        val records = listOf(
            MaintenanceRecordEntity(propertyId = propertyId, title = "Overdue", category = "C", status = "Scheduled", serviceDate = today.minusMonths(1), nextDueDate = today.minusDays(1)),
            MaintenanceRecordEntity(propertyId = propertyId, title = "Completed", category = "C", status = "Completed", serviceDate = today, nextDueDate = null)
        )
        
        recordsFlow.value = records
        viewModel.setPropertyId(propertyId)
        advanceUntilIdle()

        viewModel.setFilter(MaintenanceFilter.Due)
        advanceUntilIdle()

        val state = viewModel.uiState.value as MaintenanceUiState.Ready
        assertEquals(1, state.filteredRecords.size)
        assertEquals("Overdue", state.filteredRecords[0].title)
        assertEquals(1, state.counts.overdue)
    }

    @Test
    fun testStartEditingLoadsExistingRecord() = runTest {
        val recordId = UUID.randomUUID()
        val record = MaintenanceRecordEntity(id = recordId, propertyId = propertyId, title = "Exist", category = "C", serviceDate = LocalDate.now(), status = "Scheduled")
        
        coEvery { maintenanceRepo.getRecordForProperty(propertyId, recordId) } returns record
        
        viewModel.startEditing(propertyId, recordId)
        advanceUntilIdle()

        val state = viewModel.editorState.value
        assertTrue(state is MaintenanceRecordEditorUiState.Ready)
        val ready = state as MaintenanceRecordEditorUiState.Ready
        assertEquals("Exist", ready.title)
        assertEquals(recordId, ready.recordId)
    }

    @Test
    fun testInfrastructureFilteringAndCounts() = runTest {
        backgroundScope.launch { viewModel.uiState.collect() }
        
        val today = LocalDate.now()
        val itemA = UUID.randomUUID()
        val itemB = UUID.randomUUID()
        
        val recordA = MaintenanceRecordEntity(propertyId = propertyId, infrastructureItemId = itemA, title = "Item A Task", category = "A", status = "Scheduled", serviceDate = today, nextDueDate = today.minusDays(1))
        val recordB = MaintenanceRecordEntity(propertyId = propertyId, infrastructureItemId = itemB, title = "Item B Task", category = "B", status = "Completed", serviceDate = today, nextDueDate = null)
        
        recordsFlow.value = listOf(recordA, recordB)
        viewModel.setPropertyId(propertyId)
        advanceUntilIdle()

        // 1. Initially all records visible, overdue count = 1
        var state = viewModel.uiState.value as MaintenanceUiState.Ready
        assertEquals(2, state.filteredRecords.size)
        assertEquals(1, state.counts.overdue)

        // 2. Filter by Item A
        viewModel.setInfrastructureFilter(itemA)
        advanceUntilIdle()
        
        state = viewModel.uiState.value as MaintenanceUiState.Ready
        assertEquals(1, state.filteredRecords.size)
        assertEquals("Item A Task", state.filteredRecords[0].title)
        assertEquals(1, state.counts.overdue)

        // 3. Filter by Item B
        viewModel.setInfrastructureFilter(itemB)
        advanceUntilIdle()
        
        state = viewModel.uiState.value as MaintenanceUiState.Ready
        assertEquals(1, state.filteredRecords.size)
        assertEquals("Item B Task", state.filteredRecords[0].title)
        assertEquals(0, state.counts.overdue) // Item B has no overdue tasks

        // 4. Clear filter
        viewModel.setInfrastructureFilter(null)
        advanceUntilIdle()
        state = viewModel.uiState.value as MaintenanceUiState.Ready
        assertEquals(2, state.filteredRecords.size)
        assertEquals(1, state.counts.overdue)
    }

    @Test
    fun `deleteRecord emits NavigateBackAfterDelete on success`() = runTest {
        val recordId = UUID.randomUUID()
        viewModel.setPropertyId(propertyId)
        advanceUntilIdle()

        coEvery { maintenanceRepo.deleteRecordForProperty(propertyId, recordId) } returns MaintenanceWriteResult.Success(recordId)

        val events = mutableListOf<MaintenanceDetailsEvent>()
        val job = launch {
            viewModel.detailsEvents.toList(events)
        }

        viewModel.deleteRecord(recordId)
        advanceUntilIdle()

        assertTrue(events.contains(MaintenanceDetailsEvent.NavigateBackAfterDelete))
        job.cancel()
    }
}
