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
            formattedAddress = "123 Lane",
            needsAttentionTasks = emptyList(),
            upcomingTasks = emptyList(),
            recentlyAddedItems = emptyList(),
            hasAnyPropertyContent = true
        )

        composeTestRule.setContent {
            HomeContent(
                state = state,
                onAddSomething = {},
                onFindSomething = {},
                onOpenEmergency = {},
                onOpenTasks = {},
                onOpenItemDetails = {},
                onEditProperty = {},
                onNavigateToHelp = {},
                onDismissChecklist = {},
                onStepClick = {}
            )
        }

        composeTestRule.onNodeWithText("Maple Farm").assertExists()
    }

    @Test
    fun primary_actions_are_visible() {
        val state = HomeUiState.Ready(
            property = dummyProperty,
            formattedAddress = "",
            needsAttentionTasks = emptyList(),
            upcomingTasks = emptyList(),
            recentlyAddedItems = emptyList(),
            hasAnyPropertyContent = true
        )

        composeTestRule.setContent {
            HomeContent(
                state = state,
                onAddSomething = {},
                onFindSomething = {},
                onOpenEmergency = {},
                onOpenTasks = {},
                onOpenItemDetails = {},
                onEditProperty = {},
                onNavigateToHelp = {},
                onDismissChecklist = {},
                onStepClick = {}
            )
        }

        // Check for primary action labels (resources matched in English)
        composeTestRule.onNodeWithText("Add Something", ignoreCase = true).assertExists()
        composeTestRule.onNodeWithText("Find Something", ignoreCase = true).assertExists()
        composeTestRule.onNodeWithText("Emergency Guide", ignoreCase = true).assertExists()
    }

    @Test
    fun empty_needs_attention_shows_reassuring_message() {
        val state = HomeUiState.Ready(
            property = dummyProperty,
            formattedAddress = "",
            needsAttentionTasks = emptyList(),
            upcomingTasks = emptyList(),
            recentlyAddedItems = emptyList(),
            hasAnyPropertyContent = true
        )

        composeTestRule.setContent {
            HomeContent(
                state = state,
                onAddSomething = {},
                onFindSomething = {},
                onOpenEmergency = {},
                onOpenTasks = {},
                onOpenItemDetails = {},
                onEditProperty = {},
                onNavigateToHelp = {},
                onDismissChecklist = {},
                onStepClick = {}
            )
        }

        composeTestRule.onNodeWithText("Nothing needs attention right now.", ignoreCase = true).assertExists()
    }
}
