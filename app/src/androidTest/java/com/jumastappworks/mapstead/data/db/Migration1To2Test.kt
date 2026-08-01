package com.jumastappworks.mapstead.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

@RunWith(AndroidJUnit4::class)
class Migration1To2Test {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MapsteadDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2() {
        val propertyId = UUID.randomUUID().toString()
        val infraId = UUID.randomUUID().toString()
        val recordId = UUID.randomUUID().toString()
        
        val a1 = UUID.randomUUID().toString() // Property level
        val a2 = UUID.randomUUID().toString() // Infrastructure level
        val a3 = UUID.randomUUID().toString() // Maintenance level
        val deletedTime = System.currentTimeMillis()

        // 1. Create DB in version 1
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL("""
                INSERT INTO properties (id, name, propertyType, addressLine1, city, isArchived, createdAt, updatedAt, revision) 
                VALUES ('$propertyId', 'Test Prop', 'Home', '123 Main', 'Anytown', 0, 1000, 1000, 1)
            """)
            db.execSQL("""
                INSERT INTO infrastructure_items (id, propertyId, name, category, subtype, status, isEmergencyItem, createdAt, updatedAt, revision) 
                VALUES ('$infraId', '$propertyId', 'Item', 'Cat', 'Sub', 'Active', 0, 1100, 1100, 1)
            """)
            db.execSQL("""
                INSERT INTO maintenance_records (id, propertyId, title, category, description, status, serviceDate, createdAt, updatedAt, revision) 
                VALUES ('$recordId', '$propertyId', 'Record', 'Cat', 'Desc', 'Scheduled', '2026-07-24', 1200, 1200, 1)
            """)

            db.execSQL("""
                INSERT INTO attachments (id, propertyId, infrastructureItemId, maintenanceRecordId, attachmentType, localUri, appManagedCopyPath, displayName, mimeType, fileSizeBytes, sha256, caption, createdAt, updatedAt, deletedAt, revision) 
                VALUES ('$a1', '$propertyId', NULL, NULL, 'Photo', 'content://p', 'path1', 'P Photo', 'image/jpeg', 100, 'sha1', 'Cap1', 2000, 2000, NULL, 5)
            """)
            db.execSQL("""
                INSERT INTO attachments (id, propertyId, infrastructureItemId, maintenanceRecordId, attachmentType, localUri, appManagedCopyPath, displayName, mimeType, fileSizeBytes, sha256, caption, createdAt, updatedAt, deletedAt, revision) 
                VALUES ('$a2', '$propertyId', '$infraId', NULL, 'Document', 'content://i', 'path2', 'I Doc', 'application/pdf', 200, 'sha2', 'Cap2', 2100, 2100, $deletedTime, 6)
            """)
            db.execSQL("""
                INSERT INTO attachments (id, propertyId, infrastructureItemId, maintenanceRecordId, attachmentType, localUri, appManagedCopyPath, displayName, mimeType, fileSizeBytes, sha256, caption, createdAt, updatedAt, deletedAt, revision) 
                VALUES ('$a3', '$propertyId', NULL, '$recordId', 'Photo', 'content://m', 'path3', 'M Photo', 'image/png', 300, 'sha3', 'Cap3', 2200, 2200, NULL, 7)
            """)
        }

