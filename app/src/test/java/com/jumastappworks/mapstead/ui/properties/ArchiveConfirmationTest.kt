package com.jumastappworks.mapstead.ui.properties

import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.mapping.ExamplePropertySeeder
import com.jumastappworks.mapstead.data.prefs.UserPreferences
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
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
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveConfirmationTest {

    private val propertyRepo = mockk<PropertyRepository>(relaxed = true)
    private val seeder = mockk<ExamplePropertySeeder>(relaxed = true)
    private val userPrefs = mockk<UserPreferencesRepository>(relaxed = true)
    
    private lateinit var viewModel: PropertiesViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { propertyRepo.getAllProperties() } returns flowOf(emptyList())
        every { propertyRepo.getArchivedProperties() } returns flowOf(emptyList())
        every { userPrefs.userPreferencesFlow } returns flowOf(UserPreferences())
        
        viewModel = PropertiesViewModel(propertyRepo, seeder, userPrefs)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `archiving the selected property clears selection`() = runTest {
        val propertyId = UUID.randomUUID()
        every { userPrefs.userPreferencesFlow } returns flowOf(UserPreferences(selectedPropertyId = propertyId.toString()))
        
        viewModel.archiveProperty(propertyId)
        
        coVerify { userPrefs.updateSelectedProperty(null) }
        coVerify { propertyRepo.archiveProperty(propertyId) }
    }

    @Test
    fun `deleting the selected property clears selection`() = runTest {
        val propertyId = UUID.randomUUID()
        every { userPrefs.userPreferencesFlow } returns flowOf(UserPreferences(selectedPropertyId = propertyId.toString()))
        
        viewModel.softDeleteProperty(propertyId)
        
        coVerify { userPrefs.updateSelectedProperty(null) }
        coVerify { propertyRepo.softDeleteProperty(propertyId) }
    }
}
