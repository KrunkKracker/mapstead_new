package com.jumastappworks.mapstead.ui.properties

import com.jumastappworks.mapstead.data.mapping.CurrentLocationProvider
import com.jumastappworks.mapstead.data.repository.PropertyRepository
import androidx.lifecycle.SavedStateHandle
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class PropertyDirtyStateTest {

    private val propertyRepository = mockk<PropertyRepository>(relaxed = true)
    private val locationProvider = mockk<CurrentLocationProvider>(relaxed = true)
    private val addressResolver = mockk<com.jumastappworks.mapstead.data.mapping.AddressLocationResolver>(relaxed = true)
    private lateinit var viewModel: EditPropertyViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = EditPropertyViewModel(propertyRepository, locationProvider, addressResolver)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Changing only property type makes the form dirty`() = runTest {
        viewModel.name = "My Property"
        viewModel.type = "Home"
        viewModel.loadProperty(UUID.randomUUID())
        advanceUntilIdle()
        
        viewModel.type = "Farm"
        assertTrue(viewModel.isDirty())
    }

    @Test
    fun `Changing only Address Line 2 makes the form dirty`() = runTest {
        viewModel.loadProperty(UUID.randomUUID())
        advanceUntilIdle()
        
        viewModel.addressLine2 = "Unit 101"
        assertTrue(viewModel.isDirty())
    }

    @Test
    fun `AddPropertyViewModel is dirty when name is entered`() = runTest {
        val addVm = AddPropertyViewModel(propertyRepository, mockk(relaxed = true), locationProvider, addressResolver, mockk(relaxed = true), mockk(relaxed = true), SavedStateHandle())
        backgroundScope.launch { addVm.uiState.collect {} }
        advanceUntilIdle()
        
        assertFalse(addVm.isDirty())
        addVm.setName("Dirty")
        advanceUntilIdle()
        assertTrue(addVm.isDirty())
    }
}
