package com.jumastappworks.mapstead.data.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.File
import java.util.*

@RunWith(RobolectricTestRunner::class)
class AttachmentStorageServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private val uriFactory = mockk<AttachmentUriFactory>()
    private lateinit var service: AttachmentStorageService
    private lateinit var filesDir: File
    private lateinit var cacheDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        filesDir = tempFolder.newFolder("files")
        cacheDir = tempFolder.newFolder("cache")
        
        // Mock context to return our temp folders
        val mockContext = mockk<Context>()
        every { mockContext.filesDir } returns filesDir
        every { mockContext.cacheDir } returns cacheDir
        
        // Mock URI factory to return content:// URIs for any file
        every { uriFactory.getUriForFile(any()) } answers {
            val file = firstArg<File>()
            Uri.parse("content://com.jumastappworks.mapstead.fileprovider/test/${file.name}")
        }
        
        service = AttachmentStorageService(mockContext, uriFactory)
    }

    @Test
    fun testStageInputStreamDeletesOnLimitExceeded() {
        val input = ByteArrayInputStream(ByteArray(100))
        val result = service.stageInputStream(input, 50L)
        
        assertTrue(result.isFailure)
        
        // Staging dir should be empty because we delete the temp file on error
        val stagingDir = File(cacheDir, "attachment_staging")
        val files = stagingDir.listFiles() ?: emptyArray()
        assertTrue("Staging file should be cleaned up", files.isEmpty())
    }

    @Test
    fun testCreateTempCameraCaptureCreatesPhysicalFile() {
        val result = service.createTempCameraCapture()
        assertTrue(result.isSuccess)
        val capture = result.getOrThrow()
        
        assertNotNull(capture.token)
        assertTrue(capture.uri.toString().startsWith("content://"))
        
        val expectedFile = File(cacheDir, "camera_captures/capture_${capture.token}.jpg")
        assertTrue("Physical file should be created", expectedFile.exists())
        assertTrue("File should be under camera_captures", expectedFile.absolutePath.contains("camera_captures"))
    }

    @Test
    fun testCreateTempCameraCaptureCleansFileWhenUriCreationFails() {
        every { uriFactory.getUriForFile(any()) } throws RuntimeException("URI Failure")
        
        val result = service.createTempCameraCapture()
        assertTrue(result.isFailure)
        
        val cameraDir = File(cacheDir, "camera_captures")
        val files = cameraDir.listFiles() ?: emptyArray()
        assertTrue("Camera file should be cleaned up on URI failure", files.isEmpty())
    }

    @Test
    fun testDeleteTempCameraCapture() {
        val capture = service.createTempCameraCapture().getOrThrow()
        val expectedFile = File(cacheDir, "camera_captures/capture_${capture.token}.jpg")
        assertTrue(expectedFile.exists())
        
        val deleted = service.deleteTempCameraCapture(capture.token)
        assertTrue(deleted)
        assertFalse("File should be gone after deletion", expectedFile.exists())
    }

    @Test
    fun testDeleteMissingValidCaptureIsSafe() {
        val token = UUID.randomUUID().toString()
        val deleted = service.deleteTempCameraCapture(token)
        assertTrue("Should treat missing file as successful cleanup", deleted)
    }

    @Test
    fun testDeleteTempCameraCaptureRejectsMalformedToken() {
        // Create a dummy file to ensure it's NOT deleted
        val dummyFile = File(cacheDir, "dummy.txt")
        dummyFile.createNewFile()
        assertTrue(dummyFile.exists())
        
        val deleted = service.deleteTempCameraCapture("not-a-uuid")
        assertFalse(deleted)
        assertTrue(dummyFile.exists())
    }

    @Test
    fun testCommitStagedFile() {
        val staged = tempFolder.newFile("staged")
        staged.writeText("hello")
        val id = UUID.randomUUID()
        
        val result = service.commitStagedFile(staged, id)
        assertTrue(result.isSuccess)
        
        val target = service.getAttachmentFile(id)
        assertTrue(target.exists())
        assertEquals(5L, target.length())
        assertFalse(staged.exists())
    }
}
