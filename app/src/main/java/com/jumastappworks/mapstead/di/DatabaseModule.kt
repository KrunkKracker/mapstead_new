package com.jumastappworks.mapstead.di

import android.content.Context
import androidx.room.Room
import com.jumastappworks.mapstead.data.db.DatabaseTransactionRunner
import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.db.RoomDatabaseTransactionRunner
import com.jumastappworks.mapstead.data.db.dao.PropertyDao
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseModule {

    @Binds
    @Singleton
    abstract fun bindTransactionRunner(runner: RoomDatabaseTransactionRunner): DatabaseTransactionRunner

    companion object {
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): MapsteadDatabase {
            return Room.databaseBuilder(
                context,
                MapsteadDatabase::class.java,
                MapsteadDatabase.DATABASE_NAME
            )
            .addMigrations(MapsteadDatabase.MIGRATION_1_2)
            .build()
        }

        @Provides
        fun providePropertyDao(database: MapsteadDatabase): PropertyDao = database.propertyDao()

        @Provides
        fun providePlanDao(database: MapsteadDatabase) = database.planDao()

        @Provides
        fun provideLayerDao(database: MapsteadDatabase) = database.layerDao()

        @Provides
        fun provideInfrastructureDao(database: MapsteadDatabase) = database.infrastructureDao()

        @Provides
        fun provideMapFeatureDao(database: MapsteadDatabase) = database.mapFeatureDao()

        @Provides
        fun provideMaintenanceDao(database: MapsteadDatabase) = database.maintenanceDao()

        @Provides
        fun provideBackupDao(database: MapsteadDatabase) = database.backupDao()

        @Provides
        fun provideAttachmentDao(database: MapsteadDatabase) = database.attachmentDao()

        @Provides
        fun provideItemRelationshipDao(database: MapsteadDatabase) = database.itemRelationshipDao()
    }
}
