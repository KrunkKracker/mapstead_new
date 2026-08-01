package com.jumastappworks.mapstead.data.mapping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressLocationResolverTest {

    @Test
    fun `address search results maintain coordinate validity`() {
        val match = AddressLocationMatch("123 Test St", 45.0, -90.0)
        assertEquals(45.0, match.latitude, 0.0)
        assertEquals(-90.0, match.longitude, 0.0)
    }

    @Test
    fun `address search result types are distinct`() {
        val success = AddressSearchResult.Success(emptyList())
        val noMatches = AddressSearchResult.NoMatches
        assertTrue(success != noMatches)
    }
}
