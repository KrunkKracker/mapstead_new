package com.jumastappworks.mapstead.ui.reports

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PropertyReportSharerTest {

    private lateinit var context: Context
    private lateinit var sharer: PropertyReportSharer
    private lateinit var reportsDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        sharer = PropertyReportSharer(context)
        reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
    }

    @Test
    fun `sharer accepts file inside reports directory`() {
        val file = File(reportsDir, "test.pdf")
        file.createNewFile()
        
        // This will attempt to start activity, which might fail or be intercepted in Robolectric
        // But we want to test the validation logic primarily.
        val result = sharer.share(file)
        
        // In Robolectric, without any activities to handle the intent, it might return NoShareTarget 
        // or Started if it doesn't actually check for handlers.
        // The important part is it's NOT InvalidFileLocation or FileMissing.
        assertNotEquals(ReportShareResult.InvalidFileLocation, result)
        assertNotEquals(ReportShareResult.FileMissing, result)
    }

    @Test
    fun `sharer rejects file outside reports directory`() {
        val outsideFile = File(context.cacheDir, "outside.pdf")
        outsideFile.createNewFile()
        
        val result = sharer.share(outsideFile)
        assertEquals(ReportShareResult.InvalidFileLocation, result)
    }

    @Test
    fun `sharer rejects missing file`() {
        val missingFile = File(reportsDir, "missing.pdf")
        val result = sharer.share(missingFile)
        assertEquals(ReportShareResult.FileMissing, result)
    }
}
