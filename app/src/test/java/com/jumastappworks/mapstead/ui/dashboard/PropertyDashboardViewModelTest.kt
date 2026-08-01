package com.jumastappworks.mapstead.ui.dashboard

import com.jumastappworks.mapstead.data.db.entities.MaintenanceRecordEntity
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.help.GettingStartedStepId
import com.jumastappworks.mapstead.data.repository.*
import com.jumastappworks.mapstead.util.MaintenanceStatus
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class PropertyDashboardViewModelTest {

    private val propRepo = mockk<PropertyRepository>(relaxed = true)
    private val mapRepo = mockk<MapRepository>(relaxed = true)
    private val infraRepo = mockk<InfrastructureRepository>(relaxed = true)
    private val maintenanceRepo = mockk<MaintenanceRepository>(relaxed = true)
    private val attachmentRepo = mockk<AttachmentRepository>(relaxed = true)
    private val userPrefs = mockk<com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository>(relaxed = true)

    private lateinit var viewModel: PropertyDashboardViewModel

    private val propertyId = UUID.randomUUID()
    private val property = PropertyEntity(id = propertyId, name = "Test Property", propertyType = "Home")

    private val maintenanceFlow = MutableStateFlow<List<MaintenanceRecordEntity>>(emptyList())
    private val plansFlow = MutableStateFlow<List<com.jumastappworks.mapstead.data.db.entities.PlanEntity>>(emptyList())
    private val itemsFlow = MutableStateFlow<List<com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity>>(emptyList())
    private val emergencyFlow = MutableStateFlow<List<com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity>>(emptyList())
    private val attachmentsFlow = MutableStateFlow<List<com.jumastappworks.mapstead.data.db.entities.AttachmentEntity>>(emptyList())
    private val featuresFlow = MutableStateFlow<List<com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity>>(emptyList())
    private val allPropertiesFlow = MutableStateFlow<List<PropertyEntity>>(emptyList())
    private val prefsFlow = MutableStateFlow(com.jumastappworks.mapstead.data.prefs.UserPreferences(
        isDarkMode = false,
        themeSelection = com.jumastappworks.mapstead.data.prefs.ThemeSelection.SYSTEM,
        useDynamicColor = false,
        selectedPropertyId = null,
        selectedBasemapId = com.jumastappworks.mapstead.data.mapping.BasemapId.STREETS,
        guidanceDismissedPropertyIds = emptySet(),
        gettingStartedDismissedPropertyIds = emptySet()
    ))

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        
        coEvery { propRepo.getPropertyById(any()) } returns property
        every { propRepo.getAllProperties() } returns allPropertiesFlow
        every { mapRepo.getPlansForProperty(any()) } returns plansFlow
        every { mapRepo.getFeaturesForProperty(any()) } returns featuresFlow
        every { infraRepo.getItemsForProperty(any()) } returns itemsFlow
        every { infraRepo.getEmergencyItems(any()) } returns emergencyFlow
        every { maintenanceRepo.getRecordsForProperty(any()) } returns maintenanceFlow
        every { attachmentRepo.getAttachmentsForProperty(any()) } returns attachmentsFlow
        every { userPrefs.userPreferencesFlow } returns prefsFlow

        viewModel = PropertyDashboardViewModel(propRepo, mapRepo, infraRepo, maintenanceRepo, attachmentRepo, userPrefs)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun testInitialLoadingState() = runTest {
        assertTrue(viewModel.uiState.value is PropertyDashboardUiState.Loading)
    }

    @Test
    fun testReadyStateAndDueMaintenanceLogic() = runTest {
        backgroundScope.launch { viewModel.uiState.collect() }
        
        val today = LocalDate.now()
        val records = listOf(
            MaintenanceRecordEntity(propertyId = propertyId, title = "R1", category = "C", status = "Scheduled", nextDueDate = today.minusDays(1), serviceDate = today.minusMonths(1)),
            MaintenanceRecordEntity(propertyId = propertyId, title = "R2", category = "C", status = "In Progress", nextDueDate = today, serviceDate = today.minusMonths(1))
        )
        
        maintenanceFlow.value = records
        allPropertiesFlow.value = listOf(property)
        viewModel.setPropertyId(propertyId)
        
        val state = viewModel.uiState.first { it is PropertyDashboardUiState.Ready }
        assertTrue(state is PropertyDashboardUiState.Ready)
        val ready = state as PropertyDashboardUiState.Ready
        assertEquals(2, ready.dueMaintenanceCount)
    }

    @Test
    fun testPropertyNotFound() = runTest {
        backgroundScope.launch { viewModel.uiState.collect() }
        
        val targetId = UUID.randomUUID()
        coEvery { propRepo.getPropertyById(targetId) } returns null
        maintenanceFlow.value = emptyList()
        plansFlow.value = emptyList()
        itemsFlow.value = emptyList()
        emergencyFlow.value = emptyList()
        attachmentsFlow.value = emptyList()
        featuresFlow.value = emptyList()
        allPropertiesFlow.value = emptyList()
        
        viewModel.setPropertyId(targetId)
        
        val state = viewModel.uiState.first { it is PropertyDashboardUiState.NotFound }
        assertTrue("State should be NotFound but was $state", state is PropertyDashboardUiState.NotFound)
    }

    @Test
    fun testChecklistCompletionLogic() = runTest {
        backgroundScope.launch { viewModel.uiState.collect() }
        
        allPropertiesFlow.value = listOf(property)
        maintenanceFlow.value = emptyList()
        plansFlow.value = emptyList()
        itemsFlow.value = emptyList()
        emergencyFlow.value = emptyList()
        attachmentsFlow.value = emptyList()
        featuresFlow.value = emptyList()

        viewModel.setPropertyId(propertyId)
        
        // Initial: Only property created
        val state = viewModel.uiState.first { it is PropertyDashboardUiState.Ready } as PropertyDashboardUiState.Ready
        assertTrue(state.checklist.find { it.stepId == GettingStartedStepId.CREATE_PROPERTY }?.isCompleted == true)
        assertFalse(state.checklist.find { it.stepId == GettingStartedStepId.CREATE_MAP }?.isCompleted == true)
        
        // Add a plan
        plansFlow.value = listOf(mockk(relaxed = true))
        val updatedState = viewModel.uiState.first { (it as? PropertyDashboardUiState.Ready)?.checklist?.find { c -> c.stepId == GettingStartedStepId.CREATE_MAP }?.isCompleted == true }
        assertTrue((updatedState as PropertyDashboardUiState.Ready).checklist.find { it.stepId == GettingStartedStepId.CREATE_MAP }?.isCompleted == true)
    }

    @Test
    fun testChecklistDismissal() = runTest {
        backgroundScope.launch { viewModel.uiState.collect() }
        allPropertiesFlow.value = listOf(property)
        maintenanceFlow.value = emptyList()
        plansFlow.value = emptyList()
        itemsFlow.value = emptyList()
        emergencyFlow.value = emptyList()
        attachmentsFlow.value = emptyList()
        featuresFlow.value = emptyList()
        
        viewModel.setPropertyId(propertyId)
        
        val state = viewModel.uiState.first { it is PropertyDashboardUiState.Ready } as PropertyDashboardUiState.Ready
        assertTrue(state.showChecklist)
        
        viewModel.dismissChecklist()
        
        prefsFlow.value = prefsFlow.value.copy(gettingStartedDismissedPropertyIds = setOf(propertyId.toString()))
        val dismissedState = viewModel.uiState.first { (it as? PropertyDashboardUiState.Ready)?.showChecklist == false }
        assertFalse((dismissedState as PropertyDashboardUiState.Ready).showChecklist)
        coVerify { userPrefs.updateGettingStartedDismissed(propertyId.toString(), true) }
    }
}
