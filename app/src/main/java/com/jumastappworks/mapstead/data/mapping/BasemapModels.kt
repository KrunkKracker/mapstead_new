package com.jumastappworks.mapstead.data.mapping

/**
 * Customer-facing basemap preference IDs.
 */
enum class BasemapId {
    STREETS,
    BASE,
    TOPO,
    SATELLITE_HYBRID,
    OUTDOOR
}

enum class BasemapProviderType {
    MAPTILER,
    OPEN_FREE_MAP
}

enum class BasemapRole {
    PRIMARY,
    BACKUP
}

/**
 * Concrete provider source IDs.
 */
enum class BasemapSourceId {
    MAPTILER_STREETS,
    MAPTILER_BASE,
    MAPTILER_TOPO,
    MAPTILER_HYBRID,
    MAPTILER_OUTDOOR,
    OPEN_FREE_MAP_LIBERTY,
    OPEN_FREE_MAP_POSITRON,
    OPEN_FREE_MAP_FIORD,
    OPEN_FREE_MAP_DARK
}

data class BasemapDefinition(
    val sourceId: BasemapSourceId,
    val provider: BasemapProviderType,
    val role: BasemapRole,
    val styleUrl: String,
    val displayNameRes: Int,
    val descriptionRes: Int,
    val isAvailable: Boolean,
    val preferredId: BasemapId? = null,
    val backupSourceId: BasemapSourceId? = null
)
