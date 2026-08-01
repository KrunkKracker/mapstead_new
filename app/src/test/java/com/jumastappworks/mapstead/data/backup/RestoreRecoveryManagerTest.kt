package com.jumastappworks.mapstead.data.backup

import com.jumastappworks.mapstead.data.backup.RestoreRecoveryManager.RecoveryStatus
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RestoreRecoveryManagerTest {

    private lateinit var tempJournalFile: File
    private lateinit var journalManager: RestoreJournalManager
    private val attachmentStorage = mockk<AttachmentStorageService>(relaxed = true)
    private val db = mockk<com.jumastappworks.mapstead.data.db.MapsteadDatabase>(relaxed = true)
    private val archiveService = mockk<BackupArchiveService>(relaxed = true)
    private lateinit var manager: RestoreRecoveryManager
    private val testScope = TestScope(StandardTestDispatcher())
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Before
    fun setup() {
        tempJournalFile = File.createTempFile("restore_journal", ".json")
        journalManager = RestoreJournalManager(tempJournalFile, json)
        manager = RestoreRecoveryManager(journalManager, attachmentStorage, db, archiveService, FakeBackupFeatureGate(isEnabled = true), StandardTestDispatcher(testScope.testScheduler))
    }

    @After
    fun tearDown() {
        tempJournalFile.delete()
    }

    @Test
    fun testCleanupOnEarlyStages() = testScope.runTest {
        val stages = listOf(
            RestoreJournalStage.INITIALIZED,
            RestoreJournalStage.STAGING_ATTACHMENTS,
            RestoreJournalStage.CREATING_SAFETY_BACKUP
        )

        for (stage in stages) {
            val journal = RestoreJournalData(
                stage = stage,
                stagingAttachmentPath = File.createTempFile("stage", "").absolutePath,
                downloadedArchivePath = File.createTempFile("archive", ".zip").absolutePath
            )
            journalManager.saveJournal(journal)
            
            manager.checkAndRecover(this)
            advanceUntilIdle()
            
            assertEquals(RecoveryStatus.Success, manager.recoveryStatus.value)
            // Verify journal file was deleted after success
            assertTrue(!tempJournalFile.exists())
        }
    }

    @Test
    fun testRollbackOnActivatingAttachments() = testScope.runTest {
        val tempDir = File.createTempFile("rollback", "").apply { delete(); mkdir() }
        val journal = RestoreJournalData(
            stage = RestoreJournalStage.ACTIVATING_ATTACHMENTS,
            rollbackAttachmentPath = tempDir.absolutePath
        )
        journalManager.saveJournal(journal)
        
        manager.checkAndRecover(this)
        advanceUntilIdle()
        
        verify { attachmentStorage.rollback(match { it.absolutePath == tempDir.absolutePath }) }
        assertTrue(manager.recoveryStatus.value is RecoveryStatus.Success)
        tempDir.delete()
    }

    @Test
    fun testManualRecoveryRequiredOnDbTransactionFailure() = testScope.runTest {
        val journal = RestoreJournalData(
            stage = RestoreJournalStage.DATABASE_TRANSACTION,
            wasDbCommitted = false
        )
        journalManager.saveJournal(journal)
        
        manager.checkAndRecover(this)
        advanceUntilIdle()
        
        assertEquals(RecoveryStatus.RecoveryRequired, manager.recoveryStatus.value)
        val loaded = journalManager.loadJournal()
        assertEquals(RestoreJournalStage.RECOVERY_FAILED, loaded?.stage)
    }

    @Test
    fun testManualRecoveryRequiredOnPostRestoreVerificationFailure() = testScope.runTest {
        val journal = RestoreJournalData(
            stage = RestoreJournalStage.POST_RESTORE_VERIFICATION,
            wasPostRestoreVerified = false
        )
        journalManager.saveJournal(journal)
        
        manager.checkAndRecover(this)
        advanceUntilIdle()
        
        assertEquals(RecoveryStatus.RecoveryRequired, manager.recoveryStatus.value)
    }
}
