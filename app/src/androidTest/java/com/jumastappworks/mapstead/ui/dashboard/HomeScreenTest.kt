package com.jumastappworks.mapstead.ui.dashboard

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
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

    @Test
    fun mismatched_ready_id_displays_loading() {
        val otherId = UUID.randomUUID()
        val state = HomeUiState.Ready(
            property = dummyProperty, // ID mismatch with otherId
            formattedAddress = "",
            needsAttentionTasks = emptyList(),
            upcomingTasks = emptyList(),
            recentlyAddedItems = emptyList(),
            hasAnyPropertyContent = true
        )

        composeTestRule.setContent {
            HomeScreen(
                viewModel = mockk(relaxed = true) {
                    every { uiState } returns MutableStateFlow(state)
                },
                properties = listOf(dummyProperty),
                selectedPropertyId = otherId,
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

        // Should show Loading (CircularProgressIndicator)
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
        // Content should NOT exist
        composeTestRule.onNodeWithText("Add Something", ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun authoritative_title_shown_during_mismatch() {
        val otherProperty = PropertyEntity(id = UUID.randomUUID(), name = "Correct Name", propertyType = "Home")
        val state = HomeUiState.Ready(
            property = dummyProperty, // "Maple Farm"
            formattedAddress = "",
            needsAttentionTasks = emptyList(),
            upcomingTasks = emptyList(),
            recentlyAddedItems = emptyList(),
            hasAnyPropertyContent = true
        )

        composeTestRule.setContent {
            HomeScreen(
                viewModel = mockk(relaxed = true) {
                    every { uiState } returns MutableStateFlow(state)
                },
                properties = listOf(dummyProperty, otherProperty),
                selectedPropertyId = otherProperty.id, // Auth is "Correct Name"
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

        // Top bar title should be authoritative
        composeTestRule.onNodeWithText("Correct Name").assertExists()
        composeTestRule.onNodeWithText("Maple Farm").assertDoesNotExist()
    }
}
