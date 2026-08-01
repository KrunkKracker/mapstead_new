package com.jumastappworks.mapstead.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jumastappworks.mapstead.data.attachments.*
import com.jumastappworks.mapstead.data.backup.AttachmentStorageService
import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.db.RoomDatabaseTransactionRunner
import com.jumastappworks.mapstead.data.db.entities.*
import com.jumastappworks.mapstead.data.mapping.MapFeatureContextResolver
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

@RunWith(AndroidJUnit4::class)
class AttachmentTransactionTest {

    private lateinit var db: MapsteadDatabase
    private lateinit var repository: AttachmentRepository
    private val resolver = mockk<MapFeatureContextResolver>(relaxed = true)
    
    private val storageService = mockk<AttachmentStorageService>(relaxed = true)
    
    private val propertyId = UUID.randomUUID()
    private val planId = UUID.randomUUID()
    private val layerId = UUID.randomUUID()
    private val featureId = UUID.randomUUID()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MapsteadDatabase::class.java).build()
        
        val transactionRunner = RoomDatabaseTransactionRunner(db)
        repository = AttachmentRepository(
            database = db,
            storageService = storageService,
            mapFeatureContextResolver = resolver,
            transactionRunner = transactionRunner,
            context = context
        )

        runBlocking {
            db.propertyDao().insertProperty(PropertyEntity(id = propertyId, name = "P", propertyType = "H"))
            db.planDao().insertPlan(PlanEntity(id = planId, propertyId = propertyId, name = "P", planType = "M", backgroundType = "M"))
            db.layerDao().insertLayer(LayerEntity(id = layerId, propertyId = propertyId, planId = planId, name = "L", category = "C"))
            db.mapFeatureDao().insertFeature(MapFeatureEntity(
                id = featureId, propertyId = propertyId, planId = planId, layerId = layerId,
                geometryType = "Point", geometryJson = "{}", coordinateSpace = "LOCAL", styleJson = "{}",
                accuracySource = "M"
            ))
            
            coEvery { resolver.resolveFromFeature(propertyId, featureId) } returns ActiveMapFeatureContext(
                feature = db.mapFeatureDao().getFeatureById(featureId)!!,
                plan = db.planDao().getAllPlansOnce().first(),
                layer = db.layerDao().getAllLayersOnce().first()
            )
        }
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun testSetFeatureCoverAtomicReplacement() {
        runBlocking {
            val a1 = UUID.randomUUID()
            val a2 = UUID.randomUUID()
            
            db.attachmentDao().insertAttachment(AttachmentEntity(
                id = a1, propertyId = propertyId, mapFeatureId = featureId, attachmentType = "Photo",
                localUri = "", displayName = "A1", mimeType = "image/jpeg", isCover = true,
                appManagedCopyPath = "path1", fileSizeBytes = 100L, sha256 = "h1"
            ))
            db.attachmentDao().insertAttachment(AttachmentEntity(
                id = a2, propertyId = propertyId, mapFeatureId = featureId, attachmentType = "Photo",
                localUri = "", displayName = "A2", mimeType = "image/jpeg", isCover = false,
                appManagedCopyPath = "path2", fileSizeBytes = 100L, sha256 = "h2"
            ))

            val mockFile = java.io.File.createTempFile("test", "jpg")
            mockFile.writeBytes(ByteArray(100))
            every { storageService.resolveFromEntityPath(any()) } returns Result.success(mockFile)
            every { storageService.calculateSha256(any()) } returns "h2"
            
            val res = repository.setFeatureCoverAttachment(propertyId, featureId, a2)
            
            assertTrue("Result should be Set: $res", res is CoverResult.Set)
            
            val loaded1 = db.attachmentDao().getAttachmentById(a1)
            val loaded2 = db.attachmentDao().getAttachmentById(a2)
            
            assertFalse("A1 should no longer be cover", loaded1!!.isCover)
            assertTrue("A2 should be cover", loaded2!!.isCover)
            
            mockFile.delete()
        }
    }

    @Test
    fun testSetFeatureCoverRollbackOnFailure() {
        runBlocking {
            val a1 = UUID.randomUUID()
            db.attachmentDao().insertAttachment(AttachmentEntity(id = a1, propertyId = propertyId, mapFeatureId = featureId, attachmentType = "Photo", localUri = "", displayName = "A1", mimeType = "image/jpeg", isCover = true))

            val transactionRunner = RoomDatabaseTransactionRunner(db)
            
            try {
                transactionRunner.run {
                    db.attachmentDao().clearFeatureCover(propertyId, featureId)
                    // Simulate failure before setting new cover
                    throw RuntimeException("Simulated Failure")
                }
            } catch (e: Exception) {
                assertEquals("Simulated Failure", e.message)
            }

            val loaded = db.attachmentDao().getAttachmentById(a1)
            assertTrue("A1 should still be cover after rollback", loaded!!.isCover)
        }
    }
}
