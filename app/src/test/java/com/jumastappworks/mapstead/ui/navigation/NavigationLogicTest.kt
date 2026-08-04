package com.jumastappworks.mapstead.ui.navigation

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class NavigationLogicTest {

    @Test
    fun testNavigationPresentationThresholds() {
        // 1. 320 dp uses IconOnly
        assertEquals(NavigationPresentation.IconOnly, navigationPresentation(320, 1.0f, false))

        // 2. 360 dp at font scale 1.0 uses CompactLabels
        assertEquals(NavigationPresentation.CompactLabels, navigationPresentation(360, 1.0f, false))

        // 3. 360 dp at font scale 1.3 uses IconOnly
        assertEquals(NavigationPresentation.IconOnly, navigationPresentation(360, 1.3f, false))

        // 4. 411 dp at font scale 1.0 uses CompactLabels
        assertEquals(NavigationPresentation.CompactLabels, navigationPresentation(411, 1.0f, false))

        // 5. Medium/expanded rail layouts use NavigationRail (ignoring width/font if useRail is true)
        assertEquals(NavigationPresentation.NavigationRail, navigationPresentation(600, 1.0f, true))
        assertEquals(NavigationPresentation.NavigationRail, navigationPresentation(360, 2.0f, true))
    }

    @Test
    fun testRouteTopLevelMapping() {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()

        // Properties section
        assertEquals(MainDestination.Properties, Route.Properties.topLevelDestination())
        assertEquals(MainDestination.Properties, Route.AddProperty.topLevelDestination())
        assertEquals(MainDestination.Properties, Route.EditProperty(propId).topLevelDestination())
        assertEquals(MainDestination.Properties, Route.PropertyDashboard(propId).topLevelDestination())
        assertEquals(MainDestination.Properties, Route.InfrastructureList(propId).topLevelDestination())
        assertEquals(MainDestination.Properties, Route.InfrastructureItemDetails(propId, UUID.randomUUID()).topLevelDestination())
        assertEquals(MainDestination.Properties, Route.InfrastructureItemEditor(propId).topLevelDestination())

        // Map section
        assertEquals(MainDestination.Map, Route.Plans(propId).topLevelDestination())
        assertEquals(MainDestination.Map, Route.CreatePlan(propId).topLevelDestination())
        assertEquals(MainDestination.Map, Route.MapEditor(propId, planId).topLevelDestination())

        // Maintenance section
        assertEquals(MainDestination.Maintenance, Route.Maintenance(propId).topLevelDestination())

        // Emergency section
        assertEquals(MainDestination.Emergency, Route.Emergency(propId).topLevelDestination())

        // Settings section
        assertEquals(MainDestination.Settings, Route.Settings.topLevelDestination())
    }

    @Test
    fun testMatchesTopLevelRootPropertyAwareness() {
        val propA = UUID.randomUUID()
        val propB = UUID.randomUUID()

        // Plans for Property A matches Map root for Property A
        assertTrue(Route.Plans(propA).matchesTopLevelRoot(MainDestination.Map, propA))
        // Plans for Property A DOES NOT match Map root for Property B
        assertFalse(Route.Plans(propA).matchesTopLevelRoot(MainDestination.Map, propB))

        // Maintenance for Property A matches Maintenance root for Property A
        assertTrue(Route.Maintenance(propA).matchesTopLevelRoot(MainDestination.Maintenance, propA))
        // Maintenance for Property A DOES NOT match Property B
        assertFalse(Route.Maintenance(propA).matchesTopLevelRoot(MainDestination.Maintenance, propB))

        // Emergency for Property A matches Emergency root for Property A
        assertTrue(Route.Emergency(propA).matchesTopLevelRoot(MainDestination.Emergency, propA))
        // Emergency for Property A DOES NOT match Property B
        assertFalse(Route.Emergency(propA).matchesTopLevelRoot(MainDestination.Emergency, propB))

        // Settings matches the single Settings root (regardless of property)
        assertTrue(Route.Settings.matchesTopLevelRoot(MainDestination.Settings, propA))
        assertTrue(Route.Settings.matchesTopLevelRoot(MainDestination.Settings, null))

        // Properties root matches Properties destination
        assertTrue(Route.Properties.matchesTopLevelRoot(MainDestination.Properties, null))
    }

    @Test
    fun testInfrastructureSaveNavigationLogic() {
        val propId = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        val backStack = mutableListOf<Route>()

        // Case 1: Saving a NEW item
        // Initial state: [List, Editor]
        backStack.add(Route.InfrastructureList(propId))
        backStack.add(Route.InfrastructureItemEditor(propId, null))

        handleInfrastructureSave(itemId, wasEditing = false, propId, backStack)

        // Expected: [List, Details]
        assertEquals(2, backStack.size)
        assertTrue(backStack[0] is Route.InfrastructureList)
        assertTrue(backStack[1] is Route.InfrastructureItemDetails)
        assertEquals(itemId, (backStack[1] as Route.InfrastructureItemDetails).itemId)

        // Case 2: Saving an EXISTING item
        // Initial state: [List, Details, Editor]
        backStack.clear()
        backStack.add(Route.InfrastructureList(propId))
        backStack.add(Route.InfrastructureItemDetails(propId, itemId))
        backStack.add(Route.InfrastructureItemEditor(propId, itemId))

        handleInfrastructureSave(itemId, wasEditing = true, propId, backStack)

        // Expected: [List, Details] (Popped editor, returned to details)
        assertEquals(2, backStack.size)
        assertTrue(backStack[0] is Route.InfrastructureList)
        assertTrue(backStack[1] is Route.InfrastructureItemDetails)
        assertEquals(itemId, (backStack[1] as Route.InfrastructureItemDetails).itemId)
    }

    @Test
    fun testOpenOrReturnToMapFeature() {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val featureA = "feat-a"
        val featureB = "feat-b"
        val backStack = mutableListOf<Route>()

        // 1. No existing map creates a new MapEditor
        openOrReturnToMapFeature(backStack, propId, planId, featureA)
        assertEquals(1, backStack.size)
        assertTrue(backStack[0] is Route.MapEditor)
        assertEquals(featureA, (backStack[0] as Route.MapEditor).featureId)

        // 2. Already on matching map updates featureId without duplication
        openOrReturnToMapFeature(backStack, propId, planId, featureB)
        assertEquals(1, backStack.size)
        assertEquals(featureB, (backStack[0] as Route.MapEditor).featureId)

        // 3. Matching map already exists below other screens
        backStack.add(Route.InfrastructureItemDetails(propId, UUID.randomUUID()))
        backStack.add(Route.InfrastructureItemDetails(propId, UUID.randomUUID()))
        assertEquals(3, backStack.size)
        
        openOrReturnToMapFeature(backStack, propId, planId, featureA)
        assertEquals(1, backStack.size)
        assertEquals(featureA, (backStack[0] as Route.MapEditor).featureId)

        // 4. Different plan creates a new MapEditor
        val planId2 = UUID.randomUUID()
        openOrReturnToMapFeature(backStack, propId, planId2, featureB)
        assertEquals(2, backStack.size)
        assertEquals(planId2, (backStack[1] as Route.MapEditor).planId)
    }

    @Test
    fun testAddInfrastructureDetailsUnlessTop() {
        val propId = UUID.randomUUID()
        val itemA = UUID.randomUUID()
        val itemB = UUID.randomUUID()
        val backStack = mutableListOf<Route>()

        // 1. Initial add
        addInfrastructureDetailsUnlessTop(backStack, propId, itemA)
        assertEquals(1, backStack.size)
        assertEquals(itemA, (backStack[0] as Route.InfrastructureItemDetails).itemId)

        // 2. Exact same detail route at top is not duplicated
        addInfrastructureDetailsUnlessTop(backStack, propId, itemA)
        assertEquals(1, backStack.size)

        // 3. Different item detail is added
        addInfrastructureDetailsUnlessTop(backStack, propId, itemB)
        assertEquals(2, backStack.size)
        assertEquals(itemB, (backStack[1] as Route.InfrastructureItemDetails).itemId)

        // 4. History between different items remains intact (A -> B -> A)
        addInfrastructureDetailsUnlessTop(backStack, propId, itemA)
        assertEquals(3, backStack.size)
        assertEquals(itemA, (backStack[2] as Route.InfrastructureItemDetails).itemId)
    }

    @Test
    fun testLinkedRecordFromMapNavigation() {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        val backStack = mutableListOf<Route>()

        // Initial state: On Map
        backStack.add(Route.MapEditor(propId, planId, "feat-1"))
        
        // Open linked record from feature details
        addInfrastructureDetailsUnlessTop(backStack, propId, itemId)
        
        // Expected: Map is below details
        assertEquals(2, backStack.size)
        assertTrue(backStack[0] is Route.MapEditor)
        assertTrue(backStack[1] is Route.InfrastructureItemDetails)
        
        // Back from details
        backStack.removeAt(backStack.size - 1)
        assertEquals(1, backStack.size)
        assertTrue(backStack[0] is Route.MapEditor)
        assertEquals("feat-1", (backStack[0] as Route.MapEditor).featureId)
    }

    @Test
    fun testOpenOrReturnToInfrastructureOwner() {
        val propId = UUID.randomUUID()
        val itemA = UUID.randomUUID()
        val itemB = UUID.randomUUID()
        val backStack = mutableListOf<Route>()

        // 1. No matching route adds new
        openOrReturnToInfrastructureOwner(backStack, propId, itemA)
        assertEquals(1, backStack.size)
        assertTrue(backStack[0] is Route.InfrastructureItemDetails)
        assertEquals(itemA, (backStack[0] as Route.InfrastructureItemDetails).itemId)

        // 2. Matching route already on top does nothing
        openOrReturnToInfrastructureOwner(backStack, propId, itemA)
        assertEquals(1, backStack.size)

        // 3. Matching route exists below other screens: Pops back to it
        // [Details A, Maintenance list, Record details]
        backStack.add(Route.Maintenance(propId, itemA))
        backStack.add(Route.MaintenanceRecordDetails(propId, UUID.randomUUID()))
        assertEquals(3, backStack.size)
        
        openOrReturnToInfrastructureOwner(backStack, propId, itemA)
        assertEquals(1, backStack.size)
        assertEquals(itemA, (backStack[0] as Route.InfrastructureItemDetails).itemId)

        // 4. Different item details exist: Preserve history and open new
        openOrReturnToInfrastructureOwner(backStack, propId, itemB)
        assertEquals(2, backStack.size)
        assertEquals(itemB, (backStack[1] as Route.InfrastructureItemDetails).itemId)
        
        openOrReturnToInfrastructureOwner(backStack, propId, itemA)
        assertEquals(1, backStack.size) // Popped back to first A
    }
}
