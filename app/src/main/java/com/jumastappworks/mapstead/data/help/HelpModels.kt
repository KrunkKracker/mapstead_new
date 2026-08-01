package com.jumastappworks.mapstead.data.help

import com.jumastappworks.mapstead.data.db.entities.PropertyEntity

enum class HelpTopicId {
    GETTING_STARTED,
    PROPERTIES,
    PROPERTY_MAPS,
    ADD_TO_MAP,
    GPS_AND_ACCURACY,
    LAYERS,
    INFRASTRUCTURE,
    CONNECTIONS,
    MAINTENANCE,
    REMINDERS,
    PHOTOS_AND_FILES,
    EMERGENCY_MODE,
    REPORTS_AND_HANDOFF,
    BACKUP_AND_RESTORE,
    MEASUREMENTS_AND_UNITS,
    SAFETY_AND_LIMITATIONS
}

enum class SettingsSectionId {
    APPEARANCE, MEASUREMENTS, MAP_LOCATION, GUIDANCE_HELP, DATA_BACKUP, ABOUT_SAFETY
}

sealed interface ActivePropertiesState {
    data object Loading : ActivePropertiesState
    data class Loaded(val properties: List<PropertyEntity>) : ActivePropertiesState
}

sealed interface GettingStartedPropertyContext {
    data object Loading : GettingStartedPropertyContext
    data object NoProperties : GettingStartedPropertyContext
    data class NeedsSelection(
        val activeProperties: List<PropertyEntity>
    ) : GettingStartedPropertyContext
    data class Selected(
        val property: PropertyEntity,
        val activeProperties: List<PropertyEntity>
    ) : GettingStartedPropertyContext
}

data class HelpTopic(
    val id: HelpTopicId,
    val titleRes: Int,
    val summaryRes: Int,
    val sections: List<HelpSection>,
    val keywords: List<Int> = emptyList(),
    val category: HelpCategory = HelpCategory.BASICS
)

enum class HelpCategory {
    BASICS, MAPPING, PROPERTY_RECORDS, MAINTENANCE_SAFETY, DATA_APP_INFO
}

data class HelpSection(
    val headingRes: Int,
    val bodyRes: Int,
    val steps: List<Int> = emptyList()
)

data class ResolvedHelpTopic(
    val id: HelpTopicId,
    val title: String,
    val summary: String,
    val sections: List<ResolvedHelpSection>,
    val keywords: List<String>
)

data class ResolvedHelpSection(
    val heading: String,
    val body: String
)

data class HelpSearchResult(
    val topic: HelpTopic,
    val score: Int
)

data class GettingStartedProgress(
    val hasProperty: Boolean,
    val hasMap: Boolean,
    val hasMappedItem: Boolean,
    val hasInfrastructure: Boolean,
    val hasMaintenance: Boolean,
    val hasAttachment: Boolean,
    val emergencyReviewed: Boolean,
    val dismissed: Boolean = false
)

data class GettingStartedStep(
    val stepId: GettingStartedStepId,
    val titleRes: Int,
    val isCompleted: Boolean,
    val isEnabled: Boolean = true
)

enum class GettingStartedStepId {
    CREATE_PROPERTY, 
    CREATE_MAP, 
    ADD_FEATURE, 
    ADD_INFRA, 
    ADD_MAINT, 
    ADD_PHOTO, 
    REVIEW_EMERGENCY
}

object GettingStartedStepBuilder {
    fun buildSteps(
        progress: GettingStartedProgress,
        propertySelected: Boolean
    ): List<GettingStartedStep> {
        return listOf(
            GettingStartedStep(
                stepId = GettingStartedStepId.CREATE_PROPERTY,
                titleRes = com.jumastappworks.mapstead.R.string.gs_step_property,
                isCompleted = progress.hasProperty
            ),
            GettingStartedStep(
                stepId = GettingStartedStepId.CREATE_MAP,
                titleRes = com.jumastappworks.mapstead.R.string.gs_step_map,
                isCompleted = progress.hasMap,
                isEnabled = progress.hasProperty && propertySelected
            ),
            GettingStartedStep(
                stepId = GettingStartedStepId.ADD_FEATURE,
                titleRes = com.jumastappworks.mapstead.R.string.gs_step_feature,
                isCompleted = progress.hasMappedItem,
                isEnabled = progress.hasMap && propertySelected
            ),
            GettingStartedStep(
                stepId = GettingStartedStepId.ADD_INFRA,
                titleRes = com.jumastappworks.mapstead.R.string.gs_step_infra,
                isCompleted = progress.hasInfrastructure,
                isEnabled = propertySelected
            ),
            GettingStartedStep(
                stepId = GettingStartedStepId.ADD_MAINT,
                titleRes = com.jumastappworks.mapstead.R.string.gs_step_maint,
                isCompleted = progress.hasMaintenance,
                isEnabled = progress.hasInfrastructure && propertySelected
            ),
            GettingStartedStep(
                stepId = GettingStartedStepId.ADD_PHOTO,
                titleRes = com.jumastappworks.mapstead.R.string.gs_step_photo,
                isCompleted = progress.hasAttachment,
                isEnabled = propertySelected
            ),
            GettingStartedStep(
                stepId = GettingStartedStepId.REVIEW_EMERGENCY,
                titleRes = com.jumastappworks.mapstead.R.string.gs_step_emergency,
                isCompleted = progress.emergencyReviewed,
                isEnabled = propertySelected
            )
        )
    }
}
