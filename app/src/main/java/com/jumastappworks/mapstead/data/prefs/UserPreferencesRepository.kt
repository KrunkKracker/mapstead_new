package com.jumastappworks.mapstead.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.jumastappworks.mapstead.data.mapping.BasemapId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeSelection {
    SYSTEM, LIGHT, DARK
}

enum class MeasurementSystem {
    IMPERIAL, METRIC
}

data class UserPreferences(
    val isDarkMode: Boolean = false, // legacy
    val themeSelection: ThemeSelection = ThemeSelection.SYSTEM,
    val useDynamicColor: Boolean = false,
    val selectedPropertyId: String? = null,
    val selectedBasemapId: BasemapId = BasemapId.STREETS,
    val measurementSystem: MeasurementSystem = MeasurementSystem.IMPERIAL,
    val guidanceDismissedPropertyIds: Set<String> = emptySet(),
    val gettingStartedDismissedPropertyIds: Set<String> = emptySet(),
    val welcomeDismissed: Boolean = false,
    val boundaryDisclaimerAcknowledged: Boolean = false,
    val starterLayersCompletedPlanIds: Set<String> = emptySet(),
    val starterLayerBindings: Set<String> = emptySet(),
    val emergencyReviewedPropertyIds: Set<String> = emptySet()
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object PreferencesKeys {
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val THEME_SELECTION = stringPreferencesKey("theme_selection")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val SELECTED_PROPERTY_ID = stringPreferencesKey("selected_property_id")
        val SELECTED_BASEMAP_ID = stringPreferencesKey("selected_basemap_id")
        val MEASUREMENT_SYSTEM = stringPreferencesKey("measurement_system")
        val GUIDANCE_DISMISSED_PROPERTY_IDS = stringSetPreferencesKey("guidance_dismissed_property_ids")
        val BOUNDARY_DISCLAIMER_ACKNOWLEDGED = booleanPreferencesKey("boundary_disclaimer_acknowledged")
        val STARTER_LAYERS_COMPLETED_PLAN_IDS = stringSetPreferencesKey("starter_layers_completed_plan_ids")
        val STARTER_LAYER_BINDINGS = stringSetPreferencesKey("starter_layer_bindings")
        val GETTING_STARTED_DISMISSED_PROPERTY_IDS = stringSetPreferencesKey("getting_started_dismissed_property_ids")
        val WELCOME_DISMISSED = booleanPreferencesKey("welcome_dismissed")
        val EMERGENCY_REVIEWED_PROPERTY_IDS = stringSetPreferencesKey("emergency_reviewed_property_ids")
    }

    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val isDarkMode = preferences[PreferencesKeys.IS_DARK_MODE] ?: false
            
            val themeSelection = parseThemeSelection(preferences[PreferencesKeys.THEME_SELECTION])
            val useDynamicColor = preferences[PreferencesKeys.USE_DYNAMIC_COLOR] ?: false
            val selectedPropertyId = preferences[PreferencesKeys.SELECTED_PROPERTY_ID]
            
            val selectedBasemapId = parseBasemapId(preferences[PreferencesKeys.SELECTED_BASEMAP_ID])
            val measurementSystem = parseMeasurementSystem(preferences[PreferencesKeys.MEASUREMENT_SYSTEM])
            
            val guidanceDismissed = preferences[PreferencesKeys.GUIDANCE_DISMISSED_PROPERTY_IDS] ?: emptySet()
            val gettingStartedDismissed = preferences[PreferencesKeys.GETTING_STARTED_DISMISSED_PROPERTY_IDS] ?: emptySet()
            val welcomeDismissed = preferences[PreferencesKeys.WELCOME_DISMISSED] ?: false
            val boundaryAck = preferences[PreferencesKeys.BOUNDARY_DISCLAIMER_ACKNOWLEDGED] ?: false
            val starterLayersPlans = preferences[PreferencesKeys.STARTER_LAYERS_COMPLETED_PLAN_IDS] ?: emptySet()
            val layerBindings = preferences[PreferencesKeys.STARTER_LAYER_BINDINGS] ?: emptySet()
            val emergencyReviewed = preferences[PreferencesKeys.EMERGENCY_REVIEWED_PROPERTY_IDS] ?: emptySet()

            UserPreferences(
                isDarkMode, themeSelection, useDynamicColor, selectedPropertyId, 
                selectedBasemapId, measurementSystem, guidanceDismissed,
                gettingStartedDismissed, welcomeDismissed, boundaryAck,
                starterLayersPlans, layerBindings, emergencyReviewed
            )
        }

    suspend fun updateDarkMode(isDarkMode: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_DARK_MODE] = isDarkMode
        }
    }

    suspend fun updateThemeSelection(selection: ThemeSelection) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_SELECTION] = selection.name
        }
    }

    suspend fun updateUseDynamicColor(useDynamicColor: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_DYNAMIC_COLOR] = useDynamicColor
        }
    }

    suspend fun updateSelectedProperty(propertyId: String?) {
        dataStore.edit { preferences ->
            if (propertyId != null) {
                preferences[PreferencesKeys.SELECTED_PROPERTY_ID] = propertyId
            } else {
                preferences.remove(PreferencesKeys.SELECTED_PROPERTY_ID)
            }
        }
    }

    suspend fun updateSelectedBasemap(basemapId: BasemapId) {
        try {
            dataStore.edit { preferences ->
                preferences[PreferencesKeys.SELECTED_BASEMAP_ID] = basemapId.name
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // Catch ordinary write failure to prevent crash
        }
    }

    suspend fun updateMeasurementSystem(system: MeasurementSystem) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MEASUREMENT_SYSTEM] = system.name
        }
    }

    suspend fun updateGuidanceDismissed(propertyId: String, dismissed: Boolean) {
        dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.GUIDANCE_DISMISSED_PROPERTY_IDS] ?: emptySet()
            preferences[PreferencesKeys.GUIDANCE_DISMISSED_PROPERTY_IDS] = if (dismissed) current + propertyId else current - propertyId
        }
    }

    suspend fun updateGettingStartedDismissed(propertyId: String, dismissed: Boolean) {
        dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.GETTING_STARTED_DISMISSED_PROPERTY_IDS] ?: emptySet()
            preferences[PreferencesKeys.GETTING_STARTED_DISMISSED_PROPERTY_IDS] = if (dismissed) current + propertyId else current - propertyId
        }
    }

    suspend fun updateWelcomeDismissed(dismissed: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.WELCOME_DISMISSED] = dismissed
        }
    }

    suspend fun resetOnboardingGuidance() {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.GUIDANCE_DISMISSED_PROPERTY_IDS] = emptySet()
            preferences[PreferencesKeys.GETTING_STARTED_DISMISSED_PROPERTY_IDS] = emptySet()
            preferences[PreferencesKeys.WELCOME_DISMISSED] = false
        }
    }

    suspend fun reopenGettingStarted(propertyId: String) {
        dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.GETTING_STARTED_DISMISSED_PROPERTY_IDS] ?: emptySet()
            preferences[PreferencesKeys.GETTING_STARTED_DISMISSED_PROPERTY_IDS] = current - propertyId
        }
    }

    suspend fun resetMapGuidance() {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.GUIDANCE_DISMISSED_PROPERTY_IDS] = emptySet()
        }
    }

    suspend fun resetWelcomeGuidance() {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.WELCOME_DISMISSED] = false
        }
    }

    suspend fun markEmergencyReviewed(propertyId: String) {
        dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.EMERGENCY_REVIEWED_PROPERTY_IDS] ?: emptySet()
            preferences[PreferencesKeys.EMERGENCY_REVIEWED_PROPERTY_IDS] = current + propertyId
        }
    }

    suspend fun resetBoundaryAcknowledgment() {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.BOUNDARY_DISCLAIMER_ACKNOWLEDGED] = false
        }
    }

    suspend fun updateBoundaryDisclaimerAcknowledged(acknowledged: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.BOUNDARY_DISCLAIMER_ACKNOWLEDGED] = acknowledged
        }
    }

    suspend fun markStarterLayersCompleted(planId: String) {
        dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.STARTER_LAYERS_COMPLETED_PLAN_IDS] ?: emptySet()
            preferences[PreferencesKeys.STARTER_LAYERS_COMPLETED_PLAN_IDS] = current + planId
        }
    }

    suspend fun saveStarterLayerBindings(planId: String, mapping: Map<com.jumastappworks.mapstead.data.mapping.SuggestedMapLayer, java.util.UUID>) {
        dataStore.edit { preferences ->
            val current = (preferences[PreferencesKeys.STARTER_LAYER_BINDINGS] ?: emptySet()).toMutableSet()
            
            mapping.forEach { (type, layerId) ->
                val prefix = "$planId|${type.name}|"
                // Remove any old record for this exact plan/type pair
                current.removeIf { it.startsWith(prefix) }
                // Add the new record
                current.add("$prefix$layerId")
            }
            
            // Safe filtering of malformed records
            val validRecords = current.filter { record ->
                val parts = record.split("|")
                if (parts.size != 3) return@filter false
                try {
                    java.util.UUID.fromString(parts[0]) // planId
                    com.jumastappworks.mapstead.data.mapping.SuggestedMapLayer.valueOf(parts[1]) // type
                    java.util.UUID.fromString(parts[2]) // layerId
                    true
                } catch (e: Exception) {
                    false
                }
            }.toSet()

            preferences[PreferencesKeys.STARTER_LAYER_BINDINGS] = validRecords
        }
    }

    fun getStarterLayerBinding(planId: String, type: com.jumastappworks.mapstead.data.mapping.SuggestedMapLayer, bindings: Set<String>): java.util.UUID? {
        val prefix = "$planId|${type.name}|"
        val record = bindings.find { it.startsWith(prefix) } ?: return null
        return try {
            java.util.UUID.fromString(record.substring(prefix.length))
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        fun parseThemeSelection(value: String?): ThemeSelection {
            return try {
                ThemeSelection.valueOf(value ?: ThemeSelection.SYSTEM.name)
            } catch (e: Exception) {
                ThemeSelection.SYSTEM
            }
        }

        fun parseBasemapId(value: String?): BasemapId {
            if (value.isNullOrBlank()) return BasemapId.STREETS
            return when (value) {
                "STREET", "STREETS" -> BasemapId.STREETS
                "SATELLITE", "SATELLITE_HYBRID" -> BasemapId.SATELLITE_HYBRID
                "OUTDOORS", "OUTDOOR" -> BasemapId.OUTDOOR
                "LIGHT", "BASE" -> BasemapId.BASE
                "TOPO" -> BasemapId.TOPO
                "DARK" -> BasemapId.STREETS // Per instructions
                else -> {
                    try { BasemapId.valueOf(value) } catch (e: Exception) { BasemapId.STREETS }
                }
            }
        }

        fun parseMeasurementSystem(value: String?): MeasurementSystem {
            return try {
                MeasurementSystem.valueOf(value ?: MeasurementSystem.IMPERIAL.name)
            } catch (e: Exception) {
                MeasurementSystem.IMPERIAL
            }
        }
    }
}
