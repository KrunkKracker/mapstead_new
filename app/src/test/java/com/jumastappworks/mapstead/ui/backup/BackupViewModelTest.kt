package com.jumastappworks.mapstead.ui.backup

import android.app.Activity
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.jumastappworks.mapstead.data.backup.*
import com.jumastappworks.mapstead.data.db.dao.BackupDao
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BackupViewModelTest {

    private val authManager = FakeDriveAuthorizationManager()
    private val driveClient = FakeMapsteadDriveClient()
    private val factory = FakeMapsteadDriveClientFactory(driveClient)
    private val backupArchiveService = mockk<BackupArchiveService>()
    private val restoreCoordinator = mockk<RestoreCoordinator>()
    private val backupDao = mockk<BackupDao>(relaxed = true)
    private val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val activity = mockk<Activity>()
    private val recoveryManager = mockk<RestoreRecoveryManager>(relaxed = true)
    private lateinit var context: Context

    private lateinit var viewModel: BackupViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val gate = FakeBackupFeatureGate(isEnabled = true)
        Dispatchers.setMain(StandardTestDispatcher())
        every { backupDao.getAllBackupRecords() } returns flowOf(emptyList())
        coEvery { restoreCoordinator.getSafetyBackups() } returns emptyList()
        every { recoveryManager.recoveryStatus } returns kotlinx.coroutines.flow.MutableStateFlow(RestoreRecoveryManager.RecoveryStatus.Idle)
        
        viewModel = BackupViewModel(
            authManager, factory, backupArchiveService, restoreCoordinator, 
            mockk(relaxed = true), recoveryManager, backupDao, 
            userPreferencesRepository, SavedStateHandle(), gate, context
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testConnectDriveSuccess() = runTest {
        viewModel.onConnectDrive(activity)
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.isDriveAuthorized)
    }

    @Test
    fun testConnectDriveFailure() = runTest {
        authManager.shouldFail = true
        viewModel.onConnectDrive(activity)
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.error != null)
    }

    @Test
    fun testAuthorizationExpiredRecoveryRetriesOnce() = runTest {
        // 1. Initial authorize
        viewModel.onConnectDrive(activity)
        advanceUntilIdle()
        assertEquals(1, authManager.authorizeCallCount)
        
        // 2. Set to fail with 401
        driveClient.shouldFailWith401 = true
        
        // 3. Trigger action that fails with 401
        viewModel.loadDriveBackups(activity)
        advanceUntilIdle()
        
        // 4. Verify it retried authorize exactly once (1 initial + 1 first attempt + 1 retry = 3)
        assertEquals(3, authManager.authorizeCallCount)
        
        // 5. Subsequent separate triggers should do their own first attempt and retry once (total 5) but not infinite retry
        viewModel.loadDriveBackups(activity)
        advanceUntilIdle()
        assertEquals(5, authManager.authorizeCallCount)
    }

    @Test
    fun testSavedStateHandlePersistence() = runTest {
        val savedStateHandle = SavedStateHandle()
        val gate = FakeBackupFeatureGate(isEnabled = true)
        val vm = BackupViewModel(
            authManager, factory, backupArchiveService, restoreCoordinator, mockk(relaxed = true),
            recoveryManager, backupDao, userPreferencesRepository, savedStateHandle, gate, context
        )
        
        vm.onBackupNow(activity)
        assertTrue(savedStateHandle.get<PendingDriveAction>("pending_action") is PendingDriveAction.CreateBackup)
    }

    @Test
    fun testPendingRestorePreservationAndCancellation() = runTest {
        val tempFile = File.createTempFile("test_backup", ".zip").apply { writeText("dummy content") }
        val metadata = DriveBackupMetadata("bid", "2026-07-14T12:00:00Z", "0.1.0", 1)
        driveClient.uploadBackup(tempFile, metadata)

        val report = mockk<BackupValidationReport>(relaxed = true)
        coEvery { backupArchiveService.getRestorePreview(any()) } returns Result.success(report)

        viewModel.loadDriveBackups(activity)
        advanceUntilIdle()

        val driveBackup = viewModel.uiState.value.driveBackups.first()
        viewModel.onRestoreClick(activity, driveBackup)
        advanceUntilIdle()

        val pending = viewModel.uiState.value.pendingRestore
        assertNotNull(pending) // Enabled in test setup

        val localArchive = pending!!.localArchive
        assertTrue(localArchive.exists())

        viewModel.onCancelRestore()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingRestore)
        assertFalse(localArchive.exists())

        tempFile.delete()
    }
}
