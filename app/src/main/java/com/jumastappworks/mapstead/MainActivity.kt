package com.jumastappworks.mapstead

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import com.jumastappworks.mapstead.ui.theme.MapsteadTheme
import com.jumastappworks.mapstead.ui.navigation.MapsteadNavGraph
import com.jumastappworks.mapstead.data.mapping.BasemapProvider
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import com.jumastappworks.mapstead.data.prefs.ThemeSelection
import com.jumastappworks.mapstead.data.repository.InfrastructureRelationshipRepository
import com.jumastappworks.mapstead.data.repository.MapRepository
import com.jumastappworks.mapstead.data.repository.PropertyRepository
import com.jumastappworks.mapstead.data.repository.PropertySelectionManager
import com.jumastappworks.mapstead.util.UuidHelper
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var basemapProvider: BasemapProvider

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var backupFeatureGate: com.jumastappworks.mapstead.data.backup.BackupFeatureGate

    @Inject
    lateinit var relationshipRepository: InfrastructureRelationshipRepository

    @Inject
    lateinit var mapRepository: MapRepository

    @Inject
    lateinit var propertyRepository: com.jumastappworks.mapstead.data.repository.PropertyRepository

    @Inject
    lateinit var selectionManager: PropertySelectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val propertyIdExtra = intent?.getStringExtra("property_id")
        val itemIdExtra = intent?.getStringExtra("item_id")
        val recordIdExtra = intent?.getStringExtra("maintenance_record_id")
        val reminderIdExtra = intent?.getStringExtra("reminder_id")

        val parsedPropertyId = UuidHelper.safeParse(propertyIdExtra)
        val parsedItemId = UuidHelper.safeParse(itemIdExtra)
        val parsedRecordId = UuidHelper.safeParse(recordIdExtra)
        val parsedReminderId = UuidHelper.safeParse(reminderIdExtra)

        setContent {
            val prefs by userPreferencesRepository.userPreferencesFlow.collectAsState(initial = null)
            val systemDark = isSystemInDarkTheme()
            
            val isDark = when (prefs?.themeSelection) {
                ThemeSelection.LIGHT -> false
                ThemeSelection.DARK -> true
                else -> systemDark
            }
            val useDynamic = prefs?.useDynamicColor ?: false

                MapsteadTheme(darkTheme = isDark, dynamicColor = useDynamic) {
                    MapsteadNavGraph(
                        basemapProvider = basemapProvider,
                        userPreferencesRepository = userPreferencesRepository,
                        relationshipRepository = relationshipRepository,
                        propertyRepository = propertyRepository,
                        selectionManager = selectionManager,
                        initialPropertyId = parsedPropertyId,
                        initialItemId = parsedItemId,
                        initialRecordId = parsedRecordId,
                        initialReminderId = parsedReminderId,
                        isBackupEnabled = backupFeatureGate.isEnabled
                    )
                }
        }
    }
}
