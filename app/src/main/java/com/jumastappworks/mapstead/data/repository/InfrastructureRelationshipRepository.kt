package com.jumastappworks.mapstead.data.repository

import com.jumastappworks.mapstead.data.db.MapsteadDatabase
import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import com.jumastappworks.mapstead.data.db.entities.ItemRelationshipEntity
import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.data.relationships.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InfrastructureRelationshipRepository @Inject constructor(
    private val database: MapsteadDatabase
) {
    private val relationshipDao = database.itemRelationshipDao()
    private val infrastructureDao = database.infrastructureDao()
    private val mapFeatureDao = database.mapFeatureDao()

    fun observeRelationshipsForProperty(propertyId: UUID): Flow<List<ItemRelationshipEntity>> =
        relationshipDao.getRelationshipsForProperty(propertyId)

    fun observeRelationshipsForItem(propertyId: UUID, itemId: UUID): Flow<List<ItemRelationshipUiModel>> {
        return relationshipDao.getRelationshipsForItem(propertyId, itemId).map { entities ->
            entities.mapNotNull { entity ->
                val isOutgoing = entity.sourceItemId == itemId
                val relatedId = if (isOutgoing) entity.targetItemId else entity.sourceItemId
                
                // Validate related item exists and belongs to the same property
                val relatedItem = infrastructureDao.getActiveItemForProperty(propertyId, relatedId) 
                    ?: return@mapNotNull null
                
                val type = ItemRelationshipType.fromString(entity.relationshipType)
                val features = mapFeatureDao.getFeaturesForItemOnce(propertyId, relatedId)

                ItemRelationshipUiModel(
                    relationshipId = entity.id,
                    currentItemId = itemId,
                    relatedItemId = relatedId,
                    relatedItemName = relatedItem.name,
                    relatedItemCategory = relatedItem.category,
                    relatedItemSubtype = relatedItem.subtype,
                    canonicalType = type,
                    displayLabel = getDirectionalLabel(type, isOutgoing),
                    description = entity.description,
                    direction = if (type.isSymmetric) RelationshipDirection.SYMMETRIC 
                               else if (isOutgoing) RelationshipDirection.OUTGOING 
                               else RelationshipDirection.INCOMING,
                    relatedItemStatus = relatedItem.status,
                    hasMappedFeature = features.isNotEmpty()
                )
            }
        }
    }

    private fun getDirectionalLabel(type: ItemRelationshipType, isOutgoing: Boolean): String {
        return when (type) {
            ItemRelationshipType.FEEDS -> if (isOutgoing) "Feeds" else "Fed by"
            ItemRelationshipType.CONTROLS -> if (isOutgoing) "Controls" else "Controlled by"
            ItemRelationshipType.PROTECTS -> if (isOutgoing) "Protects" else "Protected by"
            ItemRelationshipType.DRAINS_TO -> if (isOutgoing) "Drains to" else "Receives drainage from"
            ItemRelationshipType.SERVES -> if (isOutgoing) "Serves" else "Served by"
            ItemRelationshipType.DEPENDS_ON -> if (isOutgoing) "Depends on" else "Required by"
            ItemRelationshipType.CONNECTED_TO -> "Connected to"
            ItemRelationshipType.OTHER -> "Related to"
        }
    }

    suspend fun getRelationshipForProperty(propertyId: UUID, relationshipId: UUID): ItemRelationshipEntity? {
        return relationshipDao.getRelationshipById(propertyId, relationshipId)
    }

    suspend fun createRelationship(
        propertyId: UUID,
        sourceId: UUID,
        targetId: UUID,
        type: ItemRelationshipType,
        description: String?
    ): RelationshipWriteResult = withContext(Dispatchers.IO) {
        try {
            if (sourceId == targetId) return@withContext RelationshipWriteResult.SelfRelationship
            
            val source = infrastructureDao.getActiveItemForProperty(propertyId, sourceId) ?: return@withContext RelationshipWriteResult.InvalidSource
            val target = infrastructureDao.getActiveItemForProperty(propertyId, targetId) ?: return@withContext RelationshipWriteResult.InvalidTarget
            
            if (type == ItemRelationshipType.OTHER && description.isNullOrBlank()) {
                return@withContext RelationshipWriteResult.InvalidDescription
            }

            // Duplicate check
            if (type.isSymmetric) {
                if (relationshipDao.findSymmetricDuplicate(propertyId, sourceId, targetId) != null) {
                    return@withContext RelationshipWriteResult.Duplicate
                }
            } else {
                if (relationshipDao.findDirectionalDuplicate(propertyId, sourceId, targetId, type.canonicalName) != null) {
                    return@withContext RelationshipWriteResult.Duplicate
                }
            }

            // Dependency cycle check
            if (type == ItemRelationshipType.DEPENDS_ON) {
                if (wouldCreateDependencyCycle(propertyId, sourceId, targetId)) {
                    return@withContext RelationshipWriteResult.DependencyCycle
                }
            }

            val id = UUID.randomUUID()
            val now = Instant.now()
            val entity = ItemRelationshipEntity(
                id = id,
                propertyId = propertyId,
                sourceItemId = sourceId,
                targetItemId = targetId,
                relationshipType = type.canonicalName,
                description = description,
                createdAt = now,
                updatedAt = now,
                revision = 1L
            )
            
            relationshipDao.insertRelationship(entity)
            RelationshipWriteResult.Success(id)
        } catch (e: Exception) {
            RelationshipWriteResult.Error(e.message)
        }
    }

    suspend fun updateRelationship(
        propertyId: UUID,
        relationshipId: UUID,
        sourceId: UUID,
        targetId: UUID,
        type: ItemRelationshipType,
        description: String?
    ): RelationshipWriteResult = withContext(Dispatchers.IO) {
        try {
            if (sourceId == targetId) return@withContext RelationshipWriteResult.SelfRelationship
            
            val existing = relationshipDao.getRelationshipById(propertyId, relationshipId)
                ?: return@withContext RelationshipWriteResult.NotFound
            
            val source = infrastructureDao.getActiveItemForProperty(propertyId, sourceId) ?: return@withContext RelationshipWriteResult.InvalidSource
            val target = infrastructureDao.getActiveItemForProperty(propertyId, targetId) ?: return@withContext RelationshipWriteResult.InvalidTarget

            if (type == ItemRelationshipType.OTHER && description.isNullOrBlank()) {
                return@withContext RelationshipWriteResult.InvalidDescription
            }

            // Duplicate check (excluding current relationship)
            if (type.isSymmetric) {
                val duplicate = relationshipDao.findSymmetricDuplicate(propertyId, sourceId, targetId)
                if (duplicate != null && duplicate.id != relationshipId) {
                    return@withContext RelationshipWriteResult.Duplicate
                }
            } else {
                val duplicate = relationshipDao.findDirectionalDuplicate(propertyId, sourceId, targetId, type.canonicalName)
                if (duplicate != null && duplicate.id != relationshipId) {
                    return@withContext RelationshipWriteResult.Duplicate
                }
            }

            // Dependency cycle check
            if (type == ItemRelationshipType.DEPENDS_ON) {
                if (wouldCreateDependencyCycle(propertyId, sourceId, targetId, relationshipId)) {
                    return@withContext RelationshipWriteResult.DependencyCycle
                }
            }

            val updated = existing.copy(
                sourceItemId = sourceId,
                targetItemId = targetId,
                relationshipType = type.canonicalName,
                description = description,
                updatedAt = Instant.now(),
                revision = existing.revision + 1
            )
            
            val rows = relationshipDao.updateRelationship(updated)
            if (rows == 1) {
                RelationshipWriteResult.Success(relationshipId)
            } else {
                RelationshipWriteResult.NotFound
            }
        } catch (e: Exception) {
            RelationshipWriteResult.Error(e.message)
        }
    }

    private suspend fun wouldCreateDependencyCycle(
        propertyId: UUID, 
        sourceId: UUID, 
        targetId: UUID,
        excludeRelationshipId: UUID? = null
    ): Boolean {
        val allDeps = relationshipDao.getActiveDependencies(propertyId)
            .filter { it.id != excludeRelationshipId }
            
        val adj = mutableMapOf<UUID, MutableList<UUID>>()
        allDeps.forEach {
            adj.getOrPut(it.sourceItemId) { mutableListOf() }.add(it.targetItemId)
        }
        // Add the proposed edge
        adj.getOrPut(sourceId) { mutableListOf() }.add(targetId)
        
        return hasCycle(adj)
    }

    private fun hasCycle(adj: Map<UUID, List<UUID>>): Boolean {
        val visited = mutableSetOf<UUID>()
        val recStack = mutableSetOf<UUID>()
        
        for (node in adj.keys) {
            if (dfs(node, adj, visited, recStack)) return true
        }
        return false
    }

    private fun dfs(node: UUID, adj: Map<UUID, List<UUID>>, visited: MutableSet<UUID>, recStack: MutableSet<UUID>): Boolean {
        if (recStack.contains(node)) return true
        if (visited.contains(node)) return false
        
        visited.add(node)
        recStack.add(node)
        
        adj[node]?.forEach { neighbor ->
            if (dfs(neighbor, adj, visited, recStack)) return true
        }
        
        recStack.remove(node)
        return false
    }

    suspend fun softDeleteRelationship(propertyId: UUID, relationshipId: UUID): RelationshipWriteResult = withContext(Dispatchers.IO) {
        try {
            val rows = relationshipDao.softDeleteRelationship(propertyId, relationshipId)
            if (rows == 1) {
                RelationshipWriteResult.Success(relationshipId)
            } else {
                RelationshipWriteResult.NotFound
            }
        } catch (e: Exception) {
            RelationshipWriteResult.Error(e.message)
        }
    }

    // Hierarchy
    suspend fun setParent(propertyId: UUID, itemId: UUID, parentId: UUID?): RelationshipWriteResult = withContext(Dispatchers.IO) {
        try {
            if (itemId == parentId) return@withContext RelationshipWriteResult.SelfRelationship
            
            val item = infrastructureDao.getActiveItemForProperty(propertyId, itemId) ?: return@withContext RelationshipWriteResult.NotFound
            
            if (parentId != null) {
                val parent = infrastructureDao.getActiveItemForProperty(propertyId, parentId) ?: return@withContext RelationshipWriteResult.InvalidTarget
                
                if (wouldCreateHierarchyCycle(propertyId, itemId, parentId)) {
                    return@withContext RelationshipWriteResult.HierarchyCycle
                }
            }
            
            val rows = infrastructureDao.updateParent(propertyId, itemId, parentId)
            if (rows == 1) {
                RelationshipWriteResult.Success(itemId)
            } else {
                RelationshipWriteResult.NotFound
            }
        } catch (e: Exception) {
            RelationshipWriteResult.Error(e.message)
        }
    }

    private suspend fun wouldCreateHierarchyCycle(propertyId: UUID, itemId: UUID, parentId: UUID): Boolean {
        val allItems = infrastructureDao.getAllItemsOnce().filter { it.propertyId == propertyId && it.deletedAt == null }
        val parentMap = allItems.associate { it.id to it.parentItemId }
        
        var current: UUID? = parentId
        while (current != null) {
            if (current == itemId) return true
            current = parentMap[current]
        }
        return false
    }

    fun getChildrenForItem(propertyId: UUID, itemId: UUID): Flow<List<InfrastructureItemEntity>> =
        infrastructureDao.getChildrenForItem(propertyId, itemId)

    suspend fun getMappedFeatureForItem(propertyId: UUID, itemId: UUID): MapFeatureEntity? = withContext(Dispatchers.IO) {
        mapFeatureDao.getFeaturesForItemOnce(propertyId, itemId).firstOrNull()
    }
}
