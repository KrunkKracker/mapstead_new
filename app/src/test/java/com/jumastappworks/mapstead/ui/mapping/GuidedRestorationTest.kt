package com.jumastappworks.mapstead.ui.mapping

import androidx.lifecycle.SavedStateHandle
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.db.entities.LayerEntity
import com.jumastappworks.mapstead.data.db.entities.PlanEntity
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.data.repository.*
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class GuidedRestorationTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val mapRepo = mockk<MapRepository>(relaxed = true)
    private val infraRepo = mockk<InfrastructureRepository>(relaxed = true)
    private val propertyRepo = mockk<PropertyRepository>(relaxed = true)
    private val userPrefs = mockk<UserPreferencesRepository>(relaxed = true)
    private val namingService = mockk<FeatureNamingService>(relaxed = true)
    private val context = mockk<android.content.Context>(relaxed = true)
    
    private val propertyId = UUID.randomUUID()
    private val planId = UUID.randomUUID()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val layer = LayerEntity(id = UUID.randomUUID(), propertyId = propertyId, planId = planId, name = "L", category = "C")
        val plan = PlanEntity(id = planId, propertyId = propertyId, name = "P", planType = "T", backgroundType = "B")
        
        every { userPrefs.userPreferencesFlow } returns flowOf(com.jumastappworks.mapstead.data.prefs.UserPreferences())
        every { context.getString(any()) } returns "Localized"
        every { mapRepo.getLayersForPlan(planId) } returns flowOf(listOf(layer))
        coEvery { mapRepo.getPlanById(planId) } returns plan
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `incomplete restored session is safely discarded during init`() = runTest {
        val sid = UUID.randomUUID()
        val did = UUID.randomUUID()
        val handle = SavedStateHandle(mapOf(
            "guided_session_id" to sid.toString(),
            "guided_preset_id" to GuidedMapPresetId.WELL.name,
            "guided_draft_id" to did.toString(),
            "guided_phase" to GuidedMappingPhase.REVIEWING.name,
            "guided_name" to "Restored Well"
        ))

        val viewModel = MapViewModel(
            mapRepo, mockk(relaxed = true), infraRepo, propertyRepo, 
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), 
            userPrefs, namingService, context, handle
        )
        
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertNull("Session must be discarded per Strategy B in this pass", state.guidedSession)
        assertEquals(R.string.unfinished_item_not_saved, state.mapErrorRes)
    }
}
