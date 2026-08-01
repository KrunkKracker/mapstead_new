package com.jumastappworks.mapstead.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.jumastappworks.mapstead.data.attachments.*
import com.jumastappworks.mapstead.data.backup.AttachmentStorageService
import com.jumastappworks.mapstead.data.db.DatabaseTransactionRunner
import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.db.dao.*
import com.jumastappworks.mapstead.data.db.entities.*
import com.jumastappworks.mapstead.data.mapping.MapFeatureContextResolver
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.util.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class AttachmentRepositoryTest {

    private val db = mockk<MapsteadDatabase>(relaxed = true)
    private val storageService = mockk<AttachmentStorageService>(relaxed = true)
    private val resolver = mockk<MapFeatureContextResolver>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val contentResolver = mockk<ContentResolver>(relaxed = true)
    
    private val attachmentDao = mockk<AttachmentDao>(relaxed = true)
    private val propertyDao = mockk<PropertyDao>(relaxed = true)
    private val infrastructureDao = mockk<InfrastructureDao>(relaxed = true)
    private val maintenanceDao = mockk<MaintenanceDao>(relaxed = true)
    private val mapFeatureDao = mockk<MapFeatureDao>(relaxed = true)

    private val transactionRunner = object : DatabaseTransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }

    @Before
    fun setup() {
        every { db.attachmentDao() } returns attachmentDao
        every { db.propertyDao() } returns propertyDao
        every { db.infrastructureDao() } returns infrastructureDao
        every { db.maintenanceDao() } returns maintenanceDao
        every { db.mapFeatureDao() } returns mapFeatureDao
        every { context.contentResolver } returns contentResolver
    }

    private fun createRepository(dispatcher: TestDispatcher) = AttachmentRepository(
        database = db,
        storageService = storageService,
        mapFeatureContextResolver = resolver,
        transactionRunner = transactionRunner,
        context = context,
        ioDispatcher = dispatcher
    )

    @Test
    fun testPropertyAttachmentAccepted() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = createRepository(dispatcher)
        val propId = UUID.randomUUID()
        coEvery { propertyDao.getPropertyById(propId) } returns PropertyEntity(id = propId, name = "Test", propertyType = "Home")
        
        val uri = mockk<Uri>()
        val inputStream = mockk<InputStream>(relaxed = true)
        every { contentResolver.openInputStream(uri) } returns inputStream
        every { contentResolver.getType(uri) } returns "image/jpeg"
        every { storageService.stageInputStream(any(), any()) } returns Result.success(
            AttachmentStorageService.StagedFileResult(File("dummy"), 100L, "hash")
        )
        every { storageService.commitStagedFile(any(), any()) } returns Result.success(File("final"))
        every { storageService.getEntityPath(any()) } returns "managed/path"

        val result = repo.importAttachment(
            AttachmentOwner.Property(propId),
            uri,
            AttachmentType.Photo,
            "Photo",
            null
        )

        assertTrue(result is AttachmentWriteResult.Success)
        coVerify { attachmentDao.insertAttachment(any()) }
    }

    @Test
    fun testMapFeatureAttachmentAccepted() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = createRepository(dispatcher)
        val propId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        coEvery { propertyDao.getPropertyById(propId) } returns PropertyEntity(id = propId, name = "Test", propertyType = "Home")
        
        val mockContext = ActiveMapFeatureContext(
            feature = MapFeatureEntity(
                id = featureId, propertyId = propId, planId = UUID.randomUUID(), layerId = UUID.randomUUID(),
                geometryType = "Point", geometryJson = "{}", coordinateSpace = "LOCAL", styleJson = "{}",
                accuracySource = "MANUAL"
            ),
            plan = mockk(relaxed = true),
            layer = mockk(relaxed = true)
        )
        coEvery { resolver.resolveFromFeature(propId, featureId) } returns mockContext
        
        val uri = mockk<Uri>()
        val inputStream = mockk<InputStream>(relaxed = true)
        every { contentResolver.openInputStream(uri) } returns inputStream
        every { contentResolver.getType(uri) } returns "image/jpeg"
        every { storageService.stageInputStream(any(), any()) } returns Result.success(
            AttachmentStorageService.StagedFileResult(File("dummy"), 100L, "hash")
        )
        every { storageService.commitStagedFile(any(), any()) } returns Result.success(File("final"))

        val result = repo.importAttachment(
            AttachmentOwner.MapFeature(propId, featureId),
            uri,
            AttachmentType.Photo,
            "Feature Photo",
            null
        )

        assertTrue(result is AttachmentWriteResult.Success)
        val slot = slot<AttachmentEntity>()
        coVerify { attachmentDao.insertAttachment(capture(slot)) }
        assertEquals(featureId, slot.captured.mapFeatureId)
    }

    @Test
    fun testCrossPropertyInfrastructureRejected() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = createRepository(dispatcher)
        val propId1 = UUID.randomUUID()
        val propId2 = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        
        coEvery { propertyDao.getPropertyById(propId1) } returns PropertyEntity(id = propId1, name = "P1", propertyType = "Home")
        coEvery { infrastructureDao.getItemById(itemId) } returns InfrastructureItemEntity(id = itemId, propertyId = propId2, name = "Item", category = "Cat", status = "Active")

        val result = repo.importAttachment(
            AttachmentOwner.InfrastructureItem(propId1, itemId),
            mockk(),
            AttachmentType.Photo,
            "Photo",
            null
        )

        assertEquals(AttachmentWriteResult.InvalidOwner, result)
    }

    @Test
    fun testDeletedMaintenanceOwnerRejected() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = createRepository(dispatcher)
        val propId = UUID.randomUUID()
        val recordId = UUID.randomUUID()
        
        coEvery { propertyDao.getPropertyById(propId) } returns PropertyEntity(id = propId, name = "P", propertyType = "Home")
        coEvery { maintenanceDao.getRecordByIdOnce(recordId) } returns MaintenanceRecordEntity(
            id = recordId, propertyId = propId, title = "T", category = "C", 
            deletedAt = Instant.now(), serviceDate = java.time.LocalDate.now(),
            status = "Scheduled"
        )

        val result = repo.importAttachment(
            AttachmentOwner.MaintenanceRecord(propId, recordId),
            mockk(),
            AttachmentType.Photo,
            "Photo",
            null
        )

        assertEquals(AttachmentWriteResult.InvalidOwner, result)
    }

    @Test
    fun testOversizedFileRejected() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = createRepository(dispatcher)
        val propId = UUID.randomUUID()
        coEvery { propertyDao.getPropertyById(propId) } returns PropertyEntity(id = propId, name = "P", propertyType = "Home")
        
        val uri = mockk<Uri>()
        val inputStream = mockk<InputStream>(relaxed = true)
        every { contentResolver.openInputStream(uri) } returns inputStream
        every { contentResolver.getType(uri) } returns "image/jpeg"
        every { storageService.stageInputStream(any(), any()) } returns Result.failure(java.io.IOException("limit reached"))

        val result = repo.importAttachment(
            AttachmentOwner.Property(propId),
            uri,
            AttachmentType.Photo,
            "Photo",
            null
        )

        assertEquals(AttachmentWriteResult.TooLarge, result)
    }

    @Test
    fun testSoftDeleteRemovesFileBestEffort() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = createRepository(dispatcher)
        val propId = UUID.randomUUID()
        val attachmentId = UUID.randomUUID()
        val entity = AttachmentEntity(id = attachmentId, propertyId = propId, attachmentType = "Photo", localUri = "", displayName = "D")
        
        coEvery { attachmentDao.getAttachmentById(attachmentId) } returns entity
        coEvery { attachmentDao.softDeletePropertyAttachment(propId, attachmentId, any(), any()) } returns 1
        every { storageService.deleteManagedFile(attachmentId) } returns Result.success(Unit)
        
        val result = repo.softDeleteAttachment(propId, attachmentId)
        
        assertEquals(AttachmentDeleteState.Deleted, result)
        coVerify { attachmentDao.softDeletePropertyAttachment(propId, attachmentId, any(), any()) }
        verify { storageService.deleteManagedFile(attachmentId) }
    }

    @Test
    fun testSetFeatureCoverValidatesOwner() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = createRepository(dispatcher)
        val propId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        val otherFeatureId = UUID.randomUUID()
        val attachmentId = UUID.randomUUID()
        
        coEvery { resolver.resolveFromFeature(propId, featureId) } returns null
        
        coEvery { attachmentDao.getAttachmentById(attachmentId) } returns AttachmentEntity(
            id = attachmentId, propertyId = propId, mapFeatureId = otherFeatureId,
            attachmentType = "Photo", localUri = "", displayName = "D", mimeType = "image/jpeg"
        )

        val result = repo.setFeatureCoverAttachment(propId, featureId, attachmentId)
        
        assertEquals(CoverResult.FeatureNotFound, result)
    }

    @Test
    fun testSetFeatureCoverRejectsNonImage() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = createRepository(dispatcher)
        val propId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        val attachmentId = UUID.randomUUID()
        
        val mockContext = ActiveMapFeatureContext(
            feature = MapFeatureEntity(
                id = featureId, propertyId = propId, planId = UUID.randomUUID(), layerId = UUID.randomUUID(),
                geometryType = "Point", geometryJson = "{}", coordinateSpace = "LOCAL", styleJson = "{}",
                accuracySource = "MANUAL"
            ),
            plan = mockk(relaxed = true),
            layer = mockk(relaxed = true)
        )
        coEvery { resolver.resolveFromFeature(propId, featureId) } returns mockContext
        
        coEvery { attachmentDao.getAttachmentById(attachmentId) } returns AttachmentEntity(
            id = attachmentId, propertyId = propId, mapFeatureId = featureId,
            attachmentType = "Document", localUri = "", displayName = "D", mimeType = "application/pdf"
        )

        val result = repo.setFeatureCoverAttachment(propId, featureId, attachmentId)
        
        assertEquals(CoverResult.UnsupportedType, result)
    }

    @Test
    fun testOwnerExclusivityAcceptedForSingleOwner() {
        val dispatcher = StandardTestDispatcher()
        val repo = createRepository(dispatcher)
        val propId = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        val entity = AttachmentEntity(id = UUID.randomUUID(), propertyId = propId, infrastructureItemId = itemId, attachmentType = "Photo", localUri = "", displayName = "D")
        
        val result = repo.parseStoredAttachmentOwner(entity)
        assertTrue(result is StoredAttachmentOwnerResult.Valid)
        assertTrue((result as StoredAttachmentOwnerResult.Valid).owner is AttachmentOwner.InfrastructureItem)
    }

    @Test
    fun testOwnerExclusivityRejectedForMultipleOwners() {
        val dispatcher = StandardTestDispatcher()
        val repo = createRepository(dispatcher)
        val propId = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        val recordId = UUID.randomUUID()
        val entity = AttachmentEntity(id = UUID.randomUUID(), propertyId = propId, infrastructureItemId = itemId, maintenanceRecordId = recordId, attachmentType = "Photo", localUri = "", displayName = "D")
        
        val result = repo.parseStoredAttachmentOwner(entity)
        assertEquals(StoredAttachmentOwnerResult.MultipleOwners, result)
    }

    @Test
    fun testMetadataUpdateRejectsMultipleOwners() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = createRepository(dispatcher)
        val propertyId = UUID.randomUUID()
        val attachmentId = UUID.randomUUID()
        val entity = AttachmentEntity(id = attachmentId, propertyId = propertyId, infrastructureItemId = UUID.randomUUID(), maintenanceRecordId = UUID.randomUUID(), attachmentType = "Photo", localUri = "", displayName = "D")
        
        // Strict mock for attachment lookup
        coEvery { attachmentDao.getAttachmentById(attachmentId) } returns entity
        
        val result = withTimeout(2.seconds) {
            repo.updateMetadata(propertyId, attachmentId, AttachmentType.Photo, "New Name", null)
        }
        assertEquals(AttachmentWriteResult.InvalidOwner, result)
        
        coVerify(exactly = 0) {
            attachmentDao.updateAttachment(any())
            propertyDao.getPropertyById(any())
            infrastructureDao.getItemById(any())
            maintenanceDao.getRecordByIdOnce(any())
        }
    }

    @Test
    fun testOwnerExclusivityRejectsInfraAndFeature() {
        val dispatcher = StandardTestDispatcher()
        val repo = createRepository(dispatcher)
        val entity = AttachmentEntity(
            propertyId = UUID.randomUUID(),
            infrastructureItemId = UUID.randomUUID(),
            mapFeatureId = UUID.randomUUID(),
            attachmentType = "Photo", localUri = "", displayName = "D"
        )
        assertEquals(StoredAttachmentOwnerResult.MultipleOwners, repo.parseStoredAttachmentOwner(entity))
    }

    @Test
    fun testOwnerExclusivityRejectsMaintenanceAndFeature() {
        val dispatcher = StandardTestDispatcher()
        val repo = createRepository(dispatcher)
        val entity = AttachmentEntity(
            propertyId = UUID.randomUUID(),
            maintenanceRecordId = UUID.randomUUID(),
            mapFeatureId = UUID.randomUUID(),
            attachmentType = "Photo", localUri = "", displayName = "D"
        )
        assertEquals(StoredAttachmentOwnerResult.MultipleOwners, repo.parseStoredAttachmentOwner(entity))
    }

    @Test
    fun testOwnerExclusivityRejectsAllThreeOwners() {
        val dispatcher = StandardTestDispatcher()
        val repo = createRepository(dispatcher)
        val entity = AttachmentEntity(
            propertyId = UUID.randomUUID(),
            infrastructureItemId = UUID.randomUUID(),
            maintenanceRecordId = UUID.randomUUID(),
            mapFeatureId = UUID.randomUUID(),
            attachmentType = "Photo", localUri = "", displayName = "D"
        )
        assertEquals(StoredAttachmentOwnerResult.MultipleOwners, repo.parseStoredAttachmentOwner(entity))
    }

    @Test
    fun testSetFeatureCoverRejectsMalformedOwner() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = createRepository(dispatcher)
        val propId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        val attachmentId = UUID.randomUUID()
        val entity = AttachmentEntity(
            id = attachmentId, propertyId = propId, mapFeatureId = featureId,
            infrastructureItemId = UUID.randomUUID(), // Malformed!
            attachmentType = "Photo", localUri = "", displayName = "D", mimeType = "image/jpeg"
        )
        
        coEvery { attachmentDao.getAttachmentById(attachmentId) } returns entity
        
        val result = repo.setFeatureCoverAttachment(propId, featureId, attachmentId)
        assertEquals(CoverResult.InvalidOwner, result)
    }

    @Test
    fun testResolveFileRejectsMalformedOwner() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = createRepository(dispatcher)
        val propId = UUID.randomUUID()
        val attachmentId = UUID.randomUUID()
        val entity = AttachmentEntity(
            id = attachmentId, propertyId = propId, infrastructureItemId = UUID.randomUUID(),
            maintenanceRecordId = UUID.randomUUID(), // Malformed!
            attachmentType = "Photo", localUri = "", displayName = "D"
        )
        
        coEvery { attachmentDao.getAttachmentById(attachmentId) } returns entity
        
        val result = repo.resolveAttachmentFile(propId, attachmentId)
        assertEquals(AttachmentFileState.InvalidPath, result)
    }

    @Test
    fun testSetFeatureCoverReturnsErrorWhenSelectedRowIsNotUpdated() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = createRepository(dispatcher)
        val propId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        val attachmentId = UUID.randomUUID()
        
        val mockContext = ActiveMapFeatureContext(
            feature = MapFeatureEntity(
                id = featureId, propertyId = propId, planId = UUID.randomUUID(), layerId = UUID.randomUUID(),
                geometryType = "Point", geometryJson = "{}", coordinateSpace = "LOCAL", styleJson = "{}",
                accuracySource = "MANUAL"
            ),
            plan = mockk(relaxed = true),
            layer = mockk(relaxed = true)
        )
        coEvery { resolver.resolveFromFeature(propId, featureId) } returns mockContext
        
        val entity = AttachmentEntity(
            id = attachmentId, propertyId = propId, mapFeatureId = featureId,
            attachmentType = "Photo", localUri = "", displayName = "D", mimeType = "image/jpeg",
            appManagedCopyPath = "path", fileSizeBytes = 100L, sha256 = "hash"
        )
        coEvery { attachmentDao.getAttachmentById(attachmentId) } returns entity
        
        val mockFile = mockk<File>(relaxed = true)
        every { mockFile.exists() } returns true
        every { mockFile.isFile } returns true
        every { mockFile.length() } returns 100L
        
        every { storageService.resolveFromEntityPath("path") } returns Result.success(mockFile)
        every { storageService.calculateSha256(mockFile) } returns "hash"
        
        // Mock setFeatureCover returning 0 to trigger rollback exception in production
        coEvery { attachmentDao.setFeatureCover(propId, featureId, attachmentId, any()) } returns 0
        
        val result = repo.setFeatureCoverAttachment(propId, featureId, attachmentId)
        
        assertTrue(result is CoverResult.Error)
        coVerify(exactly = 1) { attachmentDao.clearFeatureCover(propId, featureId, any()) }
        coVerify(exactly = 1) { attachmentDao.setFeatureCover(propId, featureId, attachmentId, any()) }
    }

    @Test
    fun testResolveActiveAttachmentOwnerRejectsDeleted() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = createRepository(dispatcher)
        val propId = UUID.randomUUID()
        val attachmentId = UUID.randomUUID()
        val deletedEntity = AttachmentEntity(
            id = attachmentId, propertyId = propId, attachmentType = "Photo",
            localUri = "", displayName = "D", deletedAt = Instant.now()
        )
        
        coEvery { attachmentDao.getAttachmentById(attachmentId) } returns deletedEntity
        
        val result = repo.resolveActiveAttachmentOwner(propId, attachmentId)
        assertEquals(ActiveAttachmentOwnerResult.AttachmentNotFound, result)
    }

    @Test
    fun testGetAttachmentForPropertyRejectsDeleted() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = createRepository(dispatcher)
        val propId = UUID.randomUUID()
        val attachmentId = UUID.randomUUID()
        val deletedEntity = AttachmentEntity(
            id = attachmentId, propertyId = propId, attachmentType = "Photo",
            localUri = "", displayName = "D", deletedAt = Instant.now()
        )
        
        coEvery { attachmentDao.getAttachmentById(attachmentId) } returns deletedEntity
        
        val result = repo.getAttachmentForProperty(propId, attachmentId)
        assertNull(result)
    }

    @Test
    fun testSoftDeleteRejectsAlreadyDeleted() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = createRepository(dispatcher)
        val propId = UUID.randomUUID()
        val attachmentId = UUID.randomUUID()
        val deletedEntity = AttachmentEntity(
            id = attachmentId, propertyId = propId, attachmentType = "Photo",
            localUri = "", displayName = "D", deletedAt = Instant.now()
        )
        
        coEvery { attachmentDao.getAttachmentById(attachmentId) } returns deletedEntity
        
        val result = repo.softDeleteAttachment(propId, attachmentId)
        assertTrue(result is AttachmentDeleteState.Error)
        assertEquals(com.jumastappworks.mapstead.R.string.error_attachment_not_found, (result as AttachmentDeleteState.Error).messageRes)
    }

    @Test
    fun testUpdateMetadataRejectsDeleted() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = createRepository(dispatcher)
        val propId = UUID.randomUUID()
        val attachmentId = UUID.randomUUID()
        val deletedEntity = AttachmentEntity(
            id = attachmentId, propertyId = propId, attachmentType = "Photo",
            localUri = "", displayName = "D", deletedAt = Instant.now()
        )
        
        coEvery { attachmentDao.getAttachmentById(attachmentId) } returns deletedEntity
        
        val result = repo.updateMetadata(propId, attachmentId, AttachmentType.Photo, "New", null)
        assertEquals(AttachmentWriteResult.OwnershipMismatch, result)
    }
}
