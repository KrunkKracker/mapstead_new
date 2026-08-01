package com.jumastappworks.mapstead.data.backup

import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

class GoogleDriveClient(
    private val accessToken: String
) : MapsteadDriveClient {

    private val driveService: Drive = Drive.Builder(
        NetHttpTransport(),
        GsonFactory.getDefaultInstance(),
        HttpCredentialsAdapter(GoogleCredentials.create(AccessToken(accessToken, null)))
    ).setApplicationName("Mapstead").build()

    override suspend fun ensureBackupFolder(): Result<DriveFolder> = withContext(Dispatchers.IO) {
        try {
            val folders = mutableListOf<File>()
            var pageToken: String? = null
            do {
                val query = "mimeType = 'application/vnd.google-apps.folder' and appProperties has { key='mapsteadType' and value='backupFolder' } and trashed = false"
                val result = driveService.files().list()
                    .setQ(query)
                    .setSpaces("drive")
                    .setFields("nextPageToken, files(id, name)")
                    .setPageToken(pageToken)
                    .execute()
                
                result.files?.let { folders.addAll(it) }
                pageToken = result.nextPageToken
            } while (pageToken != null)

            val folder = folders.sortedBy { it.id }.firstOrNull() ?: createBackupFolder()
            Result.success(DriveFolder(folder.id, folder.name))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun createBackupFolder(): File {
        val metadata = File()
            .setName("Mapstead Backups")
            .setMimeType("application/vnd.google-apps.folder")
            .setAppProperties(mapOf(
                "mapsteadType" to "backupFolder",
                "mapsteadFormatVersion" to "1"
            ))
        return driveService.files().create(metadata).setFields("id, name").execute()
    }

    override suspend fun uploadBackup(localFile: java.io.File, metadata: DriveBackupMetadata): Result<DriveBackupFile> = withContext(Dispatchers.IO) {
        try {
            val folder = ensureBackupFolder().getOrThrow()
            
            val fileMetadata = File()
                .setName(localFile.name)
                .setParents(listOf(folder.driveFolderId))
                .setAppProperties(mapOf(
                    "mapsteadType" to "backup",
                    "mapsteadFormatVersion" to metadata.formatVersion.toString(),
                    "mapsteadBackupId" to metadata.backupId,
                    "mapsteadCreatedAt" to metadata.createdAt,
                    "mapsteadAppVersion" to metadata.appVersion
                ))

            val mediaContent = FileContent("application/zip", localFile)
            val driveFile = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id, name, size, createdTime, appProperties")
                .execute()

            Result.success(mapToDriveBackupFile(driveFile))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listBackups(): Result<List<DriveBackupFile>> = withContext(Dispatchers.IO) {
        try {
            val backups = mutableListOf<DriveBackupFile>()
            var pageToken: String? = null
            
            do {
                val query = "appProperties has { key='mapsteadType' and value='backup' } and trashed = false"
                val result = driveService.files().list()
                    .setQ(query)
                    .setSpaces("drive")
                    .setFields("nextPageToken, files(id, name, size, createdTime, appProperties)")
                    .setPageToken(pageToken)
                    .execute()

                result.files?.map { mapToDriveBackupFile(it) }?.let { backups.addAll(it) }
                pageToken = result.nextPageToken
            } while (pageToken != null)

            Result.success(backups.sortedByDescending { it.createdAt })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun downloadBackup(fileId: String, destination: java.io.File): Result<java.io.File> = withContext(Dispatchers.IO) {
        try {
            FileOutputStream(destination).use { outputStream ->
                driveService.files().get(fileId)
                    .executeMediaAndDownloadTo(outputStream)
            }
            Result.success(destination)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteBackup(fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            driveService.files().delete(fileId).execute()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mapToDriveBackupFile(file: File): DriveBackupFile {
        val props = file.appProperties ?: emptyMap()
        return DriveBackupFile(
            driveFileId = file.id,
            name = file.name,
            size = file.getSize() ?: 0L,
            createdAt = props["mapsteadCreatedAt"] ?: file.createdTime?.toString() ?: "",
            appVersion = props["mapsteadAppVersion"] ?: "",
            formatVersion = props["mapsteadFormatVersion"]?.toIntOrNull() ?: 1,
            backupId = props["mapsteadBackupId"] ?: ""
        )
    }
}

@Singleton
class GoogleDriveClientFactory @Inject constructor() : MapsteadDriveClientFactory {
    override fun create(accessToken: String): MapsteadDriveClient {
        return GoogleDriveClient(accessToken)
    }
}
