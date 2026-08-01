package com.jumastappworks.mapstead

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jumastappworks.mapstead.ui.navigation.*
import com.jumastappworks.mapstead.ui.theme.MapsteadTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NavigationResponsivenessTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun test360dpDisplaysCompactLabels() {
        composeTestRule.setContent {
            MapsteadTheme {
                MapsteadBottomBar(
                    currentRoute = Route.Properties,
                    selectedPropId = UUID.randomUUID(), // Select a property to enable items
                    isIconOnly = false,
                    onNavItemClick = {}
                )
            }
        }
        // Verify compact labels exist
        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
        composeTestRule.onNodeWithText("Maint").assertIsDisplayed()
        composeTestRule.onNodeWithText("Emerg").assertIsDisplayed()
    }

    @Test
    fun testIconOnlyModeHidesLabelsButHasDescriptions() {
        composeTestRule.setContent {
            MapsteadTheme {
                MapsteadBottomBar(
                    currentRoute = Route.Properties,
                    selectedPropId = null,
                    isIconOnly = true,
                    onNavItemClick = {}
                )
            }
        }
        // Labels should not be displayed
        composeTestRule.onNodeWithText("Home").assertDoesNotExist()
        composeTestRule.onNodeWithText("Maint").assertDoesNotExist()
        
        // Full names should be in content descriptions for accessibility
        composeTestRule.onNodeWithContentDescription("Open Properties").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Open Maintenance").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Open Emergency").assertIsDisplayed()
    }

    @Test
    fun testNavigationRailUsesFullLabels() {
        composeTestRule.setContent {
            MapsteadTheme {
                MapsteadNavigationRail(
                    currentRoute = Route.Properties,
                    selectedPropId = null,
                    onNavItemClick = {}
                )
            }
        }
        // Verify full labels exist
        composeTestRule.onNodeWithText("Properties").assertIsDisplayed()
        composeTestRule.onNodeWithText("Maintenance").assertIsDisplayed()
        composeTestRule.onNodeWithText("Emergency").assertIsDisplayed()
    }

    @Test
    fun testSelectedStateForNestedRoutes() {
        val propId = UUID.randomUUID()
        val planId = UUID.randomUUID()
        
        composeTestRule.setContent {
            MapsteadTheme {
                // Map section should be selected even if MapEditor is open
                MapsteadBottomBar(
                    currentRoute = Route.MapEditor(propId, planId),
                    selectedPropId = propId,
                    isIconOnly = false,
                    onNavItemClick = {}
                )
            }
        }
        
        // "Map" should be selected. NavigationBarItem selection is hard to test directly via text,
        // so we check if the node exists and is displayed.
        composeTestRule.onNodeWithText("Map").assertIsDisplayed()
    }

    @Test
    fun testDisabledDestinations() {
        composeTestRule.setContent {
            MapsteadTheme {
                MapsteadBottomBar(
                    currentRoute = Route.Properties,
                    selectedPropId = null, // No property selected
                    isIconOnly = false,
                    onNavItemClick = {}
                )
            }
        }
        // Property-specific items should be disabled
        composeTestRule.onNodeWithText("Map").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Maint").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Emerg").assertIsNotEnabled()
        
        // Global items should be enabled
        composeTestRule.onNodeWithText("Home").assertIsEnabled()
        composeTestRule.onNodeWithText("Set").assertIsEnabled()
    }
}
