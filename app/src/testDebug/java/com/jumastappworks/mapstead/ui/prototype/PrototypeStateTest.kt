package com.jumastappworks.mapstead.ui.prototype

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PrototypeStateTest {

    @Test
    fun `initial destination is Home and data is loaded`() {
        val state = PrototypeAppState()
        assertEquals(PrototypeDestination.Home, state.currentDestination)
        assertTrue(state.items.isNotEmpty())
        assertTrue(state.tasks.isNotEmpty())
        // Pool Pump should not be in initial items
        assertFalse(state.items.any { it.name == "Pool Pump" })
    }

    @Test
    fun `startAddJourney initializes one shared draft`() {
        val state = PrototypeAppState()
        state.startAddJourney()
        assertNotNull(state.addDraft)
        assertEquals("", state.addDraft?.name)
    }

    @Test
    fun `completeAddJourney clears journey stack and saves item`() {
        val state = PrototypeAppState()
        state.navigateTo(PrototypeDestination.Items)
        state.startAddJourney()
        state.navigateTo(PrototypeDestination.AddJourney(AddStep.Preset("Water")))
        
        val savedItem = PrototypePropertyItem(name = "Pool Pump", category = "Pool Equipment", locationDescription = "Backyard")
        state.completeAddJourney(savedItem)
        
        // 1. One Pool Pump exists
        assertEquals(1, state.items.count { it.name == "Pool Pump" })
        
        // 2. AddJourney removed from backstack
        assertFalse(state.backStack.any { it is PrototypeDestination.AddJourney })
        
        // 3. Current destination is details
        assertTrue(state.currentDestination is PrototypeDestination.ItemDetails)
        assertEquals(savedItem.id, (state.currentDestination as PrototypeDestination.ItemDetails).itemId)
        
        // 4. Back returns to origin (Items)
        state.goBack()
        assertEquals(PrototypeDestination.Items, state.currentDestination)
    }

    @Test
    fun `saved task references the saved Pool Pump`() {
        val state = PrototypeAppState()
        val savedItem = PrototypePropertyItem(name = "Pool Pump", category = "Pool Equipment", locationDescription = "Backyard")
        state.saveItem(savedItem)
        
        val task = state.tasks.find { it.title.contains("pool-pump", ignoreCase = true) }
        assertNotNull(task)
        assertEquals(savedItem.id, task?.relatedItemId)
    }

    @Test
    fun `Show on Map retains return path and returnToDetails pops Map`() {
        val state = PrototypeAppState()
        val itemId = state.items[0].id
        state.navigateTo(PrototypeDestination.ItemDetails(itemId))
        
        // Show on Map
        state.navigateTo(PrototypeDestination.Map(highlightItemId = itemId, returnToDetails = true))
        assertEquals(3, state.backStack.size) // [Home, Details, Map]
        
        // Pop back to Details
        state.goBack()
        assertTrue(state.currentDestination is PrototypeDestination.ItemDetails)
        assertEquals(itemId, (state.currentDestination as PrototypeDestination.ItemDetails).itemId)
        assertEquals(2, state.backStack.size)
    }

    @Test
    fun `Review editing preserves the complete draft`() {
        val state = PrototypeAppState()
        state.startAddJourney()
        state.addDraft = state.addDraft?.copy(name = "Original", category = "Cat", hasPhoto = true, note = "Note")
        
        // Edit Name
        state.addDraft = state.addDraft?.copy(name = "Updated")
        
        assertEquals("Updated", state.addDraft?.name)
        assertEquals("Cat", state.addDraft?.category)
        assertTrue(state.addDraft?.hasPhoto == true)
        assertEquals("Note", state.addDraft?.note)
    }

    @Test
    fun `Reset restores initial fake state`() {
        val state = PrototypeAppState()
        state.items.add(PrototypePropertyItem(name = "Extra", category = "Test", locationDescription = "Test"))
        
        state.reset()
        assertFalse(state.items.any { it.name == "Extra" })
        assertFalse(state.items.any { it.name == "Pool Pump" })
    }
}
