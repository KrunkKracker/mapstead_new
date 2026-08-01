package com.jumastappworks.mapstead.data.backup

import android.content.Context
import android.net.Uri
import com.jumastappworks.mapstead.data.attachments.TempCameraCaptureInspectionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

data class TemporaryCameraCapture(
    val uri: Uri,
    val token: String
)

@Singleton
class AttachmentStorageService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val attachmentUriFactory: AttachmentUriFactory
) {
    private val rootDir = File(context.filesDir, "mapstead_attachments")
    private val stagingRootDir = File(context.cacheDir, "attachment_staging")
    private val cameraRootDir = File(context.cacheDir, "camera_captures")

    init {
        if (!rootDir.exists()) rootDir.mkdirs()
        if (!stagingRootDir.exists()) stagingRootDir.mkdirs()
        if (!cameraRootDir.exists()) cameraRootDir.mkdirs()
    }

    fun getAttachmentFile(attachmentId: UUID): File {
        return File(rootDir, attachmentId.toString())
    }

    fun getEntityPath(attachmentId: UUID): String {
        return "mapstead_attachments/$attachmentId"
    }

    fun getUriForFile(file: File): Uri {
        return attachmentUriFactory.getUriForFile(file)
    }

    fun getUriForAttachment(attachmentId: UUID): Uri {
        return getUriForFile(getAttachmentFile(attachmentId))
    }

    fun createTempCameraCapture(): Result<TemporaryCameraCapture> {
        return try {
            val token = UUID.randomUUID().toString()
            val file = File(cameraRootDir, "capture_$token.jpg")
            
            // Confirm the canonical file remains under camera_captures
            if (!isUnderDir(file, cameraRootDir)) {
                return Result.failure(SecurityException("Invalid camera capture path"))
            }

            if (!cameraRootDir.exists() && !cameraRootDir.mkdirs()) {
                return Result.failure(IOException("Failed to create camera captures directory"))
            }

            // Physically create the temporary file
            if (!file.createNewFile()) {
                return Result.failure(IOException("Failed to create temporary camera file"))
            }

            try {
                val uri = getUriForFile(file)
                Result.success(TemporaryCameraCapture(uri, token))
            } catch (e: Exception) {
                // If URI generation fails, delete the newly created temporary file
                if (file.exists()) file.delete()
                Result.failure(e)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deleteTempCameraCapture(token: String): Boolean {
        if (token.isBlank()) return false
        return try {
            // Validate token as UUID
            UUID.fromString(token)
            
            val file = File(cameraRootDir, "capture_$token.jpg")
            if (isUnderDir(file, cameraRootDir)) {
                if (file.exists()) {
                    file.delete()
                } else true // Already gone is success
            } else false
        } catch (e: Exception) {
            false
        }
    }

    fun inspectTempCameraCapture(token: String, uri: Uri): TempCameraCaptureInspectionResult {
        if (token.isBlank()) return TempCameraCaptureInspectionResult.Missing
        return try {
            UUID.fromString(token)
            val file = File(cameraRootDir, "capture_$token.jpg")
            if (!file.exists()) return TempCameraCaptureInspectionResult.Missing
            if (file.length() <= 0) return TempCameraCaptureInspectionResult.Empty
            
            // Check if readable by ContentResolver
            try {
                context.contentResolver.openInputStream(uri)?.use { 
                    // Verify it's an image
                    val options = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    android.graphics.BitmapFactory.decodeStream(it, null, options)
                    if (options.outWidth <= 0 || options.outHeight <= 0) {
                        return TempCameraCaptureInspectionResult.InvalidImage
                    }
                } ?: return TempCameraCaptureInspectionResult.Unreadable
            } catch (e: Exception) {
                return TempCameraCaptureInspectionResult.Unreadable
            }
            
            TempCameraCaptureInspectionResult.Ready
        } catch (e: Exception) {
            TempCameraCaptureInspectionResult.Missing
        }
    }

    fun stageInputStream(inputStream: InputStream, limitBytes: Long): Result<StagedFileResult> {
        val tempFile = File(stagingRootDir, "stage_${UUID.randomUUID()}")
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytes = 0L
            
            FileOutputStream(tempFile).use { outputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    totalBytes += bytesRead
                    if (totalBytes > limitBytes) {
                        throw IOException("File size exceeds limit of $limitBytes bytes")
                    }
                    outputStream.write(buffer, 0, bytesRead)
                    digest.update(buffer, 0, bytesRead)
                }
                outputStream.flush()
            }
            
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            Result.success(StagedFileResult(tempFile, totalBytes, hash))
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            Result.failure(e)
        }
    }

    data class StagedFileResult(val file: File, val size: Long, val sha256: String)

    fun commitStagedFile(stagedFile: File, attachmentId: UUID): Result<File> {
        return try {
            val targetFile = getAttachmentFile(attachmentId)
            val expectedSize = stagedFile.length()
            
            try {
                Files.move(stagedFile.toPath(), targetFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (e: Exception) {
                // Fallback for non-atomic or cross-filesystem move
                Files.copy(stagedFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                stagedFile.delete()
            }
            
            // Verify target existence and exact size post-move
            if (targetFile.exists() && targetFile.length() == expectedSize) {
                Result.success(targetFile)
            } else {
                if (targetFile.exists()) targetFile.delete()
                Result.failure(IOException("Target file verification failed after commit"))
            }
        } catch (e: Exception) {
            if (stagedFile.exists()) stagedFile.delete()
            Result.failure(e)
        }
    }

    fun deleteManagedFile(attachmentId: UUID): Result<Unit> {
        return try {
            val file = getAttachmentFile(attachmentId)
            if (file.exists() && !file.delete()) {
                Result.failure(IOException("Failed to delete file: ${file.absolutePath}"))
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun exists(attachmentId: UUID): Boolean {
        val file = getAttachmentFile(attachmentId)
        return file.exists() && file.isFile
    }

    fun resolveFromEntityPath(entityPath: String): Result<File> {
        return try {
            val file = File(context.filesDir, entityPath)
            if (isUnderManagedRoot(file)) {
                Result.success(file)
            } else {
                Result.failure(SecurityException("Path traversal or unmanaged path: $entityPath"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isUnderManagedRoot(file: File): Boolean {
        return isUnderDir(file, rootDir)
    }

    private fun isUnderDir(file: File, dir: File): Boolean {
        return try {
            val canonicalRoot = dir.canonicalFile.toPath().normalize()
            val canonicalFile = file.canonicalFile.toPath().normalize()
            canonicalFile.startsWith(canonicalRoot)
        } catch (e: Exception) {
            false
        }
    }

    fun validateFile(file: File, expectedSize: Long?, expectedHash: String?): Result<Unit> {
        return try {
            if (!file.exists()) return Result.failure(IOException("File does not exist: ${file.absolutePath}"))
            if (!file.isFile) return Result.failure(IOException("Not a file: ${file.absolutePath}"))
            
            if (expectedSize != null && file.length() != expectedSize) {
                return Result.failure(IOException("Size mismatch: expected $expectedSize, got ${file.length()}"))
            }
            
            if (expectedHash != null) {
                val actualHash = calculateSha256(file)
                if (actualHash != expectedHash) {
                    return Result.failure(IOException("Hash mismatch: expected $expectedHash, got $actualHash"))
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { isStream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (isStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun prepareStagingDir(): Result<File> {
        return try {
            val dir = File(context.cacheDir, "stage_restore_${UUID.randomUUID()}")
            if (dir.exists() || dir.mkdirs()) {
                Result.success(dir)
            } else {
                Result.failure(IOException("Failed to create staging directory"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun prepareRollbackDir(): Result<File> {
        return try {
            val dir = File(context.filesDir, "rollback_attachments_${UUID.randomUUID()}")
            if (dir.exists() || dir.mkdirs()) {
                Result.success(dir)
            } else {
                Result.failure(IOException("Failed to create rollback directory: ${dir.absolutePath}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun moveActiveToRollback(rollbackDir: File): Result<Unit> {
        return try {
            if (rootDir.exists()) {
                moveRecursively(rootDir, rollbackDir).getOrThrow()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun verifyRollbackStorage(rollbackDir: File, expectedCount: Int, expectedSize: Long): Result<Unit> {
        return try {
            val files = rollbackDir.listFiles() ?: emptyArray()
            val actualCount = files.size
            val actualSize = files.sumOf { it.length() }
            if (actualCount != expectedCount || actualSize != expectedSize) {
                Result.failure(IOException("Rollback verification failed: Count $actualCount/$expectedCount, Size $actualSize/$expectedSize"))
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun verifyActiveAttachmentRoot(expectedCount: Int, expectedSize: Long): Result<Unit> {
        return try {
            val files = rootDir.listFiles() ?: emptyArray()
            val actualCount = files.size
            val actualSize = files.sumOf { it.length() }
            if (actualCount != expectedCount || actualSize != expectedSize) {
                Result.failure(IOException("Active root verification failed: Count $actualCount/$expectedCount, Size $actualSize/$expectedSize"))
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun activateStagedAttachments(stagingDir: File): Result<Unit> {
        return try {
            moveRecursively(stagingDir, rootDir).getOrThrow()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun rollback(rollbackDir: File): Result<Unit> {
        return try {
            if (rollbackDir.exists()) {
                if (rootDir.exists()) {
                    if (!rootDir.deleteRecursively()) {
                        return Result.failure(IOException("Failed to delete rootDir during rollback: ${rootDir.absolutePath}"))
                    }
                }
                moveRecursively(rollbackDir, rootDir).getOrThrow()
                if (rollbackDir.exists() && !rollbackDir.deleteRecursively()) {
                    return Result.failure(IOException("Failed to delete rollbackDir after rollback: ${rollbackDir.absolutePath}"))
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun moveRecursively(source: File, target: File): Result<Unit> {
        return try {
            if (source.isDirectory) {
                if (!target.exists() && !target.mkdirs()) {
                    return Result.failure(IOException("Failed to create target directory: ${target.absolutePath}"))
                }
                val files = source.listFiles() ?: emptyArray()
                for (file in files) {
                    moveRecursively(file, File(target, file.name)).getOrThrow()
                }
                if (!source.delete()) {
                    return Result.failure(IOException("Failed to delete source directory: ${source.absolutePath}"))
                }
            } else {
                val expectedSize = source.length()
                
                try {
                    Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (e: IOException) {
                    // Fallback to copy-delete
                    Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    if (!source.delete()) {
                        return Result.failure(IOException("Failed to delete source file after copy: ${source.absolutePath}"))
                    }
                }
                
                // Verify move result: size and target file exists
                if (!target.exists()) {
                    return Result.failure(IOException("Move verification failed: target file does not exist: ${target.absolutePath}"))
                }
                val actualSize = target.length()
                if (actualSize != expectedSize) {
                    return Result.failure(IOException("Move verification failed: size mismatch on target: ${target.absolutePath} (expected $expectedSize, got $actualSize)"))
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
