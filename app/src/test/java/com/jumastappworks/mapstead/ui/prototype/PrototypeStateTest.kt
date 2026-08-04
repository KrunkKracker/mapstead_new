package com.jumastappworks.mapstead.ui.prototype

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PrototypeStateTest {

    @Test
    fun `initial destination is Home`() {
        val state = PrototypeAppState()
        assertEquals(PrototypeDestination.Home, state.currentDestination)
    }

    @Test
    fun `navigation updates current destination`() {
        val state = PrototypeAppState()
        state.navigateTo(PrototypeDestination.Items)
        assertEquals(PrototypeDestination.Items, state.currentDestination)
    }

    @Test
    fun `goBack returns to previous destination`() {
        val state = PrototypeAppState()
        state.navigateTo(PrototypeDestination.Items)
        state.navigateTo(PrototypeDestination.Tasks)
        assertEquals(PrototypeDestination.Tasks, state.currentDestination)
        
        state.goBack()
        assertEquals(PrototypeDestination.Items, state.currentDestination)
        
        state.goBack()
        assertEquals(PrototypeDestination.Home, state.currentDestination)
    }

    @Test
    fun `Home is directly reachable and clears backstack`() {
        val state = PrototypeAppState()
        state.navigateTo(PrototypeDestination.Items)
        state.navigateTo(PrototypeDestination.Tasks)
        
        state.navigateTo(PrototypeDestination.Home)
        assertEquals(PrototypeDestination.Home, state.currentDestination)
        
        // backstack should be empty (except Home)
        state.goBack()
        assertEquals(PrototypeDestination.Home, state.currentDestination)
    }

    @Test
    fun `Pool Pump journey review step`() {
        val state = PrototypeAppState()
        val reviewStep = AddStep.Review("Pool Pump", "Pool Equipment", hasPhoto = true)
        state.navigateTo(PrototypeDestination.AddJourney(reviewStep))
        
        assertTrue(state.currentDestination is PrototypeDestination.AddJourney)
        val currentStep = (state.currentDestination as PrototypeDestination.AddJourney).step
        assertTrue(currentStep is AddStep.Review)
        assertEquals("Pool Pump", (currentStep as AddStep.Review).name)
    }
}