        // 2. Migrate to version 2 and verify
        helper.runMigrationsAndValidate(TEST_DB, 2, true, MapsteadDatabase.MIGRATION_1_2).use { db ->
            // Verify properties preserved
            var cursor = db.query("SELECT * FROM properties WHERE id = '$propertyId'")
            cursor.moveToFirst()
            assertEquals("123 Main", cursor.getString(cursor.getColumnIndex("addressLine1")))
            cursor.close()

            // Verify infrastructure preserved
            cursor = db.query("SELECT * FROM infrastructure_items WHERE id = '$infraId'")
            cursor.moveToFirst()
            assertEquals("Sub", cursor.getString(cursor.getColumnIndex("subtype")))
            cursor.close()

            // Verify maintenance preserved
            cursor = db.query("SELECT * FROM maintenance_records WHERE id = '$recordId'")
            cursor.moveToFirst()
            assertEquals("Desc", cursor.getString(cursor.getColumnIndex("description")))
            cursor.close()

            // Verify attachment row count
            cursor = db.query("SELECT COUNT(*) FROM attachments")
            cursor.moveToFirst()
            assertEquals(3, cursor.getInt(0))
            cursor.close()

            // Check a1 (Property level)
            cursor = db.query("SELECT * FROM attachments WHERE id = '$a1'")
            cursor.moveToFirst()
            assertEquals(propertyId, cursor.getString(cursor.getColumnIndex("propertyId")))
            assertNull(cursor.getString(cursor.getColumnIndex("infrastructureItemId")))
            assertNull(cursor.getString(cursor.getColumnIndex("maintenanceRecordId")))
            assertNull(cursor.getString(cursor.getColumnIndex("mapFeatureId")))
            assertEquals("Photo", cursor.getString(cursor.getColumnIndex("attachmentType")))
            assertEquals("content://p", cursor.getString(cursor.getColumnIndex("localUri")))
            assertEquals("path1", cursor.getString(cursor.getColumnIndex("appManagedCopyPath")))
            assertEquals("P Photo", cursor.getString(cursor.getColumnIndex("displayName")))
            assertEquals("image/jpeg", cursor.getString(cursor.getColumnIndex("mimeType")))
            assertEquals(100L, cursor.getLong(cursor.getColumnIndex("fileSizeBytes")))
            assertEquals("sha1", cursor.getString(cursor.getColumnIndex("sha256")))
            assertEquals("Cap1", cursor.getString(cursor.getColumnIndex("caption")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndex("isCover")))
            assertEquals(2000L, cursor.getLong(cursor.getColumnIndex("createdAt")))
            assertEquals(2000L, cursor.getLong(cursor.getColumnIndex("updatedAt")))
            assertEquals(5, cursor.getInt(cursor.getColumnIndex("revision")))
            cursor.close()

            // Check a2 (Infrastructure level)
            cursor = db.query("SELECT * FROM attachments WHERE id = '$a2'")
            cursor.moveToFirst()
            assertEquals(propertyId, cursor.getString(cursor.getColumnIndex("propertyId")))
            assertEquals(infraId, cursor.getString(cursor.getColumnIndex("infrastructureItemId")))
            assertNull(cursor.getString(cursor.getColumnIndex("maintenanceRecordId")))
            assertNull(cursor.getString(cursor.getColumnIndex("mapFeatureId")))
            assertEquals("Document", cursor.getString(cursor.getColumnIndex("attachmentType")))
            assertEquals("content://i", cursor.getString(cursor.getColumnIndex("localUri")))
            assertEquals("path2", cursor.getString(cursor.getColumnIndex("appManagedCopyPath")))
            assertEquals("I Doc", cursor.getString(cursor.getColumnIndex("displayName")))
            assertEquals("application/pdf", cursor.getString(cursor.getColumnIndex("mimeType")))
            assertEquals(200L, cursor.getLong(cursor.getColumnIndex("fileSizeBytes")))
            assertEquals("sha2", cursor.getString(cursor.getColumnIndex("sha256")))
            assertEquals("Cap2", cursor.getString(cursor.getColumnIndex("caption")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndex("isCover")))
            assertEquals(2100L, cursor.getLong(cursor.getColumnIndex("createdAt")))
            assertEquals(2100L, cursor.getLong(cursor.getColumnIndex("updatedAt")))
            assertEquals(deletedTime, cursor.getLong(cursor.getColumnIndex("deletedAt")))
            assertEquals(6, cursor.getInt(cursor.getColumnIndex("revision")))
            cursor.close()

            // Check a3 (Maintenance level)
            cursor = db.query("SELECT * FROM attachments WHERE id = '$a3'")
            cursor.moveToFirst()
            assertEquals(propertyId, cursor.getString(cursor.getColumnIndex("propertyId")))
            assertNull(cursor.getString(cursor.getColumnIndex("infrastructureItemId")))
            assertEquals(recordId, cursor.getString(cursor.getColumnIndex("maintenanceRecordId")))
            assertNull(cursor.getString(cursor.getColumnIndex("mapFeatureId")))
            assertEquals("Photo", cursor.getString(cursor.getColumnIndex("attachmentType")))
            assertEquals("content://m", cursor.getString(cursor.getColumnIndex("localUri")))
            assertEquals("path3", cursor.getString(cursor.getColumnIndex("appManagedCopyPath")))
            assertEquals("M Photo", cursor.getString(cursor.getColumnIndex("displayName")))
            assertEquals("image/png", cursor.getString(cursor.getColumnIndex("mimeType")))
            assertEquals(300L, cursor.getLong(cursor.getColumnIndex("fileSizeBytes")))
            assertEquals("sha3", cursor.getString(cursor.getColumnIndex("sha256")))
            assertEquals("Cap3", cursor.getString(cursor.getColumnIndex("caption")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndex("isCover")))
            assertEquals(2200L, cursor.getLong(cursor.getColumnIndex("createdAt")))
            assertEquals(2200L, cursor.getLong(cursor.getColumnIndex("updatedAt")))
            assertNull(cursor.getString(cursor.getColumnIndex("deletedAt")))
            assertEquals(7, cursor.getInt(cursor.getColumnIndex("revision")))
            cursor.close()
            
            // Verify indices exist
            cursor = db.query("PRAGMA index_list('attachments')")
            val indices = mutableListOf<String>()
            while (cursor.moveToNext()) {
                indices.add(cursor.getString(cursor.getColumnIndex("name")))
            }
            cursor.close()
            
            assertTrue(indices.contains("index_attachments_propertyId"))
            assertTrue(indices.contains("index_attachments_infrastructureItemId"))
            assertTrue(indices.contains("index_attachments_maintenanceRecordId"))
            assertTrue(indices.contains("index_attachments_mapFeatureId"))

            // Verify foreign keys exactly
            cursor = db.query("PRAGMA foreign_key_list('attachments')")
            val fkList = mutableListOf<ForeignKeyInfo>()
            while (cursor.moveToNext()) {
                fkList.add(ForeignKeyInfo(
                    from = cursor.getString(cursor.getColumnIndex("from")),
                    table = cursor.getString(cursor.getColumnIndex("table")),
                    to = cursor.getString(cursor.getColumnIndex("to")),
                    onUpdate = cursor.getString(cursor.getColumnIndex("on_update")),
                    onDelete = cursor.getString(cursor.getColumnIndex("on_delete"))
                ))
            }
            cursor.close()

            assertForeignKey(fkList, "propertyId", "properties", "id")
            assertForeignKey(fkList, "infrastructureItemId", "infrastructure_items", "id")
            assertForeignKey(fkList, "maintenanceRecordId", "maintenance_records", "id")
            assertForeignKey(fkList, "mapFeatureId", "map_features", "id")
        }
    }

    private fun assertForeignKey(fks: List<ForeignKeyInfo>, from: String, table: String, to: String) {
        val match = fks.find { it.from == from && it.table == table && it.to == to }
        assertNotNull("Missing FK for $from to $table($to)", match)
        assertEquals("FK for $from must have ON DELETE CASCADE", "CASCADE", match?.onDelete)
        assertEquals("FK for $from must have ON UPDATE NO ACTION", "NO ACTION", match?.onUpdate)
    }

    private data class ForeignKeyInfo(
        val from: String,
        val table: String,
        val to: String,
        val onUpdate: String,
        val onDelete: String
    )
}
