package com.jumastappworks.mapstead.data.backup

import com.jumastappworks.mapstead.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface that defines whether the Google Drive Backup and Restore features are enabled.
 */
interface BackupFeatureGate {
    val isEnabled: Boolean
}

/**
 * Production implementation of the feature gate driven by build-time configuration in BuildConfig.
 */
@Singleton
class BuildConfigBackupFeatureGate @Inject constructor() : BackupFeatureGate {
    override val isEnabled: Boolean
        get() = BuildConfig.GOOGLE_DRIVE_BACKUP_ENABLED
}
