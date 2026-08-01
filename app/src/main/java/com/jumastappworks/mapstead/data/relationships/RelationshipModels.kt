package com.jumastappworks.mapstead.data.relationships

import com.jumastappworks.mapstead.R
import java.util.UUID

enum class ItemRelationshipType(val canonicalName: String, val isSymmetric: Boolean, val labelRes: Int) {
    FEEDS("FEEDS", false, R.string.rel_type_feeds),
    CONTROLS("CONTROLS", false, R.string.rel_type_controls),
    PROTECTS("PROTECTS", false, R.string.rel_type_protects),
    DRAINS_TO("DRAINS_TO", false, R.string.rel_type_drains_to),
    SERVES("SERVES", false, R.string.rel_type_serves),
    DEPENDS_ON("DEPENDS_ON", false, R.string.rel_type_depends_on),
    CONNECTED_TO("CONNECTED_TO", true, R.string.rel_type_connected_to),
    OTHER("OTHER", false, R.string.rel_type_other);

    companion object {
        fun fromString(value: String?): ItemRelationshipType {
            val normalized = value?.trim()?.uppercase() ?: return OTHER
            return entries.find { it.canonicalName == normalized } ?: OTHER
        }
    }
}

enum class RelationshipDirection {
    OUTGOING,
    INCOMING,
    SYMMETRIC
}

data class ItemRelationshipUiModel(
    val relationshipId: UUID,
    val currentItemId: UUID,
    val relatedItemId: UUID,
    val relatedItemName: String,
    val relatedItemCategory: String,
    val relatedItemSubtype: String?,
    val canonicalType: ItemRelationshipType,
    val displayLabel: String,
    val description: String?,
    val direction: RelationshipDirection,
    val relatedItemStatus: String,
    val hasMappedFeature: Boolean
)

sealed interface RelationshipWriteResult {
    data class Success(val relationshipId: UUID) : RelationshipWriteResult
    data object NotFound : RelationshipWriteResult
    data object OwnershipMismatch : RelationshipWriteResult
    data object InvalidSource : RelationshipWriteResult
    data object InvalidTarget : RelationshipWriteResult
    data object SelfRelationship : RelationshipWriteResult
    data object Duplicate : RelationshipWriteResult
    data object HierarchyCycle : RelationshipWriteResult
    data object DependencyCycle : RelationshipWriteResult
    data object InvalidDescription : RelationshipWriteResult
    data class Error(val message: String?) : RelationshipWriteResult
}
