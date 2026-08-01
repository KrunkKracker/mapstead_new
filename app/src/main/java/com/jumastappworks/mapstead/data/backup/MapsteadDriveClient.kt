package com.jumastappworks.mapstead.data.backup

import java.io.File

interface MapsteadDriveClient {
    suspend fun ensureBackupFolder(): Result<DriveFolder>
    suspend fun uploadBackup(localFile: File, metadata: DriveBackupMetadata): Result<DriveBackupFile>
    suspend fun listBackups(): Result<List<DriveBackupFile>>
    suspend fun downloadBackup(fileId: String, destination: File): Result<File>
    suspend fun deleteBackup(fileId: String): Result<Unit>
}

interface MapsteadDriveClientFactory {
    fun create(accessToken: String): MapsteadDriveClient
}
