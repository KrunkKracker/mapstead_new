package com.jumastappworks.mapstead.data.mapping

import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.data.repository.MapRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class FeatureNamingServiceTest {

    private val mapRepository = mockk<MapRepository>()
    private val service = FeatureNamingService(mapRepository)
    private val propertyId = UUID.randomUUID()

    @Test
    fun `generateUniqueName returns base name when no duplicates exist`() = runBlocking {
        every { mapRepository.getFeaturesForProperty(propertyId) } returns flowOf(emptyList())

        val name = service.generateUniqueName(propertyId, "Well")
        assertEquals("Well", name)
    }

    @Test
    fun `generateUniqueName returns numbered name when base name exists`() = runBlocking {
        val features = listOf(
            createFeature("Well")
        )
        every { mapRepository.getFeaturesForProperty(propertyId) } returns flowOf(features)

        val name = service.generateUniqueName(propertyId, "Well")
        assertEquals("Well 2", name)
    }

    @Test
    fun `generateUniqueName handles case insensitivity`() = runBlocking {
        val features = listOf(
            createFeature("well")
        )
        every { mapRepository.getFeaturesForProperty(propertyId) } returns flowOf(features)

        val name = service.generateUniqueName(propertyId, "Well")
        assertEquals("Well 2", name)
    }

    @Test
    fun `generateUniqueName finds gaps in numbering`() = runBlocking {
        val features = listOf(
            createFeature("Well"),
            createFeature("Well 3")
        )
        every { mapRepository.getFeaturesForProperty(propertyId) } returns flowOf(features)

        val name = service.generateUniqueName(propertyId, "Well")
        assertEquals("Well 2", name)
    }

    @Test
    fun `generateUniqueName ignores deleted features`() = runBlocking {
        val features = listOf(
            createFeature("Well", deleted = true)
        )
        every { mapRepository.getFeaturesForProperty(propertyId) } returns flowOf(features)

        val name = service.generateUniqueName(propertyId, "Well")
        assertEquals("Well", name)
    }

    private fun createFeature(label: String, deleted: Boolean = false): MapFeatureEntity {
        return MapFeatureEntity(
            id = UUID.randomUUID(),
            propertyId = propertyId,
            planId = UUID.randomUUID(),
            layerId = UUID.randomUUID(),
            geometryType = "POINT",
            geometryJson = "{}",
            coordinateSpace = "GEOGRAPHIC",
            styleJson = "{}",
            label = label,
            accuracySource = "Manual",
            deletedAt = if (deleted) java.time.Instant.now() else null
        )
    }
}
