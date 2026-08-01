package com.jumastappworks.mapstead.data.mapping

import com.jumastappworks.mapstead.data.db.dao.LayerDao
import com.jumastappworks.mapstead.data.db.dao.MapFeatureDao
import com.jumastappworks.mapstead.data.db.dao.PlanDao
import com.jumastappworks.mapstead.data.db.dao.PropertyDao
import com.jumastappworks.mapstead.data.db.entities.LayerEntity
import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.data.db.entities.PlanEntity
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.*

class MapFeatureContextResolverTest {

    private val propertyDao = mockk<PropertyDao>()
    private val planDao = mockk<PlanDao>()
    private val layerDao = mockk<LayerDao>()
    private val mapFeatureDao = mockk<MapFeatureDao>()

    private lateinit var resolver: MapFeatureContextResolver

    @Before
    fun setup() {
        resolver = MapFeatureContextResolver(propertyDao, planDao, layerDao, mapFeatureDao)
    }

    @Test
    fun `resolve returns context for valid feature`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val layerId = UUID.randomUUID()
        val featureId = UUID.randomUUID()

        val property = PropertyEntity(id = propId, name = "P", propertyType = "Home")
        val plan = PlanEntity(id = planId, propertyId = propId, name = "PL", planType = "M", backgroundType = "N")
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = planId, name = "L", category = "C")
        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = layerId, geometryType = "POINT", geometryJson = "{}", coordinateSpace = "LOCAL", styleJson = "{}", accuracySource = "M")

        coEvery { mapFeatureDao.getFeatureById(featureId) } returns feature
        coEvery { propertyDao.getPropertyById(propId) } returns property
        coEvery { planDao.getPlanByIdOnce(planId) } returns plan
        coEvery { layerDao.getLayerById(layerId) } returns layer

        val context = resolver.resolve(propId, planId, featureId)
        assertNotNull(context)
        assertEquals(feature, context?.feature)
    }

    @Test
    fun `resolve returns null for cross-plan feature`() = runTest {
        val propId = UUID.randomUUID()
        val planId1 = UUID.randomUUID()
        val planId2 = UUID.randomUUID()
        val featureId = UUID.randomUUID()

        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId2, layerId = UUID.randomUUID(), geometryType = "POINT", geometryJson = "{}", coordinateSpace = "LOCAL", styleJson = "{}", accuracySource = "M")

        coEvery { mapFeatureDao.getFeatureById(featureId) } returns feature

        val context = resolver.resolve(propId, planId1, featureId)
        assertNull(context)
    }

    @Test
    fun `resolve returns null for layer-plan mismatch`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val otherPlanId = UUID.randomUUID()
        val featureId = UUID.randomUUID()
        val layerId = UUID.randomUUID()

        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = layerId, geometryType = "POINT", geometryJson = "{}", coordinateSpace = "LOCAL", styleJson = "{}", accuracySource = "M")
        val layer = LayerEntity(id = layerId, propertyId = propId, planId = otherPlanId, name = "L", category = "C")

        coEvery { mapFeatureDao.getFeatureById(featureId) } returns feature
        coEvery { propertyDao.getPropertyById(propId) } returns PropertyEntity(id = propId, name = "P", propertyType = "H")
        coEvery { planDao.getPlanByIdOnce(planId) } returns PlanEntity(id = planId, propertyId = propId, name = "PL", planType = "M", backgroundType = "N")
        coEvery { layerDao.getLayerById(layerId) } returns layer

        val context = resolver.resolve(propId, planId, featureId)
        assertNull(context)
    }

    @Test
    fun `resolveFromFeature returns context for valid feature`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val featureId = UUID.randomUUID()

        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = UUID.randomUUID(), geometryType = "POINT", geometryJson = "{}", coordinateSpace = "LOCAL", styleJson = "{}", accuracySource = "M")
        
        coEvery { mapFeatureDao.getFeatureById(featureId) } returns feature
        coEvery { propertyDao.getPropertyById(propId) } returns PropertyEntity(id = propId, name = "P", propertyType = "H")
        coEvery { planDao.getPlanByIdOnce(planId) } returns PlanEntity(id = planId, propertyId = propId, name = "PL", planType = "M", backgroundType = "N")
        coEvery { layerDao.getLayerById(any()) } returns LayerEntity(id = feature.layerId, propertyId = propId, planId = planId, name = "L", category = "C")

        val context = resolver.resolveFromFeature(propId, featureId)
        assertNotNull(context)
        assertEquals(featureId, context?.feature?.id)
    }

    @Test
    fun `resolveFromFeature returns null for mismatched property`() = runTest {
        val propId1 = UUID.randomUUID()
        val propId2 = UUID.randomUUID()
        val featureId = UUID.randomUUID()

        val feature = MapFeatureEntity(id = featureId, propertyId = propId2, planId = UUID.randomUUID(), layerId = UUID.randomUUID(), geometryType = "POINT", geometryJson = "{}", coordinateSpace = "LOCAL", styleJson = "{}", accuracySource = "M")
        
        coEvery { mapFeatureDao.getFeatureById(featureId) } returns feature

        val context = resolver.resolveFromFeature(propId1, featureId)
        assertNull(context)
    }

    @Test
    fun `resolve returns null for deleted property`() = runTest {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val featureId = UUID.randomUUID()

        val feature = MapFeatureEntity(id = featureId, propertyId = propId, planId = planId, layerId = UUID.randomUUID(), geometryType = "POINT", geometryJson = "{}", coordinateSpace = "LOCAL", styleJson = "{}", accuracySource = "M")
        val property = PropertyEntity(id = propId, name = "P", propertyType = "Home", deletedAt = java.time.Instant.now())

        coEvery { mapFeatureDao.getFeatureById(featureId) } returns feature
        coEvery { propertyDao.getPropertyById(propId) } returns property

        val context = resolver.resolve(propId, planId, featureId)
        assertNull(context)
    }
}
