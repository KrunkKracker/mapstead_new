package com.jumastappworks.mapstead.ui.dashboard

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val dummyProperty = PropertyEntity(
        id = UUID.randomUUID(),
        name = "Maple Farm",
        propertyType = "Farm"
    )

    @Test
    fun selected_property_name_is_displayed() {
        val state = HomeUiState.Ready(
            property = dummyProperty,
            needsAttentionTasks = emptyList(),
            upcomingTasks = emptyList(),
            recentlyAddedItems = emptyList()
        )

        composeTestRule.setContent {
            HomeScreenContent(
                uiState = state,
                properties = listOf(dummyProperty),
                selectedPropertyId = dummyProperty.id,
                onSelectProperty = {},
                onAddProperty = {},
                onManageProperties = {},
                onNavigateToSettings = {},
                onNavigateToHelp = {},
                onAddSomething = {},
                onFindSomething = {},
                onOpenEmergency = {},
                onOpenTasks = {},
                onOpenItemDetails = {},
                onEditProperty = {}
            )
        }

        composeTestRule.onNodeWithText("Maple Farm").assertExists()
    }

    @Test
    fun primary_actions_are_visible() {
        val state = HomeUiState.Ready(
            property = dummyProperty
        )

        composeTestRule.setContent {
            HomeScreenContent(
                uiState = state,
                properties = listOf(dummyProperty),
                selectedPropertyId = dummyProperty.id,
                onSelectProperty = {},
                onAddProperty = {},
                onManageProperties = {},
                onNavigateToSettings = {},
                onNavigateToHelp = {},
                onAddSomething = {},
                onFindSomething = {},
                onOpenEmergency = {},
                onOpenTasks = {},
                onOpenItemDetails = {},
                onEditProperty = {}
            )
        }

        // Check for primary action labels (using default English values for now, 
        // but in real app they come from resources)
        // We can use resource IDs if we use AndroidComposeTestRule, but createComposeRule is for unit-like tests.
        // Mapstead usually uses text matching in these tests.
        composeTestRule.onNodeWithText("Add Something", ignoreCase = true).assertExists()
        composeTestRule.onNodeWithText("Find Something", ignoreCase = true).assertExists()
        composeTestRule.onNodeWithText("Emergency Guide", ignoreCase = true).assertExists()
    }

    @Test
    fun empty_needs_attention_shows_reassuring_message() {
        val state = HomeUiState.Ready(
            property = dummyProperty,
            needsAttentionTasks = emptyList()
        )

        composeTestRule.setContent {
            HomeScreenContent(
                uiState = state,
                properties = listOf(dummyProperty),
                selectedPropertyId = dummyProperty.id,
                onSelectProperty = {},
                onAddProperty = {},
                onManageProperties = {},
                onNavigateToSettings = {},
                onNavigateToHelp = {},
                onAddSomething = {},
                onFindSomething = {},
                onOpenEmergency = {},
                onOpenTasks = {},
                onOpenItemDetails = {},
                onEditProperty = {}
            )
        }

        composeTestRule.onNodeWithText("Nothing needs attention right now.", ignoreCase = true).assertExists()
    }
}
