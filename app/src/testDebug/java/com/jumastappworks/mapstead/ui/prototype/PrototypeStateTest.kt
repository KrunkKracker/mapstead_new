package com.jumastappworks.mapstead.ui.prototype

import org.junit.Assert.*
import org.junit.Test

class PrototypeStateTest {

    @Test
    fun `initial destination is Welcome`() {
        val state = PrototypeAppState()
        assertEquals(PrototypeDestination.Welcome, state.currentDestination)
        assertTrue(state.properties.isEmpty())
    }

    @Test
    fun `Explore Sample Property initializes sample data`() {
        val state = PrototypeAppState()
        state.reset() // This is the "Explore Sample" action in current impl
        
        assertEquals(PrototypeDestination.Home, state.currentDestination)
        assertEquals(1, state.properties.size)
        assertTrue(state.properties[0].isSample)
        assertTrue(state.items.isNotEmpty())
    }

    @Test
    fun `startPropertySetup initializes draft`() {
        val state = PrototypeAppState()
        state.startPropertySetup()
        
        assertNotNull(state.setupDraft)
        assertEquals(PrototypeDestination.PropertySetup(SetupStep.Basics), state.currentDestination)
    }

    @Test
    fun `finalizePropertyCreation adds property and selects it`() {
        val state = PrototypeAppState()
        state.startPropertySetup()
        state.setupDraft = state.setupDraft?.copy(name = "New Prop", type = "Land")
        
        state.finalizePropertyCreation()
        
        assertEquals(1, state.properties.size)
        assertEquals("New Prop", state.properties[0].name)
        assertEquals(state.properties[0].id, state.selectedPropertyId)
        assertEquals(PrototypeDestination.PropertySetup(SetupStep.Success), state.currentDestination)
    }

    @Test
    fun `startAddJourney initializes one shared draft`() {
        val state = PrototypeAppState()
        state.reset()
        state.startAddJourney()
        
        assertNotNull(state.addDraft)
        assertTrue(state.addDraft?.name?.isEmpty() == true)
        assertEquals(state.selectedPropertyId, state.addDraft?.propertyId)
    }

    @Test
    fun `completeAddJourney clears journey stack and saves item`() {
        val state = PrototypeAppState()
        state.reset()
        state.navigateTo(PrototypeDestination.Items)
        state.startAddJourney()
        state.navigateTo(PrototypeDestination.AddJourney(AddStep.Entry))
        
        val draft = state.addDraft!!.copy(name = "Pool Pump", category = "Pool Equipment")
        state.completeAddJourney(draft)
        
        // 1. One Pool Pump exists
        assertEquals(1, state.items.count { it.name == "Pool Pump" })
        
        // 2. AddJourney removed from backstack
        assertFalse(state.backStack.any { it is PrototypeDestination.AddJourney })
        
        // 3. Current destination is details
        assertTrue(state.currentDestination is PrototypeDestination.ItemDetails)
    }

    @Test
    fun `saved task references the saved Pool Pump`() {
        val state = PrototypeAppState()
        state.reset()
        val savedItem = PrototypePropertyItem(propertyId = state.selectedPropertyId!!, name = "Pool Pump", category = "Pool Equipment", locationDescription = "Backyard")
        state.saveItem(savedItem)
        
        val task = state.tasks.find { it.title.contains("pool-pump", ignoreCase = true) }
        assertNotNull(task)
        assertEquals(savedItem.id, task?.relatedItemId)
    }

    @Test
    fun `Show on Map retains return path and returnToDetails pops Map`() {
        val state = PrototypeAppState()
        state.reset()
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
        state.reset()
        state.startAddJourney()
        state.addDraft = state.addDraft?.copy(name = "Original", category = "Cat", hasPhoto = true, note = "Note")
        
        // Edit Name (simulated by updating draft)
        state.addDraft = state.addDraft?.copy(name = "Updated")
        
        assertEquals("Updated", state.addDraft?.name)
        assertEquals("Cat", state.addDraft?.category)
        assertTrue(state.addDraft?.hasPhoto == true)
        assertEquals("Note", state.addDraft?.note)
    }

    @Test
    fun `Property switching clears context belonging to prior property`() {
        val state = PrototypeAppState()
        state.reset() // Selects Sample (id1)
        val pid1 = state.selectedPropertyId!!
        
        // Create second property
        val pid2 = java.util.UUID.randomUUID()
        state.properties.add(PrototypeProperty(id = pid2, name = "New", type = "Home"))
        
        state.navigateTo(PrototypeDestination.ItemDetails(state.items[0].id))
        
        // Switch to property 2
        state.selectProperty(pid2)
        
        assertEquals(pid2, state.selectedPropertyId)
        assertEquals(PrototypeDestination.Home, state.currentDestination)
        assertEquals(1, state.backStack.size)
        assertTrue(state.items.isEmpty()) // New property starts empty
    }
}
