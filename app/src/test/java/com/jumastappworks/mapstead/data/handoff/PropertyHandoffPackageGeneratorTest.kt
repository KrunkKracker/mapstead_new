package com.jumastappworks.mapstead.data.handoff

import android.content.Context
import com.jumastappworks.mapstead.data.attachments.*
import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.db.dao.AttachmentDao
import com.jumastappworks.mapstead.data.db.entities.AttachmentEntity
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.reports.*
import com.jumastappworks.mapstead.data.repository.AttachmentExportFileResult
import com.jumastappworks.mapstead.data.repository.AttachmentRepository
import com.jumastappworks.mapstead.data.repository.PropertyRepository
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
class PropertyHandoffPackageGeneratorTest {

    private val context = mockk<Context>()
    private val database = mockk<MapsteadDatabase>()
    private val attachmentDao = mockk<AttachmentDao>()
    private val propertyRepository = mockk<PropertyRepository>()
    private val reportRepository = mockk<PropertyReportRepository>()
    private val documentBuilder = mockk<PropertyReportDocumentBuilder>()
    private val pdfGenerator = mockk<PropertyReportPdfGenerator>()
    private val attachmentRepository = mockk<AttachmentRepository>()

    private lateinit var generator: PropertyHandoffPackageGenerator
    private lateinit var cacheDir: File

    @Before
    fun setup() {
        cacheDir = File("build/test_cache_handoff").apply { mkdirs() }
        val reportsDir = File(cacheDir, "reports").apply { mkdirs() }
        every { context.cacheDir } returns cacheDir
        every { database.attachmentDao() } returns attachmentDao
        
        generator = PropertyHandoffPackageGenerator(
            context, database, propertyRepository, reportRepository,
            documentBuilder, pdfGenerator, attachmentRepository
        )
    }
    
    @After
    fun teardown() {
        cacheDir.deleteRecursively()
    }

    @Test
    fun `NothingSelected returned when all components disabled`() = runTest {
        val options = PropertyHandoffOptions(UUID.randomUUID(), enabledComponents = emptySet())
        val result = generator.generate(options) {}
        assertEquals(PropertyHandoffResult.NothingSelected, result)
    }

    @Test
    fun `PDF generation failure returns PdfReportFailed`() = runTest {
        val propId = UUID.randomUUID()
        val property = PropertyEntity(id = propId, name = "Test", propertyType = "House")
        coEvery { propertyRepository.getPropertyById(propId) } returns property
        
        coEvery { reportRepository.buildPropertyReportData(any()) } returns PropertyReportResult.Error(PropertyReportError.DATA_LOAD_FAILED)

        val options = PropertyHandoffOptions(propId, enabledComponents = setOf(PropertyHandoffComponent.PDF_REPORT))
        val result = generator.generate(options) {}
        
        assertEquals(PropertyHandoffResult.PdfReportFailed, result)
    }

    @Test
    fun `duplicate attachment names receive suffixes in ZIP`() = runTest {
        val propId = UUID.randomUUID()
        val property = PropertyEntity(id = propId, name = "Test", propertyType = "House")
        coEvery { propertyRepository.getPropertyById(propId) } returns property
        
        val a1 = AttachmentEntity(id = UUID.randomUUID(), propertyId = propId, displayName = "Photo", attachmentType = "Photo", localUri = "u1", appManagedCopyPath = "p1", fileSizeBytes = 4)
        val a2 = AttachmentEntity(id = UUID.randomUUID(), propertyId = propId, displayName = "Photo", attachmentType = "Photo", localUri = "u2", appManagedCopyPath = "p2", fileSizeBytes = 4)
        
        every { attachmentDao.getAttachmentsForProperty(propId) } returns flowOf(listOf(a1, a2))
        every { attachmentRepository.parseStoredAttachmentOwner(a1) } returns StoredAttachmentOwnerResult.Valid(AttachmentOwner.Property(propId))
        every { attachmentRepository.parseStoredAttachmentOwner(a2) } returns StoredAttachmentOwnerResult.Valid(AttachmentOwner.Property(propId))

        val mockFile = File(cacheDir, "mock.jpg").apply { createNewFile(); writeText("data") }
        
        coEvery { attachmentRepository.resolveVerifiedAttachmentFileForExport(propId, a1.id) } returns AttachmentExportFileResult.Available(a1, AttachmentOwner.Property(propId), mockFile)
        coEvery { attachmentRepository.resolveVerifiedAttachmentFileForExport(propId, a2.id) } returns AttachmentExportFileResult.Available(a2, AttachmentOwner.Property(propId), mockFile)
        
        val options = PropertyHandoffOptions(propId, enabledComponents = setOf(PropertyHandoffComponent.PROPERTY_ATTACHMENTS))
        val result = generator.generate(options) {}
        
        assertTrue(result is PropertyHandoffResult.Success)
        val success = result as PropertyHandoffResult.Success
        assertEquals(2, success.includedAttachmentCount)
        
        val zipFile = ZipFile(success.packageFile)
        val entries = zipFile.entries().asSequence().map { it.name }.toList()
        assertTrue(entries.contains("attachments/property/Photo.jpg"))
        assertTrue(entries.contains("attachments/property/Photo_2.jpg"))
        zipFile.close()
    }
}
