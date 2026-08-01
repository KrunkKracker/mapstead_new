package com.jumastappworks.mapstead.data.reports

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID

class PropertyReportDocumentBuilderTest {

    private val builder = PropertyReportDocumentBuilder()

    private val sampleData = PropertyReportData(
        propertyName = "Test Property",
        propertyType = "House",
        address = "123 Main St",
        acreage = 0.5,
        parcelNumber = "123-456",
        description = "A nice place",
        planCount = 1,
        layerCount = 2,
        pointCount = 5,
        lineCount = 1,
        areaCount = 1,
        infrastructureItems = emptyList(),
        maintenanceRecords = emptyList(),
        upcomingMaintenance = emptyList(),
        attachmentSummary = ReportAttachmentSummary(0, 0, 0, 0, 0, 0, 0, 0),
        generatedAt = Instant.now()
    )

    @Test
    fun `builder includes only selected sections`() {
        val options = PropertyReportOptions(
            propertyId = UUID.randomUUID(),
            enabledSections = setOf(PropertyReportSection.PROPERTY_PROFILE, PropertyReportSection.MAP_SUMMARY)
        )

        val doc = builder.build(sampleData, options)
        
        assertEquals(2, doc.sections.size)
        assertTrue(doc.sections.any { it is PropertyReportDocumentSection.PropertyProfile })
        assertTrue(doc.sections.any { it is PropertyReportDocumentSection.MapSummary })
        assertFalse(doc.sections.any { it is PropertyReportDocumentSection.Infrastructure })
    }

    @Test
    fun `builder preserves section order`() {
        // Defined order in builder is Profile, Infrastructure, Maintenance, Upcoming, Map, Attachments
        val options = PropertyReportOptions(
            propertyId = UUID.randomUUID(),
            enabledSections = PropertyReportSection.entries.toSet()
        )

        val doc = builder.build(sampleData, options)
        
        assertTrue(doc.sections[0] is PropertyReportDocumentSection.PropertyProfile)
        assertTrue(doc.sections[1] is PropertyReportDocumentSection.Infrastructure)
        assertTrue(doc.sections[2] is PropertyReportDocumentSection.MaintenanceHistory)
        assertTrue(doc.sections[3] is PropertyReportDocumentSection.UpcomingMaintenance)
        assertTrue(doc.sections[4] is PropertyReportDocumentSection.MapSummary)
        assertTrue(doc.sections[5] is PropertyReportDocumentSection.AttachmentSummary)
    }
}
