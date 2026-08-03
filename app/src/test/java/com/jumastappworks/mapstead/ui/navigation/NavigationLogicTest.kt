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
}
