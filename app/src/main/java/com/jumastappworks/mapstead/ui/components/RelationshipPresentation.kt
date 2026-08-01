package com.jumastappworks.mapstead.ui.components

import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.relationships.RelationshipWriteResult

object RelationshipPresentation {
    fun getErrorRes(result: RelationshipWriteResult): Int? {
        return when (result) {
            is RelationshipWriteResult.Success -> null
            RelationshipWriteResult.NotFound -> R.string.error_feature_not_found
            RelationshipWriteResult.OwnershipMismatch -> R.string.ownership_error
            RelationshipWriteResult.InvalidSource -> R.string.context_mismatch
            RelationshipWriteResult.InvalidTarget -> R.string.context_mismatch
            RelationshipWriteResult.SelfRelationship -> R.string.context_mismatch
            RelationshipWriteResult.Duplicate -> R.string.error_relationship_exists
            RelationshipWriteResult.HierarchyCycle -> R.string.error_circular_hierarchy
            RelationshipWriteResult.DependencyCycle -> R.string.error_circular_dependency
            RelationshipWriteResult.InvalidDescription -> R.string.error_save_failed
            is RelationshipWriteResult.Error -> R.string.error_save_failed
        }
    }
}
