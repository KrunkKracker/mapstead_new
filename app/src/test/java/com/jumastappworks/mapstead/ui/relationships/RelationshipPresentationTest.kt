package com.jumastappworks.mapstead.ui.relationships

import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.relationships.RelationshipDirection
import com.jumastappworks.mapstead.data.repository.InfrastructureRelationshipRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.*

class RelationshipPresentationTest {

    private val db = mockk<MapsteadDatabase>(relaxed = true)
    private lateinit var repository: InfrastructureRelationshipRepository

    @Before
    fun setup() {
        repository = InfrastructureRelationshipRepository(db)
    }

    @Test
    fun testFeedsDirectionalLabels() = runTest {
        val itemId = UUID.randomUUID()
        val relatedId = UUID.randomUUID()
        val propId = UUID.randomUUID()
        
        val outgoingEntity = com.jumastappworks.mapstead.data.db.entities.ItemRelationshipEntity(
            id = UUID.randomUUID(),
            propertyId = propId,
            sourceItemId = itemId,
            targetItemId = relatedId,
            relationshipType = "FEEDS"
        )
        
        every { db.itemRelationshipDao().getRelationshipsForItem(propId, itemId) } returns flowOf(listOf(outgoingEntity))
        coEvery { db.infrastructureDao().getActiveItemForProperty(propId, relatedId) } returns com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity(id = relatedId, propertyId = propId, name = "Target", category = "C", status = "A")

        val uiModels = repository.observeRelationshipsForItem(propId, itemId).first()
        val model = uiModels[0]
        
        assertEquals("Feeds", model.displayLabel)
        assertEquals(RelationshipDirection.OUTGOING, model.direction)
        
        // Test Incoming
        val incomingEntity = com.jumastappworks.mapstead.data.db.entities.ItemRelationshipEntity(
            id = UUID.randomUUID(),
            propertyId = propId,
            sourceItemId = relatedId,
            targetItemId = itemId,
            relationshipType = "FEEDS"
        )
        
        every { db.itemRelationshipDao().getRelationshipsForItem(propId, itemId) } returns flowOf(listOf(incomingEntity))
        coEvery { db.infrastructureDao().getActiveItemForProperty(propId, relatedId) } returns com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity(id = relatedId, propertyId = propId, name = "Source", category = "C", status = "A")
        
        val uiModels2 = repository.observeRelationshipsForItem(propId, itemId).first()
        assertEquals("Fed by", uiModels2[0].displayLabel)
        assertEquals(RelationshipDirection.INCOMING, uiModels2[0].direction)
    }

    @Test
    fun testConnectedToSymmetricLabel() = runTest {
        val itemId = UUID.randomUUID()
        val relatedId = UUID.randomUUID()
        val propId = UUID.randomUUID()
        
        val entity = com.jumastappworks.mapstead.data.db.entities.ItemRelationshipEntity(
            id = UUID.randomUUID(),
            propertyId = propId,
            sourceItemId = itemId,
            targetItemId = relatedId,
            relationshipType = "CONNECTED_TO"
        )
        
        every { db.itemRelationshipDao().getRelationshipsForItem(propId, itemId) } returns flowOf(listOf(entity))
        coEvery { db.infrastructureDao().getActiveItemForProperty(propId, relatedId) } returns com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity(id = relatedId, propertyId = propId, name = "Target", category = "C", status = "A")

        val model = repository.observeRelationshipsForItem(propId, itemId).first()[0]
        
        assertEquals("Connected to", model.displayLabel)
        assertEquals(RelationshipDirection.SYMMETRIC, model.direction)
    }
}
