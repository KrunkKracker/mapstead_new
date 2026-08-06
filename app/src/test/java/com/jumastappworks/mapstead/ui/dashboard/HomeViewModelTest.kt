package com.jumastappworks.mapstead.ui.dashboard

import com.jumastappworks.mapstead.data.db.entities.*
import com.jumastappworks.mapstead.data.prefs.UserPreferences
import com.jumastappworks.mapstead.data.repository.*
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.delay

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val propertyRepository = mockk<PropertyRepository>()
    private val mapRepository = mockk<MapRepository>()
    private val infrastructureRepository = mockk<InfrastructureRepository>()
    private val maintenanceRepository = mockk<MaintenanceRepository>()
    private val attachmentRepository = mockk<AttachmentRepository>()
    private val userPreferencesRepository = mockk<UserPreferencesRepository>()

    private lateinit var viewModel: HomeViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val propertyId = UUID.randomUUID()
    private val property = PropertyEntity(id = propertyId, name = "Test Property", propertyType = "Home")

    private val itemsFlow = MutableStateFlow<List<InfrastructureItemEntity>>(emptyList())
    private val recordsFlow = MutableStateFlow<List<MaintenanceRecordEntity>>(emptyList())
    private val featuresFlow = MutableStateFlow<List<MapFeatureEntity>>(emptyList())
    private val plansFlow = MutableStateFlow<List<PlanEntity>>(emptyList())
    private val attachmentsFlow = MutableStateFlow<List<AttachmentEntity>>(emptyList())
    private val allPropsFlow = MutableStateFlow<List<PropertyEntity>>(listOf(property))
    private val prefsFlow = MutableStateFlow(UserPreferences())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        every { userPreferencesRepository.userPreferencesFlow } returns prefsFlow
        coEvery { propertyRepository.getPropertyById(propertyId) } returns property
        every { propertyRepository.getAllProperties() } returns allPropsFlow
        every { infrastructureRepository.getItemsForProperty(propertyId) } returns itemsFlow
        every { maintenanceRepository.getRecordsForProperty(propertyId) } returns recordsFlow
        every { mapRepository.getFeaturesForProperty(propertyId) } returns featuresFlow
        every { mapRepository.getPlansForProperty(propertyId) } returns plansFlow
        every { attachmentRepository.getAttachmentsForProperty(propertyId) } returns attachmentsFlow

        viewModel = HomeViewModel(
            propertyRepository,
            mapRepository,
            infrastructureRepository,
            maintenanceRepository,
            attachmentRepository,
            userPreferencesRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading`() = runTest {
        assertEquals(HomeUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `setPropertyId triggers data load and emits Ready`() = runTest {
        viewModel.setPropertyId(propertyId)
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertTrue(state is HomeUiState.Ready)
        assertEquals("Test Property", (state as HomeUiState.Ready).property.name)
    }

    @Test
    fun `maintenance classification is correct and deterministic`() {
        val today = LocalDate.of(2026, 8, 6)
        
        val overdue = MaintenanceRecordEntity(id = UUID.randomUUID(), propertyId = propertyId, title = "Overdue", category = "C", status = "Active", nextDueDate = today.minusDays(1), serviceDate = today.minusMonths(1))
        val dueToday = MaintenanceRecordEntity(id = UUID.randomUUID(), propertyId = propertyId, title = "Today", category = "C", status = "Active", nextDueDate = today, serviceDate = today.minusMonths(1))
        val upcoming = MaintenanceRecordEntity(id = UUID.randomUUID(), propertyId = propertyId, title = "Upcoming", category = "C", status = "Active", nextDueDate = today.plusDays(1), serviceDate = today.minusMonths(1))
        val completed = MaintenanceRecordEntity(id = UUID.randomUUID(), propertyId = propertyId, title = "Done", category = "C", status = "Completed", nextDueDate = today.minusDays(1), serviceDate = today)
        val cancelled = MaintenanceRecordEntity(id = UUID.randomUUID(), propertyId = propertyId, title = "Cancelled", category = "C", status = "Cancelled", nextDueDate = today.minusDays(1), serviceDate = today)

        assertTrue(HomeViewModel.isOverdueOrDueToday(overdue, today))
        assertTrue(HomeViewModel.isOverdueOrDueToday(dueToday, today))
        assertFalse(HomeViewModel.isOverdueOrDueToday(upcoming, today))
        
        assertTrue(HomeViewModel.isUpcoming(upcoming, today))
        assertFalse(HomeViewModel.isUpcoming(dueToday, today))
        
        assertTrue(HomeViewModel.isTaskActive(overdue))
        assertFalse(HomeViewModel.isTaskActive(completed))
        assertFalse(HomeViewModel.isTaskActive(cancelled))
    }

    @Test
    fun `recent items limited to 5 and sorted by createdAt`() = runTest {
        val now = java.time.Instant.now()
        val items = (1..10).map { i ->
            InfrastructureItemEntity(id = UUID.randomUUID(), propertyId = propertyId, name = "Item $i", category = "Cat", status = "Good", createdAt = now.plusSeconds(i.toLong()))
        }
        
        itemsFlow.value = items
        viewModel.setPropertyId(propertyId)
        advanceUntilIdle()
        
        val state = viewModel.uiState.value as HomeUiState.Ready
        assertEquals(5, state.recentlyAddedItems.size)
        assertEquals("Item 10", state.recentlyAddedItems[0].name)
    }

    @Test
    fun `repository failure emits safe Error state and Retry works`() = runTest {
        viewModel.setPropertyId(propertyId)
        advanceUntilIdle()
        
        // Mock failure
        every { infrastructureRepository.getItemsForProperty(propertyId) } returns flow { throw RuntimeException("Fail") }
        
        viewModel.retry()
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value is HomeUiState.Error)
        
        // Fix and retry
        every { infrastructureRepository.getItemsForProperty(propertyId) } returns itemsFlow
        
        viewModel.retry()
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value is HomeUiState.Ready)
    }

    @Test
    fun `property switching clears prior state and emits Loading`() = runTest {
        viewModel.setPropertyId(propertyId)
        advanceUntilIdle()

        val states = mutableListOf<HomeUiState>()
        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect { states.add(it) }
        }

        val newId = UUID.randomUUID()
        coEvery { propertyRepository.getPropertyById(newId) } returns PropertyEntity(id = newId, name = "New Prop", propertyType = "Home")
        
        // Delay the new property flow to see Loading
        every { infrastructureRepository.getItemsForProperty(newId) } returns flow {
             delay(100)
             emit(emptyList())
        }
        every { maintenanceRepository.getRecordsForProperty(newId) } returns flowOf(emptyList())
        every { mapRepository.getFeaturesForProperty(newId) } returns flowOf(emptyList())
        every { mapRepository.getPlansForProperty(newId) } returns flowOf(emptyList())
        every { attachmentRepository.getAttachmentsForProperty(newId) } returns flowOf(emptyList())

        viewModel.setPropertyId(newId)
        
        // Outer flatMapLatest processes new ID
        runCurrent()
        
        // The flow inside should have emitted Loading first
        assertTrue(states.any { it is HomeUiState.Loading })
        
        // Late prior property emission check
        itemsFlow.value = listOf(mockk(relaxed = true))
        runCurrent()
        
        advanceUntilIdle()
        assertTrue(states.last() is HomeUiState.Ready)
        assertEquals(newId, (states.last() as HomeUiState.Ready).property.id)
        
        collectJob.cancel()
    }
}
