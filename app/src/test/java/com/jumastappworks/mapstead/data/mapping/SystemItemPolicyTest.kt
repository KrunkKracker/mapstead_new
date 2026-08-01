package com.jumastappworks.mapstead.data.mapping

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemItemPolicyTest {

    @Test
    fun `Location presets have correct policies`() {
        val well = GuidedMapPresets.LOCATIONS.find { it.id == GuidedMapPresetId.WELL }
        assertEquals(SystemItemPolicy.OPTIONAL, well?.systemItemPolicy)

        val gate = GuidedMapPresets.LOCATIONS.find { it.id == GuidedMapPresetId.GATE }
        assertEquals(SystemItemPolicy.OPTIONAL, gate?.systemItemPolicy)

        val custom = GuidedMapPresets.LOCATIONS.find { it.id == GuidedMapPresetId.CUSTOM_LOCATION }
        assertEquals(SystemItemPolicy.OPTIONAL, custom?.systemItemPolicy)
        
        val panel = GuidedMapPresets.LOCATIONS.find { it.id == GuidedMapPresetId.ELECTRICAL_PANEL }
        assertEquals(SystemItemPolicy.AUTOMATIC, panel?.systemItemPolicy)
    }

    @Test
    fun `Route presets have correct policies`() {
        val waterLine = GuidedMapPresets.ROUTES.find { it.id == GuidedMapPresetId.WATER_LINE }
        assertEquals(SystemItemPolicy.OPTIONAL, waterLine?.systemItemPolicy)

        val drainage = GuidedMapPresets.ROUTES.find { it.id == GuidedMapPresetId.DRAINAGE_ROUTE }
        assertEquals(SystemItemPolicy.MAP_ONLY, drainage?.systemItemPolicy)
    }

    @Test
    fun `Area presets have correct policies`() {
        val septicField = GuidedMapPresets.AREAS.find { it.id == GuidedMapPresetId.SEPTIC_FIELD }
        assertEquals(SystemItemPolicy.OPTIONAL, septicField?.systemItemPolicy)

        val garden = GuidedMapPresets.AREAS.find { it.id == GuidedMapPresetId.GARDEN }
        assertEquals(SystemItemPolicy.MAP_ONLY, garden?.systemItemPolicy)
    }
}
