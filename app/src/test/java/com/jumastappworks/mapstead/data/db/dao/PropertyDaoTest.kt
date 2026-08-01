package com.jumastappworks.mapstead.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PropertyDaoTest {

    private lateinit var database: MapsteadDatabase
    private lateinit var propertyDao: PropertyDao

    @Before
    fun createDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MapsteadDatabase::class.java
        ).allowMainThreadQueries().build()
        propertyDao = database.propertyDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun insertAndGetProperty() = runBlocking {
        val property = PropertyEntity(name = "Test Home", propertyType = "Residential")
        propertyDao.insertProperty(property)
        val retrieved = propertyDao.getPropertyById(property.id)
        assertNotNull(retrieved)
        assertEquals("Test Home", retrieved?.name)
    }

    @Test
    fun softDeleteFiltersResults() = runBlocking {
        val property = PropertyEntity(name = "Delete Me", propertyType = "Home")
        propertyDao.insertProperty(property)
        
        var all = propertyDao.getAllProperties().first()
        assertTrue(all.any { it.id == property.id })

        propertyDao.softDeleteProperty(property.id)
        
        all = propertyDao.getAllProperties().first()
        assertFalse(all.any { it.id == property.id })
        
        val retrieved = propertyDao.getPropertyById(property.id)
        assertNull(retrieved)
    }
}
