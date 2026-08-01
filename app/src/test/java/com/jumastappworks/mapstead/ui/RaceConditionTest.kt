package com.jumastappworks.mapstead.ui

import androidx.lifecycle.SavedStateHandle
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.data.relationships.RelationshipWriteResult
import com.jumastappworks.mapstead.data.repository.*
import com.jumastappworks.mapstead.ui.mapping.MapViewModel
import com.jumastappworks.mapstead.ui.relationships.ParentEditorViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class RaceConditionTest {

    private val testDispatcher = StandardTestDispatcher()
    
    // Mocks for MapViewModel
    private val mapRepo = mockk<MapRepository>(relaxed = true)
    private val attachmentRepo = mockk<AttachmentRepository>(relaxed = true)
    private val infraRepo = mockk<InfrastructureRepository>(relaxed = true)
    private val propRepo = mockk<PropertyRepository>(relaxed = true)
    private val resolver = mockk<MapFeatureContextResolver>(relaxed = true)
    private val locationProvider = mockk<CurrentLocationProvider>(relaxed = true)
    private val basemapProvider = mockk<BasemapProvider>(relaxed = true)
    private val userPrefs = mockk<com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository>(relaxed = true)
    private val namingService = mockk<com.jumastappworks.mapstead.data.mapping.FeatureNamingService>(relaxed = true)
    private val context = mockk<android.content.Context>(relaxed = true)
    private val savedStateHandle = SavedStateHandle()

    // Mocks for ParentEditorViewModel
    private val relRepo = mockk<InfrastructureRelationshipRepository>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { basemapProvider.defaultBasemap() } returns BasemapId.STREETS
        every { userPrefs.userPreferencesFlow } returns flowOf(
            com.jumastappworks.mapstead.data.prefs.UserPreferences(
                isDarkMode = false,
                themeSelection = com.jumastappworks.mapstead.data.prefs.ThemeSelection.SYSTEM,
                useDynamicColor = false,
                selectedPropertyId = null,
                selectedBasemapId = BasemapId.STREETS,
                measurementSystem = com.jumastappworks.mapstead.data.prefs.MeasurementSystem.IMPERIAL,
                guidanceDismissedPropertyIds = emptySet(),
                gettingStartedDismissedPropertyIds = emptySet(),
                boundaryDisclaimerAcknowledged = false
            )
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Parent rapid calls produce one repository operation`() = runTest {
        val viewModel = ParentEditorViewModel(relRepo, infraRepo)
        val propId = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        
        // Setup initial state
        coEvery { infraRepo.getActiveItemForProperty(propId, itemId) } returns mockk { every { name } returns "Item"; every { parentItemId } returns null }
        coEvery { infraRepo.getItemsForProperty(propId) } returns flowOf(emptyList())
        
        viewModel.init(propId, itemId)
        advanceUntilIdle()

        coEvery { relRepo.setParent(any(), any(), any()) } coAnswers {
            kotlinx.coroutines.delay(1000)
            RelationshipWriteResult.Success(UUID.randomUUID())
        }

        viewModel.setParent(UUID.randomUUID())
        // Second call while isSaving is true
        viewModel.setParent(UUID.randomUUID())
        
        advanceUntilIdle()
        
        coVerify(exactly = 1) { relRepo.setParent(any(), any(), any()) }
    }

    @Test
    fun `Feature rapid delete calls produce one repository operation`() = runTest {
        val viewModel = MapViewModel(mapRepo, attachmentRepo, infraRepo, propRepo, resolver, locationProvider, basemapProvider, userPrefs, namingService, context, savedStateHandle)
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        
        viewModel.setProperty(propId)
        viewModel.selectPlan(planId)
        advanceUntilIdle()

        coEvery { mapRepo.softDeleteFeatureWithAttachments(any(), any(), any()) } coAnswers {
            kotlinx.coroutines.delay(1000)
            com.jumastappworks.mapstead.data.attachments.AttachmentDeleteState.Deleted
        }

        viewModel.deleteFeature(featureId)
        // Second call while isDeletingFeature is true
        viewModel.deleteFeature(featureId)
        
        advanceUntilIdle()
        
        coVerify(exactly = 1) { mapRepo.softDeleteFeatureWithAttachments(any(), any(), any()) }
    }
}
