package com.jumastappworks.mapstead.data.reports

import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PropertyReportDocumentBuilder @Inject constructor() {

    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())

    fun build(data: PropertyReportData, options: PropertyReportOptions): PropertyReportDocument {
        val sections = mutableListOf<PropertyReportDocumentSection>()

        if (options.includes(PropertyReportSection.PROPERTY_PROFILE)) {
            sections.add(PropertyReportDocumentSection.PropertyProfile(
                type = data.propertyType,
                address = data.address,
                parcelNumber = data.parcelNumber,
                acreage = data.acreage?.toString(),
                description = data.description
            ))
        }

        if (options.includes(PropertyReportSection.INFRASTRUCTURE)) {
            sections.add(PropertyReportDocumentSection.Infrastructure(data.infrastructureItems))
        }

        if (options.includes(PropertyReportSection.MAINTENANCE_HISTORY)) {
            sections.add(PropertyReportDocumentSection.MaintenanceHistory(data.maintenanceRecords))
        }

        if (options.includes(PropertyReportSection.UPCOMING_MAINTENANCE)) {
            sections.add(PropertyReportDocumentSection.UpcomingMaintenance(data.upcomingMaintenance))
        }

        if (options.includes(PropertyReportSection.MAP_SUMMARY)) {
            sections.add(PropertyReportDocumentSection.MapSummary(
                planCount = data.planCount,
                layerCount = data.layerCount,
                pointCount = data.pointCount,
                lineCount = data.lineCount,
                areaCount = data.areaCount
            ))
        }

        if (options.includes(PropertyReportSection.ATTACHMENT_SUMMARY)) {
            sections.add(PropertyReportDocumentSection.AttachmentSummary(data.attachmentSummary))
        }

        return PropertyReportDocument(
            title = "Property Documentation Report",
            propertyName = data.propertyName,
            generatedAt = data.generatedAt,
            sections = sections
        )
    }
}
