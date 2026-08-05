package com.jumastappworks.mapstead.ui.dashboard

import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import com.jumastappworks.mapstead.data.db.entities.MaintenanceRecordEntity
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.repository.InfrastructureRepository
import com.jumastappworks.mapstead.data.repository.MaintenanceRepository
import com.jumastappworks.mapstead.data.repository.PropertyRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val propertyRepo = mockk<PropertyRepository>(relaxed = true)
    private val infrastructureRepo = mockk<InfrastructureRepository>(relaxed = true)
    private val maintenanceRepo = mockk<MaintenanceRepository>(relaxed = true)

    private lateinit var viewModel: HomeViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = HomeViewModel(propertyRepo, infrastructureRepo, maintenanceRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `setting property ID loads property scoped data`() = runTest {
        val propId = UUID.randomUUID()
        val property = PropertyEntity(id = propId, name = "Test Property", propertyType = "Home")
        val item = InfrastructureItemEntity(id = UUID.randomUUID(), propertyId = propId, name = "Item", category = "Utility", status = "Active")
        val task = MaintenanceRecordEntity(id = UUID.randomUUID(), propertyId = propId, title = "Task", category = "M", serviceDate = LocalDate.now().plusDays(1), status = "Scheduled")

        every { propertyRepo.getAllProperties() } returns flowOf(listOf(property))
        every { infrastructureRepo.getItemsForProperty(propId) } returns flowOf(listOf(item))
        every { maintenanceRepo.getRecordsForProperty(propId) } returns flowOf(listOf(task))

        viewModel.setPropertyId(propId)

        val state = viewModel.uiState.value
        assertEquals(property, state.property)
        assertEquals(1, state.recentlyAddedItems.size)
        assertEquals(1, state.upcomingTasks.size)
        assertEquals(0, state.needsAttentionTasks.size)
    }

    @Test
    fun `switching properties clears old data`() = runTest {
        val propId1 = UUID.randomUUID()
        val propId2 = UUID.randomUUID()
        val property1 = PropertyEntity(id = propId1, name = "Prop 1", propertyType = "Home")
        val property2 = PropertyEntity(id = propId2, name = "Prop 2", propertyType = "Home")

        every { propertyRepo.getAllProperties() } returns flowOf(listOf(property1, property2))
        every { infrastructureRepo.getItemsForProperty(propId1) } returns flowOf(emptyList())
        every { infrastructureRepo.getItemsForProperty(propId2) } returns flowOf(listOf(mockk(relaxed = true)))
        every { maintenanceRepo.getRecordsForProperty(any()) } returns flowOf(emptyList())

        viewModel.setPropertyId(propId1)
        assertEquals(0, viewModel.uiState.value.recentlyAddedItems.size)

        viewModel.setPropertyId(propId2)
        assertEquals(1, viewModel.uiState.value.recentlyAddedItems.size)
    }

    @Test
    fun `needs attention includes overdue and today tasks`() = runTest {
        val propId = UUID.randomUUID()
        val now = LocalDate.now()
        val overdue = MaintenanceRecordEntity(id = UUID.randomUUID(), propertyId = propId, title = "Overdue", category = "M", serviceDate = now.minusDays(1), status = "Scheduled")
        val today = MaintenanceRecordEntity(id = UUID.randomUUID(), propertyId = propId, title = "Today", category = "M", serviceDate = now, status = "Scheduled")
        val future = MaintenanceRecordEntity(id = UUID.randomUUID(), propertyId = propId, title = "Future", category = "M", serviceDate = now.plusDays(1), status = "Scheduled")

        every { propertyRepo.getAllProperties() } returns flowOf(listOf(mockk(relaxed = true) { every { id } returns propId }))
        every { infrastructureRepo.getItemsForProperty(any()) } returns flowOf(emptyList())
        every { maintenanceRepo.getRecordsForProperty(propId) } returns flowOf(listOf(overdue, today, future))

        viewModel.setPropertyId(propId)

        val state = viewModel.uiState.value
        assertEquals(2, state.needsAttentionTasks.size)
        assertEquals(1, state.upcomingTasks.size)
    }
}
