package com.jumastappworks.mapstead.ui.plans

import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.data.repository.MapRepository
import com.jumastappworks.mapstead.data.repository.PropertyRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class CreatePlanViewModelTest {

    private val mapRepo = mockk<MapRepository>(relaxed = true)
    private val propRepo = mockk<PropertyRepository>(relaxed = true)
    private val locationProvider = mockk<CurrentLocationProvider>(relaxed = true)
    private val addressResolver = mockk<AddressLocationResolver>(relaxed = true)
    private lateinit var viewModel: CreatePlanViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = CreatePlanViewModel(mapRepo, propRepo, locationProvider, addressResolver)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `blank address query does not call resolver`() = runTest {
        viewModel.searchAddress("  ")
        advanceUntilIdle()
        coVerify(exactly = 0) { addressResolver.search(any()) }
        assertTrue(viewModel.locationState.value is CreatePlanLocationState.Error)
    }

    @Test
    fun `delayed old query cannot overwrite newer query`() = runTest {
        val q1 = "First"
        val q2 = "Second"
        
        coEvery { addressResolver.search(q1) } coAnswers {
            delay(2.seconds)
            AddressSearchResult.Success(listOf(AddressLocationMatch("Addr 1", 1.0, 1.0)))
        }
        coEvery { addressResolver.search(q2) } returns AddressSearchResult.Success(listOf(AddressLocationMatch("Addr 2", 2.0, 2.0)))
        
        viewModel.searchAddress(q1)
        runCurrent()
        
        viewModel.searchAddress(q2)
        advanceUntilIdle()
        
        // Final state should be Addr 2 from q2
        assertEquals(1, viewModel.addressSearchResults.value.size)
        assertEquals("Addr 2", viewModel.addressSearchResults.value[0].displayAddress)
    }

    @Test
    fun `missing selected location produces select location error on save`() = runTest {
        viewModel.name = "My Map"
        viewModel.propertyId = UUID.randomUUID()
        viewModel.savePlan {}
        advanceUntilIdle()
        
        assertEquals(com.jumastappworks.mapstead.R.string.error_select_location, viewModel.saveErrorRes)
        coVerify(exactly = 0) { mapRepo.createPlanWithDefaultLayer(any()) }
    }
}
