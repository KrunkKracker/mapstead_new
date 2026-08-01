package com.jumastappworks.mapstead.data.reports

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class PropertyReportSection {
    PROPERTY_PROFILE,
    INFRASTRUCTURE,
    MAINTENANCE_HISTORY,
    UPCOMING_MAINTENANCE,
    MAP_SUMMARY,
    ATTACHMENT_SUMMARY
}

enum class MaintenanceHistoryRange {
    ALL_TIME,
    LAST_12_MONTHS,
    CURRENT_YEAR
}

data class PropertyReportOptions(
    val propertyId: UUID,
    val enabledSections: Set<PropertyReportSection> = PropertyReportSection.entries.toSet(),
    val maintenanceRange: MaintenanceHistoryRange = MaintenanceHistoryRange.ALL_TIME,
    val maintenanceHistoryRangeStart: LocalDate? = null,
    val maintenanceHistoryRangeEnd: LocalDate? = null
) {
    fun includes(section: PropertyReportSection): Boolean = enabledSections.contains(section)
}

data class PropertyReportData(
    val propertyName: String,
    val propertyType: String,
    val address: String,
    val acreage: Double?,
    val parcelNumber: String?,
    val description: String?,
    
    val planCount: Int,
    val layerCount: Int,
    val pointCount: Int,
    val lineCount: Int,
    val areaCount: Int,
    
    val infrastructureItems: List<ReportInfrastructureItem>,
    val maintenanceRecords: List<ReportMaintenanceRecord>,
    val upcomingMaintenance: List<ReportUpcomingTask>,
    val attachmentSummary: ReportAttachmentSummary,
    
    val generatedAt: Instant
)

data class ReportInfrastructureItem(
    val name: String,
    val category: String,
    val status: String,
    val manufacturer: String?,
    val model: String?,
    val isEmergency: Boolean
)

data class ReportMaintenanceRecord(
    val date: LocalDate,
    val title: String,
    val category: String,
    val infrastructureName: String?,
    val cost: Double?,
    val currencyCode: String?,
    val notes: String?
)

data class ReportUpcomingTask(
    val dueDate: LocalDate,
    val itemName: String?,
    val taskTitle: String,
    val isEnabled: Boolean
)

data class ReportAttachmentSummary(
    val totalCount: Int,
    val photoCount: Int,
    val documentCount: Int,
    val otherCount: Int,
    val propertyLevelCount: Int,
    val infrastructureCount: Int,
    val maintenanceCount: Int,
    val mapFeatureCount: Int
)

sealed interface PropertyReportResult {
    data class Success(val data: PropertyReportData) : PropertyReportResult
    data object PropertyNotFound : PropertyReportResult
    data object NoSectionsSelected : PropertyReportResult
    data class Error(val error: PropertyReportError, val technicalMessage: String? = null) : PropertyReportResult
}

enum class PropertyReportError {
    PROPERTY_NOT_FOUND,
    NO_SECTIONS_SELECTED,
    DATA_LOAD_FAILED,
    PDF_CREATION_FAILED,
    STORAGE_UNAVAILABLE,
    GENERATED_FILE_MISSING,
    SHARE_UNAVAILABLE,
    SHARE_PERMISSION_FAILED
}

enum class PdfGenerationResult {
    SUCCESS,
    ERROR,
    STORAGE_FAILURE,
    CANCELLED
}

// Pure Document Models for Rendering
data class PropertyReportDocument(
    val title: String,
    val propertyName: String,
    val generatedAt: Instant,
    val sections: List<PropertyReportDocumentSection>
)

sealed interface PropertyReportDocumentSection {
    data class PropertyProfile(
        val type: String,
        val address: String,
        val parcelNumber: String?,
        val acreage: String?,
        val description: String?
    ) : PropertyReportDocumentSection

    data class Infrastructure(
        val items: List<ReportInfrastructureItem>
    ) : PropertyReportDocumentSection

    data class MaintenanceHistory(
        val records: List<ReportMaintenanceRecord>
    ) : PropertyReportDocumentSection

    data class UpcomingMaintenance(
        val tasks: List<ReportUpcomingTask>
    ) : PropertyReportDocumentSection

    data class MapSummary(
        val planCount: Int,
        val layerCount: Int,
        val pointCount: Int,
        val lineCount: Int,
        val areaCount: Int
    ) : PropertyReportDocumentSection

    data class AttachmentSummary(
        val summary: ReportAttachmentSummary
    ) : PropertyReportDocumentSection
}
