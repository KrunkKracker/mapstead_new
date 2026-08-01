package com.jumastappworks.mapstead.data.backup

import com.jumastappworks.mapstead.data.db.MapsteadDatabase

enum class WarningReason {
    APP_VERSION_MISMATCH
}

enum class IncompatibilityReason {
    UNSUPPORTED_FORMAT_VERSION,
    UNSUPPORTED_SCHEMA_VERSION,
    INVALID_MANIFEST_METADATA
}

sealed interface BackupCompatibilityResult {
    data object Compatible : BackupCompatibilityResult
    data class CompatibleWithWarning(val reason: WarningReason) : BackupCompatibilityResult
    data class Incompatible(val reason: IncompatibilityReason) : BackupCompatibilityResult
}

object BackupCompatibilityPolicy {

    const val CURRENT_FORMAT_VERSION = 1
    const val MIN_SUPPORTED_SCHEMA = 1
    val MAX_SUPPORTED_SCHEMA = MapsteadDatabase.CURRENT_SCHEMA_VERSION

    fun evaluate(manifest: BackupManifest, currentAppVersion: String): BackupCompatibilityResult {
        // 1. Format version
        if (manifest.formatVersion != CURRENT_FORMAT_VERSION) {
            return BackupCompatibilityResult.Incompatible(IncompatibilityReason.UNSUPPORTED_FORMAT_VERSION)
        }

        // 2. Manifest Metadata
        if (manifest.appVersionName.isBlank() || manifest.appVersionCode <= 0) {
            return BackupCompatibilityResult.Incompatible(IncompatibilityReason.INVALID_MANIFEST_METADATA)
        }

        // 3. Schema Version
        if (manifest.databaseSchemaVersion < MIN_SUPPORTED_SCHEMA || manifest.databaseSchemaVersion > MAX_SUPPORTED_SCHEMA) {
            return BackupCompatibilityResult.Incompatible(IncompatibilityReason.UNSUPPORTED_SCHEMA_VERSION)
        }

        // 4. App Version Warning (informational only)
        if (manifest.appVersionName != currentAppVersion) {
            return BackupCompatibilityResult.CompatibleWithWarning(WarningReason.APP_VERSION_MISMATCH)
        }

        return BackupCompatibilityResult.Compatible
    }
}
