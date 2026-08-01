package com.jumastappworks.mapstead.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.jumastappworks.mapstead.data.mapping.BasemapId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class UserPreferencesResetTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun createRepository(scope: kotlinx.coroutines.CoroutineScope): UserPreferencesRepository {
        val file = temporaryFolder.newFile("test_prefs_${UUID.randomUUID()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file }
        )
        return UserPreferencesRepository(dataStore)
    }

    @Test
    fun `reopenGettingStarted only affects gettingStartedDismissed`() = runTest {
        val repository = createRepository(backgroundScope)
        val prop1 = "prop1"
        
        repository.updateGettingStartedDismissed(prop1, true)
        repository.updateGuidanceDismissed(prop1, true)
        repository.markStarterLayersCompleted("plan1")
        
        repository.reopenGettingStarted(prop1)
        
        val prefs = repository.userPreferencesFlow.first()
        assertFalse(prefs.gettingStartedDismissedPropertyIds.contains(prop1))
        assertTrue(prefs.guidanceDismissedPropertyIds.contains(prop1))
        assertEquals(setOf("plan1"), prefs.starterLayersCompletedPlanIds)
    }

    @Test
    fun `resetMapGuidance only affects guidanceDismissed`() = runTest {
        val repository = createRepository(backgroundScope)
        val prop1 = "prop1"
        
        repository.updateGuidanceDismissed(prop1, true)
        repository.updateBoundaryDisclaimerAcknowledged(true)
        
        repository.resetMapGuidance()
        
        val prefs = repository.userPreferencesFlow.first()
        assertFalse(prefs.guidanceDismissedPropertyIds.contains(prop1))
        assertTrue(prefs.boundaryDisclaimerAcknowledged)
    }

    @Test
    fun `resetBoundaryAcknowledgment requires dedicated call`() = runTest {
        val repository = createRepository(backgroundScope)
        
        repository.updateBoundaryDisclaimerAcknowledged(true)
        repository.resetOnboardingGuidance()
        
        var prefs = repository.userPreferencesFlow.first()
        assertTrue("Broad reset should NOT clear boundary acknowledgment", prefs.boundaryDisclaimerAcknowledged)
        
        repository.resetBoundaryAcknowledgment()
        prefs = repository.userPreferencesFlow.first()
        assertFalse(prefs.boundaryDisclaimerAcknowledged)
    }

    @Test
    fun `resetWelcomeGuidance only affects welcomeDismissed`() = runTest {
        val repository = createRepository(backgroundScope)
        val prop1 = "prop1"
        repository.updateWelcomeDismissed(true)
        repository.updateGuidanceDismissed(prop1, true)
        
        repository.resetWelcomeGuidance()
        
        val prefs = repository.userPreferencesFlow.first()
        assertFalse(prefs.welcomeDismissed)
        assertTrue(prefs.guidanceDismissedPropertyIds.contains(prop1))
    }

    @Test
    fun `emergency reviewed persistence works per property`() = runTest {
        val repository = createRepository(backgroundScope)
        
        val propId = UUID.randomUUID().toString()
        repository.markEmergencyReviewed(propId)
        
        val prefs = repository.userPreferencesFlow.first()
        assertTrue(prefs.emergencyReviewedPropertyIds.contains(propId))
    }
}
