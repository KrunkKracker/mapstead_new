package com.jumastappworks.mapstead.data.repository

import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.db.dao.InfrastructureDao
import com.jumastappworks.mapstead.data.db.dao.ItemRelationshipDao
import com.jumastappworks.mapstead.data.db.dao.MapFeatureDao
import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import com.jumastappworks.mapstead.data.relationships.RelationshipWriteResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.*

class ParentHierarchyCycleTest {

    private val db = mockk<MapsteadDatabase>(relaxed = true)
    private val infrastructureDao = mockk<InfrastructureDao>(relaxed = true)
    private val relationshipDao = mockk<ItemRelationshipDao>(relaxed = true)
    private val mapFeatureDao = mockk<MapFeatureDao>(relaxed = true)
    private lateinit var repository: InfrastructureRelationshipRepository

    @Before
    fun setup() {
        every { db.infrastructureDao() } returns infrastructureDao
        every { db.itemRelationshipDao() } returns relationshipDao
        every { db.mapFeatureDao() } returns mapFeatureDao
        repository = InfrastructureRelationshipRepository(db)
    }

    @Test
    fun testDeepHierarchyCycleRejected() = runTest {
        val propId = UUID.randomUUID()
        val root = UUID.randomUUID()
        val middle = UUID.randomUUID()
        val leaf = UUID.randomUUID()
        
        // Root -> Middle -> Leaf
        val items = listOf(
            InfrastructureItemEntity(id = root, propertyId = propId, name = "Root", category = "C", parentItemId = null, status = "A"),
            InfrastructureItemEntity(id = middle, propertyId = propId, name = "Middle", category = "C", parentItemId = root, status = "A"),
            InfrastructureItemEntity(id = leaf, propertyId = propId, name = "Leaf", category = "C", parentItemId = middle, status = "A")
        )
        
        coEvery { infrastructureDao.getAllItemsOnce() } returns items
        coEvery { infrastructureDao.getActiveItemForProperty(propId, root) } returns items[0]
        coEvery { infrastructureDao.getActiveItemForProperty(propId, leaf) } returns items[2]

        // Try to make Root a child of Leaf
        val result = repository.setParent(propId, root, leaf)
        
        assertEquals(RelationshipWriteResult.HierarchyCycle, result)
    }
}
