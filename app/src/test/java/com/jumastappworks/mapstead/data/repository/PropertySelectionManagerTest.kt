package com.jumastappworks.mapstead.data.repository

import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.prefs.UserPreferences
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class PropertySelectionManagerTest {

    private val propertyRepo = mockk<PropertyRepository>()
    private val userPrefsRepo = mockk<UserPreferencesRepository>()
    private val propertyFlow = MutableStateFlow<List<PropertyEntity>>(emptyList())
    private val prefsFlow = MutableStateFlow(UserPreferences(
        isDarkMode = false,
        themeSelection = com.jumastappworks.mapstead.data.prefs.ThemeSelection.SYSTEM,
        useDynamicColor = false,
        selectedPropertyId = null,
        selectedBasemapId = com.jumastappworks.mapstead.data.mapping.BasemapId.STREETS,
        measurementSystem = com.jumastappworks.mapstead.data.prefs.MeasurementSystem.IMPERIAL,
        guidanceDismissedPropertyIds = emptySet(),
        gettingStartedDismissedPropertyIds = emptySet(),
        boundaryDisclaimerAcknowledged = false
    ))
    
    @Before
    fun setup() {
        propertyFlow.value = emptyList()
        prefsFlow.value = UserPreferences(
            isDarkMode = false,
            themeSelection = com.jumastappworks.mapstead.data.prefs.ThemeSelection.SYSTEM,
            useDynamicColor = false,
            selectedPropertyId = null,
            selectedBasemapId = com.jumastappworks.mapstead.data.mapping.BasemapId.STREETS,
            measurementSystem = com.jumastappworks.mapstead.data.prefs.MeasurementSystem.IMPERIAL,
            guidanceDismissedPropertyIds = emptySet(),
            gettingStartedDismissedPropertyIds = emptySet(),
            boundaryDisclaimerAcknowledged = false
        )
        
        every { propertyRepo.getAllProperties() } returns propertyFlow
        every { userPrefsRepo.userPreferencesFlow } returns prefsFlow
        coEvery { userPrefsRepo.updateSelectedProperty(any()) } coAnswers {
            val id = it.invocation.args[0] as String?
            val current = prefsFlow.value
            prefsFlow.value = current.copy(selectedPropertyId = id)
        }
    }

    private fun TestScope.createManager() = PropertySelectionManager(propertyRepo, userPrefsRepo, backgroundScope)

    @Test
    fun `one active property adoption requires successful persistence`() = runTest {
        val pid = UUID.randomUUID()
        val property = PropertyEntity(id = pid, name = "P1", propertyType = "Home")
        
        val manager = createManager()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { manager.selectionState.collect() }
        
        propertyFlow.value = listOf(property)
        
        // Wait for the state that confirms we've seen the property but haven't persisted selection yet
        val state = manager.selectionState.first { it is PropertySelectionState.NeedsSelection && it.activeProperties.size == 1 }
        assertNotNull(state)
        
        // Advance to allow background persistence
        advanceUntilIdle()
        
        val finalState = manager.selectionState.first { it is PropertySelectionState.Selected }
        assertEquals(pid, (finalState as PropertySelectionState.Selected).selectedProperty.id)
    }

    @Test
    fun `malformed stored ID is cleared when no properties exist`() = runTest {
        // Setup state: malformed ID in prefs, no properties
        propertyFlow.value = emptyList()
        prefsFlow.value = prefsFlow.value.copy(selectedPropertyId = "not-a-uuid")

        val manager = createManager()
        
        // Wait for it to be cleared
        manager.selectionState.first { it is PropertySelectionState.NoProperties }
        
        // The answer to updateSelectedProperty(null) will set prefsFlow to null
        assertEquals(null, prefsFlow.value.selectedPropertyId)
    }

    @Test
    fun `selection write failure does not terminate monitor`() = runTest {
        val p1Id = UUID.randomUUID()
        val p1 = PropertyEntity(id = p1Id, name = "P1", propertyType = "H")
        
        coEvery { userPrefsRepo.updateSelectedProperty(any()) } throws Exception("Persistence Failed")
        
        val manager = createManager()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { manager.selectionState.collect() }
        
        propertyFlow.value = listOf(p1)
        
        // Wait for the needs selection state (adoption attempt)
        manager.selectionState.first { it is PropertySelectionState.NeedsSelection && it.activeProperties.size == 1 }
        
        advanceUntilIdle()
        
        // Monitor survived? Allow success now.
        coEvery { userPrefsRepo.updateSelectedProperty(any()) } coAnswers {
            val id = it.invocation.args[0] as String?
            val current = prefsFlow.value
            prefsFlow.value = current.copy(selectedPropertyId = id)
        }
        
        // Trigger another update
        val p2Id = UUID.randomUUID()
        val p2 = PropertyEntity(id = p2Id, name = "P2", propertyType = "H")
        propertyFlow.value = listOf(p2)
        
        val state = manager.selectionState.first { it is PropertySelectionState.Selected && it.selectedProperty.id == p2Id }
        assertNotNull(state)
    }

    @Test
    fun `multiple properties results in needs selection`() = runTest {
        val p1 = PropertyEntity(id = UUID.randomUUID(), name = "P1", propertyType = "H")
        val p2 = PropertyEntity(id = UUID.randomUUID(), name = "P2", propertyType = "H")
        
        val manager = createManager()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { manager.selectionState.collect() }
        
        propertyFlow.value = listOf(p1, p2)
        
        val state = manager.selectionState.first { it is PropertySelectionState.NeedsSelection && it.activeProperties.size == 2 }
        assertNotNull(state)
        coVerify(exactly = 0) { userPrefsRepo.updateSelectedProperty(any()) }
    }
}
