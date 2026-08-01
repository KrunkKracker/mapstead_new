package com.jumastappworks.mapstead.data.backup

import android.content.Context
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveValidatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val context = mockk<Context>()
    private val limits = BackupArchiveLimits()
    private lateinit var validator: BackupArchiveValidator

    @Before
    fun setup() {
        every { context.cacheDir } returns tempFolder.newFolder("cache")
        validator = BackupArchiveValidator(json, context, limits)
    }

    @Test
    fun `legacy 1_32 schema 1 backup is accepted with warning`() {
        val manifest = mockManifest(appVersion = "1.32", appCode = 24, schema = 1)
        val zipFile = createArchive(manifest)
        
        val result = validator.validate(zipFile)
        
        assertTrue("Validation should succeed, error: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val report = result.getOrThrow()
        assertTrue(report.warnings.any { it.contains("APP_VERSION_MISMATCH") })
    }

    @Test
    fun `future unsupported schema is rejected`() {
        val manifest = mockManifest(schema = 99)
        val zipFile = createArchive(manifest)
        
        val result = validator.validate(zipFile)
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("UNSUPPORTED_SCHEMA_VERSION") == true)
    }

    @Test
    fun `corrupt checksum is rejected`() {
        val manifest = mockManifest()
        // Create archive but then manually corrupt a data file
        val zipFile = createArchive(manifest, corruptData = true)
        
        val result = validator.validate(zipFile)
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Checksum mismatch") == true)
    }

    private fun mockManifest(appVersion: String = "0.02", appCode: Int = 2, schema: Int = 2) = BackupManifest(
        formatVersion = 1,
        backupId = UUID.randomUUID().toString(),
        createdAt = "2026-07-27T10:00:00Z",
        appVersionName = appVersion,
        appVersionCode = appCode,
        databaseSchemaVersion = schema,
        deviceManufacturer = "Test",
        deviceModel = "Test",
        androidVersion = "14",
        propertyCount = 0, planCount = 0, layerCount = 0, mapFeatureCount = 0,
        infrastructureCount = 0, maintenanceCount = 0, reminderCount = 0,
        attachmentCount = 0, relationshipCount = 0, includedAttachmentBytes = 0
    )

    private fun createArchive(manifest: BackupManifest, corruptData: Boolean = false): File {
        val zipFile = tempFolder.newFile("test.mapsteadbackup")
        val dataFiles = mapOf(
            "data/properties.json" to "[]",
            "data/plans.json" to "[]",
            "data/layers.json" to "[]",
            "data/map_features.json" to "[]",
            "data/infrastructure_items.json" to "[]",
            "data/maintenance_records.json" to "[]",
            "data/reminders.json" to "[]",
            "data/attachments.json" to "[]",
            "data/item_relationships.json" to "[]"
        )
        
        val manifestJson = json.encodeToString(manifest)
        
        val checksums = mutableListOf<BackupFileChecksum>()
        
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // Add data files
            dataFiles.forEach { (path, content) ->
                val entryContent = if (corruptData && path.endsWith("properties.json")) "corrupt" else content
                val bytes = entryContent.toByteArray()
                checksums.add(BackupFileChecksum(path, sha256(content.toByteArray()), bytes.size.toLong()))
                zos.putNextEntry(ZipEntry(path))
                zos.write(bytes)
                zos.closeEntry()
            }
            
            // Add manifest
            val manifestBytes = manifestJson.toByteArray()
            checksums.add(BackupFileChecksum("manifest.json", sha256(manifestBytes), manifestBytes.size.toLong()))
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifestBytes)
            zos.closeEntry()
            
            // Add checksums.json
            val checksumsObj = BackupChecksums(checksums)
            val checksumsJson = json.encodeToString(checksumsObj)
            zos.putNextEntry(ZipEntry("checksums.json"))
            zos.write(checksumsJson.toByteArray())
            zos.closeEntry()
        }
        
        return zipFile
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
