package com.jumastappworks.mapstead.di

import com.jumastappworks.mapstead.data.backup.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import kotlinx.serialization.json.Json
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BackupModule {

    @Provides
    @Singleton
    fun provideBackupFeatureGate(
        impl: BuildConfigBackupFeatureGate
    ): BackupFeatureGate {
        return impl
    }

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideBackupArchiveLimits(): BackupArchiveLimits = BackupArchiveLimits()

    @Provides
    @Singleton
    fun provideDriveAuthorizationManager(
        impl: DriveAuthorizationManagerImpl
    ): DriveAuthorizationManager = impl

    @Provides
    @Singleton
    fun provideMapsteadDriveClientFactory(
        impl: GoogleDriveClientFactory
    ): MapsteadDriveClientFactory = impl

    @Provides
    @Singleton
    fun provideRestoreJournalManager(
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
        json: Json
    ): RestoreJournalManager {
        val journalFile = java.io.File(context.filesDir, "restore_journal.json")
        return RestoreJournalManager(journalFile, json)
    }

    @Provides
    fun provideCoroutineDispatcher(): kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
}
