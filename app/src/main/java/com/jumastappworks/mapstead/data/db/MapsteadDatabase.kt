package com.jumastappworks.mapstead.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jumastappworks.mapstead.data.db.dao.*
import com.jumastappworks.mapstead.data.db.entities.*

@Database(
    entities = [
        PropertyEntity::class,
        PlanEntity::class,
        LayerEntity::class,
        InfrastructureItemEntity::class,
        MapFeatureEntity::class,
        ItemRelationshipEntity::class,
        AttachmentEntity::class,
        MaintenanceRecordEntity::class,
        ReminderEntity::class,
        BackupRecordEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MapsteadDatabase : RoomDatabase() {
    abstract fun propertyDao(): PropertyDao
    abstract fun planDao(): PlanDao
    abstract fun layerDao(): LayerDao
    abstract fun infrastructureDao(): InfrastructureDao
    abstract fun mapFeatureDao(): MapFeatureDao
    abstract fun maintenanceDao(): MaintenanceDao
    abstract fun backupDao(): BackupDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun itemRelationshipDao(): ItemRelationshipDao

    companion object {
        const val DATABASE_NAME = "mapstead_database"
        const val CURRENT_SCHEMA_VERSION = 2

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Rebuild attachments table to add mapFeatureId FK and isCover
                db.execSQL("""
                    CREATE TABLE attachments_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        propertyId TEXT NOT NULL,
                        infrastructureItemId TEXT,
                        maintenanceRecordId TEXT,
                        mapFeatureId TEXT,
                        attachmentType TEXT NOT NULL,
                        localUri TEXT NOT NULL,
                        appManagedCopyPath TEXT,
                        displayName TEXT NOT NULL,
                        mimeType TEXT,
                        fileSizeBytes INTEGER,
                        sha256 TEXT,
                        caption TEXT,
                        isCover INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deletedAt INTEGER,
                        revision INTEGER NOT NULL,
                        FOREIGN KEY(propertyId) REFERENCES properties(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(infrastructureItemId) REFERENCES infrastructure_items(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(maintenanceRecordId) REFERENCES maintenance_records(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(mapFeatureId) REFERENCES map_features(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                
                db.execSQL("""
                    INSERT INTO attachments_new (
                        id, propertyId, infrastructureItemId, maintenanceRecordId, mapFeatureId,
                        attachmentType, localUri, appManagedCopyPath, displayName, mimeType,
                        fileSizeBytes, sha256, caption, isCover, createdAt, updatedAt, deletedAt, revision
                    )
                    SELECT 
                        id, propertyId, infrastructureItemId, maintenanceRecordId, NULL,
                        attachmentType, localUri, appManagedCopyPath, displayName, mimeType,
                        fileSizeBytes, sha256, caption, 0, createdAt, updatedAt, deletedAt, revision
                    FROM attachments
                """)
                
                db.execSQL("DROP TABLE attachments")
                db.execSQL("ALTER TABLE attachments_new RENAME TO attachments")
                
                db.execSQL("CREATE INDEX index_attachments_propertyId ON attachments(propertyId)")
                db.execSQL("CREATE INDEX index_attachments_infrastructureItemId ON attachments(infrastructureItemId)")
                db.execSQL("CREATE INDEX index_attachments_maintenanceRecordId ON attachments(maintenanceRecordId)")
                db.execSQL("CREATE INDEX index_attachments_mapFeatureId ON attachments(mapFeatureId)")
            }
        }
    }
}
