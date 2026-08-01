package com.jumastappworks.mapstead.data.mapping

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.db.RoomDatabaseTransactionRunner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class ExamplePropertySeederTest {

    private lateinit var database: MapsteadDatabase
    private lateinit var seeder: ExamplePropertySeeder

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            MapsteadDatabase::class.java
        ).allowMainThreadQueries().build()
        
        val transactionRunner = RoomDatabaseTransactionRunner(database)
        seeder = ExamplePropertySeeder(database, transactionRunner)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun seedExampleIsTransactionalAndComplete() = runBlocking {
        seeder.seedExample()
        
        assertTrue(seeder.isExampleInstalled())
        
        val property = database.propertyDao().getPropertyById(ExamplePropertySeeder.EXAMPLE_PROPERTY_ID)
        assertNotNull(property)
        assertEquals("Example Property — Safe to Explore", property?.name)

        val plans = database.planDao().getPlansForProperty(ExamplePropertySeeder.EXAMPLE_PROPERTY_ID).first()
        assertEquals(1, plans.size)
        val plan = plans.first()
        assertEquals(ExamplePropertySeeder.EXAMPLE_PLAN_ID, plan.id)

        val layers = database.layerDao().getLayersForPlan(ExamplePropertySeeder.EXAMPLE_PLAN_ID).first()
        assertTrue(layers.size >= 3)

        val features = database.mapFeatureDao().getFeaturesForPlan(ExamplePropertySeeder.EXAMPLE_PLAN_ID).first()
        assertTrue(features.size >= 7)

        val items = database.infrastructureDao().getItemsForProperty(ExamplePropertySeeder.EXAMPLE_PROPERTY_ID).first()
        assertTrue(items.size >= 3)

        val relationships = database.itemRelationshipDao().getRelationshipsForProperty(ExamplePropertySeeder.EXAMPLE_PROPERTY_ID).first()
        assertTrue(relationships.isNotEmpty())
    }

    @Test
    fun removeExampleDeletesEntireGraph() = runBlocking {
        seeder.seedExample()
        assertTrue(seeder.isExampleInstalled())

        seeder.removeExample()
        assertFalse(seeder.isExampleInstalled())

        // Verify no orphans
        val plans = database.planDao().getAllPlansOnce().filter { it.propertyId == ExamplePropertySeeder.EXAMPLE_PROPERTY_ID }
        assertTrue(plans.isEmpty())

        val features = database.mapFeatureDao().getFeaturesForProperty(ExamplePropertySeeder.EXAMPLE_PROPERTY_ID).first()
        assertTrue(features.isEmpty())

        val items = database.infrastructureDao().getItemsForProperty(ExamplePropertySeeder.EXAMPLE_PROPERTY_ID).first()
        assertTrue(items.isEmpty())
    }

    @Test
    fun seedExampleIsIdempotent() = runBlocking {
        seeder.seedExample()
        val count1 = database.mapFeatureDao().getFeaturesForProperty(ExamplePropertySeeder.EXAMPLE_PROPERTY_ID).first().size

        seeder.seedExample()
        val count2 = database.mapFeatureDao().getFeaturesForProperty(ExamplePropertySeeder.EXAMPLE_PROPERTY_ID).first().size

        assertEquals(count1, count2)
    }
}
