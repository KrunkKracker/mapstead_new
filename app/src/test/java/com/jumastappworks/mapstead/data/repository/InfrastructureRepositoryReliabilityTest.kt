package com.jumastappworks.mapstead.data.repository

import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.db.dao.InfrastructureDao
import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class InfrastructureRepositoryReliabilityTest {

    private val db = mockk<MapsteadDatabase>(relaxed = true)
    private val dao = mockk<InfrastructureDao>(relaxed = true)
    private lateinit var repository: InfrastructureRepositoryImpl

    private val propertyId = UUID.randomUUID()
    private val itemId = UUID.randomUUID()

    @Before
    fun setup() {
        every { db.infrastructureDao() } returns dao
        repository = InfrastructureRepositoryImpl(db)
    }

    @Test
    fun `updateItemForProperty returns NotFound when row does not exist`() = runTest {
        val item = InfrastructureItemEntity(id = itemId, propertyId = propertyId, name = "Test", category = "C", status = "A")
        
        coEvery { dao.getActiveItemForProperty(propertyId, itemId) } returns null

        val result = repository.updateItemForProperty(propertyId, item)

        assertTrue("Result should be NotFound", result is InfrastructureWriteResult.NotFound)
    }

    @Test
    fun `softDeleteItemForProperty returns Success only when exactly one row affected`() = runTest {
        coEvery { dao.softDeletePropertyItem(propertyId, itemId, any(), any()) } returns 1
        
        val result = repository.softDeleteItemForProperty(propertyId, itemId)
        assertTrue("Result should be Success", result is InfrastructureWriteResult.Success)

        coEvery { dao.softDeletePropertyItem(propertyId, itemId, any(), any()) } returns 0
        val result2 = repository.softDeleteItemForProperty(propertyId, itemId)
        assertTrue("Result should be NotFound for 0 affected rows", result2 is InfrastructureWriteResult.NotFound)
    }
}
