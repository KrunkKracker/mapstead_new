package com.jumastappworks.mapstead.data.backup

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.jumastappworks.mapstead.data.db.DatabaseTransactionRunner
import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.db.entities.AttachmentEntity
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import com.jumastappworks.mapstead.data.work.ReminderScheduler
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class BackupArchiveServiceTest {

    private lateinit var context: Context
    private lateinit var db: MapsteadDatabase
    private lateinit var attachmentStorage: AttachmentStorageService
    private lateinit var service: BackupArchiveService
    private lateinit var coordinator: RestoreCoordinator
    private lateinit var prefs: UserPreferencesRepository
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val transactionRunner = object : DatabaseTransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction(block)
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MapsteadDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        
        // Mock URI factory for tests
        val uriFactory = mockk<AttachmentUriFactory>(relaxed = true)
        attachmentStorage = AttachmentStorageService(context, uriFactory)
        
        val validator = BackupArchiveValidator(json, context, BackupArchiveLimits())
        service = BackupArchiveService(db, transactionRunner, attachmentStorage, json, validator, context)
        prefs = UserPreferencesRepository(mockk(relaxed = true) {
            every { data } returns flowOf(androidx.datastore.preferences.core.emptyPreferences())
        })
        val journalManager = RestoreJournalManager(File(context.filesDir, "test_journal.json"), json)
        coordinator = RestoreCoordinator(
            db, transactionRunner, service, attachmentStorage, prefs, journalManager, validator, 
            mockk(relaxed = true), mockk(relaxed = true), context
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testBackupAndRestoreRoundTrip() {
        runBlocking {
            val propertyId = UUID.randomUUID()
            val property = PropertyEntity(id = propertyId, name = "Test Property", propertyType = "Home")
            db.propertyDao().insertProperty(property)

            val attachmentId = UUID.randomUUID()
            val attachment = AttachmentEntity(
                id = attachmentId,
                propertyId = propertyId,
                attachmentType = "Photo",
                localUri = "content://photo",
                displayName = "Test Photo",
                appManagedCopyPath = "mapstead_attachments/$attachmentId",
                fileSizeBytes = 100L,
                sha256 = "dummy_hash"
            )
            
            // Manually create the file in storage
            val file = attachmentStorage.getAttachmentFile(attachmentId)
            file.writeText("test content")
            val actualHash = attachmentStorage.calculateSha256(file)
            val actualSize = file.length()
            
            db.attachmentDao().insertAttachment(attachment.copy(sha256 = actualHash, fileSizeBytes = actualSize))

            val result = service.createBackupArchive()
            assertTrue(result.isSuccess)
            val archive = result.getOrThrow()
            assertTrue(archive.file.exists())
            assertEquals(1, archive.manifest.propertyCount)
            assertEquals(1, archive.manifest.attachmentCount)

            // Now restore
            val restoreResult = coordinator.restore(archive.file) { _, _ -> }
            assertTrue(restoreResult.isSuccess)

            val restoredProperties = db.propertyDao().getAllPropertiesOnce()
            assertEquals(1, restoredProperties.size)
            assertEquals("Test Property", restoredProperties[0].name)

            val restoredAttachments = db.attachmentDao().getAllAttachmentsOnce()
            assertEquals(1, restoredAttachments.size)
            assertEquals("Test Photo", restoredAttachments[0].displayName)
            
            assertTrue(attachmentStorage.exists(restoredAttachments[0].id))
        }
    }

    @Test
    fun testSafetyBackupOnRestore() {
        runBlocking {
            val propertyId = UUID.randomUUID()
            db.propertyDao().insertProperty(PropertyEntity(id = propertyId, name = "Original", propertyType = "Home"))

            // Create a backup of a DIFFERENT state
            val otherDb = Room.inMemoryDatabaseBuilder(context, MapsteadDatabase::class.java).allowMainThreadQueries().build()
            val otherPropId = UUID.randomUUID()
            otherDb.propertyDao().insertProperty(PropertyEntity(id = otherPropId, name = "New State", propertyType = "Home"))
            
            // We'll skip creating a real zip and just mock the validator to return our entities
            // Wait, coordinator.restore needs a real zip to extract.
            // Let's use the actual service to create one from current state, then clear db, then restore.
            
            val archive = service.createBackupArchive().getOrThrow()
            
            // Change state
            db.propertyDao().clearAll()
            db.propertyDao().insertProperty(PropertyEntity(id = UUID.randomUUID(), name = "Intermediate", propertyType = "Home"))
            
            val restoreResult = coordinator.restore(archive.file, createSafetyBackup = true) { _, _ -> }
            assertTrue(restoreResult.isSuccess)
            
            // Verify safety backup was created
            val safetyBackups = coordinator.getSafetyBackups()
            assertEquals(1, safetyBackups.size)
            
            otherDb.close()
        }
    }
}
