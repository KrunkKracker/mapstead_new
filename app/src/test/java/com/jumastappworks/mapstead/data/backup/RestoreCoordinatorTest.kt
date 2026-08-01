package com.jumastappworks.mapstead.data.backup

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.jumastappworks.mapstead.data.db.DatabaseTransactionRunner
import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import com.jumastappworks.mapstead.data.work.ReminderScheduler
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException
import java.util.*

@RunWith(RobolectricTestRunner::class)
class RestoreCoordinatorTest {

    private lateinit var db: MapsteadDatabase
    private lateinit var context: Context
    private lateinit var archiveService: BackupArchiveService
    private lateinit var attachmentStorage: AttachmentStorageService
    private lateinit var prefs: UserPreferencesRepository
    private lateinit var journalManager: RestoreJournalManager
    private lateinit var validator: BackupArchiveValidator
    private lateinit var coordinator: RestoreCoordinator
    private val reminderScheduler = mockk<ReminderScheduler>(relaxed = true)
    private val operationCoordinator = mockk<BackupOperationCoordinator>(relaxed = true)

    private val transactionRunner = object : DatabaseTransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction(block)
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MapsteadDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        
        // Mock URI factory
        val uriFactory = mockk<AttachmentUriFactory>(relaxed = true)
        attachmentStorage = AttachmentStorageService(context, uriFactory)
        
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
        validator = BackupArchiveValidator(json, context, BackupArchiveLimits())
        archiveService = BackupArchiveService(db, transactionRunner, attachmentStorage, json, validator, context)
        prefs = UserPreferencesRepository(mockk(relaxed = true) {
            every { data } returns flowOf(androidx.datastore.preferences.core.emptyPreferences())
        })
        journalManager = RestoreJournalManager(File(context.filesDir, "test_journal.json"), json)
        
