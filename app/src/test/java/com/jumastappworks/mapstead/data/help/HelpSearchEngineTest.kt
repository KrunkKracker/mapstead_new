package com.jumastappworks.mapstead.data.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpSearchEngineTest {

    private val topics = listOf(
        HelpTopic(HelpTopicId.PROPERTIES, 1, 1, emptyList(), listOf(101)),
        HelpTopic(HelpTopicId.PROPERTY_MAPS, 2, 2, emptyList(), listOf(102)),
        HelpTopic(HelpTopicId.LAYERS, 3, 3, emptyList(), listOf(103))
    )

    private val resolved = mapOf(
        HelpTopicId.PROPERTIES to ResolvedHelpTopic(HelpTopicId.PROPERTIES, "Managing Properties", "Details about properties", emptyList(), listOf("Address")),
        HelpTopicId.PROPERTY_MAPS to ResolvedHelpTopic(HelpTopicId.PROPERTY_MAPS, "Property Maps", "Visual map information", emptyList(), listOf("Basemap")),
        HelpTopicId.LAYERS to ResolvedHelpTopic(HelpTopicId.LAYERS, "Map Layers", "Organize items", emptyList(), listOf("Visibility"))
    )

    @Test
    fun `exact title match ranks first`() {
        val results = HelpSearchEngine.searchHelpTopics(topics, resolved, "Property Maps")
        assertEquals(HelpTopicId.PROPERTY_MAPS, results.first().id)
    }

    @Test
    fun `starts with ranks higher than contains`() {
        // "Properties" starts with "Prop", "Property Maps" starts with "Prop"
        // But "Managing Properties" contains "Properties"
        val results = HelpSearchEngine.searchHelpTopics(topics, resolved, "Prop")
        // In our ranking: "Property Maps" (starts with) should be high.
        // "Managing Properties" (contains) should be lower than starts with.
        
        val propertyMapsIndex = results.indexOfFirst { it.id == HelpTopicId.PROPERTY_MAPS }
        val propertiesIndex = results.indexOfFirst { it.id == HelpTopicId.PROPERTIES }
        
        // "Managing Properties" starts with M, contains Properties.
        // "Property Maps" starts with Property.
        assertTrue("Starts with should rank higher than contains", propertyMapsIndex < propertiesIndex)
    }

    @Test
    fun `keyword match works`() {
        val results = HelpSearchEngine.searchHelpTopics(topics, resolved, "Address")
        assertEquals(HelpTopicId.PROPERTIES, results.first().id)
    }

    @Test
    fun `search is case insensitive`() {
        val results = HelpSearchEngine.searchHelpTopics(topics, resolved, "layers")
        assertEquals(HelpTopicId.LAYERS, results.first().id)
    }

    @Test
    fun `blank query returns all topics in original order`() {
        val results = HelpSearchEngine.searchHelpTopics(topics, resolved, "")
        assertEquals(topics, results)
    }

    @Test
    fun `repeated whitespace is normalized`() {
        val results = HelpSearchEngine.searchHelpTopics(topics, resolved, "Property   Maps")
        assertEquals(HelpTopicId.PROPERTY_MAPS, results.first().id)
    }

    @Test
    fun `no result query returns empty list`() {
        val results = HelpSearchEngine.searchHelpTopics(topics, resolved, "xyzabc")
        assertTrue(results.isEmpty())
    }
}
