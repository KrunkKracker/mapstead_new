package com.jumastappworks.mapstead.data.mapping

import com.jumastappworks.mapstead.BuildConfig
import org.junit.Assert.*
import org.junit.Test

class BasemapProviderTest {

    @Test
    fun testProductionBasemapProviderArchitecture() {
        val provider = ProductionBasemapProvider(maptilerKey = "key", isMaptilerAvailable = true)
        
        // 1. Verify default ID is Streets
        assertEquals(BasemapId.STREETS, provider.getDefaultBasemapId())
        
        // 2. Verify all primary semantic choices exist in correct order
        val expectedOrder = listOf(
            BasemapId.STREETS,
            BasemapId.BASE,
            BasemapId.TOPO,
            BasemapId.SATELLITE_HYBRID,
            BasemapId.OUTDOOR
        )
        
        val actualOrder = provider.getPrimaryBasemaps().mapNotNull { it.preferredId }
        assertEquals("Primary basemaps must follow the approved order", expectedOrder, actualOrder)

        assertEquals(BasemapSourceId.OPEN_FREE_MAP_LIBERTY, provider.resolveDefaultBackup(BasemapId.STREETS))
        assertEquals(BasemapSourceId.OPEN_FREE_MAP_POSITRON, provider.resolveDefaultBackup(BasemapId.BASE))
        assertEquals(BasemapSourceId.OPEN_FREE_MAP_FIORD, provider.resolveDefaultBackup(BasemapId.TOPO))
        assertEquals(BasemapSourceId.OPEN_FREE_MAP_LIBERTY, provider.resolveDefaultBackup(BasemapId.SATELLITE_HYBRID))
        assertEquals(BasemapSourceId.OPEN_FREE_MAP_FIORD, provider.resolveDefaultBackup(BasemapId.OUTDOOR))
    }

    @Test
    fun testUrlBuildingAndRedaction() {
        val provider = ProductionBasemapProvider(maptilerKey = "test-key", isMaptilerAvailable = true)
        
        // 1. Basic alphanumeric key
        assertEquals("https://host/style.json?key=REDACTED", provider.redactUrl("https://host/style.json?key=ABC123XYZ"))
        
        // 2. Hyphenated and underscored key
        assertEquals("https://host/style.json?key=REDACTED", provider.redactUrl("https://host/style.json?key=abc-123_xyz"))
        
        // 3. Key with dots and other safe chars
        assertEquals("https://host/style.json?key=REDACTED", provider.redactUrl("https://host/style.json?key=abc.123.xyz"))
        
        // 4. Case insensitivity
        assertEquals("https://host/style.json?KEY=REDACTED", provider.redactUrl("https://host/style.json?KEY=ABC"))
        
        // 5. Preservation of other parameters
        assertEquals("https://host/style.json?key=REDACTED&other=val", provider.redactUrl("https://host/style.json?key=ABC&other=val"))
        assertEquals("https://host/style.json?first=val&key=REDACTED", provider.redactUrl("https://host/style.json?first=val&key=ABC"))
        
        // 6. Fragment preservation
        assertEquals("https://host/style.json?key=REDACTED#pos", provider.redactUrl("https://host/style.json?key=ABC#pos"))

        // Verify no backup URL is blank
        provider.getBackupBasemaps().forEach { def ->
            assertTrue("Backup URL should not be blank for ${def.sourceId}", def.styleUrl.isNotBlank())
        }
    }

    @Test
    fun testUnconfiguredProviderBehavior() {
        val provider = ProductionBasemapProvider(maptilerKey = "", isMaptilerAvailable = false)
        
        // 1. Primary choices should be empty
        assertTrue("Unconfigured provider should have no primaries", provider.getPrimaryBasemaps().isEmpty())
        
        // 2. Default backup mapping should still work
        assertEquals(BasemapSourceId.OPEN_FREE_MAP_LIBERTY, provider.resolveDefaultBackup(BasemapId.STREETS))
    }

    @Test
    fun testPrimarySourceResolutions() {
        val provider = ProductionBasemapProvider(maptilerKey = "key", isMaptilerAvailable = true)
        
        // Verify primary MapTiler sources use v4
        val streetsDef = provider.getDefinition(BasemapSourceId.MAPTILER_STREETS)
        assertNotNull(streetsDef)
        assertTrue(streetsDef?.styleUrl?.contains("streets-v4") == true)
        
        val hybridDef = provider.getDefinition(BasemapSourceId.MAPTILER_HYBRID)
        assertNotNull(hybridDef)
        assertTrue(hybridDef?.styleUrl?.contains("hybrid-v4") == true)
    }
}
