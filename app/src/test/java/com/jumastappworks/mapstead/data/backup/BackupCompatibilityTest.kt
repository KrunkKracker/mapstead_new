package com.jumastappworks.mapstead.data.backup

import org.junit.Assert.*
import org.junit.Test

class BackupCompatibilityTest {

    @Test
    fun `production compatibility policy accepts development build manifests`() {
        val currentAppVersion = "0.02"
        val devManifest = BackupManifest(
            formatVersion = 1,
            backupId = "test-id",
            createdAt = "2026-07-27T10:00:00Z",
            appVersionName = "1.32",
            appVersionCode = 24,
            databaseSchemaVersion = 2,
            deviceManufacturer = "Google",
            deviceModel = "Pixel",
            androidVersion = "14",
            propertyCount = 1,
            planCount = 1,
            layerCount = 1,
            mapFeatureCount = 1,
            infrastructureCount = 1,
            maintenanceCount = 1,
            reminderCount = 1,
            attachmentCount = 1,
            relationshipCount = 1,
            includedAttachmentBytes = 100
        )

        val result = BackupCompatibilityPolicy.evaluate(devManifest, currentAppVersion)
        
        // Should be compatible with a warning about version mismatch, but NOT incompatible
        assertTrue("Result should be compatible or warning", 
            result is BackupCompatibilityResult.Compatible || 
            result is BackupCompatibilityResult.CompatibleWithWarning)
        
        if (result is BackupCompatibilityResult.CompatibleWithWarning) {
            assertEquals(WarningReason.APP_VERSION_MISMATCH, result.reason)
        }
    }

    @Test
    fun `unsupported format version is rejected`() {
        val manifest = mockManifest().copy(formatVersion = 99)
        val result = BackupCompatibilityPolicy.evaluate(manifest, "0.02")
        assertTrue(result is BackupCompatibilityResult.Incompatible)
        assertEquals(IncompatibilityReason.UNSUPPORTED_FORMAT_VERSION, (result as BackupCompatibilityResult.Incompatible).reason)
    }

    @Test
    fun `blank app version name is rejected`() {
        val manifest = mockManifest().copy(appVersionName = "")
        val result = BackupCompatibilityPolicy.evaluate(manifest, "0.02")
        assertTrue(result is BackupCompatibilityResult.Incompatible)
    }

    @Test
    fun `future schema version is rejected`() {
        val manifest = mockManifest().copy(databaseSchemaVersion = 99)
        val result = BackupCompatibilityPolicy.evaluate(manifest, "0.02")
        assertTrue(result is BackupCompatibilityResult.Incompatible)
        assertEquals(IncompatibilityReason.UNSUPPORTED_SCHEMA_VERSION, (result as BackupCompatibilityResult.Incompatible).reason)
    }

    private fun mockManifest() = BackupManifest(
        formatVersion = 1,
        backupId = "id",
        createdAt = "2026-07-27T10:00:00Z",
        appVersionName = "0.02",
        appVersionCode = 2,
        databaseSchemaVersion = 1,
        deviceManufacturer = "M",
        deviceModel = "M",
        androidVersion = "14",
        propertyCount = 0, planCount = 0, layerCount = 0, mapFeatureCount = 0,
        infrastructureCount = 0, maintenanceCount = 0, reminderCount = 0,
        attachmentCount = 0, relationshipCount = 0, includedAttachmentBytes = 0
    )
}
