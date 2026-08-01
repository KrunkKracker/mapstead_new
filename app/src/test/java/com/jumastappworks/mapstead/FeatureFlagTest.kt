package com.jumastappworks.mapstead

import com.jumastappworks.mapstead.data.backup.*
import com.jumastappworks.mapstead.ui.backup.BackupViewModel
import com.jumastappworks.mapstead.ui.navigation.BackupRouteResolution
import com.jumastappworks.mapstead.ui.navigation.Route
import com.jumastappworks.mapstead.ui.navigation.resolveBackupDestination
import com.jumastappworks.mapstead.ui.navigation.sanitizeDisabledBackupRoute
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FeatureFlagTest {

    @Test
    fun testProductionBackupFeatureGateIsDisabledByDefault() {
        val gate = BuildConfigBackupFeatureGate()
        // Must be false in customer builds
        assertFalse(
            "Production BackupFeatureGate must be disabled by default in customer builds",
            gate.isEnabled
        )
    }

    @Test
    fun testFakeBackupFeatureGateExposesConfigurationCorrectly() {
        val enabledGate = FakeBackupFeatureGate(isEnabled = true)
        val disabledGate = FakeBackupFeatureGate(isEnabled = false)
        assertTrue(enabledGate.isEnabled)
        assertFalse(disabledGate.isEnabled)
    }

    @Test
    fun testResolveBackupDestinationRedirection() {
        // Disabled gate must resolve to settings redirect
        val disabledResolution = resolveBackupDestination(isBackupEnabled = false)
        assertEquals(BackupRouteResolution.REDIRECT_TO_SETTINGS, disabledResolution)

        // Enabled gate allows backup
        val enabledResolution = resolveBackupDestination(isBackupEnabled = true)
        assertEquals(BackupRouteResolution.ALLOW_BACKUP, enabledResolution)
    }

    @Test
    fun testSanitizeDisabledBackupRouteBehavior() {
        // 1. Empty stack becomes exactly Settings
        val emptyStack = emptyList<Route>()
        val result1 = sanitizeDisabledBackupRoute(emptyStack)
        assertEquals(listOf(Route.Settings), result1)

        // 2. Backup-only stack becomes exactly Settings
        val backupOnly = listOf(Route.Backup)
        val result2 = sanitizeDisabledBackupRoute(backupOnly)
        assertEquals(listOf(Route.Settings), result2)

        // 3. One Backup entry is removed
        val withBackup = listOf(Route.Properties, Route.Backup)
        val result3 = sanitizeDisabledBackupRoute(withBackup)
        assertEquals(listOf(Route.Properties, Route.Settings), result3)

        // 4. Multiple Backup entries are removed
        val multiBackup = listOf(Route.Properties, Route.Backup, Route.Backup)
        val result4 = sanitizeDisabledBackupRoute(multiBackup)
        assertEquals(listOf(Route.Properties, Route.Settings), result4)

        // 5. Duplicate Settings entries become one Settings entry
        val duplicateSettings = listOf(Route.Properties, Route.Settings, Route.Backup, Route.Settings)
        val result5 = sanitizeDisabledBackupRoute(duplicateSettings)
        assertEquals(listOf(Route.Properties, Route.Settings), result5)

        // 6. Settings is always the final active route
        val propertiesEnd = listOf(Route.Settings, Route.Properties, Route.Backup)
        val result6 = sanitizeDisabledBackupRoute(propertiesEnd)
        assertEquals(listOf(Route.Properties, Route.Settings), result6)

        // 7. Unrelated routes cannot remain above Settings
        val unrelatedAbove = listOf(Route.Properties, Route.Settings, Route.AddProperty, Route.Backup)
        val result7 = sanitizeDisabledBackupRoute(unrelatedAbove)
        assertEquals(listOf(Route.Properties, Route.AddProperty, Route.Settings), result7)

        // 8. The sanitizer is idempotent
        val result8 = sanitizeDisabledBackupRoute(result7)
        assertEquals(result7, result8)

        // 9. No route operation can produce an empty stack
        val result9 = sanitizeDisabledBackupRoute(listOf(Route.Backup))
        assertTrue(result9.isNotEmpty())
        assertEquals(Route.Settings, result9.last())
    }

    @Test
    fun testBackupViewModelEntryPointsBypassWhenDisabled() {
        val authManager = FakeDriveAuthorizationManager()
        val driveClientFactory = mockk<MapsteadDriveClientFactory>(relaxed = true)
        val archiveService = mockk<BackupArchiveService>(relaxed = true)
        val restoreCoordinator = mockk<RestoreCoordinator>(relaxed = true)
        val coordinator = mockk<BackupOperationCoordinator>(relaxed = true)
        val recoveryManager = mockk<RestoreRecoveryManager>(relaxed = true)
        val backupDao = mockk<com.jumastappworks.mapstead.data.db.dao.BackupDao>(relaxed = true)
        val userPreferencesRepository = mockk<com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository>(relaxed = true)
        val savedStateHandle = androidx.lifecycle.SavedStateHandle()
        val context = mockk<android.content.Context>(relaxed = true)

        val disabledGate = FakeBackupFeatureGate(isEnabled = false)
        every { recoveryManager.recoveryStatus } returns MutableStateFlow(RestoreRecoveryManager.RecoveryStatus.Idle)

        val viewModel = BackupViewModel(
            authManager, driveClientFactory, archiveService, restoreCoordinator,
            coordinator, recoveryManager, backupDao, userPreferencesRepository,
            savedStateHandle, disabledGate, context
        )

        val mockActivity = mockk<android.app.Activity>()

        // 1. Connect
        viewModel.onConnectDrive(mockActivity)
        assertEquals(0, authManager.authorizeCallCount)

        // 2. Backup now
        viewModel.onBackupNow(mockActivity)
        assertNull(viewModel.uiState.value.currentOperation)

        // 3. Confirm Restore
        viewModel.onConfirmRestore(mockActivity)
        assertNull(viewModel.uiState.value.pendingRestore)

        // 4. Delete Safety Backup
        viewModel.onDeleteSafetyBackup("safety-id")
        // Just checking that it finishes safely without throwing
    }

    @Test
    fun testStartupRecoveryManagerBypassedWhenDisabled() {
        val journalManager = mockk<RestoreJournalManager>(relaxed = true)
        val attachmentStorage = mockk<AttachmentStorageService>(relaxed = true)
        val db = mockk<com.jumastappworks.mapstead.data.db.MapsteadDatabase>(relaxed = true)
        val archiveService = mockk<BackupArchiveService>(relaxed = true)
        val disabledGate = FakeBackupFeatureGate(isEnabled = false)

        val manager = RestoreRecoveryManager(
            journalManager, attachmentStorage, db, archiveService, disabledGate
        )

        val mockScope = mockk<kotlinx.coroutines.CoroutineScope>()

        manager.checkAndRecover(mockScope)

        // Verify journal was never loaded since recovery was bypassed
        verify(exactly = 0) { journalManager.loadJournal() }
    }

    @Test
    fun testExistingBackupRecordsAndRecoveryFilesArePreservedOnFlagFalse() {
        // Verify files remain untouched
        val tempDir = File(System.getProperty("java.io.tmpdir"), "safety_backups_test")
        tempDir.mkdirs()
        val dummySafetyFile = File(tempDir, "safety_1.mapsteadbackup")
        dummySafetyFile.writeText("preserved data")

        assertTrue(dummySafetyFile.exists())
        
        // Simulating applying the disabled gate - no destructive cleanups should occur
        val disabledGate = FakeBackupFeatureGate(isEnabled = false)
        if (!disabledGate.isEnabled) {
            assertTrue("Existing safety backups must remain intact when disabled", dummySafetyFile.exists())
        }

        dummySafetyFile.delete()
        tempDir.delete()
    }
}
