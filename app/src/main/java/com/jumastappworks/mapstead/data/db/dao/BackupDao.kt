package com.jumastappworks.mapstead.data.db.dao

import androidx.room.*
import com.jumastappworks.mapstead.data.db.entities.BackupRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupDao {
    @Query("SELECT * FROM backup_records ORDER BY startedAt DESC")
    fun getAllBackupRecords(): Flow<List<BackupRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBackupRecord(record: BackupRecordEntity)

    @Update
    suspend fun updateBackupRecord(record: BackupRecordEntity)

    @Query("DELETE FROM backup_records WHERE status IN ('SUCCESS', 'FAILED', 'CANCELLED')")
    suspend fun clearCompletedRecords()

    @Query("DELETE FROM backup_records WHERE id = :id")
    suspend fun deleteBackupRecordById(id: java.util.UUID)
}
