package com.jumastappworks.mapstead.ui.relationships

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import com.jumastappworks.mapstead.data.db.entities.ItemRelationshipEntity
import com.jumastappworks.mapstead.data.relationships.*
import com.jumastappworks.mapstead.data.repository.InfrastructureRelationshipRepository
import com.jumastappworks.mapstead.data.repository.InfrastructureRepository
import com.jumastappworks.mapstead.data.repository.PropertyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class RelationshipViewFilter(val labelRes: Int) {
    Hierarchy(R.string.filter_hierarchy),
    Connections(R.string.filter_connections),
    Unconnected(R.string.filter_unconnected)
}

data class PropertyRelationshipUiModel(
    val relationshipId: UUID,
    val sourceItemId: UUID,
    val sourceItemName: String,
    val targetItemId: UUID,
    val targetItemName: String,
    val canonicalType: ItemRelationshipType,
    val displayLabel: String,
    val description: String?
)

data class InfrastructureRelationshipsUiState(
    val propertyId: UUID,
    val propertyName: String = "",
    val flattenedHierarchy: List<HierarchyNode> = emptyList(),
    val connections: List<PropertyRelationshipUiModel> = emptyList(),
    val unconnectedItems: List<InfrastructureItemEntity> = emptyList(),
    val currentFilter: RelationshipViewFilter = RelationshipViewFilter.Hierarchy,
    val counts: RelationshipCounts = RelationshipCounts(),
    val isLoading: Boolean = true,
    val error: String? = null
)

data class HierarchyNode(
    val item: InfrastructureItemEntity,
    val children: List<HierarchyNode> = emptyList(),
    val depth: Int = 0
)

data class RelationshipCounts(
    val totalItems: Int = 0,
    val hierarchyLinks: Int = 0,
    val systemRelationships: Int = 0,
    val unconnected: Int = 0
)

@HiltViewModel
class InfrastructureRelationshipsViewModel @Inject constructor(
    private val relationshipRepository: InfrastructureRelationshipRepository,
    private val infrastructureRepository: InfrastructureRepository,
    private val propertyRepository: PropertyRepository
) : ViewModel() {

    private val _propertyId = MutableStateFlow<UUID?>(null)
    private val _currentFilter = MutableStateFlow(RelationshipViewFilter.Hierarchy)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _items = _propertyId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else infrastructureRepository.getItemsForProperty(id)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _relationships = _propertyId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else relationshipRepository.observeRelationshipsForProperty(id)
    }

    val uiState: StateFlow<InfrastructureRelationshipsUiState?> = combine(
        _propertyId,
        _items,
        _relationships,
        _currentFilter
    ) { id, items, rels, filter ->
        if (id == null) return@combine null

        val property = propertyRepository.getPropertyById(id)
        
        val activeItems = items.filter { it.deletedAt == null }
        val activeRels = rels.filter { it.deletedAt == null }
        
        val hierarchyLinks = activeItems.count { it.parentItemId != null }
        
        val validRels = activeRels.filter { rel ->
            activeItems.any { it.id == rel.sourceItemId } && activeItems.any { it.id == rel.targetItemId }
        }

        val hierarchyNodes = if (filter == RelationshipViewFilter.Hierarchy) buildHierarchy(activeItems) else emptyList()
        val flattenedHierarchy = if (filter == RelationshipViewFilter.Hierarchy) flattenHierarchy(hierarchyNodes) else emptyList()
        val connectionUiModels = if (filter == RelationshipViewFilter.Connections) buildPropertyRelationshipUiModels(activeItems, validRels) else emptyList()
        val unconnected = buildUnconnected(activeItems, validRels)

        InfrastructureRelationshipsUiState(
            propertyId = id,
            propertyName = property?.name ?: "",
            flattenedHierarchy = flattenedHierarchy,
            connections = connectionUiModels,
            unconnectedItems = unconnected,
            currentFilter = filter,
            counts = RelationshipCounts(
                totalItems = activeItems.size,
                hierarchyLinks = hierarchyLinks,
                systemRelationships = validRels.size,
                unconnected = unconnected.size
            ),
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun buildHierarchy(items: List<InfrastructureItemEntity>): List<HierarchyNode> {
        val rootItems = items.filter { it.parentItemId == null }
        val childMap = items.groupBy { it.parentItemId }
        
        return rootItems.map { buildHierarchyNode(it, childMap, 0, mutableSetOf()) }
    }

    private fun buildHierarchyNode(item: InfrastructureItemEntity, childMap: Map<UUID?, List<InfrastructureItemEntity>>, depth: Int, visited: MutableSet<UUID>): HierarchyNode {
        if (visited.contains(item.id) || depth > 64) {
            return HierarchyNode(item, emptyList(), depth)
        }
        val newVisited = visited.toMutableSet()
        newVisited.add(item.id)
        
        val children = childMap[item.id]?.map { buildHierarchyNode(it, childMap, depth + 1, newVisited) } ?: emptyList()
        return HierarchyNode(item, children, depth)
    }

    private fun flattenHierarchy(nodes: List<HierarchyNode>): List<HierarchyNode> {
        val result = mutableListOf<HierarchyNode>()
        nodes.forEach { node ->
            result.add(node)
            result.addAll(flattenHierarchy(node.children))
        }
        return result
    }

    private fun buildPropertyRelationshipUiModels(items: List<InfrastructureItemEntity>, rels: List<ItemRelationshipEntity>): List<PropertyRelationshipUiModel> {
        val itemMap = items.associateBy { it.id }
        return rels.mapNotNull { rel ->
            val source = itemMap[rel.sourceItemId] ?: return@mapNotNull null
            val target = itemMap[rel.targetItemId] ?: return@mapNotNull null
            val type = ItemRelationshipType.fromString(rel.relationshipType)
            
            PropertyRelationshipUiModel(
                relationshipId = rel.id,
                sourceItemId = source.id,
                sourceItemName = source.name,
                targetItemId = target.id,
                targetItemName = target.name,
                canonicalType = type,
                displayLabel = getDirectionalLabel(type),
                description = rel.description
            )
        }
    }

    private fun getDirectionalLabel(type: ItemRelationshipType): String {
        return when (type) {
            ItemRelationshipType.FEEDS -> "Feeds"
            ItemRelationshipType.CONTROLS -> "Controls"
            ItemRelationshipType.PROTECTS -> "Protects"
            ItemRelationshipType.DRAINS_TO -> "Drains to"
            ItemRelationshipType.SERVES -> "Serves"
            ItemRelationshipType.DEPENDS_ON -> "Depends on"
            ItemRelationshipType.CONNECTED_TO -> "Connected to"
            ItemRelationshipType.OTHER -> "Related to"
        }
    }

    private fun buildUnconnected(items: List<InfrastructureItemEntity>, rels: List<ItemRelationshipEntity>): List<InfrastructureItemEntity> {
        val linkedItemIds = mutableSetOf<UUID>()
        items.forEach { 
            it.parentItemId?.let { pid -> linkedItemIds.add(it.id); linkedItemIds.add(pid) }
        }
        rels.forEach { 
            linkedItemIds.add(it.sourceItemId)
            linkedItemIds.add(it.targetItemId)
        }
        return items.filter { !linkedItemIds.contains(it.id) }
    }

    fun init(propertyId: UUID) {
        _propertyId.value = propertyId
    }

    fun setFilter(filter: RelationshipViewFilter) {
        _currentFilter.value = filter
    }

    fun removeRelationship(relationshipId: UUID) {
        val pid = _propertyId.value ?: return
        viewModelScope.launch {
            relationshipRepository.softDeleteRelationship(pid, relationshipId)
        }
    }
}
