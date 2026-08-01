package com.jumastappworks.mapstead.data.prefs

import com.jumastappworks.mapstead.data.mapping.BasemapId
import org.junit.Assert.assertEquals
import org.junit.Test

class BasemapMigrationTest {

    @Test
    fun testLegacyBasemapMigration() {
        assertEquals(BasemapId.STREETS, UserPreferencesRepository.parseBasemapId("STREET"))
        assertEquals(BasemapId.STREETS, UserPreferencesRepository.parseBasemapId("STREETS"))
        assertEquals(BasemapId.SATELLITE_HYBRID, UserPreferencesRepository.parseBasemapId("SATELLITE"))
        assertEquals(BasemapId.SATELLITE_HYBRID, UserPreferencesRepository.parseBasemapId("SATELLITE_HYBRID"))
        assertEquals(BasemapId.OUTDOOR, UserPreferencesRepository.parseBasemapId("OUTDOORS"))
        assertEquals(BasemapId.OUTDOOR, UserPreferencesRepository.parseBasemapId("OUTDOOR"))
        assertEquals(BasemapId.BASE, UserPreferencesRepository.parseBasemapId("LIGHT"))
        assertEquals(BasemapId.BASE, UserPreferencesRepository.parseBasemapId("BASE"))
        assertEquals(BasemapId.TOPO, UserPreferencesRepository.parseBasemapId("TOPO"))
        assertEquals(BasemapId.STREETS, UserPreferencesRepository.parseBasemapId("DARK"))
        assertEquals(BasemapId.STREETS, UserPreferencesRepository.parseBasemapId(null))
        assertEquals(BasemapId.STREETS, UserPreferencesRepository.parseBasemapId(""))
        assertEquals(BasemapId.STREETS, UserPreferencesRepository.parseBasemapId("UNKNOWN"))
    }
}
