package com.jumastappworks.mapstead.data.repository

import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.db.dao.InfrastructureDao
import com.jumastappworks.mapstead.data.db.dao.ItemRelationshipDao
import com.jumastappworks.mapstead.data.db.dao.MapFeatureDao
import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import com.jumastappworks.mapstead.data.relationships.ItemRelationshipType
import com.jumastappworks.mapstead.data.relationships.RelationshipWriteResult
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.*

class InfrastructureRelationshipRepositoryTest {

    private val db = mockk<MapsteadDatabase>(relaxed = true)
    private val relationshipDao = mockk<ItemRelationshipDao>(relaxed = true)
    private val infrastructureDao = mockk<InfrastructureDao>(relaxed = true)
    private val mapFeatureDao = mockk<MapFeatureDao>(relaxed = true)
    
    private lateinit var repository: InfrastructureRelationshipRepository

    @Before
    fun setup() {
        every { db.itemRelationshipDao() } returns relationshipDao
        every { db.infrastructureDao() } returns infrastructureDao
        every { db.mapFeatureDao() } returns mapFeatureDao
        
        // Ensure duplicate checks return null by default for creation tests
        coEvery { relationshipDao.findDirectionalDuplicate(any(), any(), any(), any()) } returns null
        coEvery { relationshipDao.findSymmetricDuplicate(any(), any(), any()) } returns null
        
        repository = InfrastructureRelationshipRepository(db)
    }

    @Test
    fun testSetValidParent() = runTest {
        val propId = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        val parentId = UUID.randomUUID()
        
        coEvery { infrastructureDao.getActiveItemForProperty(propId, itemId) } returns InfrastructureItemEntity(id = itemId, propertyId = propId, name = "Child", category = "C", status = "A")
        coEvery { infrastructureDao.getActiveItemForProperty(propId, parentId) } returns InfrastructureItemEntity(id = parentId, propertyId = propId, name = "Parent", category = "C", status = "A")
        coEvery { infrastructureDao.getAllItemsOnce() } returns emptyList() // No cycles
        coEvery { infrastructureDao.updateParent(propId, itemId, parentId, any()) } returns 1

        val result = repository.setParent(propId, itemId, parentId)
        
        assertTrue(result is RelationshipWriteResult.Success)
        coVerify { infrastructureDao.updateParent(propId, itemId, parentId, any()) }
    }

    @Test
    fun testRejectHierarchyCycle() = runTest {
        val propId = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        val parentId = UUID.randomUUID()
        
        coEvery { infrastructureDao.getActiveItemForProperty(propId, itemId) } returns InfrastructureItemEntity(id = itemId, propertyId = propId, name = "Item", category = "C", status = "A")
        coEvery { infrastructureDao.getActiveItemForProperty(propId, parentId) } returns InfrastructureItemEntity(id = parentId, propertyId = propId, name = "Parent", category = "C", status = "A")
        
        // Item is already a parent of the proposed parent
        coEvery { infrastructureDao.getAllItemsOnce() } returns listOf(
            InfrastructureItemEntity(id = parentId, propertyId = propId, name = "Parent", category = "C", parentItemId = itemId, status = "A")
        )

        val result = repository.setParent(propId, itemId, parentId)
        
        assertEquals(RelationshipWriteResult.HierarchyCycle, result)
        coVerify(exactly = 0) { infrastructureDao.updateParent(any(), any(), any(), any()) }
    }

    @Test
    fun testCreateValidRelationship() = runTest {
        val propId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        
        coEvery { infrastructureDao.getActiveItemForProperty(propId, sourceId) } returns InfrastructureItemEntity(id = sourceId, propertyId = propId, name = "S", category = "C", status = "A")
        coEvery { infrastructureDao.getActiveItemForProperty(propId, targetId) } returns InfrastructureItemEntity(id = targetId, propertyId = propId, name = "T", category = "C", status = "A")

        val result = repository.createRelationship(propId, sourceId, targetId, ItemRelationshipType.FEEDS, null)
        
        assertTrue(result is RelationshipWriteResult.Success)
        coVerify { relationshipDao.insertRelationship(any()) }
    }

    @Test
    fun testRejectCrossPropertyRelationshipEndpoints() = runTest {
        val propId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        
        // Target belongs to another property, so getActiveItemForProperty(propId, targetId) returns null
        coEvery { infrastructureDao.getActiveItemForProperty(propId, sourceId) } returns InfrastructureItemEntity(id = sourceId, propertyId = propId, name = "S", category = "C", status = "A")
        coEvery { infrastructureDao.getActiveItemForProperty(propId, targetId) } returns null

        val result = repository.createRelationship(propId, sourceId, targetId, ItemRelationshipType.FEEDS, null)
        
        assertEquals(RelationshipWriteResult.InvalidTarget, result)
    }

    @Test
    fun testRejectDependsOnCycle() = runTest {
        val propId = UUID.randomUUID()
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        
        coEvery { infrastructureDao.getActiveItemForProperty(propId, a) } returns InfrastructureItemEntity(id = a, propertyId = propId, name = "A", category = "C", status = "A")
        coEvery { infrastructureDao.getActiveItemForProperty(propId, b) } returns InfrastructureItemEntity(id = b, propertyId = propId, name = "B", category = "C", status = "A")
        
        // B already depends on A
        coEvery { relationshipDao.getActiveDependencies(propId) } returns listOf(
            com.jumastappworks.mapstead.data.db.entities.ItemRelationshipEntity(
                id = UUID.randomUUID(), propertyId = propId, sourceItemId = b, targetItemId = a, relationshipType = "DEPENDS_ON"
            )
        )

        // Try to make A depend on B
        val result = repository.createRelationship(propId, a, b, ItemRelationshipType.DEPENDS_ON, null)
        
        assertEquals(RelationshipWriteResult.DependencyCycle, result)
    }

    @Test
    fun `observeRelationshipsForItem omits invalid related endpoints`() = runTest {
        val propId = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        val invalidRelatedId = UUID.randomUUID()
        
        val relationship = com.jumastappworks.mapstead.data.db.entities.ItemRelationshipEntity(
            id = UUID.randomUUID(),
            propertyId = propId,
            sourceItemId = itemId,
            targetItemId = invalidRelatedId,
            relationshipType = "CONNECTED_TO"
        )
        
        every { relationshipDao.getRelationshipsForItem(propId, itemId) } returns flowOf(listOf(relationship))
        // Target belongs to another property or is deleted, so returns null
        coEvery { infrastructureDao.getActiveItemForProperty(propId, invalidRelatedId) } returns null

        val results = repository.observeRelationshipsForItem(propId, itemId).first()
        
        assertTrue("Results should be empty because target was invalid", results.isEmpty())
    }
}
