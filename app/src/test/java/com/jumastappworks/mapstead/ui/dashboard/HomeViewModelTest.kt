package com.jumastappworks.mapstead.ui.dashboard

import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import com.jumastappworks.mapstead.data.db.entities.MaintenanceRecordEntity
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.data.repository.InfrastructureRepository
import com.jumastappworks.mapstead.data.repository.MaintenanceRepository
import com.jumastappworks.mapstead.data.repository.PropertyRepository
import com.jumastappworks.mapstead.data.repository.MapRepository
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
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val propertyRepo = mockk<PropertyRepository>(relaxed = true)
    private val infrastructureRepo = mockk<InfrastructureRepository>(relaxed = true)
    private val maintenanceRepo = mockk<MaintenanceRepository>(relaxed = true)
    private val mapRepo = mockk<MapRepository>(relaxed = true)

    private lateinit var viewModel: HomeViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        // Default mocks to avoid combine hanging
        every { propertyRepo.getAllProperties() } returns flowOf(emptyList())
        every { infrastructureRepo.getItemsForProperty(any()) } returns flowOf(emptyList())
        every { maintenanceRepo.getRecordsForProperty(any()) } returns flowOf(emptyList())
        every { mapRepo.getFeaturesForProperty(any()) } returns flowOf(emptyList())
        
        viewModel = HomeViewModel(propertyRepo, infrastructureRepo, maintenanceRepo, mapRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `initial state is Loading`() = runTest {
        assertTrue(viewModel.uiState.value is HomeUiState.Loading)
    }

    @Test
    fun `setting property ID loads property scoped data`() = runTest {
        val propId = UUID.randomUUID()
        val property = PropertyEntity(id = propId, name = "Test Property", propertyType = "Home")
        val item = InfrastructureItemEntity(id = UUID.randomUUID(), propertyId = propId, name = "Item", category = "Utility", status = "Active", createdAt = Instant.now())
        val task = MaintenanceRecordEntity(id = UUID.randomUUID(), propertyId = propId, title = "Task", category = "M", serviceDate = LocalDate.now(), nextDueDate = LocalDate.now().plusDays(1), status = "Scheduled")

        every { propertyRepo.getAllProperties() } returns flowOf(listOf(property))
        every { infrastructureRepo.getItemsForProperty(propId) } returns flowOf(listOf(item))
        every { maintenanceRepo.getRecordsForProperty(propId) } returns flowOf(listOf(task))

        viewModel.setPropertyId(propId)
        runCurrent()

        val state = viewModel.uiState.value as HomeUiState.Ready
        assertEquals(property, state.property)
        assertEquals(1, state.recentlyAddedItems.size)
        assertEquals(1, state.upcomingTasks.size)
        assertEquals(0, state.needsAttentionTasks.size)
    }

    @Test
    fun `switching property emits Loading then new Ready`() = runTest {
        val propId1 = UUID.randomUUID()
        val propId2 = UUID.randomUUID()
        val p1 = PropertyEntity(id = propId1, name = "P1", propertyType = "H")
        val p2 = PropertyEntity(id = propId2, name = "P2", propertyType = "H")

        every { propertyRepo.getAllProperties() } returns flowOf(listOf(p1, p2))
        
        val states = mutableListOf<HomeUiState>()
        val job = backgroundScope.launch { viewModel.uiState.collect { states.add(it) } }
        runCurrent() // Get initial Loading

        viewModel.setPropertyId(propId1)
        runCurrent()
        
        viewModel.setPropertyId(propId2)
        runCurrent()

        val readyStates = states.filterIsInstance<HomeUiState.Ready>()
        assertEquals(2, readyStates.size)
        assertEquals(propId1, readyStates[0].property.id)
        assertEquals(propId2, readyStates[1].property.id)
        
        job.cancel()
    }

    @Test
    fun `late prior-property emissions are ignored`() = runTest {
        val propId1 = UUID.randomUUID()
        val propId2 = UUID.randomUUID()
        val p1 = PropertyEntity(id = propId1, name = "P1", propertyType = "H")
        val p2 = PropertyEntity(id = propId2, name = "P2", propertyType = "H")

        val p1Items = MutableSharedFlow<List<InfrastructureItemEntity>>(replay = 1)
        val p2Items = MutableSharedFlow<List<InfrastructureItemEntity>>(replay = 1)

        every { propertyRepo.getAllProperties() } returns flowOf(listOf(p1, p2))
        every { infrastructureRepo.getItemsForProperty(propId1) } returns p1Items
        every { infrastructureRepo.getItemsForProperty(propId2) } returns p2Items

        val states = mutableListOf<HomeUiState>()
        val job = backgroundScope.launch { viewModel.uiState.collect { states.add(it) } }
        runCurrent()

        viewModel.setPropertyId(propId1)
        p1Items.emit(emptyList())
        runCurrent()
        
        viewModel.setPropertyId(propId2)
        runCurrent() // Should be Loading
        
        p2Items.emit(listOf(InfrastructureItemEntity(id = UUID.randomUUID(), propertyId = propId2, name = "P2 Item", category = "T", status = "Active", createdAt = Instant.now())))
        runCurrent()
        
        // Late emission from p1
        p1Items.emit(listOf(mockk(relaxed = true), mockk(relaxed = true)))
        runCurrent()

        val lastState = viewModel.uiState.value as HomeUiState.Ready
        assertEquals(propId2, lastState.property.id)
        assertEquals("Should only show p2 items", 1, lastState.recentlyAddedItems.size)
        
        job.cancel()
    }

    @Test
    fun `repository failure emits safe Error state and Retry works`() = runTest {
        val propId = UUID.randomUUID()
        val property = PropertyEntity(id = propId, name = "P", propertyType = "H")
        val itemsFlow = MutableSharedFlow<List<InfrastructureItemEntity>>(replay = 1)

        every { propertyRepo.getAllProperties() } returns flowOf(listOf(property))
        every { infrastructureRepo.getItemsForProperty(propId) } returns itemsFlow

        viewModel.setPropertyId(propId)
        itemsFlow.emit(emptyList())
        runCurrent()
        
        assertTrue(viewModel.uiState.value is HomeUiState.Ready)

        // Simulate failure for next collection
        every { infrastructureRepo.getItemsForProperty(propId) } returns flow { throw RuntimeException("Repo Fail") }
        
        viewModel.retry()
        runCurrent()
        
        assertTrue("Should emit Error state", viewModel.uiState.value is HomeUiState.Error)

        // Fix and Retry
        every { infrastructureRepo.getItemsForProperty(propId) } returns flowOf(emptyList())
        viewModel.retry()
        runCurrent()
        
        assertTrue("Should recover to Ready state", viewModel.uiState.value is HomeUiState.Ready)
    }

    @Test
    fun `maintenance classification is correct and deterministic`() {
        val now = LocalDate.of(2026, 8, 6)
        
        val overdueTask = MaintenanceRecordEntity(id = UUID.randomUUID(), propertyId = UUID.randomUUID(), title = "Overdue", category = "M", serviceDate = now.minusDays(5), nextDueDate = now.minusDays(1), status = "Scheduled")
        val todayTask = MaintenanceRecordEntity(id = UUID.randomUUID(), propertyId = UUID.randomUUID(), title = "Today", category = "M", serviceDate = now.minusDays(5), nextDueDate = now, status = "Scheduled")
        val futureTask = MaintenanceRecordEntity(id = UUID.randomUUID(), propertyId = UUID.randomUUID(), title = "Future", category = "M", serviceDate = now.minusDays(5), nextDueDate = now.plusDays(1), status = "Scheduled")
        val completedTask = MaintenanceRecordEntity(id = UUID.randomUUID(), propertyId = UUID.randomUUID(), title = "Done", category = "M", serviceDate = now.minusDays(5), nextDueDate = now.minusDays(1), status = "Completed")
        val cancelledTask = MaintenanceRecordEntity(id = UUID.randomUUID(), propertyId = UUID.randomUUID(), title = "Cancelled", category = "M", serviceDate = now.minusDays(5), nextDueDate = now.minusDays(1), status = "  cancelled  ")

        assertTrue(HomeViewModel.isOverdueOrDueToday(overdueTask, now))
        assertTrue(HomeViewModel.isOverdueOrDueToday(todayTask, now))
        assertFalse(HomeViewModel.isOverdueOrDueToday(futureTask, now))
        
        assertTrue(HomeViewModel.isUpcoming(futureTask, now))
        assertFalse(HomeViewModel.isUpcoming(todayTask, now))
        
        assertTrue(HomeViewModel.isTaskActive(overdueTask))
        assertFalse(HomeViewModel.isTaskActive(completedTask))
        assertFalse(HomeViewModel.isTaskActive(cancelledTask))
    }

    @Test
    fun `hasMapFeaturesOnly is true when items are empty but map has features`() = runTest {
        val propId = UUID.randomUUID()
        val property = PropertyEntity(id = propId, name = "P", propertyType = "H")
        every { propertyRepo.getAllProperties() } returns flowOf(listOf(property))
        every { infrastructureRepo.getItemsForProperty(propId) } returns flowOf(emptyList())
        every { mapRepo.getFeaturesForProperty(propId) } returns flowOf(listOf(MapFeatureEntity(id = UUID.randomUUID(), propertyId = propId, planId = UUID.randomUUID(), layerId = UUID.randomUUID(), geometryType = "POINT", geometryJson = "{}", coordinateSpace = "G", styleJson = "{}", accuracySource = "M", label = "F")))

        viewModel.setPropertyId(propId)
        runCurrent()

        val state = viewModel.uiState.value as HomeUiState.Ready
        assertTrue(state.hasMapFeaturesOnly)
    }
}
