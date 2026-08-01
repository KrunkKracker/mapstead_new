package com.jumastappworks.mapstead.data.backup

import java.io.File
import java.util.UUID

class FakeMapsteadDriveClient : MapsteadDriveClient {
    private val backups = mutableListOf<DriveBackupFile>()
    private val backupContents = mutableMapOf<String, File>()
    var shouldFailWith401 = false

    override suspend fun ensureBackupFolder(): Result<DriveFolder> {
        return Result.success(DriveFolder("fake-folder-id", "Mapstead Backups"))
    }

    override suspend fun uploadBackup(localFile: File, metadata: DriveBackupMetadata): Result<DriveBackupFile> {
        if (shouldFailWith401) return Result.failure(Exception("401 Unauthorized"))
        val driveFile = DriveBackupFile(
            driveFileId = UUID.randomUUID().toString(),
            name = localFile.name,
            size = localFile.length(),
            createdAt = metadata.createdAt,
            appVersion = metadata.appVersion,
            formatVersion = metadata.formatVersion,
            backupId = metadata.backupId
        )
        backups.add(driveFile)
        backupContents[driveFile.driveFileId] = localFile
        return Result.success(driveFile)
    }

    override suspend fun listBackups(): Result<List<DriveBackupFile>> {
        if (shouldFailWith401) return Result.failure(Exception("401 Unauthorized"))
        return Result.success(backups.sortedByDescending { it.createdAt })
    }

    override suspend fun downloadBackup(fileId: String, destination: File): Result<File> {
        val source = backupContents[fileId] ?: return Result.failure(Exception("Not found"))
        source.copyTo(destination, overwrite = true)
        return Result.success(destination)
    }

    override suspend fun deleteBackup(fileId: String): Result<Unit> {
        backups.removeIf { it.driveFileId == fileId }
        backupContents.remove(fileId)
        return Result.success(Unit)
    }
}

class FakeMapsteadDriveClientFactory(val client: MapsteadDriveClient) : MapsteadDriveClientFactory {
    override fun create(accessToken: String): MapsteadDriveClient = client
}
