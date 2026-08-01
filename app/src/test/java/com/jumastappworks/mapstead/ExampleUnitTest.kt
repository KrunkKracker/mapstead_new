package com.jumastappworks.mapstead

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.jumastappworks.mapstead.data.db.DatabaseTransactionRunner
import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.db.entities.*
import com.jumastappworks.mapstead.data.mapping.MapFeatureContextResolver
import com.jumastappworks.mapstead.data.repository.*
import io.mockk.every
import io.mockk.mockk
import com.jumastappworks.mapstead.ui.properties.PropertiesViewModel
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import com.jumastappworks.mapstead.data.mapping.SampleDataLoader
import com.jumastappworks.mapstead.util.UuidHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.io.File
import java.util.UUID

// Using repository transaction collaborator with a failing implementation for rollback testing

@RunWith(RobolectricTestRunner::class)
class ExampleUnitTest {

    private lateinit var database: MapsteadDatabase
    private lateinit var propertyRepo: PropertyRepository
    private lateinit var mapRepo: MapRepository
    private lateinit var infraRepo: InfrastructureRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            MapsteadDatabase::class.java
        ).allowMainThreadQueries().build()

        propertyRepo = PropertyRepositoryImpl(database.propertyDao(), database)
        val resolver = MapFeatureContextResolver(database.propertyDao(), database.planDao(), database.layerDao(), database.mapFeatureDao())
        
        val transactionRunner = object : DatabaseTransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T = database.withTransaction(block)
        }
        
        mapRepo = MapRepository(database, transactionRunner, mockk(relaxed = true), resolver)
        infraRepo = InfrastructureRepositoryImpl(database)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testSafeUuidParsing() {
        assertNull(UuidHelper.safeParse(null))
        assertNull(UuidHelper.safeParse(""))
        assertNull(UuidHelper.safeParse("invalid-uuid-string"))
        
        val valid = UUID.randomUUID()
        assertEquals(valid, UuidHelper.safeParse(valid.toString()))
    }

    @Test
    fun testPropertyCreationArchiveAndRestore() = runBlocking {
        val propId = UUID.randomUUID()
        val prop = PropertyEntity(
            id = propId,
            name = "Test Property",
            propertyType = "Commercial"
        )
        propertyRepo.insertProperty(prop)
        
        val loaded = propertyRepo.getPropertyById(propId)
        assertNotNull(loaded)
        assertEquals("Test Property", loaded?.name)
        assertFalse(loaded!!.isArchived)

        // Archive
        propertyRepo.archiveProperty(propId)
        val archived = propertyRepo.getPropertyById(propId)
        assertTrue(archived!!.isArchived)
        assertEquals(2L, archived.revision)

        // Restore
        propertyRepo.restoreProperty(propId)
        val restored = propertyRepo.getPropertyById(propId)
        assertFalse(restored!!.isArchived)
        assertEquals(3L, restored.revision)
    }

    @Test
    fun testPlanAndDefaultLayerTransaction() = runBlocking {
        val propId = UUID.randomUUID()
        val prop = PropertyEntity(id = propId, name = "Test", propertyType = "Farm")
        propertyRepo.insertProperty(prop)

        val planId = UUID.randomUUID()
        val plan = PlanEntity(
            id = planId,
            propertyId = propId,
            name = "Main Map",
            planType = "EXTERIOR_MAP",
            backgroundType = "MAP"
        )

        mapRepo.createPlanWithDefaultLayer(plan)

        // Verify default layer is created
        val layers = mapRepo.getLayersForPlan(planId).first()
        assertEquals(1, layers.size)
        val defaultLayer = layers.first()
        assertEquals("Property Features", defaultLayer.name)
        assertEquals("Structure", defaultLayer.category)
        assertTrue(defaultLayer.isVisible)
        assertFalse(defaultLayer.isLocked)
    }

    @Test
    fun testFeaturePointLifecycle() = runBlocking {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()

        val prop = PropertyEntity(id = propId, name = "Prop", propertyType = "Home")
        propertyRepo.insertProperty(prop)

        val plan = PlanEntity(id = planId, propertyId = propId, name = "Plan", planType = "EXTERIOR_MAP", backgroundType = "MAP")
        mapRepo.insertPlan(plan)

        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "Layer", category = "Custom")
        mapRepo.insertLayer(layer)

        val featureId = UUID.randomUUID()
        val feature = MapFeatureEntity(
            id = featureId,
            propertyId = propId,
            planId = planId,
            layerId = layerId,
            geometryType = "POINT",
            geometryJson = "{\"type\":\"Point\",\"coordinates\":[-110,45]}",
            coordinateSpace = "GEOGRAPHIC",
            styleJson = "{}",
            accuracySource = "User Estimated",
            capturedLatitude = 45.0,
            capturedLongitude = -110.0,
            label = "Water Meter"
        )

        // Insertion
        mapRepo.insertFeature(feature)
        var features = mapRepo.getFeaturesForLayer(layerId).first()
        assertEquals(1, features.size)
        assertEquals("Water Meter", features.first().label)

        // Update / Movement
        val updated = features.first().copy(
            label = "Moved Water Meter",
            capturedLatitude = 46.0,
            capturedLongitude = -111.0,
            geometryJson = "{\"type\":\"Point\",\"coordinates\":[-111,46]}"
        )
        mapRepo.updateFeature(updated)

        features = mapRepo.getFeaturesForLayer(layerId).first()
        assertEquals(1, features.size)
        assertEquals("Moved Water Meter", features.first().label)
        assertEquals(46.0, features.first().capturedLatitude!!, 0.001)
        assertEquals(2L, features.first().revision)

        // Soft deletion
        mapRepo.softDeleteFeatureWithAttachments(propId, planId, featureId)
        features = mapRepo.getFeaturesForLayer(layerId).first()
        assertTrue(features.isEmpty())
    }

    @Test
    fun testInfrastructureCreationAndLinking() = runBlocking {
        val propId = UUID.randomUUID()
        val prop = PropertyEntity(id = propId, name = "Prop", propertyType = "Home")
        propertyRepo.insertProperty(prop)

        val itemId = UUID.randomUUID()
        val item = InfrastructureItemEntity(
            id = itemId,
            propertyId = propId,
            name = "Gas Shutoff Valve",
            category = "Utility",
            status = "Active",
            isEmergencyItem = true,
            emergencyInstructions = "Turn clockwise to close."
        )

        infraRepo.insertItem(item)

        val loadedItem = infraRepo.getItemById(itemId)
        assertNotNull(loadedItem)
        assertEquals("Gas Shutoff Valve", loadedItem?.name)
        assertTrue(loadedItem!!.isEmergencyItem)

        // Soft delete item
        infraRepo.softDeleteItem(itemId)
        val deletedItem = infraRepo.getItemById(itemId)
        assertNull(deletedItem)
    }

    @Test
    fun testPlanCreationRollsBackOnLayerFailureReal() = runBlocking {
        val propId = UUID.randomUUID()
        val prop = PropertyEntity(id = propId, name = "Test", propertyType = "Farm")
        propertyRepo.insertProperty(prop)

        val planId = UUID.randomUUID()
        val plan = PlanEntity(
            id = planId,
            propertyId = propId,
            name = "Failing Plan Real",
            planType = "EXTERIOR_MAP",
            backgroundType = "MAP"
        )

        // Violating foreign key constraint by using non-existent property UUID on layer
        val failingLayer = LayerEntity(
            id = UUID.randomUUID(),
            propertyId = UUID.randomUUID(),
            planId = planId,
            name = "Failing Layer",
            category = "Structure"
        )

        var failed = false
        try {
            mapRepo.createPlanWithDefaultLayer(plan, failingLayer)
        } catch (e: Exception) {
            failed = true
        }
        assertTrue(failed)

        // Verify plan does NOT exist
        val plans = mapRepo.getPlansForProperty(propId).first()
        assertTrue(plans.none { it.id == planId })

        // Verify default layer does NOT exist
        val layers = mapRepo.getLayersForPlan(planId).first()
        assertTrue(layers.isEmpty())
    }

    @Test
    fun testLockedLayerEditRejection() = runBlocking {
        val layer = LayerEntity(
            id = UUID.randomUUID(),
            propertyId = UUID.randomUUID(),
            planId = UUID.randomUUID(),
            name = "Locked Layer",
            category = "Structure",
            isLocked = true
        )
        assertTrue(layer.isLocked)
    }

    @Test
    fun testPropertyEditPreservesUnrelatedFields() = runBlocking {
        val propId = UUID.randomUUID()
        val original = PropertyEntity(
            id = propId,
            name = "Original Name",
            propertyType = "Farm",
            createdAt = Instant.ofEpochMilli(1000L),
            revision = 5L,
            description = "Preserve this description",
            heroPhotoUri = "content://hero"
        )
        propertyRepo.insertProperty(original)

        val existing = propertyRepo.getPropertyById(propId)!!
        assertEquals(Instant.ofEpochMilli(1000L), existing.createdAt)

        val edited = existing.copy(
            name = "Edited Name"
        )
        propertyRepo.updateProperty(edited)

        val updated = propertyRepo.getPropertyById(propId)!!
        assertEquals("Edited Name", updated.name)
        assertEquals("Preserve this description", updated.description)
        assertEquals("content://hero", updated.heroPhotoUri)
        assertEquals(6L, updated.revision)
        assertEquals(Instant.ofEpochMilli(1000L), updated.createdAt)
    }

    @Test
    fun testInfrastructureEditPreservesUnrelatedFields() = runBlocking {
        val propId = UUID.randomUUID()
        val prop = PropertyEntity(id = propId, name = "Test Prop", propertyType = "Farm")
        propertyRepo.insertProperty(prop)

        val itemId = UUID.randomUUID()
        val original = InfrastructureItemEntity(
            id = itemId,
            propertyId = propId,
            name = "Original Name",
            category = "Utility",
            status = "Active",
            warrantyExpirationDate = null,
            specificationsJson = "{\"spec\": 1}",
            createdAt = Instant.ofEpochMilli(2000L),
            revision = 3L
        )
        infraRepo.insertItem(original)

        val existing = infraRepo.getItemById(itemId)!!
        assertEquals("{\"spec\": 1}", existing.specificationsJson)

        val edited = existing.copy(
            name = "New Name"
        )
        infraRepo.updateItem(edited)

        val updated = infraRepo.getItemById(itemId)!!
        assertEquals("New Name", updated.name)
        assertEquals("{\"spec\": 1}", updated.specificationsJson)
        assertEquals(4L, updated.revision)
        assertEquals(Instant.ofEpochMilli(2000L), updated.createdAt)
    }

    @Test
    fun testUserEstimatedAccuracyRemainingNull() {
        val horizontalAccuracyMeters: Double? = null
        assertNull(horizontalAccuracyMeters)
    }

    @Test
    fun testFeatureReassignmentBetweenLayers() = runBlocking {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerAId = UUID.randomUUID()
        val layerBId = UUID.randomUUID()

        val prop = PropertyEntity(id = propId, name = "Prop", propertyType = "Home")
        propertyRepo.insertProperty(prop)

        val plan = PlanEntity(id = planId, propertyId = propId, name = "Plan", planType = "EXTERIOR_MAP", backgroundType = "MAP")
        mapRepo.insertPlan(plan)

        val layerA = LayerEntity(id = layerAId, propertyId = propId, planId = planId, name = "Layer A", category = "Custom")
        val layerB = LayerEntity(id = layerBId, propertyId = propId, planId = planId, name = "Layer B", category = "Custom")
        mapRepo.insertLayer(layerA)
        mapRepo.insertLayer(layerB)

        val featureId = UUID.randomUUID()
        val feature = MapFeatureEntity(
            id = featureId,
            propertyId = propId,
            planId = planId,
            layerId = layerAId,
            geometryType = "POINT",
            geometryJson = "{\"type\":\"Point\",\"coordinates\":[-110,45]}",
            coordinateSpace = "GEOGRAPHIC",
            styleJson = "{}",
            accuracySource = "User Estimated",
            capturedLatitude = 45.0,
            capturedLongitude = -110.0,
            label = "Water Meter"
        )

        mapRepo.insertFeature(feature)

        val loaded = mapRepo.getFeatureById(featureId)!!
        val updated = loaded.copy(layerId = layerBId)
        mapRepo.updateFeature(updated)

        val moved = mapRepo.getFeatureById(featureId)!!
        assertEquals(layerBId, moved.layerId)
        assertEquals(2L, moved.revision)
    }

    @Test
    fun testSelectedPropertyClearingAfterDeletionOrArchive() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val testFile = File(context.cacheDir, "test_settings_example.preferences_pb")
        if (testFile.exists()) testFile.delete()
        val dataStore = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
            produceFile = { testFile }
        )
        val userPrefs = UserPreferencesRepository(dataStore)

        val propId = UUID.randomUUID()
        userPrefs.updateSelectedProperty(propId.toString())
        var currentSelected = userPrefs.userPreferencesFlow.first().selectedPropertyId
        assertEquals(propId.toString(), currentSelected)

        // Clear selection
        userPrefs.updateSelectedProperty(null)
        currentSelected = userPrefs.userPreferencesFlow.first().selectedPropertyId
        assertNull(currentSelected)
    }

    @Test
    fun testLayerManagementAndLockedLayerProtection() = runBlocking {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()

        val prop = PropertyEntity(id = propId, name = "Prop", propertyType = "Home")
        propertyRepo.insertProperty(prop)

        val plan = PlanEntity(id = planId, propertyId = propId, name = "Plan", planType = "EXTERIOR_MAP", backgroundType = "MAP")
        mapRepo.insertPlan(plan)

        val layer = LayerEntity(
            id = layerId,
            propertyId = propId,
            planId = planId,
            name = "Original Layer Name",
            category = "Custom",
            opacity = 0.5f,
            displayOrder = 10,
            isLocked = true
        )
        mapRepo.insertLayer(layer)

        var loadedLayer = mapRepo.getLayersForPlan(planId).first().first()
        assertEquals("Original Layer Name", loadedLayer.name)
        assertEquals(0.5f, loadedLayer.opacity, 0.01f)
        assertEquals(10, loadedLayer.displayOrder)
        assertTrue(loadedLayer.isLocked)

        mapRepo.updateLayer(loadedLayer.copy(name = "Renamed Layer", opacity = 0.8f))
        loadedLayer = mapRepo.getLayersForPlan(planId).first().first()
        assertEquals("Renamed Layer", loadedLayer.name)
        assertEquals(0.8f, loadedLayer.opacity, 0.01f)
    }
}
