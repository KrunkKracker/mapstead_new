package com.jumastappworks.mapstead.data.mapping

import com.jumastappworks.mapstead.data.repository.MapRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class FeatureNamingService @Inject constructor(
    private val mapRepository: MapRepository
) {
    /**
     * Generates a unique name for a feature within a property.
     * Example: "Well", "Well 2", "Well 3".
     * Scope: Property-wide.
     */
    suspend fun generateUniqueName(propertyId: UUID, baseName: String): String {
        val existingFeatures = mapRepository.getFeaturesForProperty(propertyId).first()
        val existingNames = existingFeatures
            .filter { it.deletedAt == null }
            .mapNotNull { it.label?.trim()?.lowercase() }
            .toSet()

        val trimmedBase = baseName.trim()
        if (trimmedBase.lowercase() !in existingNames) return trimmedBase

        var count = 2
        while ("${trimmedBase.lowercase()} $count" in existingNames) {
            count++
        }
        return "$trimmedBase $count"
    }
}