        coordinator = RestoreCoordinator(
            db, transactionRunner, archiveService, attachmentStorage, prefs, journalManager, validator, 
            operationCoordinator, reminderScheduler, context
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testSuccessfulRestoreWorkflow() {
        runBlocking {
            // 1. Setup local data
            db.propertyDao().insertProperty(PropertyEntity(name = "Original", propertyType = "Home"))
            
            // 2. Create a backup zip
            val backupFile = archiveService.createBackupArchive().getOrThrow()
            
            // 3. Destructive change
            db.propertyDao().clearAll()
            
            // 4. Restore
            val result = coordinator.restore(backupFile.file) { _, _ -> }
            assertTrue("Restore failed: ${result.exceptionOrNull()}", result.isSuccess)
            
            // 5. Verify data recovered
            val properties = db.propertyDao().getAllPropertiesOnce()
            assertEquals(1, properties.size)
            assertEquals("Original", properties[0].name)
            
            backupFile.file.delete()
        }
    }

    @Test
    fun testSafetyBackupCreatedBeforeMutation() {
        runBlocking {
            db.propertyDao().insertProperty(PropertyEntity(name = "SafetyTest", propertyType = "Home"))
            val backupFile = archiveService.createBackupArchive().getOrThrow()
            
            coordinator.restore(backupFile.file) { _, _ -> }
            
            val safetyDir = File(context.filesDir, "safety_backups")
            assertTrue(safetyDir.exists())
            assertTrue(safetyDir.listFiles()?.isNotEmpty() == true)
            
            backupFile.file.delete()
        }
    }

    @Test
    fun testOldRootMoveFailure() {
        runBlocking {
            val backupFile = createDummyBackup()
            val mockStorage = mockk<AttachmentStorageService>(relaxed = true)
            every { mockStorage.moveActiveToRollback(any()) } returns Result.failure(IOException("Move failed"))
            every { mockStorage.prepareStagingDir() } returns Result.success(File(context.cacheDir, "stage"))
            every { mockStorage.prepareRollbackDir() } returns Result.success(File(context.cacheDir, "rollback"))
            
            val failingCoordinator = createCoordinator(mockStorage = mockStorage)
            val result = failingCoordinator.restore(backupFile) { _, _ -> }
            
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Move failed") == true)
            // Verify rollback was NOT called because we failed during move TO rollback
            coVerify(exactly = 0) { mockStorage.rollback(any()) }
            
            backupFile.delete()
        }
    }

    @Test
    fun testStagedRootActivationFailure() {
        runBlocking {
            val backupFile = createDummyBackup()
            val mockStorage = mockk<AttachmentStorageService>(relaxed = true)
            every { mockStorage.prepareStagingDir() } returns Result.success(File(context.cacheDir, "stage").apply { mkdirs() })
            every { mockStorage.prepareRollbackDir() } returns Result.success(File(context.cacheDir, "rollback").apply { mkdirs() })
            every { mockStorage.moveActiveToRollback(any()) } returns Result.success(Unit)
            every { mockStorage.activateStagedAttachments(any()) } returns Result.failure(IOException("Activation failed"))
            
            val failingCoordinator = createCoordinator(mockStorage = mockStorage)
            val result = failingCoordinator.restore(backupFile) { _, _ -> }
            
            assertTrue(result.isFailure)
            // Verify compensation called rollback
            coVerify { mockStorage.rollback(any()) }
            
            backupFile.delete()
        }
    }

    @Test
    fun testPostCommitAttachmentVerificationFailure() {
        runBlocking {
            val backupFile = createDummyBackup()
            val mockStorage = mockk<AttachmentStorageService>(relaxed = true)
            every { mockStorage.prepareStagingDir() } returns Result.success(File(context.cacheDir, "stage").apply { mkdirs() })
            every { mockStorage.prepareRollbackDir() } returns Result.success(File(context.cacheDir, "rollback").apply { mkdirs() })
            every { mockStorage.moveActiveToRollback(any()) } returns Result.success(Unit)
            every { mockStorage.activateStagedAttachments(any()) } returns Result.success(Unit)
            every { mockStorage.verifyActiveAttachmentRoot(any(), any()) } throws IOException("Verification failed")

            val failingCoordinator = createCoordinator(mockStorage = mockStorage)
            val result = failingCoordinator.restore(backupFile) { _, _ -> }

            assertTrue(result.isFailure)
            // Verify rollback called because it failed after activation
            coVerify { mockStorage.rollback(any()) }

            backupFile.delete()
        }
    }

    @Test
    fun testSafetyBackupCreationFailure() {
        runBlocking {
            val backupFile = createDummyBackup()
            val fakeArchiveService = object : BackupArchiveService(db, transactionRunner, attachmentStorage, kotlinx.serialization.json.Json { ignoreUnknownKeys = true }, validator, context) {
                override suspend fun createBackupArchive(isSafetyBackup: Boolean, onProgress: (Int) -> Unit): Result<CreatedBackupArchive> {
                    return Result.failure(IOException("Safety failed"))
                }
            }

            val failingCoordinator = createCoordinator(mockArchiveService = fakeArchiveService)
            val result = failingCoordinator.restore(backupFile) { _, _ -> }

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Safety backup failed") == true)
            
            backupFile.delete()
        }
    }

    @Test
    fun testSafetyBackupValidationFailure() {
        runBlocking {
            val backupFile = createDummyBackup()
            val mockValidator = mockk<BackupArchiveValidator>()
            // First validation (of restore file) succeeds
            every { mockValidator.validate(eq(backupFile)) } answers { validator.validate(backupFile) }
            // Second validation (of safety backup) fails
            every { mockValidator.validate(neq(backupFile)) } returns Result.failure(IOException("Safety invalid"))

            val failingCoordinator = createCoordinator(mockValidator = mockValidator)
            val result = failingCoordinator.restore(backupFile) { _, _ -> }

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Safety invalid") == true)

            backupFile.delete()
        }
    }

    @Test
    fun testPreflightReadsActualRoomDatabaseFiles() {
        runBlocking {
            val dbFile = context.getDatabasePath(MapsteadDatabase.DATABASE_NAME)
            dbFile.parentFile?.mkdirs()
            dbFile.writeText("some database dummy content")
            val walFile = File(dbFile.path + "-wal")
            walFile.writeText("wal dummy content")
            val shmFile = File(dbFile.path + "-shm")
            shmFile.writeText("shm dummy content")
            
            val expectedSize = dbFile.length() + walFile.length() + shmFile.length()
            
            val slotEstimatedDbBytes = slot<Long>()
            val slotEstimatedSafetyBytes = slot<Long>()
            
            val mockOpCoordinator = mockk<BackupOperationCoordinator>(relaxed = true)
            coEvery {
                mockOpCoordinator.checkStorageCapacity(
                    any(), any(), any(), capture(slotEstimatedSafetyBytes), capture(slotEstimatedDbBytes)
                )
            } returns Result.success(Unit)
            
            val tempBackupFile = createDummyBackup()
            val localCoordinator = createCoordinator(mockOpCoordinator = mockOpCoordinator)
            
            localCoordinator.restore(tempBackupFile, createSafetyBackup = false) { _, _ -> }
            
            assertTrue(slotEstimatedDbBytes.isCaptured)
            assertEquals(expectedSize, slotEstimatedDbBytes.captured)
            
            dbFile.delete()
            walFile.delete()
            shmFile.delete()
            tempBackupFile.delete()
        }
    }

    private suspend fun createDummyBackup(): File {
        db.propertyDao().insertProperty(PropertyEntity(name = "Test", propertyType = "Home"))
        return archiveService.createBackupArchive().getOrThrow().file
    }

    private fun createCoordinator(
        mockStorage: AttachmentStorageService? = null,
        mockArchiveService: BackupArchiveService? = null,
        mockValidator: BackupArchiveValidator? = null,
        mockOpCoordinator: BackupOperationCoordinator? = null
    ): RestoreCoordinator {
        return RestoreCoordinator(
            db,
            transactionRunner,
            mockArchiveService ?: archiveService,
            mockStorage ?: attachmentStorage,
            prefs,
            journalManager,
            mockValidator ?: validator,
            mockOpCoordinator ?: operationCoordinator,
            reminderScheduler,
            context
        )
    }
}
