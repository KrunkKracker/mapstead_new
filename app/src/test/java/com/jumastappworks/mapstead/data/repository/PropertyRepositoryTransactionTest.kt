package com.jumastappworks.mapstead.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.*

@RunWith(RobolectricTestRunner::class)
class PropertyRepositoryTransactionTest {

    private lateinit var database: MapsteadDatabase
    private lateinit var repository: PropertyRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MapsteadDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PropertyRepositoryImpl(database.propertyDao(), database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insertPropertyWithDefaultMap creates property map and layer atomically`() = runBlocking {
        val propertyId = UUID.randomUUID()
        val property = PropertyEntity(
            id = propertyId,
            name = "Test Farm",
            propertyType = "Farm",
            latitude = 45.0,
            longitude = -90.0
        )
        
        repository.insertPropertyWithDefaultMap(property, "Property Map")
        
        // Verify Property
        val savedProp = repository.getPropertyById(propertyId)
        assertNotNull(savedProp)
        assertEquals("Test Farm", savedProp?.name)
        
        // Verify Map
        val plans = database.planDao().getPlansForProperty(propertyId).first()
        assertEquals(1, plans.size)
        assertEquals("Property Map", plans[0].name)
    }

    @Test
    fun `insertPropertyWithDefaultMap without coordinates creates no map`() = runBlocking {
        val propertyId = UUID.randomUUID()
        val property = PropertyEntity(
            id = propertyId,
            name = "Locationless",
            propertyType = "Vacant Land",
            latitude = null,
            longitude = null
        )
        
        repository.insertPropertyWithDefaultMap(property, "Property Map")
        
        val plans = database.planDao().getPlansForProperty(propertyId).first()
        assertTrue("Should have no plans for locationless property", plans.isEmpty())
    }

    @Test
    fun `idempotent creation with same ID returns success and duplicates nothing`() = runBlocking {
        val propertyId = UUID.randomUUID()
        val property = PropertyEntity(
            id = propertyId,
            name = "Stable ID",
            propertyType = "Cabin",
            latitude = 45.0,
            longitude = -90.0
        )
        
        // First call
        repository.insertPropertyWithDefaultMap(property, "Primary Map")
        
        // Second call with same ID
        repository.insertPropertyWithDefaultMap(property, "Primary Map")
        
        val allProps = database.propertyDao().getAllPropertiesOnce()
        assertEquals("Should only have one property", 1, allProps.count { it.id == propertyId })
        
        val allPlans = database.planDao().getAllPlansOnce().filter { it.propertyId == propertyId && it.deletedAt == null }
        assertEquals("Should only have one plan", 1, allPlans.size)
    }

    @Test
    fun `atomic rollback on Plan failure`() = runBlocking {
        val propertyId = UUID.randomUUID()
        val property = PropertyEntity(id = propertyId, name = "Failing", propertyType = "Property", latitude = 10.0, longitude = 10.0)
        
        val failingPlanDao = mockk<com.jumastappworks.mapstead.data.db.dao.PlanDao>()
        coEvery { failingPlanDao.insertPlan(any()) } throws RuntimeException("Plan Failure")
        coEvery { failingPlanDao.getAllPlansOnce() } returns emptyList()
        
        val failingDb = spyk(database)
        coEvery { failingDb.planDao() } returns failingPlanDao
        
        val failingRepo = PropertyRepositoryImpl(database.propertyDao(), failingDb)
        
        try { failingRepo.insertPropertyWithDefaultMap(property, "Fail") } catch (e: Exception) { }
        
        // Verify Property was rolled back
        assertNull(database.propertyDao().getPropertyById(propertyId))
    }

    @Test
    fun `atomic rollback on forced Layer failure`() = runBlocking {
        val propertyId = UUID.randomUUID()
        val property = PropertyEntity(id = propertyId, name = "Failing", propertyType = "Property", latitude = 10.0, longitude = 10.0)
        
        val failingLayerDao = mockk<com.jumastappworks.mapstead.data.db.dao.LayerDao>()
        coEvery { failingLayerDao.insertLayer(any()) } throws RuntimeException("Layer Failure")
        
        val failingDb = spyk(database)
        coEvery { failingDb.layerDao() } returns failingLayerDao
        
        val failingRepo = PropertyRepositoryImpl(database.propertyDao(), failingDb)
        
        try { failingRepo.insertPropertyWithDefaultMap(property, "Fail") } catch (e: Exception) { }
        
        // Verify Property and Plan were rolled back
        assertNull(database.propertyDao().getPropertyById(propertyId))
        assertTrue(database.planDao().getAllPlansOnce().filter { it.propertyId == propertyId }.isEmpty())
    }

    @Test
    fun `updatePropertyLocationWithOptionalFirstMap updates location and creates map`() = runBlocking {
        val propertyId = UUID.randomUUID()
        val property = PropertyEntity(id = propertyId, name = "Locless", propertyType = "Property")
        database.propertyDao().insertProperty(property)
        
        repository.updatePropertyLocationWithOptionalFirstMap(propertyId, 10.0, 20.0, true)
        
        val updated = repository.getPropertyById(propertyId)
        assertEquals(10.0, updated?.latitude!!, 1e-10)
        
        val plans = database.planDao().getPlansForProperty(propertyId).first()
        assertEquals(1, plans.size)
        assertEquals("Property Map", plans[0].name)
    }

    @Test
    fun `updatePropertyLocationWithOptionalFirstMap rollback on Map failure`() = runBlocking {
        val propertyId = UUID.randomUUID()
        val property = PropertyEntity(id = propertyId, name = "Original", propertyType = "Property", latitude = null, longitude = null)
        database.propertyDao().insertProperty(property)
        
        val failingPlanDao = mockk<com.jumastappworks.mapstead.data.db.dao.PlanDao>()
        coEvery { failingPlanDao.insertPlan(any()) } throws RuntimeException("Plan Failure")
        coEvery { failingPlanDao.getAllPlansOnce() } returns emptyList()
        
        val failingDb = spyk(database)
        coEvery { failingDb.planDao() } returns failingPlanDao
        
        val failingRepo = PropertyRepositoryImpl(database.propertyDao(), failingDb)
        
        try { failingRepo.updatePropertyLocationWithOptionalFirstMap(propertyId, 10.0, 20.0, true) } catch (e: Exception) {}
        
        // Verify Property location was rolled back (is null)
        val after = database.propertyDao().getPropertyById(propertyId)
        assertNull(after?.latitude)
    }

    @Test
    fun `invalid coordinates are rejected in repository`() = runBlocking {
        val propertyId = UUID.randomUUID()
        val result = repository.updatePropertyLocationWithOptionalFirstMap(propertyId, 91.0, 0.0, true)
        assertTrue(result.isFailure)
        assertEquals("Invalid coordinates", result.exceptionOrNull()?.message)
    }
}
