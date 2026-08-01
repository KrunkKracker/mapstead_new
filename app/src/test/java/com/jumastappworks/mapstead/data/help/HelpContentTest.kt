package com.jumastappworks.mapstead.data.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpContentTest {

    @Test
    fun `topic IDs are unique`() {
        val ids = HelpContent.TOPICS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `required topics exist`() {
        val expectedIds = HelpTopicId.entries
        val actualIds = HelpContent.TOPICS.map { it.id }.toSet()
        
        expectedIds.forEach { id ->
            assertTrue("Missing help topic: $id", actualIds.contains(id))
        }
    }

    @Test
    fun `each topic has at least one section`() {
        HelpContent.TOPICS.forEach { topic ->
            assertTrue("Topic ${topic.id} has no sections", topic.sections.isNotEmpty())
        }
    }
}
