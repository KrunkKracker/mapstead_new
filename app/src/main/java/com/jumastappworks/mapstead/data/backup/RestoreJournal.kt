package com.jumastappworks.mapstead.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.*

enum class RestoreJournalStage {
    IDLE,
    INITIALIZED,
    STAGING_ATTACHMENTS,
    VALIDATING_ARCHIVE,
    PREPARING_STAGING,
    EXTRACTING_ARCHIVE,
    CREATING_SAFETY_BACKUP,
    VALIDATING_SAFETY_BACKUP,
    PREPARING_ROLLBACK,
    ACTIVATING_ATTACHMENTS,
    MOVING_ACTIVE_TO_ROLLBACK,
    ACTIVATING_STAGED_ATTACHMENTS,
    DATABASE_TRANSACTION,
    DATABASE_TRANSACTION_START,
    DATABASE_TRANSACTION_COMMIT,
    POST_RESTORE_VERIFICATION,
    CLEANING_UP,
    SUCCESS,
    RECOVERY_REQUIRED,
    RECOVERY_FAILED
}

@Serializable
data class RestoreJournalData(
    val operationId: String = UUID.randomUUID().toString(),
    val manifestBackupId: String? = null,
    val driveFileId: String? = null,
    var downloadedArchivePath: String? = null,
    var safetyBackupPath: String? = null,
    var stagingAttachmentPath: String? = null,
    var rollbackAttachmentPath: String? = null,
    var activeAttachmentPath: String? = null,
    var stage: RestoreJournalStage = RestoreJournalStage.IDLE,
    val startedTimestamp: Long = System.currentTimeMillis(),
    var lastUpdatedTimestamp: Long = System.currentTimeMillis(),
    var wasDbCommitted: Boolean = false,
    var wasPostRestoreVerified: Boolean = false,
    var lastRecoveryError: String? = null
)

class RestoreJournalManager(
    private val journalFile: File,
    private val json: Json
) {
    private val backupJournalFile = File(journalFile.absolutePath + ".bak")
    private val tempJournalFile = File(journalFile.absolutePath + ".tmp")

    fun loadJournal(): RestoreJournalData? {
        // Try primary journal first
        var journalData = tryLoadFile(journalFile)
        if (journalData != null) {
            return journalData
        }
        
        // If primary is corrupt or doesn't exist, try backup journal
        journalData = tryLoadFile(backupJournalFile)
        if (journalData != null) {
            // Recover primary from backup
            try {
                saveJournalAtomically(journalData)
            } catch (_: Exception) {}
            return journalData
        }
        
        return null
    }

    private fun tryLoadFile(file: File): RestoreJournalData? {
        if (!file.exists()) return null
        return try {
            val content = file.readText()
            if (content.isBlank()) return null
            json.decodeFromString<RestoreJournalData>(content)
        } catch (_: Exception) {
            null
        }
    }

    fun saveJournal(data: RestoreJournalData) {
        data.lastUpdatedTimestamp = System.currentTimeMillis()
        
        // Before overwriting the primary journal, copy existing valid primary to backup
        if (journalFile.exists() && tryLoadFile(journalFile) != null) {
            try {
                journalFile.copyTo(backupJournalFile, overwrite = true)
            } catch (_: Exception) {}
        }
        
        saveJournalAtomically(data)
    }

    private fun saveJournalAtomically(data: RestoreJournalData) {
        val jsonString = json.encodeToString(data)
        
        // Write to temporary sibling file
        tempJournalFile.writeText(jsonString)
        
        // Flush and sync to disk
        try {
            java.io.FileOutputStream(tempJournalFile, true).use { fos ->
                fos.channel.force(true)
            }
        } catch (_: Exception) {}

        // Atomically replace using Files.move or verified fallback
        val sourcePath = tempJournalFile.toPath()
        val targetPath = journalFile.toPath()
        try {
            java.nio.file.Files.move(
                sourcePath,
                targetPath,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            // Fallback: simple rename or copy if atomic move fails
            if (tempJournalFile.exists()) {
                if (journalFile.exists()) {
                    journalFile.delete()
                }
                val success = tempJournalFile.renameTo(journalFile)
                if (!success) {
                    tempJournalFile.copyTo(journalFile, overwrite = true)
                    tempJournalFile.delete()
                }
            }
        }
    }

    fun deleteJournal() {
        journalFile.delete()
        backupJournalFile.delete()
        tempJournalFile.delete()
    }
}