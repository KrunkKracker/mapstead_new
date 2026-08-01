package com.jumastappworks.mapstead.data.handoff

import com.jumastappworks.mapstead.util.UuidSerializer
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

enum class PropertyHandoffComponent {
    PDF_REPORT,
    PROPERTY_ATTACHMENTS,
    INFRASTRUCTURE_ATTACHMENTS,
    MAINTENANCE_ATTACHMENTS,
    MAP_FEATURE_ATTACHMENTS
}

@Serializable
data class PropertyHandoffOptions(
    @Serializable(with = UuidSerializer::class) val propertyId: UUID,
    val enabledComponents: Set<PropertyHandoffComponent> = PropertyHandoffComponent.entries.toSet()
) {
    fun includes(component: PropertyHandoffComponent): Boolean = enabledComponents.contains(component)
}

data class PropertyHandoffProgress(
    val stage: PropertyHandoffStage,
    val completedItems: Int = 0,
    val totalItems: Int = 0
)

enum class PropertyHandoffStage {
    LOADING_PROPERTY,
    GENERATING_REPORT,
    VALIDATING_ATTACHMENTS,
    COPYING_ATTACHMENTS,
    WRITING_MANIFEST,
    CREATING_ARCHIVE,
    COMPLETE
}

@Serializable
data class PropertyHandoffWarning(
    val category: String,
    val displayName: String
)

sealed interface PropertyHandoffResult {
    data class Success(
        val packageFile: File,
        val includedAttachmentCount: Int,
        val skippedAttachmentCount: Int,
        val warnings: List<PropertyHandoffWarning>,
        val pdfActuallyIncluded: Boolean
    ) : PropertyHandoffResult

    data object PropertyNotFound : PropertyHandoffResult
    data object NothingSelected : PropertyHandoffResult
    data object PdfReportFailed : PropertyHandoffResult
    data object StorageUnavailable : PropertyHandoffResult
    data object Cancelled : PropertyHandoffResult
    data object Error : PropertyHandoffResult
}

enum class PropertyHandoffError {
    PROPERTY_NOT_FOUND,
    NOTHING_SELECTED,
    REPORT_GENERATION_FAILED,
    STORAGE_UNAVAILABLE,
    PACKAGE_CREATION_FAILED,
    GENERATED_FILE_MISSING,
    SHARE_UNAVAILABLE,
    SHARE_PERMISSION_FAILED
}
