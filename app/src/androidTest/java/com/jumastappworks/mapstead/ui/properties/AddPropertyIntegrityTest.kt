package com.jumastappworks.mapstead.ui.properties

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.jumastappworks.mapstead.MainActivity
import com.jumastappworks.mapstead.R
import org.junit.Rule
import org.junit.Test

class AddPropertyIntegrityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun confirmed_location_navigation_to_review() {
        // Start adding property
        composeTestRule.onNodeWithText("Add Property").performClick()
        
        // Step 1: Name
        composeTestRule.onNodeWithTag("Setup_NameInput").performTextInput("Instrumented Prop")
        composeTestRule.onNodeWithTag("Setup_ContinueToLocate").performClick()
        
        // Step 2: Locate - Choose on Map
        composeTestRule.onNodeWithText("Choose on Map").performClick()
        
        // Confirm initial location in picker
        composeTestRule.onNodeWithText("Confirm Property Location").performClick()
        
        // Confirm candidate in overlay
        composeTestRule.onNodeWithText("Confirm").performClick()
        
        // Step 3: Review
        composeTestRule.onNodeWithText("Review Your Property").assertExists()
        composeTestRule.onNodeWithText("Pinned Location").assertExists()
        
        // Final Create
        composeTestRule.onNodeWithTag("Setup_FinalCreate").performClick()
        
        // Verify reached Dashboard (Check for "Property Dashboard" title or similar)
        composeTestRule.onNodeWithText("Property Dashboard").assertExists()
    }
}
