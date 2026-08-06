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
    fun `same-ID property update refreshes Home reactively`() = runTest {
        viewModel.setPropertyId(propertyId)
        advanceUntilIdle()
        
        val updatedProperty = property.copy(name = "Updated Name")
        allPropsFlow.value = listOf(updatedProperty)
        advanceUntilIdle()
        
        val state = viewModel.uiState.value as HomeUiState.Ready
        assertEquals("Updated Name", state.property.name)
    }

    @Test
    fun `missing selected property emits NotFound`() = runTest {
        allPropsFlow.value = emptyList()
        viewModel.setPropertyId(propertyId)
        advanceUntilIdle()
        
        assertEquals(HomeUiState.NotFound, viewModel.uiState.value)
    }

    @Test
    fun `maintenance classification is correct and deterministic`() {
        val today = LocalDate.of(2026, 8, 6)
        
        val overdue = MaintenanceRecordEntity(id = UUID.randomUUID(), propertyId = propertyId, title = "Overdue", category = "C", status = "Active", nextDueDate = today.minusDays(1), serviceDate = today.minusMonths(1))
        val dueToday = MaintenanceRecordEntity(id = UUID.randomUUID(), propertyId = propertyId, title = "Today", category = "C", status = "Active", nextDueDate = today, serviceDate = today.minusMonths(1))
        val upcoming = MaintenanceRecordEntity(id = UUID.randomUUID(), propertyId = propertyId, title = "Upcoming", category = "C", status = "Active", nextDueDate = today.plusDays(1), serviceDate = today.minusMonths(1))
        val completed = MaintenanceRecordEntity(id = UUID.randomUUID(), propertyId = propertyId, title = "Done", category = "C", status = "Completed", nextDueDate = today.minusDays(1), serviceDate = today)
        val cancelled = MaintenanceRecordEntity(id = UUID.randomUUID(), propertyId = propertyId, title = "Cancelled", category = "C", status = "Cancelled", nextDueDate = today.minusDays(1), serviceDate = today)
        val noDueDate = MaintenanceRecordEntity(id = UUID.randomUUID(), propertyId = propertyId, title = "No Due", category = "C", status = "Active", nextDueDate = null, serviceDate = today)

        assertTrue(HomeViewModel.isOverdueOrDueToday(overdue, today))
        assertTrue(HomeViewModel.isOverdueOrDueToday(dueToday, today))
        assertFalse(HomeViewModel.isOverdueOrDueToday(upcoming, today))
        assertFalse(HomeViewModel.isOverdueOrDueToday(noDueDate, today))
        
        assertTrue(HomeViewModel.isUpcoming(upcoming, today))
        assertFalse(HomeViewModel.isUpcoming(dueToday, today))
        
        assertTrue(HomeViewModel.isTaskActive(overdue))
        assertFalse(HomeViewModel.isTaskActive(completed))
        assertFalse(HomeViewModel.isTaskActive(cancelled))
    }

    @Test
    fun `Needs Attention and Upcoming ordering is deterministic and limited`() = runTest {
        val today = LocalDate.now()
        val r1 = MaintenanceRecordEntity(id = UUID.randomUUID(), propertyId = propertyId, title = "A", category = "C", status = "Active", nextDueDate = today.minusDays(1), serviceDate = today)
        val r2 = MaintenanceRecordEntity(id = UUID.randomUUID(), propertyId = propertyId, title = "B", category = "C", status = "Active", nextDueDate = today.minusDays(2), serviceDate = today)
        val u1 = MaintenanceRecordEntity(id = UUID.randomUUID(), propertyId = propertyId, title = "U1", category = "C", status = "Active", nextDueDate = today.plusDays(5), serviceDate = today)
        val u2 = MaintenanceRecordEntity(id = UUID.randomUUID(), propertyId = propertyId, title = "U2", category = "C", status = "Active", nextDueDate = today.plusDays(2), serviceDate = today)
        val u3 = MaintenanceRecordEntity(id = UUID.randomUUID(), propertyId = propertyId, title = "U3", category = "C", status = "Active", nextDueDate = today.plusDays(3), serviceDate = today)
        val u4 = MaintenanceRecordEntity(id = UUID.randomUUID(), propertyId = propertyId, title = "U4", category = "C", status = "Active", nextDueDate = today.plusDays(4), serviceDate = today)

        recordsFlow.value = listOf(r1, r2, u1, u2, u3, u4)
        viewModel.setPropertyId(propertyId)
        advanceUntilIdle()
        
        val state = viewModel.uiState.value as HomeUiState.Ready
        // Needs attention sorted by date: r2 (minus 2), r1 (minus 1)
        assertEquals("B", state.needsAttentionTasks[0].title)
        assertEquals("A", state.needsAttentionTasks[1].title)
        
        // Upcoming sorted by date: u2 (2), u3 (3), u4 (4). Limit 3.
        assertEquals(3, state.upcomingTasks.size)
        assertEquals("U2", state.upcomingTasks[0].title)
        assertEquals("U3", state.upcomingTasks[1].title)
        assertEquals("U4", state.upcomingTasks[2].title)
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
    fun `map-only features set hasAnyPropertyContent and hasMapFeaturesOnly correctly`() = runTest {
        itemsFlow.value = emptyList()
        val activeFeature = mockk<MapFeatureEntity>(relaxed = true) { every { deletedAt } returns null }
        val deletedFeature = mockk<MapFeatureEntity>(relaxed = true) { every { deletedAt } returns java.time.Instant.now() }
        featuresFlow.value = listOf(activeFeature, deletedFeature)
        
        viewModel.setPropertyId(propertyId)
        advanceUntilIdle()
        
        val state = viewModel.uiState.value as HomeUiState.Ready
        assertTrue(state.hasAnyPropertyContent)
        assertTrue(state.hasMapFeaturesOnly)
    }

    @Test
    fun `repository failure emits safe Error state and Retry works`() = runTest {
        viewModel.setPropertyId(propertyId)
        advanceUntilIdle()
        
        // Mock failure for items
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
    fun `property switching clears prior state and rejects late emissions`() = runTest {
        viewModel.setPropertyId(propertyId)
        advanceUntilIdle()

        val states = mutableListOf<HomeUiState>()
        val collectJob = launch(testDispatcher) {
            viewModel.uiState.collect { states.add(it) }
        }

        val newId = UUID.randomUUID()
        val newProp = PropertyEntity(id = newId, name = "New Prop", propertyType = "Home")
        allPropsFlow.value = listOf(property, newProp)
        
        // Delay the new property flow to observe Loading
        val newItemsFlow = MutableSharedFlow<List<InfrastructureItemEntity>>(replay = 1)
        every { infrastructureRepository.getItemsForProperty(newId) } returns newItemsFlow
        every { maintenanceRepository.getRecordsForProperty(newId) } returns flowOf(emptyList())
        every { mapRepository.getFeaturesForProperty(newId) } returns flowOf(emptyList())
        every { mapRepository.getPlansForProperty(newId) } returns flowOf(emptyList())
        every { attachmentRepository.getAttachmentsForProperty(newId) } returns flowOf(emptyList())

        viewModel.setPropertyId(newId)
        runCurrent()
        
        // Should have emitted Loading
        assertTrue(states.last() is HomeUiState.Loading)
        
        // Late prior property emission (interleaved)
        itemsFlow.value = listOf(mockk(relaxed = true), mockk(relaxed = true))
        runCurrent()
        
        // Should still be Loading for the new ID
        assertTrue(states.last() is HomeUiState.Loading)
        
        // Emit for new property
        newItemsFlow.emit(emptyList())
        advanceUntilIdle()
        
        assertTrue(states.last() is HomeUiState.Ready)
        val finalReady = states.last() as HomeUiState.Ready
        assertEquals(newId, finalReady.property.id)
        assertEquals(0, finalReady.recentlyAddedItems.size) // Didn't take the late old ones
        
        collectJob.cancel()
    }
}
