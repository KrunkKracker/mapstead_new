package com.jumastappworks.mapstead

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.jumastappworks.mapstead.data.backup.RestoreRecoveryManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

@HiltAndroidApp
class MapsteadApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var recoveryManager: RestoreRecoveryManager

    @Inject
    lateinit var featureGate: com.jumastappworks.mapstead.data.backup.BackupFeatureGate

    override fun onCreate() {
        super.onCreate()
        org.maplibre.android.MapLibre.getInstance(this)
        if (featureGate.isEnabled) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            recoveryManager.checkAndRecover(scope)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
