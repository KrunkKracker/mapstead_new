package com.jumastappworks.mapstead.ui.maintenance

import com.jumastappworks.mapstead.data.repository.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MaintenanceViewModelEventTest {

    private val maintenanceRepo = mockk<MaintenanceRepository>(relaxed = true)
    private val propertyRepo = mockk<PropertyRepository>(relaxed = true)
    private val infraRepo = mockk<InfrastructureRepository>(relaxed = true)
    private val mapRepo = mockk<MapRepository>(relaxed = true)
    private val attachmentRepo = mockk<AttachmentRepository>(relaxed = true)

    private lateinit var viewModel: MaintenanceViewModel
    private val propertyId = UUID.randomUUID()

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        viewModel = MaintenanceViewModel(maintenanceRepo, propertyRepo, infraRepo, mapRepo, attachmentRepo)
        viewModel.setPropertyId(propertyId)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `deleteRecord emits NavigateBackAfterDelete on success`() = runTest {
        val recordId = UUID.randomUUID()
        coEvery { maintenanceRepo.deleteRecordForProperty(propertyId, recordId) } returns MaintenanceWriteResult.Success(recordId)

        val events = mutableListOf<MaintenanceDetailsEvent>()
        val job = launch {
            viewModel.detailsEvents.toList(events)
        }

        viewModel.deleteRecord(recordId)
        advanceUntilIdle()

        assertTrue("Should contain NavigateBackAfterDelete", events.contains(MaintenanceDetailsEvent.NavigateBackAfterDelete))
        job.cancel()
    }

    @Test
    fun `deleteRecord emits ShowSchedulingWarning on success with warning`() = runTest {
        val recordId = UUID.randomUUID()
        val warningRes = 123
        coEvery { maintenanceRepo.deleteRecordForProperty(propertyId, recordId) } returns 
            MaintenanceWriteResult.SuccessWithSchedulingWarning(recordId, warningRes)

        val events = mutableListOf<MaintenanceDetailsEvent>()
        val job = launch {
            viewModel.detailsEvents.toList(events)
        }

        viewModel.deleteRecord(recordId)
        advanceUntilIdle()

        assertTrue("Should contain NavigateBackAfterDelete", events.contains(MaintenanceDetailsEvent.NavigateBackAfterDelete))
        assertTrue("Should contain ShowSchedulingWarning", events.any { it is MaintenanceDetailsEvent.ShowSchedulingWarning && it.messageRes == warningRes })
        job.cancel()
    }
}
