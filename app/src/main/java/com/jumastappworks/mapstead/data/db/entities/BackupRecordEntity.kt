package com.jumastappworks.mapstead.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(tableName = "backup_records")
data class BackupRecordEntity(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val operationType: String, // BACKUP, RESTORE
    val status: String, // OperationStatus enum name
    val progressPercent: Int = 0,
    val startedAt: Instant = Instant.now(),
    val completedAt: Instant? = null,
    val backupId: String? = null,
    val driveFileId: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val errorCode: String? = null,
    val userSafeErrorMessage: String? = null,
    val warningCount: Int = 0,
    val safetyBackupPath: String? = null
)
