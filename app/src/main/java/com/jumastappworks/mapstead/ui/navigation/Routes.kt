package com.jumastappworks.mapstead.ui.navigation

import androidx.navigation3.runtime.NavKey
import com.jumastappworks.mapstead.data.attachments.AttachmentNavigationOrigin
import com.jumastappworks.mapstead.util.UuidSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

sealed interface Route : NavKey {
    @Serializable
    data object Properties : Route

    @Serializable
    data object AddProperty : Route

    @Serializable
    data class EditProperty(
        @Serializable(with = UuidSerializer::class) val propertyId: UUID
    ) : Route

    @Serializable
    data class AddPropertyLocation(
        @Serializable(with = UuidSerializer::class) val propertyId: UUID
    ) : Route

    @Serializable
    data class PropertyDashboard(
        @Serializable(with = UuidSerializer::class) val propertyId: UUID
    ) : Route

    @Serializable
    data class Plans(
        @Serializable(with = UuidSerializer::class) val propertyId: UUID
    ) : Route

    @Serializable
    data class CreatePlan(
        @Serializable(with = UuidSerializer::class) val propertyId: UUID
    ) : Route

    @Serializable
    data class MapEditor(
        @Serializable(with = UuidSerializer::class) val propertyId: UUID,
        @Serializable(with = UuidSerializer::class) val planId: UUID,
        val featureId: String? = null
    ) : Route

    @Serializable
    data class InfrastructureList(
        @Serializable(with = UuidSerializer::class) val propertyId: UUID
    ) : Route

    @Serializable
    data class InfrastructureItemDetails(
        @Serializable(with = UuidSerializer::class) val propertyId: UUID,
        @Serializable(with = UuidSerializer::class) val itemId: UUID
    ) : Route

    @Serializable
    data class InfrastructureItemEditor(
        @Serializable(with = UuidSerializer::class) val propertyId: UUID,
        @Serializable(with = UuidSerializer::class) val itemId: UUID? = null
    ) : Route

    @Serializable
    data class Maintenance(
        @Serializable(with = UuidSerializer::class) val propertyId: UUID
    ) : Route

    @Serializable
    data class MaintenanceRecordDetails(
        @Serializable(with = UuidSerializer::class) val propertyId: UUID,
        @Serializable(with = UuidSerializer::class) val recordId: UUID
    ) : Route

    @Serializable
    data class MaintenanceRecordEditor(
        @Serializable(with = UuidSerializer::class) val propertyId: UUID,
        @Serializable(with = UuidSerializer::class) val recordId: UUID? = null,
        @Serializable(with = UuidSerializer::class) val infrastructureItemId: UUID? = null
    ) : Route

    @Serializable
    data class ReminderEditor(
        @Serializable(with = UuidSerializer::class) val propertyId: UUID,
        @Serializable(with = UuidSerializer::class) val reminderId: UUID? = null,
        @Serializable(with = UuidSerializer::class) val maintenanceRecordId: UUID? = null,
        @Serializable(with = UuidSerializer::class) val infrastructureItemId: UUID? = null
    ) : Route

    @Serializable
    data class FeatureAttachments(
        @Serializable(with = UuidSerializer::class) val propertyId: UUID,
        @Serializable(with = UuidSerializer::class) val planId: UUID,
        @Serializable(with = UuidSerializer::class) val featureId: UUID
    ) : Route

    @Serializable
    data class Emergency(
        @Serializable(with = UuidSerializer::class) val propertyId: UUID
    ) : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object Backup : Route

    @Serializable
    data class PropertyFiles(
        @Serializable(with = UuidSerializer::class) val propertyId: UUID
    ) : Route

    @Serializable
    data class AttachmentEditor(
        @Serializable(with = UuidSerializer::class) val propertyId: UUID,
        val ownerType: String,
        @Serializable(with = UuidSerializer::class) val ownerId: UUID? = null,
        @Serializable(with = UuidSerializer::class) val attachmentId: UUID? = null,
        val stagedFileUri: String? = null,
        val cameraCaptureToken: String? = null,
        val navigationOrigin: AttachmentNavigationOrigin = AttachmentNavigationOrigin.PROPERTY_FILES
    ) : Route

    @Serializable
    data class AttachmentDetails(
        @Serializable(with = UuidSerializer::class) val propertyId: UUID,
        @Serializable(with = UuidSerializer::class) val attachmentId: UUID,
        val navigationOrigin: AttachmentNavigationOrigin = AttachmentNavigationOrigin.PROPERTY_FILES
    ) : Route

    @Serializable
    data class InfrastructureRelationships(
        @Serializable(with = UuidSerializer::class) val propertyId: UUID
    ) : Route

    @Serializable
    data class InfrastructureRelationshipEditor(
        @Serializable(with = UuidSerializer::class) val propertyId: UUID,
        @Serializable(with = UuidSerializer::class) val currentItemId: UUID,
        @Serializable(with = UuidSerializer::class) val relationshipId: UUID? = null
    ) : Route

    @Serializable
    data class InfrastructureParentEditor(
        @Serializable(with = UuidSerializer::class) val propertyId: UUID,
        @Serializable(with = UuidSerializer::class) val itemId: UUID
    ) : Route

    @Serializable
    data class PropertyReports(
        @Serializable(with = UuidSerializer::class) val propertyId: UUID
    ) : Route

    @Serializable
    data object HelpCenter : Route

    @Serializable
    data class HelpTopic(val topicId: com.jumastappworks.mapstead.data.help.HelpTopicId) : Route

    @Serializable
    data object GettingStarted : Route

    @Serializable
    data object Orientation : Route

    @Serializable
    data object Privacy : Route

    @Serializable
    data object About : Route
}
