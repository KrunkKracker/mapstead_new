package com.jumastappworks.mapstead.ui.mapping

import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import com.jumastappworks.mapstead.data.db.entities.LayerEntity
import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import java.util.UUID

object MapSearchEngine {

    fun normalize(text: String?): String =
        text?.trim()?.lowercase()?.replace(Regex("\\s+"), " ") ?: ""

    fun filterAndRank(
        query: String,
        currentPropertyId: UUID,
        currentPlanId: UUID,
        features: List<MapFeatureEntity>,
        layers: List<LayerEntity>,
        items: List<InfrastructureItemEntity>
    ): List<MapSearchResult> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return emptyList()

        return features
            .filter { feature ->
                feature.deletedAt == null &&
                feature.propertyId == currentPropertyId &&
                feature.planId == currentPlanId
            }
            .mapNotNull { feature ->
                val layer = layers.find { it.id == feature.layerId } ?: return@mapNotNull null
                
                // Authoritative Context Check
                if (layer.deletedAt != null ||
                    layer.propertyId != currentPropertyId ||
                    layer.planId != currentPlanId) return@mapNotNull null
                
                val infra = items.find { it.id == feature.infrastructureItemId }
                    ?.takeIf { it.deletedAt == null && it.propertyId == currentPropertyId }
                
                val matchScore = calculateMatchScore(feature, infra, layer, normalizedQuery)
                if (matchScore == Int.MAX_VALUE) return@mapNotNull null
                
                val result = MapSearchResult(
                    featureId = feature.id,
                    featureLabel = feature.label,
                    systemItemId = infra?.id,
                    systemItemName = infra?.name,
                    category = infra?.category ?: layer.category,
                    subtype = infra?.subtype,
                    layerId = layer.id,
                    layerName = layer.name,
                    isLayerVisible = layer.isVisible,
                    isEmergency = infra?.isEmergencyItem ?: false,
                    geometryType = feature.geometryType
                )
                result to matchScore
            }
            .sortedWith { a, b ->
                val scoreComp = a.second.compareTo(b.second)
                if (scoreComp != 0) return@sortedWith scoreComp
                
                // Deterministic tie-breaking
                val labelA = normalize(a.first.featureLabel ?: a.first.systemItemName)
                val labelB = normalize(b.first.featureLabel ?: b.first.systemItemName)
                val labelComp = labelA.compareTo(labelB)
                if (labelComp != 0) return@sortedWith labelComp
                
                a.first.featureId.compareTo(b.first.featureId)
            }
            .distinctBy { it.first.featureId }
            .map { it.first }
    }

    private fun calculateMatchScore(
        feature: MapFeatureEntity,
        infra: InfrastructureItemEntity?,
        layer: LayerEntity,
        query: String
    ): Int {
        val label = normalize(feature.label)
        val infraName = normalize(infra?.name)
        val category = normalize(infra?.category ?: layer.category)
        val subtype = normalize(infra?.subtype)

        return when {
            label == query -> 1
            infraName == query -> 2
            category == query -> 3
            subtype == query -> 4
            label.startsWith(query) -> 5
            infraName.startsWith(query) -> 6
            category.startsWith(query) -> 7
            subtype.startsWith(query) -> 8
            label.contains(query) -> 9
            infraName.contains(query) -> 10
            category.contains(query) -> 11
            subtype.contains(query) -> 12
            else -> Int.MAX_VALUE
        }
    }
}
