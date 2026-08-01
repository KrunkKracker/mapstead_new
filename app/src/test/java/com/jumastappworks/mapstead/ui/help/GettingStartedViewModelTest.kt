package com.jumastappworks.mapstead.ui.help

import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.repository.*
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import com.jumastappworks.mapstead.data.help.*
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
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class GettingStartedViewModelTest {

    private val propRepo = mockk<PropertyRepository>(relaxed = true)
    private val mapRepo = mockk<MapRepository>(relaxed = true)
    private val infraRepo = mockk<InfrastructureRepository>(relaxed = true)
    private val maintenanceRepo = mockk<MaintenanceRepository>(relaxed = true)
    private val attachmentRepo = mockk<AttachmentRepository>(relaxed = true)
    private val userPrefs = mockk<UserPreferencesRepository>(relaxed = true)
    
    private lateinit var selectionManager: PropertySelectionManager
    private lateinit var viewModel: GettingStartedViewModel

    private val prefsFlow = MutableStateFlow(com.jumastappworks.mapstead.data.prefs.UserPreferences(
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
        Dispatchers.setMain(StandardTestDispatcher())
        every { userPrefs.userPreferencesFlow } returns prefsFlow
        every { propRepo.getAllProperties() } returns flowOf(emptyList())
        every { mapRepo.getPlansForProperty(any()) } returns flowOf(emptyList())
        every { infraRepo.getItemsForProperty(any()) } returns flowOf(emptyList())
        every { maintenanceRepo.getRecordsForProperty(any()) } returns flowOf(emptyList())
        every { attachmentRepo.getAttachmentsForProperty(any()) } returns flowOf(emptyList())
        every { mapRepo.getFeaturesForProperty(any()) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.initViewModel() {
        selectionManager = PropertySelectionManager(propRepo, userPrefs, backgroundScope)
        viewModel = GettingStartedViewModel(mapRepo, infraRepo, maintenanceRepo, attachmentRepo, userPrefs, selectionManager)
    }

    @Test
    fun `when no property selected, CREATE_PROPERTY is enabled but others disabled`() = runTest {
        initViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        
        val state = viewModel.uiState.first { it is GettingStartedUiState.Ready || it is GettingStartedUiState.NeedsProperty }
        assertTrue(state is GettingStartedUiState.NeedsProperty)
        val readyState = state as GettingStartedUiState.NeedsProperty
        val createPropStep = readyState.steps.find { it.stepId == GettingStartedStepId.CREATE_PROPERTY }
        val createMapStep = readyState.steps.find { it.stepId == GettingStartedStepId.CREATE_MAP }
        
        assertTrue(createPropStep?.isEnabled == true)
        assertFalse(createMapStep?.isEnabled == true)
    }

    @Test
    fun `when property selected, steps reflect selected property data`() = runTest {
        val propId = UUID.randomUUID()
        prefsFlow.value = prefsFlow.value.copy(selectedPropertyId = propId.toString())
        every { propRepo.getAllProperties() } returns flowOf(listOf(PropertyEntity(id = propId, name = "P", propertyType = "H")))
        every { mapRepo.getPlansForProperty(propId) } returns flowOf(listOf(mockk(relaxed = true)))
        
        initViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        
        val state = viewModel.uiState.first { it is GettingStartedUiState.Ready } as GettingStartedUiState.Ready
        val createPropStep = state.steps.find { it.stepId == GettingStartedStepId.CREATE_PROPERTY }
        val createMapStep = state.steps.find { it.stepId == GettingStartedStepId.CREATE_MAP }
        
        assertTrue("Create Property should be completed", createPropStep?.isCompleted == true)
        assertTrue("Create Map should be completed", createMapStep?.isCompleted == true)
    }

    @Test
    fun `malformed or unknown selectedPropertyId is treated as NeedsSelection if other active properties exist`() = runTest {
        val activeProp = PropertyEntity(id = UUID.randomUUID(), name = "Active", propertyType = "H")
        val prop2 = PropertyEntity(id = UUID.randomUUID(), name = "P2", propertyType = "H")
        every { propRepo.getAllProperties() } returns flowOf(listOf(activeProp, prop2))
        
        // Malformed ID
        prefsFlow.value = prefsFlow.value.copy(selectedPropertyId = "not-a-uuid")
        
        initViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        
        val state = viewModel.uiState.first { 
            it is GettingStartedUiState.NeedsProperty && it.context is GettingStartedPropertyContext.NeedsSelection 
        } as GettingStartedUiState.NeedsProperty
        
        assertTrue("Context should be NeedsSelection", state.context is GettingStartedPropertyContext.NeedsSelection)
        assertFalse("Property specific steps should be disabled", state.steps.find { it.stepId == GettingStartedStepId.CREATE_MAP }?.isEnabled == true)
    }

    @Test
    fun `archived property is not selectable context`() = runTest {
        val archivedProp = PropertyEntity(id = UUID.randomUUID(), name = "Archived", propertyType = "H", deletedAt = java.time.Instant.now())
        every { propRepo.getAllProperties() } returns flowOf(listOf(archivedProp))
        
        prefsFlow.value = prefsFlow.value.copy(selectedPropertyId = archivedProp.id.toString())
        
        initViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        
        val state = viewModel.uiState.first { 
            it is GettingStartedUiState.NeedsProperty && it.context is GettingStartedPropertyContext.NoProperties 
        } as GettingStartedUiState.NeedsProperty
        assertTrue("Context should be NoProperties", state.context is GettingStartedPropertyContext.NoProperties)
    }
}
