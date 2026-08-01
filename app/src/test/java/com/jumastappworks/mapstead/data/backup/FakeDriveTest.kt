package com.jumastappworks.mapstead.data.backup

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FakeDriveTest {

    @Test
    fun testFakeDriveWorkflow() = runBlocking {
        val client = FakeMapsteadDriveClient()
        val tempFile = File.createTempFile("test", ".zip")
        tempFile.writeText("content")
        
        val metadata = DriveBackupMetadata("id1", "2026", "0.1.0", 1)
        
        // Upload
        val uploadResult = client.uploadBackup(tempFile, metadata)
        assertTrue(uploadResult.isSuccess)
        val driveFile = uploadResult.getOrThrow()
        assertEquals(tempFile.name, driveFile.name)
        
        // List
        val listResult = client.listBackups()
        assertTrue(listResult.isSuccess)
        assertEquals(1, listResult.getOrThrow().size)
        
        // Download
        val downloadDest = File.createTempFile("dest", ".zip")
        val downloadResult = client.downloadBackup(driveFile.driveFileId, downloadDest)
        assertTrue(downloadResult.isSuccess)
        assertEquals("content", downloadDest.readText())
        
        // Delete
        val deleteResult = client.deleteBackup(driveFile.driveFileId)
        assertTrue(deleteResult.isSuccess)
        assertEquals(0, client.listBackups().getOrThrow().size)
        
        tempFile.delete()
        downloadDest.delete()
        Unit
    }
}
