package com.jumastappworks.mapstead.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.db.RoomDatabaseTransactionRunner
import com.jumastappworks.mapstead.data.db.entities.*
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class TransactionRollbackTest {

    private lateinit var database: MapsteadDatabase
    private lateinit var repository: MapRepository
    private val propertyId = UUID.randomUUID()
    private val planId = UUID.randomUUID()
    private val layerId = UUID.randomUUID()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            MapsteadDatabase::class.java
        ).allowMainThreadQueries()
         .addCallback(object : androidx.room.RoomDatabase.Callback() {
             override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                 db.execSQL("PRAGMA foreign_keys = ON")
             }
         })
         .build()
        
        val transactionRunner = RoomDatabaseTransactionRunner(database)
        repository = MapRepository(database, transactionRunner, mockk(), mockk())

        runBlocking {
            // Seed basic valid context
            database.propertyDao().insertProperty(PropertyEntity(id = propertyId, name = "Test Prop", propertyType = "Home"))
            database.planDao().insertPlan(PlanEntity(id = planId, propertyId = propertyId, name = "Test Plan", planType = "MAP", backgroundType = "MAP"))
            database.layerDao().insertLayer(LayerEntity(id = layerId, propertyId = propertyId, planId = planId, name = "Test Layer", category = "Structure", displayOrder = 0))
        }
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun saveFeatureWithOptionalItemSavesBothAtomically() {
        runBlocking {
            val featureId = UUID.randomUUID()
            val itemId = UUID.randomUUID()
            
            val item = InfrastructureItemEntity(id = itemId, propertyId = propertyId, name = "Test Item", category = "Utility", status = "Active")
            val feature = MapFeatureEntity(
                id = featureId, propertyId = propertyId, planId = planId, layerId = layerId,
                infrastructureItemId = itemId, geometryType = "POINT", geometryJson = "{}",
                coordinateSpace = "GEOGRAPHIC", styleJson = "{}", label = "Test Feature", accuracySource = "Manual"
            )

            repository.saveFeatureWithOptionalItem(feature, item)

            val savedFeature = database.mapFeatureDao().getFeatureById(featureId)
            val savedItem = database.infrastructureDao().getItemById(itemId)

            assertNotNull(savedFeature)
            assertNotNull(savedItem)
            assertEquals(itemId, savedFeature?.infrastructureItemId)
        }
    }

    @Test
    fun saveFeatureWithOptionalItemRollsBackOnFailureAfterPartialWrite() {
        runBlocking {
            val featureId = UUID.randomUUID()
            val itemId = UUID.randomUUID()
            
            val item = InfrastructureItemEntity(id = itemId, propertyId = propertyId, name = "Test Item", category = "Utility", status = "Active")
            
            // Use valid metadata to pass domain validation
            val feature = MapFeatureEntity(
                id = featureId, propertyId = propertyId, planId = planId, layerId = layerId,
                infrastructureItemId = itemId, geometryType = "POINT", geometryJson = "{}",
                coordinateSpace = "GEOGRAPHIC", styleJson = "{}", label = "Should fail", accuracySource = "Manual"
            )
            
            // I'll create a trigger to force failure during the actual insert
            database.openHelper.writableDatabase.execSQL("CREATE TRIGGER force_fail BEFORE INSERT ON map_features BEGIN SELECT RAISE(ABORT, 'forced failure'); END;")

            try {
                repository.saveFeatureWithOptionalItem(feature, item)
                fail("Should have thrown forced failure")
            } catch (e: Exception) {
                assertTrue(e.message?.contains("forced failure") == true)
            }

            // Verify rollback
            assertNull("Item should have rolled back", database.infrastructureDao().getItemById(itemId))
            assertNull("Feature should not exist", database.mapFeatureDao().getFeatureById(featureId))
        }
    }

    @Test(expected = IllegalStateException::class)
    fun saveFeatureWithOptionalItemRejectsWrongPropertyOwnership() {
        runBlocking {
            val otherPropertyId = UUID.randomUUID()
            val item = InfrastructureItemEntity(id = UUID.randomUUID(), propertyId = otherPropertyId, name = "Wrong Item", category = "Utility", status = "Active")
            val feature = MapFeatureEntity(
                id = UUID.randomUUID(), propertyId = propertyId, planId = planId, layerId = layerId,
                infrastructureItemId = item.id, geometryType = "POINT", geometryJson = "{}",
                coordinateSpace = "GEOGRAPHIC", styleJson = "{}", label = "Test Feature", accuracySource = "Manual"
            )

            repository.saveFeatureWithOptionalItem(feature, item)
        }
    }

    @Test(expected = IllegalStateException::class)
    fun saveFeatureWithOptionalItemRejectsConflictingRelink() {
        runBlocking {
            val featureId = UUID.randomUUID()
            val existingItemId = UUID.randomUUID()
            val conflictingItemId = UUID.randomUUID()
            
            val item1 = InfrastructureItemEntity(id = existingItemId, propertyId = propertyId, name = "Item 1", category = "Utility", status = "Active")
            database.infrastructureDao().insertItem(item1)
            
            val feature = MapFeatureEntity(
                id = featureId, propertyId = propertyId, planId = planId, layerId = layerId,
                infrastructureItemId = existingItemId, geometryType = "POINT", geometryJson = "{}",
                coordinateSpace = "GEOGRAPHIC", styleJson = "{}", label = "Existing", accuracySource = "Manual"
            )
            database.mapFeatureDao().insertFeature(feature)
            
            // Try to relink to item2 during retry
            val item2 = InfrastructureItemEntity(id = conflictingItemId, propertyId = propertyId, name = "Item 2", category = "Utility", status = "Active")
            repository.saveFeatureWithOptionalItem(feature.copy(infrastructureItemId = conflictingItemId), item2)
        }
    }

    @Test
    fun saveFeatureWithOptionalItemHandlesSuccessfulIdempotentRetry() {
        runBlocking {
            val featureId = UUID.randomUUID()
            val itemId = UUID.randomUUID()
            
            val item = InfrastructureItemEntity(id = itemId, propertyId = propertyId, name = "Item", category = "Utility", status = "Active")
            database.infrastructureDao().insertItem(item)
            
            val feature = MapFeatureEntity(
                id = featureId, propertyId = propertyId, planId = planId, layerId = layerId,
                infrastructureItemId = itemId, geometryType = "POINT", geometryJson = "{}",
                coordinateSpace = "GEOGRAPHIC", styleJson = "{}", label = "Existing", accuracySource = "Manual"
            )
            database.mapFeatureDao().insertFeature(feature)
            
            // Re-saving with same data should be successful (idempotent)
            repository.saveFeatureWithOptionalItem(feature, item)
            
            val savedFeature = database.mapFeatureDao().getFeatureById(featureId)
            assertNotNull(savedFeature)
            assertEquals(itemId, savedFeature?.infrastructureItemId)
        }
    }
}
