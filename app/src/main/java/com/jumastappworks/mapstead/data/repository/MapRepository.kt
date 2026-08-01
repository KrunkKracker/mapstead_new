package com.jumastappworks.mapstead.data.repository

import com.jumastappworks.mapstead.data.db.DatabaseTransactionRunner
import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.data.db.entities.LayerEntity
import com.jumastappworks.mapstead.data.db.entities.PlanEntity
import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import com.jumastappworks.mapstead.data.backup.AttachmentStorageService
import com.jumastappworks.mapstead.data.attachments.AttachmentDeleteState
import com.jumastappworks.mapstead.data.mapping.MapFeatureContextResolver
import com.jumastappworks.mapstead.data.mapping.SuggestedMapLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ActiveMapFeatureContext(
    val feature: MapFeatureEntity,
    val plan: PlanEntity,
    val layer: LayerEntity
)

data class StarterLayerRequest(
    val type: SuggestedMapLayer,
    val displayName: String,
    val description: String?
)

@Singleton
class MapRepository @Inject constructor(
    private val database: MapsteadDatabase,
    private val transactionRunner: DatabaseTransactionRunner,
    private val attachmentStorageService: AttachmentStorageService,
    private val mapFeatureContextResolver: MapFeatureContextResolver
) {
    // Plans
    fun getPlansForProperty(propertyId: UUID): Flow<List<PlanEntity>> {
        return database.planDao().getPlansForProperty(propertyId)
    }

    suspend fun getPlanById(id: UUID): PlanEntity? {
        return database.planDao().getPlanByIdOnce(id)
    }

    suspend fun insertPlan(plan: PlanEntity) {
        database.planDao().insertPlan(plan.copy(
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            revision = 1L
        ))
    }
    
    suspend fun updatePlan(plan: PlanEntity) {
        database.planDao().updatePlan(plan.copy(
            updatedAt = Instant.now(),
            revision = plan.revision + 1
        ))
    }

    suspend fun updatePlanCamera(id: UUID, lat: Double, lng: Double, zoom: Double, bearing: Double) {
        database.planDao().updateCamera(id, lat, lng, zoom, bearing, Instant.now())
    }

    suspend fun softDeletePlan(id: UUID) {
        database.planDao().softDeletePlan(id, Instant.now(), Instant.now())
    }

    // Transaction to create plan and default layer together
    suspend fun createPlanWithDefaultLayer(plan: PlanEntity): UUID {
        val defaultLayer = LayerEntity(
            id = UUID.randomUUID(),
            propertyId = plan.propertyId,
            planId = plan.id,
            name = "Property Features",
            category = "Structure",
            isVisible = true,
            isLocked = false,
            opacity = 1.0f,
            displayOrder = 0,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            revision = 1L
        )
        return createPlanWithDefaultLayer(plan, defaultLayer)
    }

    suspend fun createPlanWithDefaultLayer(plan: PlanEntity, defaultLayer: LayerEntity): UUID {
        return database.planDao().createPlanWithDefaultLayerTx(plan, defaultLayer)
    }

    // Layers
    fun getLayersForPlan(planId: UUID): Flow<List<LayerEntity>> {
        return database.layerDao().getLayersForPlan(planId)
    }

    suspend fun insertLayer(layer: LayerEntity) {
        database.layerDao().insertLayer(layer.copy(
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            revision = 1L
        ))
    }
    
    suspend fun updateLayer(layer: LayerEntity) {
        database.layerDao().updateLayer(layer.copy(
            updatedAt = Instant.now(),
            revision = layer.revision + 1
        ))
    }

    suspend fun softDeleteLayer(id: UUID) {
        database.layerDao().softDeleteLayer(id, Instant.now(), Instant.now())
    }

    suspend fun getLayerById(id: UUID): LayerEntity? {
        return database.layerDao().getLayerById(id)
    }

    suspend fun ensureStarterLayers(
        propertyId: UUID,
        planId: UUID,
        requests: List<StarterLayerRequest>,
        existingBindings: Map<SuggestedMapLayer, UUID> = emptyMap()
    ): Map<SuggestedMapLayer, UUID> = withContext(Dispatchers.IO) {
        transactionRunner.run {
            val existingLayers = database.layerDao().getAllLayersOnce().filter { it.planId == planId && it.deletedAt == null && it.propertyId == propertyId }
            var nextOrder = (existingLayers.maxOfOrNull { it.displayOrder } ?: -1) + 1
            val resultMapping = mutableMapOf<SuggestedMapLayer, UUID>()

            requests.forEach { request ->
                val category = when (request.type) {
                    SuggestedMapLayer.BUILDINGS_BOUNDARIES -> "Structure"
                    SuggestedMapLayer.UTILITIES -> "Utility"
                    SuggestedMapLayer.OUTDOOR_FEATURES -> "Landscape"
                    SuggestedMapLayer.SAFETY_EMERGENCY -> "Safety"
                }

                // 1. Try existing binding
                val boundId = existingBindings[request.type]
                val reusedLayer = if (boundId != null) {
                    existingLayers.find { it.id == boundId }
                } else null

                // 2. Secondary fallback: Match by normalized name (legacy support)
                val matchedLayer = reusedLayer ?: existingLayers.find { 
                    it.category == category && (
                        it.name.trim().equals(request.displayName.trim(), ignoreCase = true) ||
                        (request.type == SuggestedMapLayer.BUILDINGS_BOUNDARIES && it.name.contains("Buildings", ignoreCase = true)) ||
                        (request.type == SuggestedMapLayer.UTILITIES && it.name.contains("Utilities", ignoreCase = true)) ||
                        (request.type == SuggestedMapLayer.OUTDOOR_FEATURES && it.name.contains("Outdoor", ignoreCase = true)) ||
                        (request.type == SuggestedMapLayer.SAFETY_EMERGENCY && it.name.contains("Safety", ignoreCase = true))
                    )
                }
                
                if (matchedLayer == null) {
                    val newId = UUID.randomUUID()
                    database.layerDao().insertLayer(LayerEntity(
                        id = newId,
                        propertyId = propertyId,
                        planId = planId,
                        name = request.displayName,
                        category = category,
                        isVisible = true,
                        isLocked = false,
                        displayOrder = nextOrder++,
                        opacity = 1.0f,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                        revision = 1L
                    ))
                    resultMapping[request.type] = newId
                } else {
                    resultMapping[request.type] = matchedLayer.id
                }
            }
            resultMapping
        }
    }

    // Features
    fun getFeaturesForLayer(layerId: UUID): Flow<List<MapFeatureEntity>> {
        return database.mapFeatureDao().getFeaturesForLayer(layerId)
    }

    fun getFeaturesForPlan(planId: UUID): Flow<List<MapFeatureEntity>> {
        return database.mapFeatureDao().getFeaturesForPlan(planId)
    }

    fun getFeaturesForProperty(propertyId: UUID): Flow<List<MapFeatureEntity>> {
        return database.mapFeatureDao().getFeaturesForProperty(propertyId)
    }

    fun getFeaturesForItem(itemId: UUID): Flow<List<MapFeatureEntity>> {
        return database.mapFeatureDao().getFeaturesForItem(itemId)
    }

    suspend fun getFeatureById(id: UUID): MapFeatureEntity? {
        return database.mapFeatureDao().getFeatureById(id)
    }

    suspend fun getActiveFeatureContext(
        propertyId: UUID,
        planId: UUID,
        featureId: UUID
    ): ActiveMapFeatureContext? = withContext(Dispatchers.IO) {
        mapFeatureContextResolver.resolve(propertyId, planId, featureId)
    }

    suspend fun insertFeature(feature: MapFeatureEntity) {
        database.mapFeatureDao().insertFeature(feature.copy(
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            revision = 1L
        ))
    }
    
    suspend fun updateFeature(feature: MapFeatureEntity) {
        database.mapFeatureDao().updateFeature(feature.copy(
            updatedAt = Instant.now(),
            revision = feature.revision + 1
        ))
    }

    suspend fun saveFeatureWithOptionalItem(
        feature: MapFeatureEntity,
        itemToCreate: InfrastructureItemEntity?
    ) = withContext(Dispatchers.IO) {
        transactionRunner.run {
            val propertyId = feature.propertyId
            
            // 1. Plan must exist and belong to property
            val plan = database.planDao().getPlanByIdOnce(feature.planId)
            if (plan == null || plan.propertyId != propertyId || plan.deletedAt != null) {
                throw IllegalStateException("Invalid plan context")
            }

            // 2. Layer must exist and belong to property and plan
            val layer = database.layerDao().getLayerById(feature.layerId)
            if (layer == null || layer.propertyId != propertyId || layer.planId != feature.planId || layer.deletedAt != null) {
                throw IllegalStateException("Invalid layer context")
            }

            // 3. Linked System Item must belong to property
            feature.infrastructureItemId?.let { itemId ->
                if (itemToCreate?.id != itemId) {
                    val existingItem = database.infrastructureDao().getItemById(itemId)
                    if (existingItem == null || existingItem.propertyId != propertyId || existingItem.deletedAt != null) {
                        throw IllegalStateException("Invalid record ownership")
                    }
                }
            }

            // 4. New item being created must belong to property
            if (itemToCreate != null && itemToCreate.propertyId != propertyId) {
                throw IllegalStateException("Invalid draft ownership")
            }

            val existingFeature = database.mapFeatureDao().getFeatureById(feature.id)
            if (existingFeature != null) {
                if (existingFeature.propertyId != propertyId || existingFeature.planId != feature.planId) {
                    throw IllegalStateException("Mismatched feature context")
                }
                
                // Conflicting relink check
                if (existingFeature.infrastructureItemId != null && 
                    feature.infrastructureItemId != null && 
                    existingFeature.infrastructureItemId != feature.infrastructureItemId) {
                    throw IllegalStateException("Conflicting link during retry")
                }
            }

            // Idempotent retry check
            if (existingFeature != null && itemToCreate != null) {
                val existingItem = database.infrastructureDao().getItemById(itemToCreate.id)
                if (existingItem != null && existingFeature.infrastructureItemId == itemToCreate.id) {
                    return@run // Creation already succeeded
                }
            }

            // Execute writes
            if (itemToCreate != null) {
                database.infrastructureDao().upsertItem(itemToCreate)
            }
            
            val now = Instant.now()
            if (existingFeature != null) {
                database.mapFeatureDao().updateFeature(feature.copy(
                    updatedAt = now,
                    revision = existingFeature.revision + 1
                ))
            } else {
                database.mapFeatureDao().insertFeature(feature.copy(
                    createdAt = now,
                    updatedAt = now,
                    revision = 1L
                ))
            }
        }
    }

    suspend fun softDeleteFeatureWithAttachments(
        propertyId: UUID,
        planId: UUID,
        featureId: UUID
    ): AttachmentDeleteState = withContext(Dispatchers.IO) {
        try {
            val context = mapFeatureContextResolver.resolve(propertyId, planId, featureId)
                ?: return@withContext AttachmentDeleteState.Error(com.jumastappworks.mapstead.R.string.error_feature_not_found)

            val attachments = database.attachmentDao().getAttachmentsForMapFeatureOnce(propertyId, featureId)
            
            transactionRunner.run {
                val affected = database.mapFeatureDao().softDeletePropertyPlanFeature(propertyId, planId, featureId, Instant.now(), Instant.now())
                if (affected == 1) {
                    attachments.forEach { 
                        database.attachmentDao().softDeletePropertyAttachment(propertyId, it.id, Instant.now(), Instant.now())
                    }
                } else {
                    throw IllegalStateException("Feature deletion failed")
                }
            }

            var cleanupFailed = false
            attachments.forEach { 
                if (attachmentStorageService.deleteManagedFile(it.id).isFailure) {
                    cleanupFailed = true
                }
            }

            if (cleanupFailed) {
                AttachmentDeleteState.DeletedWithCleanupWarning(com.jumastappworks.mapstead.R.string.error_delete_cleanup_warning)
            } else {
                AttachmentDeleteState.Deleted
            }
        } catch (e: Exception) {
            AttachmentDeleteState.Error(com.jumastappworks.mapstead.R.string.error_delete_failed)
        }
    }
}
