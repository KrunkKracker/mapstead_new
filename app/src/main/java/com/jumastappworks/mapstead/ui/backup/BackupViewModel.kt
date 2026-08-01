package com.jumastappworks.mapstead.ui.backup

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.backup.*
import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.db.dao.BackupDao
import com.jumastappworks.mapstead.data.db.entities.BackupRecordEntity
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*
import javax.inject.Inject

data class BackupUiState(
    val isDriveAuthorized: Boolean = false,
    val authorizedEmail: String? = null,
    val currentOperation: BackupRecordEntity? = null,
    val backupHistory: List<BackupRecordEntity> = emptyList(),
    val driveBackups: List<DriveBackupFile> = emptyList(),
    val safetyBackups: List<SafetyBackupReference> = emptyList(),
    val isLoadingDriveBackups: Boolean = false,
    val error: String? = null,
    val resolutionIntent: PendingIntent? = null,
    val pendingRestore: PendingRestore? = null,
    val recoveryStatus: RestoreRecoveryManager.RecoveryStatus = RestoreRecoveryManager.RecoveryStatus.Idle,
    val pendingIncompleteUpload: PendingIncompleteUpload? = null
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val authManager: DriveAuthorizationManager,
    private val driveClientFactory: MapsteadDriveClientFactory,
    private val archiveService: BackupArchiveService,
    private val restoreCoordinator: RestoreCoordinator,
    private val coordinator: BackupOperationCoordinator,
    private val recoveryManager: RestoreRecoveryManager,
    private val backupDao: BackupDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val savedStateHandle: SavedStateHandle,
    private val featureGate: BackupFeatureGate,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    private var pendingAction: PendingDriveAction?
        get() = savedStateHandle.get<PendingDriveAction>("pending_action")
        set(value) { 
            savedStateHandle["pending_action"] = value
        }

    init {
        viewModelScope.launch {
            backupDao.getAllBackupRecords().collect { records ->
                _uiState.update { it.copy(backupHistory = records) }
            }
        }
        if (featureGate.isEnabled) {
            viewModelScope.launch {
                recoveryManager.recoveryStatus.collect { status ->
                    _uiState.update { it.copy(recoveryStatus = status) }
                }
            }
            recoveryManager.checkAndRecover(viewModelScope)
            refreshSafetyBackups()
        }
    }

    fun onRetryRecovery() {
        if (!featureGate.isEnabled) return
        recoveryManager.checkAndRecover(viewModelScope)
    }

    fun onAcknowledgeRecovery() {
        // Here we could just clear the status if the manager allows it, 
        // or just let it stay Idle if checkAndRecover didn't find anything.
        // For now, let's just trigger a re-check or similar.
    }

    private fun refreshSafetyBackups() {
        viewModelScope.launch {
            val backups = restoreCoordinator.getSafetyBackups()
            _uiState.update { it.copy(safetyBackups = backups) }
        }
    }

    fun onConnectDrive(activity: Activity) {
        if (!featureGate.isEnabled) return
        pendingAction = PendingDriveAction.Connect(0, UUID.randomUUID().toString())
        executeDriveAction(activity)
    }

    fun onBackupNow(activity: Activity) {
        if (!featureGate.isEnabled) return
        pendingAction = PendingDriveAction.CreateBackup(0, UUID.randomUUID().toString())
        executeDriveAction(activity)
    }

    fun loadDriveBackups(activity: Activity) {
        if (!featureGate.isEnabled) return
        pendingAction = PendingDriveAction.ListBackups(0, UUID.randomUUID().toString())
        executeDriveAction(activity)
    }

    fun onRestoreClick(activity: Activity, file: DriveBackupFile) {
        if (!featureGate.isEnabled) return
        pendingAction = PendingDriveAction.PreviewRestore(file.driveFileId, 0, UUID.randomUUID().toString())
        executeDriveAction(activity)
    }

    fun onDeleteClick(activity: Activity, fileId: String) {
        if (!featureGate.isEnabled) return
        pendingAction = PendingDriveAction.Delete(fileId, 0, UUID.randomUUID().toString())
        executeDriveAction(activity)
    }

    private fun executeDriveAction(activity: Activity) {
        if (!featureGate.isEnabled) return
        viewModelScope.launch {
            val authResult = authManager.authorize(activity)
            handleAuthResult(authResult, activity)
        }
    }

    fun onAuthorizationResult(activity: Activity, intent: Intent?) {
        if (!featureGate.isEnabled) return
        val result = authManager.getAuthorizationResult(intent)
        handleAuthResult(result, activity)
    }

    private fun handleAuthResult(result: DriveAuthorizationResult, activity: Activity) {
        when (result) {
            is DriveAuthorizationResult.Authorized -> {
                _uiState.update { it.copy(isDriveAuthorized = true, authorizedEmail = result.email, resolutionIntent = null) }
                val action = pendingAction
                if (action != null) {
                    resumeAction(action, result.accessToken, activity)
                }
            }
            is DriveAuthorizationResult.ResolutionRequired -> {
                _uiState.update { it.copy(resolutionIntent = result.pendingIntent) }
            }
            is DriveAuthorizationResult.Failure -> {
                pendingAction = null
                handleDriveError(result.error)
            }
        }
    }

    private fun resumeAction(action: PendingDriveAction, accessToken: String, activity: Activity) {
        val driveClient = driveClientFactory.create(accessToken)
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            when(action) {
                is PendingDriveAction.Connect -> { performList(driveClient, accessToken, activity) }
                is PendingDriveAction.ListBackups -> { performList(driveClient, accessToken, activity) }
                is PendingDriveAction.CreateBackup -> { performBackup(driveClient, accessToken, activity) }
                is PendingDriveAction.Restore -> { /* Handled after preview */ }
                is PendingDriveAction.PreviewRestore -> performRestorePreview(driveClient, accessToken, action, activity)
                is PendingDriveAction.Delete -> { performDelete(driveClient, accessToken, action.fileId, activity) }
            }
        }
    }

    private suspend fun handleDrive401AndRetry(accessToken: String, activity: Activity) {
        authManager.clearToken(accessToken)
        val currentAction = pendingAction
        if (currentAction != null && currentAction.retryCount < 1) {
            val updatedAction = when (currentAction) {
                is PendingDriveAction.Connect -> currentAction.copy(retryCount = currentAction.retryCount + 1)
                is PendingDriveAction.ListBackups -> currentAction.copy(retryCount = currentAction.retryCount + 1)
                is PendingDriveAction.CreateBackup -> currentAction.copy(retryCount = currentAction.retryCount + 1)
                is PendingDriveAction.Restore -> currentAction.copy(retryCount = currentAction.retryCount + 1)
                is PendingDriveAction.PreviewRestore -> currentAction.copy(retryCount = currentAction.retryCount + 1)
                is PendingDriveAction.Delete -> currentAction.copy(retryCount = currentAction.retryCount + 1)
            }
            pendingAction = updatedAction
            _uiState.update { it.copy(isDriveAuthorized = false) }
            val authResult = authManager.authorize(activity)
            handleAuthResult(authResult, activity)
        } else {
            pendingAction = null
            handleDriveError(DriveError.AuthorizationExpired)
        }
    }

    private suspend fun performList(client: MapsteadDriveClient, accessToken: String, activity: Activity) {
        _uiState.update { it.copy(isLoadingDriveBackups = true) }
        client.listBackups().onSuccess { backups ->
            _uiState.update { it.copy(driveBackups = backups, isLoadingDriveBackups = false) }
            pendingAction = null
        }.onFailure { e ->
            val error = mapThrowableToDriveError(e)
            if (error == DriveError.AuthorizationExpired) {
                handleDrive401AndRetry(accessToken, activity)
            } else {
                pendingAction = null
                handleDriveError(error)
            }
            _uiState.update { it.copy(isLoadingDriveBackups = false) }
        }
    }

    private suspend fun performBackup(client: MapsteadDriveClient, accessToken: String, activity: Activity) {
        val record = BackupRecordEntity(
            operationType = BackupOperationType.BACKUP.name,
            status = BackupOperationPhase.PREPARING.name
        )
        backupDao.insertBackupRecord(record)
        _uiState.update { it.copy(currentOperation = record) }

        coordinator.runOperation(BackupOperationPhase.PREPARING) { updatePhase ->
            val dbFile = context.getDatabasePath(MapsteadDatabase.DATABASE_NAME)
            val walFile = File(dbFile.path + "-wal")
            val shmFile = File(dbFile.path + "-shm")
            val dbSize = (if (dbFile.exists()) dbFile.length() else 0L) +
                    (if (walFile.exists()) walFile.length() else 0L) +
                    (if (shmFile.exists()) shmFile.length() else 0L)
            val attachmentsDir = File(context.filesDir, "mapstead_attachments")
            val currentAttachmentsSize = attachmentsDir.listFiles()?.sumOf { it.length() } ?: 0L

            coordinator.checkStorageCapacity(
                estimatedArchiveBytes = (dbSize + currentAttachmentsSize) * 2,
                estimatedStagingBytes = 0,
                estimatedRollbackBytes = 0,
                estimatedSafetyBytes = 0,
                estimatedDbBytes = 0
            ).getOrThrow()

            updatePhase(BackupOperationPhase.CREATING_ARCHIVE)
            archiveService.createBackupArchive { progress ->
                updateProgress(record.id, progress / 2)
            }.mapCatching { createdBackup ->
                val zipFile = createdBackup.file
                val manifest = createdBackup.manifest

                if (manifest.warnings.isNotEmpty()) {
                    _uiState.update { 
                        it.copy(
                            pendingIncompleteUpload = PendingIncompleteUpload(zipFile, manifest, record.id, accessToken),
                            currentOperation = null
                        ) 
                    }
                    return@mapCatching
                }

                updatePhase(BackupOperationPhase.UPLOADING)
                updateProgressWithStatus(record.id, BackupOperationPhase.UPLOADING, 60)

                val metadata = DriveBackupMetadata(
                    backupId = manifest.backupId,
                    createdAt = manifest.createdAt,
                    appVersion = manifest.appVersionName,
                    formatVersion = manifest.formatVersion
                )
                client.uploadBackup(zipFile, metadata).onSuccess { driveFile ->
                    val finalRecord = record.copy(
                        status = BackupOperationPhase.SUCCESS.name,
                        progressPercent = 100,
                        completedAt = Instant.now(),
                        backupId = driveFile.backupId,
                        driveFileId = driveFile.driveFileId,
                        fileName = driveFile.name,
                        fileSize = driveFile.size
                    )
                    backupDao.updateBackupRecord(finalRecord)
                    _uiState.update { it.copy(currentOperation = null) }
                    zipFile.delete()
                    pendingAction = null
                }.onFailure { e ->
                    val error = mapThrowableToDriveError(e)
                    zipFile.delete()
                    if (error == DriveError.AuthorizationExpired) {
                        failOperation(record, "Upload failed: Authorization expired, retrying...")
                        handleDrive401AndRetry(accessToken, activity)
                    } else {
                        pendingAction = null
                        failOperation(record, "Upload failed: ${e.message}")
                        handleDriveError(error)
                    }
                }.getOrThrow()
            }.onFailure { e ->
                if (e.cause?.toDriveError() != DriveError.AuthorizationExpired && e.toDriveError() != DriveError.AuthorizationExpired) {
                    pendingAction = null
                    failOperation(record, "Backup failed: ${e.message}")
                }
            }
        }
    }

    private suspend fun performRestorePreview(client: MapsteadDriveClient, accessToken: String, action: PendingDriveAction.PreviewRestore, activity: Activity) {
        val fileId = action.fileId
        val driveFile = _uiState.value.driveBackups.find { it.driveFileId == fileId }
            ?: return 

        val tempZip = File(context.cacheDir, "preview_$fileId.zip")
        _uiState.update { it.copy(isLoadingDriveBackups = true) }
        
        client.downloadBackup(fileId, tempZip).onSuccess {
            archiveService.getRestorePreview(tempZip).onSuccess { report ->
                _uiState.update { 
                    it.copy(
                        pendingRestore = PendingRestore(driveFile, tempZip, report),
                        isLoadingDriveBackups = false 
                    ) 
                }
            }.onFailure { e ->
                _uiState.update { it.copy(error = "Invalid backup: ${e.message}", isLoadingDriveBackups = false) }
                tempZip.delete()
                pendingAction = null
            }
        }.onFailure { e ->
            val error = mapThrowableToDriveError(e)
            tempZip.delete()
            if (error == DriveError.AuthorizationExpired) {
                handleDrive401AndRetry(accessToken, activity)
            } else {
                _uiState.update { it.copy(error = "Download failed: ${e.message}", isLoadingDriveBackups = false) }
                handleDriveError(error)
                pendingAction = null
            }
        }
    }

    fun onConfirmRestore(activity: Activity) {
        if (!featureGate.isEnabled) return
        val pending = _uiState.value.pendingRestore ?: return
        
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            val record = BackupRecordEntity(
                operationType = BackupOperationType.RESTORE.name,
                status = BackupOperationPhase.DOWNLOADING.name,
                driveFileId = pending.driveFile.driveFileId,
                backupId = pending.validationReport.manifest.backupId,
                fileName = pending.driveFile.name
            )
            backupDao.insertBackupRecord(record)
            _uiState.update { it.copy(currentOperation = record, pendingRestore = null) }

            coordinator.runOperation(BackupOperationPhase.DOWNLOADING) { updatePhase ->
                // Preflight check for restore
                val dbFile = context.getDatabasePath(MapsteadDatabase.DATABASE_NAME)
                val walFile = File(dbFile.path + "-wal")
                val shmFile = File(dbFile.path + "-shm")
                val dbSize = (if (dbFile.exists()) dbFile.length() else 0L) +
                        (if (walFile.exists()) walFile.length() else 0L) +
                        (if (shmFile.exists()) shmFile.length() else 0L)
                val attachmentsDir = File(context.filesDir, "mapstead_attachments")
                val currentAttachmentsSize = attachmentsDir.listFiles()?.sumOf { it.length() } ?: 0L

                coordinator.checkStorageCapacity(
                    estimatedArchiveBytes = pending.driveFile.size,
                    estimatedStagingBytes = pending.validationReport.manifest.includedAttachmentBytes,
                    estimatedRollbackBytes = currentAttachmentsSize,
                    estimatedSafetyBytes = dbSize + currentAttachmentsSize,
                    estimatedDbBytes = dbSize // estimated new db size is similar
                ).getOrThrow()

                restoreCoordinator.restore(
                    pending.localArchive,
                    manifestBackupId = pending.validationReport.manifest.backupId,
                    driveFileId = pending.driveFile.driveFileId
                ) { status, progress ->
                    updatePhase(status)
                    updateProgressWithStatus(record.id, status, progress)
                }.onSuccess { safetyRef ->
                    val finalRecord = record.copy(
                        status = BackupOperationPhase.SUCCESS.name, 
                        progressPercent = 100, 
                        completedAt = Instant.now(),
                        safetyBackupPath = safetyRef?.file?.absolutePath
                    )
                    backupDao.updateBackupRecord(finalRecord)
                    _uiState.update { it.copy(currentOperation = null) }
                    refreshSafetyBackups()
                    pending.localArchive.delete()
                    pendingAction = null
                }.onFailure { e ->
                    failOperation(record, "Restore failed: ${e.message}")
                    pending.localArchive.delete()
                    pendingAction = null
                }
            }
        }
    }

    fun onCancelRestore() {
        val pending = _uiState.value.pendingRestore
        pending?.localArchive?.delete()
        _uiState.update { it.copy(pendingRestore = null) }
        pendingAction = null
    }

    private suspend fun performDelete(client: MapsteadDriveClient, accessToken: String, fileId: String, activity: Activity) {
        client.deleteBackup(fileId).onSuccess {
            _uiState.update { state -> state.copy(driveBackups = state.driveBackups.filter { it.driveFileId != fileId }) }
            pendingAction = null
        }.onFailure { e ->
            val error = mapThrowableToDriveError(e)
            if (error == DriveError.AuthorizationExpired) {
                handleDrive401AndRetry(accessToken, activity)
            } else {
                _uiState.update { it.copy(error = "Delete failed: ${e.message}") }
                handleDriveError(error)
                pendingAction = null
            }
        }
    }

    private fun updateProgress(recordId: UUID, progress: Int) {
        viewModelScope.launch {
            _uiState.update { state ->
                if (state.currentOperation?.id == recordId) {
                    val updated = state.currentOperation.copy(progressPercent = progress)
                    backupDao.updateBackupRecord(updated)
                    state.copy(currentOperation = updated)
                } else state
            }
        }
    }

    private fun updateProgressWithStatus(recordId: UUID, status: BackupOperationPhase, progress: Int) {
        viewModelScope.launch {
            _uiState.update { state ->
                if (state.currentOperation?.id == recordId) {
                    val updated = state.currentOperation.copy(status = status.name, progressPercent = progress)
                    backupDao.updateBackupRecord(updated)
                    state.copy(currentOperation = updated)
                } else state
            }
        }
    }

    fun onRestoreSafetyBackup(backupId: String) {
        if (!featureGate.isEnabled) return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            val record = BackupRecordEntity(
                operationType = "SAFETY_RESTORE",
                status = BackupOperationPhase.PREPARING.name,
                backupId = backupId
            )
            backupDao.insertBackupRecord(record)
            _uiState.update { it.copy(currentOperation = record) }

            restoreCoordinator.restoreSafetyBackup(backupId) { status, progress ->
                updateProgressWithStatus(record.id, status, progress)
            }.onSuccess { safetyRef ->
                val finalRecord = record.copy(
                    status = BackupOperationPhase.SUCCESS.name,
                    progressPercent = 100,
                    completedAt = Instant.now(),
                    safetyBackupPath = safetyRef?.file?.absolutePath
                )
                backupDao.updateBackupRecord(finalRecord)
                _uiState.update { it.copy(currentOperation = null) }
                refreshSafetyBackups()
            }.onFailure { e ->
                failOperation(record, "Safety restore failed: ${e.message}")
            }
        }
    }

    fun onDeleteSafetyBackup(backupId: String) {
        if (!featureGate.isEnabled) return
        viewModelScope.launch {
            restoreCoordinator.deleteSafetyBackup(backupId).onSuccess {
                refreshSafetyBackups()
            }.onFailure { e ->
                _uiState.update { it.copy(error = "Delete failed: ${e.message}") }
            }
        }
    }

    private suspend fun failOperation(record: BackupRecordEntity, message: String) {
        val failed = record.copy(
            status = BackupOperationPhase.FAILED.name,
            completedAt = Instant.now(),
            userSafeErrorMessage = message
        )
        backupDao.updateBackupRecord(failed)
        _uiState.update { it.copy(currentOperation = null, error = message) }
    }

    private fun handleDriveError(error: DriveError) {
        val messageRes = when (error) {
            DriveError.AuthorizationExpired -> R.string.error_drive_auth_expired
            DriveError.NetworkUnavailable -> R.string.error_drive_network
            DriveError.NotFound -> R.string.error_drive_not_found
            DriveError.QuotaExceeded -> R.string.error_drive_quota
            DriveError.PermissionDenied -> R.string.error_drive_permission
            DriveError.InvalidResponse -> R.string.error_drive_invalid_response
            DriveError.Cancelled -> null // Usually don't show error for cancel
            is DriveError.Unknown -> null
        }
        
        val message = try {
            if (messageRes != null) context.getString(messageRes) 
            else (error as? DriveError.Unknown)?.safeMessage ?: context.getString(R.string.error_drive_unknown)
        } catch (e: android.content.res.Resources.NotFoundException) {
            when (error) {
                DriveError.AuthorizationExpired -> "Google Drive authorization expired. Please connect again."
                DriveError.NetworkUnavailable -> "Network unavailable. Please check your connection."
                DriveError.NotFound -> "Backup file not found on Google Drive."
                DriveError.QuotaExceeded -> "Google Drive quota exceeded."
                DriveError.PermissionDenied -> "Google Drive permission denied."
                DriveError.InvalidResponse -> "Invalid response from Google Drive."
                else -> (error as? DriveError.Unknown)?.safeMessage ?: "An unknown Google Drive error occurred."
            }
        }

        if (error != DriveError.Cancelled) {
            _uiState.update { it.copy(error = message) }
        }
        
        if (error == DriveError.AuthorizationExpired) {
            _uiState.update { it.copy(isDriveAuthorized = false) }
        }
    }

    private fun mapThrowableToDriveError(e: Throwable): DriveError {
        return e.toDriveError()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearResolutionIntent() {
        _uiState.update { it.copy(resolutionIntent = null) }
    }

    fun onConfirmIncompleteUpload(activity: Activity) {
        if (!featureGate.isEnabled) return
        val pending = _uiState.value.pendingIncompleteUpload ?: return
        _uiState.update { it.copy(pendingIncompleteUpload = null) }
        
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            val record = backupDao.getAllBackupRecords().first().find { it.id == pending.recordId } ?: return@launch
            val driveClient = driveClientFactory.create(pending.accessToken)
            _uiState.update { it.copy(currentOperation = record) }
            
            val metadata = DriveBackupMetadata(
                backupId = pending.manifest.backupId,
                createdAt = pending.manifest.createdAt,
                appVersion = pending.manifest.appVersionName,
                formatVersion = pending.manifest.formatVersion
            )
            driveClient.uploadBackup(pending.archiveFile, metadata).onSuccess { driveFile ->
                val finalRecord = record.copy(
                    status = BackupOperationPhase.SUCCESS.name,
                    progressPercent = 100,
                    completedAt = Instant.now(),
                    backupId = driveFile.backupId,
                    driveFileId = driveFile.driveFileId,
                    fileName = driveFile.name,
                    fileSize = driveFile.size
                )
                backupDao.updateBackupRecord(finalRecord)
                _uiState.update { it.copy(currentOperation = null) }
                pending.archiveFile.delete()
                pendingAction = null
            }.onFailure { e ->
                val error = mapThrowableToDriveError(e)
                pending.archiveFile.delete()
                failOperation(record, "Upload failed: ${e.message}")
                handleDriveError(error)
                pendingAction = null
            }
        }
    }

    fun onCancelIncompleteUpload() {
        if (!featureGate.isEnabled) return
        val pending = _uiState.value.pendingIncompleteUpload ?: return
        _uiState.update { it.copy(pendingIncompleteUpload = null) }
        pending.archiveFile.delete()
        pendingAction = null
        viewModelScope.launch {
            val record = backupDao.getAllBackupRecords().first().find { it.id == pending.recordId }
            if (record != null) {
                failOperation(record, "Backup cancelled: incomplete backup rejected by user")
            }
        }
    }

    fun onClearHistory() {
        if (!featureGate.isEnabled) return
        viewModelScope.launch {
            backupDao.clearCompletedRecords()
        }
    }
}
