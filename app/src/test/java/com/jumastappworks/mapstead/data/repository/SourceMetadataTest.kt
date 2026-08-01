package com.jumastappworks.mapstead.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.jumastappworks.mapstead.data.backup.AttachmentStorageService
import com.jumastappworks.mapstead.data.db.DatabaseTransactionRunner
import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.db.dao.AttachmentDao
import com.jumastappworks.mapstead.data.db.dao.PropertyDao
import com.jumastappworks.mapstead.data.mapping.MapFeatureContextResolver
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.*
import org.junit.Assert.*

@RunWith(RobolectricTestRunner::class)
class SourceMetadataTest {

    private val context = mockk<Context>(relaxed = true)
    private val contentResolver = mockk<ContentResolver>(relaxed = true)
    private val db = mockk<MapsteadDatabase>(relaxed = true)
    private val storageService = mockk<AttachmentStorageService>(relaxed = true)
    private val resolver = mockk<MapFeatureContextResolver>(relaxed = true)
    private val transactionRunner = mockk<DatabaseTransactionRunner>(relaxed = true)
    
    private val attachmentDao = mockk<AttachmentDao>(relaxed = true)
    private val propertyDao = mockk<PropertyDao>(relaxed = true)

    private lateinit var repository: AttachmentRepository

    @Before
    fun setup() {
        every { context.contentResolver } returns contentResolver
        every { db.attachmentDao() } returns attachmentDao
        every { db.propertyDao() } returns propertyDao
        
        repository = AttachmentRepository(db, storageService, resolver, transactionRunner, context)
        
        // Default: property exists and is not deleted
        coEvery { propertyDao.getPropertyById(any()) } returns mockk(relaxed = true) {
            every { deletedAt } returns null
        }
    }

    @Test
    fun `importAttachment uses MIME type from ContentResolver`() = runTest {
        val propId = UUID.randomUUID()
        val uri = Uri.parse("content://media/picker/1")
        
        every { contentResolver.getType(uri) } returns "image/webp"
        every { contentResolver.openInputStream(uri) } returns mockk(relaxed = true)
        every { storageService.stageInputStream(any(), any()) } returns Result.success(
            AttachmentStorageService.StagedFileResult(java.io.File("d"), 100L, "h")
        )
        every { storageService.commitStagedFile(any(), any()) } returns Result.success(java.io.File("f"))
        
        repository.importAttachment(com.jumastappworks.mapstead.data.attachments.AttachmentOwner.Property(propId), uri, com.jumastappworks.mapstead.data.attachments.AttachmentType.Photo, null, null)
        
        val slot = slot<com.jumastappworks.mapstead.data.db.entities.AttachmentEntity>()
        coVerify { attachmentDao.insertAttachment(capture(slot)) }
        assertEquals("image/webp", slot.captured.mimeType)
    }

    @Test
    fun `importAttachment rejects unsupported MIME type`() = runTest {
        val propId = UUID.randomUUID()
        val uri = Uri.parse("content://media/picker/2")
        
        every { contentResolver.getType(uri) } returns "application/zip"

        val result = repository.importAttachment(com.jumastappworks.mapstead.data.attachments.AttachmentOwner.Property(propId), uri, com.jumastappworks.mapstead.data.attachments.AttachmentType.Other, null, null)
        
        assertEquals(com.jumastappworks.mapstead.data.attachments.AttachmentWriteResult.UnsupportedType, result)
    }

    @Test
    fun `importAttachment uses custom display name if provided`() = runTest {
        val propId = UUID.randomUUID()
        val uri = Uri.parse("content://media/picker/3")
        
        every { contentResolver.getType(uri) } returns "image/jpeg"
        every { contentResolver.openInputStream(uri) } returns mockk(relaxed = true)
        every { storageService.stageInputStream(any(), any()) } returns Result.success(
            AttachmentStorageService.StagedFileResult(java.io.File("d"), 100L, "h")
        )
        every { storageService.commitStagedFile(any(), any()) } returns Result.success(java.io.File("f"))
        
        repository.importAttachment(com.jumastappworks.mapstead.data.attachments.AttachmentOwner.Property(propId), uri, com.jumastappworks.mapstead.data.attachments.AttachmentType.Photo, "Custom Name", null)
        
        val slot = slot<com.jumastappworks.mapstead.data.db.entities.AttachmentEntity>()
        coVerify { attachmentDao.insertAttachment(capture(slot)) }
        assertEquals("Custom Name", slot.captured.displayName)
    }

    @Test
    fun `importAttachment identifies image with no extension from ContentResolver`() = runTest {
        val propId = UUID.randomUUID()
        val uri = Uri.parse("content://com.android.providers.media.documents/document/image%3A1234")
        
        every { contentResolver.getType(uri) } returns "image/jpeg"
        every { contentResolver.openInputStream(uri) } returns mockk(relaxed = true)
        every { storageService.stageInputStream(any(), any()) } returns Result.success(
            AttachmentStorageService.StagedFileResult(java.io.File("d"), 100L, "h")
        )
        every { storageService.commitStagedFile(any(), any()) } returns Result.success(java.io.File("f"))
        
        repository.importAttachment(com.jumastappworks.mapstead.data.attachments.AttachmentOwner.Property(propId), uri, com.jumastappworks.mapstead.data.attachments.AttachmentType.Photo, null, null)
        
        val slot = slot<com.jumastappworks.mapstead.data.db.entities.AttachmentEntity>()
        coVerify { attachmentDao.insertAttachment(capture(slot)) }
        assertEquals("image/jpeg", slot.captured.mimeType)
    }
}
