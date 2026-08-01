package com.jumastappworks.mapstead.data.mapping

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.inject.Inject

@RunWith(RobolectricTestRunner::class)
class SampleDataLoaderTest {

    private lateinit var database: MapsteadDatabase
    private lateinit var userPrefs: UserPreferencesRepository
    private lateinit var loader: SampleDataLoader

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            MapsteadDatabase::class.java
        ).allowMainThreadQueries().build()
        
        userPrefs = UserPreferencesRepository(mockk(relaxed = true) {
            every { data } returns flowOf(androidx.datastore.preferences.core.emptyPreferences())
        })
        loader = SampleDataLoader(database, userPrefs)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun loadSampleDataIsIdempotent() = runBlocking {
        // Load first time
        loader.loadSampleData()
        val properties1 = database.propertyDao().getAllProperties().first()
        val items1 = database.infrastructureDao().getItemsForProperty(SampleDataLoader.DEMO_PROPERTY_ID).first()
        
        assertEquals(1, properties1.size)
        assertEquals(2, items1.size)

        // Load second time
        loader.loadSampleData()
        val properties2 = database.propertyDao().getAllProperties().first()
        val items2 = database.infrastructureDao().getItemsForProperty(SampleDataLoader.DEMO_PROPERTY_ID).first()

        // Should still be 1 property and 2 items (idempotent replacement)
        assertEquals(1, properties2.size)
        assertEquals(2, items2.size)
    }

    @Test
    fun clearDemoDataRemovesDemoProperty() = runBlocking {
        loader.loadSampleData()
        assertTrue(loader.isDemoDataInstalled().first())

        loader.clearDemoData()
        assertFalse(loader.isDemoDataInstalled().first())
        
        val properties = database.propertyDao().getAllProperties().first()
        assertTrue(properties.none { it.id == SampleDataLoader.DEMO_PROPERTY_ID })
    }
}
