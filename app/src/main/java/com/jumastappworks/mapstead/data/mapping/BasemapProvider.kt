package com.jumastappworks.mapstead.data.mapping

import com.jumastappworks.mapstead.BuildConfig
import com.jumastappworks.mapstead.R
import javax.inject.Inject
import javax.inject.Singleton

interface BasemapProvider {
    fun getPrimaryBasemaps(): List<BasemapDefinition>
    fun getBackupBasemaps(): List<BasemapDefinition>
    fun getDefinition(sourceId: BasemapSourceId): BasemapDefinition?
    fun resolveDefaultBackup(preferredId: BasemapId): BasemapSourceId
    fun getDefaultBasemapId(): BasemapId
    fun buildStyleUrl(sourceId: BasemapSourceId): String
    fun redactUrl(url: String): String
    fun getAttribution(sourceId: BasemapSourceId): List<BasemapAttributionEntry>
    
    // Legacy support for older callers
    fun availableBasemaps(): List<BasemapDefinition>
    fun getBasemap(id: BasemapId): BasemapDefinition?
    fun defaultBasemap(): BasemapId
}

@Singleton
class ProductionBasemapProvider(
    private val maptilerKey: String = BuildConfig.MAPTILER_API_KEY,
    private val isMaptilerAvailable: Boolean = BuildConfig.MAPTILER_CONFIGURED
) : BasemapProvider {

    @Inject constructor() : this(BuildConfig.MAPTILER_API_KEY, BuildConfig.MAPTILER_CONFIGURED)

    private val allDefinitions = listOf(
        // MapTiler Primaries
        BasemapDefinition(
            sourceId = BasemapSourceId.MAPTILER_STREETS,
            provider = BasemapProviderType.MAPTILER,
            role = BasemapRole.PRIMARY,
            styleUrl = "https://api.maptiler.com/maps/streets-v4/style.json",
            displayNameRes = R.string.street,
            descriptionRes = R.string.basemap_streets_desc,
            isAvailable = isMaptilerAvailable,
            preferredId = BasemapId.STREETS,
            backupSourceId = BasemapSourceId.OPEN_FREE_MAP_LIBERTY
        ),
        BasemapDefinition(
            sourceId = BasemapSourceId.MAPTILER_BASE,
            provider = BasemapProviderType.MAPTILER,
            role = BasemapRole.PRIMARY,
            styleUrl = "https://api.maptiler.com/maps/base-v4/style.json",
            displayNameRes = R.string.base_map,
            descriptionRes = R.string.basemap_base_desc,
            isAvailable = isMaptilerAvailable,
            preferredId = BasemapId.BASE,
            backupSourceId = BasemapSourceId.OPEN_FREE_MAP_POSITRON
        ),
        BasemapDefinition(
            sourceId = BasemapSourceId.MAPTILER_TOPO,
            provider = BasemapProviderType.MAPTILER,
            role = BasemapRole.PRIMARY,
            styleUrl = "https://api.maptiler.com/maps/topo-v4/style.json",
            displayNameRes = R.string.topo,
            descriptionRes = R.string.basemap_topo_desc,
            isAvailable = isMaptilerAvailable,
            preferredId = BasemapId.TOPO,
            backupSourceId = BasemapSourceId.OPEN_FREE_MAP_FIORD
        ),
        BasemapDefinition(
            sourceId = BasemapSourceId.MAPTILER_HYBRID,
            provider = BasemapProviderType.MAPTILER,
            role = BasemapRole.PRIMARY,
            styleUrl = "https://api.maptiler.com/maps/hybrid-v4/style.json",
            displayNameRes = R.string.satellite_hybrid,
            descriptionRes = R.string.basemap_satellite_desc,
            isAvailable = isMaptilerAvailable,
            preferredId = BasemapId.SATELLITE_HYBRID,
            backupSourceId = BasemapSourceId.OPEN_FREE_MAP_LIBERTY
        ),
        BasemapDefinition(
            sourceId = BasemapSourceId.MAPTILER_OUTDOOR,
            provider = BasemapProviderType.MAPTILER,
            role = BasemapRole.PRIMARY,
            styleUrl = "https://api.maptiler.com/maps/outdoor-v4/style.json",
            displayNameRes = R.string.outdoor,
            descriptionRes = R.string.basemap_outdoor_desc,
            isAvailable = isMaptilerAvailable,
            preferredId = BasemapId.OUTDOOR,
            backupSourceId = BasemapSourceId.OPEN_FREE_MAP_FIORD
        ),
        // OpenFreeMap Backups
        BasemapDefinition(
            sourceId = BasemapSourceId.OPEN_FREE_MAP_LIBERTY,
            provider = BasemapProviderType.OPEN_FREE_MAP,
            role = BasemapRole.BACKUP,
            styleUrl = "https://tiles.openfreemap.org/styles/liberty",
            displayNameRes = R.string.street,
            descriptionRes = R.string.basemap_street_desc,
            isAvailable = true
        ),
        BasemapDefinition(
            sourceId = BasemapSourceId.OPEN_FREE_MAP_POSITRON,
            provider = BasemapProviderType.OPEN_FREE_MAP,
            role = BasemapRole.BACKUP,
            styleUrl = "https://tiles.openfreemap.org/styles/positron",
            displayNameRes = R.string.light,
            descriptionRes = R.string.basemap_light_desc,
            isAvailable = true
        ),
        BasemapDefinition(
            sourceId = BasemapSourceId.OPEN_FREE_MAP_FIORD,
            provider = BasemapProviderType.OPEN_FREE_MAP,
            role = BasemapRole.BACKUP,
            styleUrl = "https://tiles.openfreemap.org/styles/fiord",
            displayNameRes = R.string.outdoors,
            descriptionRes = R.string.basemap_outdoors_desc,
            isAvailable = true
        ),
        BasemapDefinition(
            sourceId = BasemapSourceId.OPEN_FREE_MAP_DARK,
            provider = BasemapProviderType.OPEN_FREE_MAP,
            role = BasemapRole.BACKUP,
            styleUrl = "https://tiles.openfreemap.org/styles/dark",
            displayNameRes = R.string.dark,
            descriptionRes = R.string.basemap_dark_desc,
            isAvailable = true
        )
    )

    override fun getPrimaryBasemaps(): List<BasemapDefinition> {
        return allDefinitions.filter { it.role == BasemapRole.PRIMARY && it.isAvailable }
    }

    override fun getBackupBasemaps(): List<BasemapDefinition> {
        return allDefinitions.filter { it.role == BasemapRole.BACKUP }
    }

    override fun getDefinition(sourceId: BasemapSourceId): BasemapDefinition? {
        return allDefinitions.find { it.sourceId == sourceId }
    }

    override fun resolveDefaultBackup(preferredId: BasemapId): BasemapSourceId {
        return allDefinitions.find { it.preferredId == preferredId }?.backupSourceId 
            ?: BasemapSourceId.OPEN_FREE_MAP_LIBERTY
    }

    override fun getDefaultBasemapId(): BasemapId = BasemapId.STREETS

    override fun buildStyleUrl(sourceId: BasemapSourceId): String {
        val def = getDefinition(sourceId) ?: return ""
        return if (def.provider == BasemapProviderType.MAPTILER) {
            val separator = if (def.styleUrl.contains("?")) "&" else "?"
            "${def.styleUrl}${separator}key=$maptilerKey"
        } else {
            def.styleUrl
        }
    }

    override fun redactUrl(url: String): String {
        return url.replace(Regex("(?i)([?&]key=)[^&#\\s]*"), "$1REDACTED")
    }

    override fun getAttribution(sourceId: BasemapSourceId): List<BasemapAttributionEntry> {
        val def = getDefinition(sourceId) ?: return emptyList()
        return if (def.provider == BasemapProviderType.MAPTILER) {
            listOf(
                BasemapAttributionEntry(R.string.attribution_maptiler, "https://www.maptiler.com/copyright/"),
                BasemapAttributionEntry(R.string.attribution_osm, "https://www.openstreetmap.org/copyright")
            )
        } else {
            listOf(
                BasemapAttributionEntry(R.string.attribution_osm, "https://www.openstreetmap.org/copyright")
            )
        }
    }

    // Legacy support
    override fun availableBasemaps(): List<BasemapDefinition> = getPrimaryBasemaps()
    override fun getBasemap(id: BasemapId): BasemapDefinition? {
        return allDefinitions.find { it.preferredId == id && it.role == BasemapRole.PRIMARY && it.isAvailable }
    }
    override fun defaultBasemap(): BasemapId = getDefaultBasemapId()
}
