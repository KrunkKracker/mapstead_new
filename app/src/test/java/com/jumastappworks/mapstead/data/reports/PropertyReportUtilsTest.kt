package com.jumastappworks.mapstead.data.reports

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate

class PropertyReportUtilsTest {

    @Test
    fun `filename sanitization removes invalid characters`() {
        val date = LocalDate.of(2026, 7, 25)
        val name = "My / Property .. Name?"
        val filename = createPropertyReportFilename(name, date)
        
        assertEquals("Mapstead_My_Property_Name_2026-07-25.pdf", filename)
    }

    @Test
    fun `filename uses fallback for empty name`() {
        val date = LocalDate.of(2026, 7, 25)
        val name = "????"
        val filename = createPropertyReportFilename(name, date)
        
        assertEquals("Mapstead_Property_2026-07-25.pdf", filename)
    }

    @Test
    fun `filename is truncated if too long`() {
        val date = LocalDate.of(2026, 7, 25)
        val name = "ThisIsAVeryVeryVeryVeryVeryVeryLongPropertyNameThatShouldBeTruncated"
        val filename = createPropertyReportFilename(name, date)
        
        assertEquals("Mapstead_ThisIsAVeryVeryVeryVeryVeryVer_2026-07-25.pdf", filename)
    }

    @Test
    fun `isFileInsideDirectory accepts valid child`() {
        val root = File("C:/reports")
        val child = File("C:/reports/my_report.pdf")
        
        // Note: canonicalFile might fail on some systems if paths don't exist, 
        // but we'll use a mocked-like approach if needed or just real files in temp
        val tempRoot = File(System.getProperty("java.io.tmpdir"), "mapstead_reports").apply { mkdirs() }
        val tempChild = File(tempRoot, "report.pdf").apply { createNewFile() }
        
        assertTrue(isFileInsideDirectory(tempRoot, tempChild))
        
        tempChild.delete()
        tempRoot.delete()
    }

    @Test
    fun `isFileInsideDirectory rejects sibling or parent`() {
        val tempRoot = File(System.getProperty("java.io.tmpdir"), "mapstead_reports").apply { mkdirs() }
        val tempSibling = File(System.getProperty("java.io.tmpdir"), "mapstead_other").apply { mkdirs() }
        val siblingFile = File(tempSibling, "stolen.pdf").apply { createNewFile() }
        
        assertFalse(isFileInsideDirectory(tempRoot, siblingFile))
        assertFalse(isFileInsideDirectory(tempRoot, tempRoot))
        
        siblingFile.delete()
        tempSibling.delete()
        tempRoot.delete()
    }
}
