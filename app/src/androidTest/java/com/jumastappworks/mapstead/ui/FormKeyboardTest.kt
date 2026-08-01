package com.jumastappworks.mapstead.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.jumastappworks.mapstead.MainActivity
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class FormKeyboardTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testPropertySetupWizardKeyboardAndFlow() {
        val uniquePropName = "Setup Wizard ${UUID.randomUUID().toString().take(4)}"
        
        // 1. Start Wizard
        composeTestRule.onNodeWithContentDescription("Open Properties").performClick()
        
        // Handle Welcome card if present, otherwise Your Properties list
        val createMyPropertyButton = composeTestRule.onAllNodesWithText("Create My Property")
        if (createMyPropertyButton.fetchSemanticsNodes().isNotEmpty()) {
            createMyPropertyButton.onFirst().performClick()
        } else {
            composeTestRule.onNodeWithText("Add Property").performClick()
        }

        // 2. Step 1: Name
        val nameInput = composeTestRule.onNodeWithTag("Setup_NameInput")
        nameInput.performTextInput(uniquePropName)
        
        // Verify primary button is reachable (sticky/above keyboard)
        composeTestRule.onNodeWithTag("Setup_ContinueToLocate").assertIsDisplayed().performClick()

        // 3. Step 2: Locate - Choose Add Location Later
        composeTestRule.onNodeWithText("Add Location Later").performClick()

        // 4. Step 3: Review
        val createButton = composeTestRule.onNodeWithTag("Setup_FinalCreate")
        createButton.assertIsDisplayed().performClick()

        // 5. Verify landed on Dashboard
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithText(uniquePropName).onFirst().isDisplayed()
        }
    }

    @Test
    fun testLocationlessDashboardAction() {
        val uniquePropName = "Locless Dashboard ${UUID.randomUUID().toString().take(4)}"
        
        // 1. Create locationless property
        composeTestRule.onNodeWithContentDescription("Open Properties").performClick()
        val addPropertyButton = composeTestRule.onAllNodesWithText("Add Property")
        if (addPropertyButton.fetchSemanticsNodes().isNotEmpty()) {
            addPropertyButton.onFirst().performClick()
        } else {
            composeTestRule.onNodeWithText("Create My Property").performClick()
        }
        
        composeTestRule.onNodeWithTag("Setup_NameInput").performTextInput(uniquePropName)
        composeTestRule.onNodeWithTag("Setup_ContinueToLocate").performClick()
        composeTestRule.onNodeWithText("Add Location Later").performClick()
        composeTestRule.onNodeWithTag("Setup_FinalCreate").performClick()

        // 2. Verify dashboard shows "Add Property Location" action
        composeTestRule.waitUntil(10000) {
            composeTestRule.onNodeWithText("Add Property Location").isDisplayed()
        }
        composeTestRule.onNodeWithText("Add Property Location").performClick()
        
        // 3. Confirm we land back in the wizard-style Locate step
        composeTestRule.onNodeWithText("Search by Address").assertIsDisplayed()
        composeTestRule.onNodeWithText("Use My Current Location").assertIsDisplayed()
    }

    @Test
    fun testCreatePlanManualCoordinateFocusAndNavigation() {
        val uniquePropName = "KB Focus Prop ${UUID.randomUUID().toString().take(4)}"
        
        // 1. Create a prerequisite property independently
        composeTestRule.onNodeWithContentDescription("Open Properties").performClick()
        val addPropertyButton = composeTestRule.onAllNodesWithText("Add Property")
        if (addPropertyButton.fetchSemanticsNodes().isNotEmpty()) {
            addPropertyButton.onFirst().performClick()
        } else {
            composeTestRule.onNodeWithText("Create My Property").performClick()
        }
        
        composeTestRule.onNodeWithTag("Setup_NameInput").performTextInput(uniquePropName)
        composeTestRule.onNodeWithTag("Setup_ContinueToLocate").performClick()
        composeTestRule.onNodeWithText("Add Location Later").performClick()
        composeTestRule.onNodeWithTag("Setup_FinalCreate").performClick()

        // 2. Wait for dashboard and open Map/Plans
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("OpenMapPlansButton").onFirst().isDisplayed()
        }
        composeTestRule.onNodeWithTag("OpenMapPlansButton").performClick()
        
        // 3. Navigate to Create Plan
        composeTestRule.onNodeWithTag("CreatePlanButton").performClick()

        // 4. Toggle Manual Coordinates
        composeTestRule.onNodeWithText("Coordinates").performClick()

        // 5. Verify focus and navigation
        val latField = composeTestRule.onNodeWithTag("CreatePlan_Latitude")
        val lngField = composeTestRule.onNodeWithTag("CreatePlan_Longitude")

        latField.performClick()
        latField.assertIsFocused()
        latField.performTextInput("45.0")
        
        // Next action should move focus to longitude
        latField.performImeAction()
        
        lngField.assertIsFocused()
        lngField.performTextInput("-75.0")
        
        // Done action should clear focus
        lngField.performImeAction()
        lngField.assertIsNotFocused()
        
        // 6. Verify Apply button is reachable
        composeTestRule.onNodeWithTag("CreatePlan_Apply").assertExists().assertIsDisplayed()
    }
}
