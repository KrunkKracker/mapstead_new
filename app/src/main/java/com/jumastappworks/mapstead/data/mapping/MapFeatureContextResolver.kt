package com.jumastappworks.mapstead.data.mapping

import com.jumastappworks.mapstead.data.db.dao.LayerDao
import com.jumastappworks.mapstead.data.db.dao.MapFeatureDao
import com.jumastappworks.mapstead.data.db.dao.PlanDao
import com.jumastappworks.mapstead.data.db.dao.PropertyDao
import com.jumastappworks.mapstead.data.repository.ActiveMapFeatureContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MapFeatureContextResolver @Inject constructor(
    private val propertyDao: PropertyDao,
    private val planDao: PlanDao,
    private val layerDao: LayerDao,
    private val mapFeatureDao: MapFeatureDao
) {
    suspend fun resolve(
        propertyId: UUID,
        planId: UUID,
        featureId: UUID
    ): ActiveMapFeatureContext? {
        val feature = mapFeatureDao.getFeatureById(featureId)
            ?: return null
        if (feature.propertyId != propertyId || feature.planId != planId || feature.deletedAt != null) return null

        val property = propertyDao.getPropertyById(propertyId)
            ?: return null
        if (property.deletedAt != null) return null

        val plan = planDao.getPlanByIdOnce(planId)
            ?: return null
        if (plan.propertyId != propertyId || plan.deletedAt != null) return null

        val layer = layerDao.getLayerById(feature.layerId)
            ?: return null
        if (layer.propertyId != propertyId || layer.planId != planId || layer.deletedAt != null) return null

        val validTypes = setOf("POINT", "LINESTRING", "POLYGON")
        if (!validTypes.contains(feature.geometryType.uppercase())) return null

        return ActiveMapFeatureContext(feature, plan, layer)
    }

    suspend fun resolveFromFeature(
        propertyId: UUID,
        featureId: UUID
    ): ActiveMapFeatureContext? {
        val feature = mapFeatureDao.getFeatureById(featureId)
            ?: return null
        if (feature.propertyId != propertyId || feature.deletedAt != null) return null
        
        return resolve(propertyId, feature.planId, featureId)
    }
}
