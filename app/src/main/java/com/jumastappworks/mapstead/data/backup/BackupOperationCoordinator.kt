package com.jumastappworks.mapstead.data.backup

import android.content.Context
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupOperationCoordinator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mutex = Mutex()
    private val _currentPhase = MutableStateFlow(BackupOperationPhase.IDLE)
    val currentPhase: StateFlow<BackupOperationPhase> = _currentPhase.asStateFlow()

    private var isCancellationEnabled = true

    suspend fun <T> runOperation(
        initialPhase: BackupOperationPhase,
        block: suspend (updatePhase: (BackupOperationPhase) -> Unit) -> Result<T>
    ): Result<T> {
        if (!mutex.tryLock()) {
            return Result.failure(IllegalStateException("Another backup/restore operation is already in progress"))
        }
        return try {
            _currentPhase.value = initialPhase
            isCancellationEnabled = true
            block { phase ->
                _currentPhase.value = phase
                isCancellationEnabled = when (phase) {
                    BackupOperationPhase.ACTIVATING_ATTACHMENTS,
                    BackupOperationPhase.REPLACING_DATABASE,
                    BackupOperationPhase.COMPENSATING -> false
                    else -> true
                }
            }
        } finally {
            _currentPhase.value = BackupOperationPhase.IDLE
            isCancellationEnabled = true
            mutex.unlock()
        }
    }

    fun canCancel(): Boolean = isCancellationEnabled && _currentPhase.value != BackupOperationPhase.IDLE

    fun checkStorageCapacity(
        estimatedArchiveBytes: Long,
        estimatedStagingBytes: Long,
        estimatedRollbackBytes: Long,
        estimatedSafetyBytes: Long,
        estimatedDbBytes: Long,
        marginBytes: Long = 100 * 1024 * 1024L // 100MB margin
    ): Result<Unit> {
        return try {
            val totalRequired = estimatedArchiveBytes +
                               estimatedStagingBytes +
                               estimatedRollbackBytes +
                               estimatedSafetyBytes +
                               estimatedDbBytes +
                               marginBytes
            
            val stat = StatFs(context.filesDir.path)
            val available = stat.availableBlocksLong * stat.blockSizeLong
            
            if (available > totalRequired) {
                Result.success(Unit)
            } else {
                Result.failure(IOException("Insufficient storage. Required: ${formatSize(totalRequired)}, Available: ${formatSize(available)}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun formatSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb > 1) "%.2f MB".format(mb) else "%.2f KB".format(kb)
    }
}

private class IOException(message: String) : Exception(message)
