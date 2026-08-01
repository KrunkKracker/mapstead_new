package com.jumastappworks.mapstead.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.jumastappworks.mapstead.data.mapping.BasemapId
import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class UserPreferencesRepositoryTest {

    private data class PreferencesFixture(
        val repository: UserPreferencesRepository,
        val dataStore: DataStore<Preferences>,
        val scope: CoroutineScope,
        val file: File
    ) {
        fun close() {
            scope.cancel()
        }
    }

    private fun createFixture(): PreferencesFixture {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "mapstead-test-preferences-${UUID.randomUUID()}.preferences_pb")
        
        if (file.exists()) {
            check(file.delete())
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file }
        )

        return PreferencesFixture(
            repository = UserPreferencesRepository(dataStore),
            dataStore = dataStore,
            scope = scope,
            file = file
        )
    }

    @Test
    fun `default measurement system is Imperial`() = runTest {
        val fixture = createFixture()
        try {
            val prefs = fixture.repository.userPreferencesFlow.first()
            assertEquals(MeasurementSystem.IMPERIAL, prefs.measurementSystem)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `can update measurement system`() = runTest {
        val fixture = createFixture()
        try {
            fixture.repository.updateMeasurementSystem(MeasurementSystem.METRIC)
            val prefs = fixture.repository.userPreferencesFlow.first()
            assertEquals(MeasurementSystem.METRIC, prefs.measurementSystem)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `can return to Imperial from Metric`() = runTest {
        val fixture = createFixture()
        try {
            fixture.repository.updateMeasurementSystem(MeasurementSystem.METRIC)
            fixture.repository.updateMeasurementSystem(MeasurementSystem.IMPERIAL)
            val prefs = fixture.repository.userPreferencesFlow.first()
            assertEquals(MeasurementSystem.IMPERIAL, prefs.measurementSystem)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `guidance dismissed defaults to empty`() = runTest {
        val fixture = createFixture()
        try {
            val prefs = fixture.repository.userPreferencesFlow.first()
            assertTrue(prefs.guidanceDismissedPropertyIds.isEmpty())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `guidance dismissed persists property id`() = runTest {
        val fixture = createFixture()
        try {
            val propId = "test-prop"
            fixture.repository.updateGuidanceDismissed(propId, true)
            val prefs = fixture.repository.userPreferencesFlow.first()
            assertTrue(prefs.guidanceDismissedPropertyIds.contains(propId))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `boundary disclaimer acknowledged defaults to false`() = runTest {
        val fixture = createFixture()
        try {
            val prefs = fixture.repository.userPreferencesFlow.first()
            assertFalse(prefs.boundaryDisclaimerAcknowledged)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `boundary disclaimer acknowledged persists true`() = runTest {
        val fixture = createFixture()
        try {
            fixture.repository.updateBoundaryDisclaimerAcknowledged(true)
            val prefs = fixture.repository.userPreferencesFlow.first()
            assertTrue(prefs.boundaryDisclaimerAcknowledged)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `parseMeasurementSystem handles invalid values`() {
        assertEquals(MeasurementSystem.IMPERIAL, UserPreferencesRepository.parseMeasurementSystem(null))
        assertEquals(MeasurementSystem.METRIC, UserPreferencesRepository.parseMeasurementSystem("METRIC"))
        assertEquals(MeasurementSystem.IMPERIAL, UserPreferencesRepository.parseMeasurementSystem("IMPERIAL"))
        assertEquals(MeasurementSystem.IMPERIAL, UserPreferencesRepository.parseMeasurementSystem("INVALID"))
    }

    @Test
    fun `parseBasemapId handles invalid values`() {
        assertEquals(BasemapId.STREETS, UserPreferencesRepository.parseBasemapId(null))
        assertEquals(BasemapId.SATELLITE_HYBRID, UserPreferencesRepository.parseBasemapId("SATELLITE_HYBRID"))
        assertEquals(BasemapId.STREETS, UserPreferencesRepository.parseBasemapId("INVALID"))
    }

    @Test
    fun `parseThemeSelection handles invalid values`() {
        assertEquals(ThemeSelection.SYSTEM, UserPreferencesRepository.parseThemeSelection(null))
        assertEquals(ThemeSelection.DARK, UserPreferencesRepository.parseThemeSelection("DARK"))
        assertEquals(ThemeSelection.SYSTEM, UserPreferencesRepository.parseThemeSelection("INVALID"))
    }

    @Test
    fun `starter layer bindings merging preserves other types and plans`() = runTest {
        val fixture = createFixture()
        try {
            val planA = UUID.randomUUID().toString()
            val planB = UUID.randomUUID().toString()
            val layer1 = UUID.randomUUID()
            val layer2 = UUID.randomUUID()
            val layer3 = UUID.randomUUID()

            // 1. Initial save for Plan A
            fixture.repository.saveStarterLayerBindings(planA, mapOf(com.jumastappworks.mapstead.data.mapping.SuggestedMapLayer.BUILDINGS_BOUNDARIES to layer1))
            
            // 2. Save another type for Plan A
            fixture.repository.saveStarterLayerBindings(planA, mapOf(com.jumastappworks.mapstead.data.mapping.SuggestedMapLayer.UTILITIES to layer2))
            
            // 3. Save for Plan B
            fixture.repository.saveStarterLayerBindings(planB, mapOf(com.jumastappworks.mapstead.data.mapping.SuggestedMapLayer.BUILDINGS_BOUNDARIES to layer3))

            val prefs = fixture.repository.userPreferencesFlow.first()
            val bindings = prefs.starterLayerBindings
            
            assertEquals(3, bindings.size)
            assertEquals(layer1, fixture.repository.getStarterLayerBinding(planA, com.jumastappworks.mapstead.data.mapping.SuggestedMapLayer.BUILDINGS_BOUNDARIES, bindings))
            assertEquals(layer2, fixture.repository.getStarterLayerBinding(planA, com.jumastappworks.mapstead.data.mapping.SuggestedMapLayer.UTILITIES, bindings))
            assertEquals(layer3, fixture.repository.getStarterLayerBinding(planB, com.jumastappworks.mapstead.data.mapping.SuggestedMapLayer.BUILDINGS_BOUNDARIES, bindings))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `malformed starter layer bindings are removed safely`() = runTest {
        val fixture = createFixture()
        try {
            val planId = UUID.randomUUID().toString()
            val layerId = UUID.randomUUID()
            
            // Manual injection of malformed data via DataStore.edit would be better but I'll use the repository method with a valid one first
            fixture.repository.saveStarterLayerBindings(planId, mapOf(com.jumastappworks.mapstead.data.mapping.SuggestedMapLayer.BUILDINGS_BOUNDARIES to layerId))
            
            // Now inject a malformed one
            fixture.dataStore.edit { preferences ->
                val key = androidx.datastore.preferences.core.stringSetPreferencesKey("starter_layer_bindings")
                val existing = preferences[key] ?: emptySet<String>()
                val updated = existing.toMutableSet()
                updated.add("malformed|record")
                updated.add("invalid-plan|BUILDINGS_BOUNDARIES|${UUID.randomUUID()}")
                preferences[key] = updated
            }

            // Save a new one which should trigger filtering
            fixture.repository.saveStarterLayerBindings(planId, mapOf(com.jumastappworks.mapstead.data.mapping.SuggestedMapLayer.UTILITIES to UUID.randomUUID()))

            val prefs = fixture.repository.userPreferencesFlow.first()
            val bindings = prefs.starterLayerBindings
            
            // All malformed ones should be gone
            assertTrue(bindings.all { it.split("|").size == 3 })
        } finally {
            fixture.close()
        }
    }
}
