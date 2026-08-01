package com.jumastappworks.mapstead.ui.maintenance

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class MaintenanceWorkflowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel = mockk<MaintenanceViewModel>(relaxed = true)

    @Test
    fun testMaintenanceHubEmptyState() {
        val propertyId = UUID.randomUUID()
        val property = PropertyEntity(id = propertyId, name = "My Property", propertyType = "Home")
        val uiState = MaintenanceUiState.Ready(
            property = property,
            allRecords = emptyList(),
            filteredRecords = emptyList(),
            infrastructureItems = emptyList(),
            reminders = emptyList(),
            selectedFilter = MaintenanceFilter.All,
            counts = MaintenanceCounts()
        )

        every { viewModel.uiState } returns MutableStateFlow(uiState)

        composeTestRule.setContent {
            MaintenanceScreen(
                viewModel = viewModel,
                onBack = {},
                onAddRecord = {},
                onOpenRecord = { _, _ -> },
                onHelpClick = {}
            )
        }

        composeTestRule.onNodeWithText("No all tasks found.").assertIsDisplayed()
        composeTestRule.onNodeWithText("My Property").assertIsDisplayed()
    }

    @Test
    fun testMaintenanceHubPopulatedList() {
        val propertyId = UUID.randomUUID()
        val record = com.jumastappworks.mapstead.data.db.entities.MaintenanceRecordEntity(
            propertyId = propertyId,
            title = "Check Roof",
            category = "Inspection",
            serviceDate = java.time.LocalDate.now(),
            status = "Scheduled"
        )
        val uiState = MaintenanceUiState.Ready(
            property = PropertyEntity(id = propertyId, name = "P1", propertyType = "H"),
            allRecords = listOf(record),
            filteredRecords = listOf(record),
            infrastructureItems = emptyList(),
            reminders = emptyList(),
            selectedFilter = MaintenanceFilter.All,
            counts = MaintenanceCounts()
        )

        every { viewModel.uiState } returns MutableStateFlow(uiState)

        composeTestRule.setContent {
            MaintenanceScreen(
                viewModel = viewModel,
                onBack = {},
                onAddRecord = {},
                onOpenRecord = { _, _ -> },
                onHelpClick = {}
            )
        }

        composeTestRule.onNodeWithText("Check Roof").assertIsDisplayed()
        composeTestRule.onNodeWithText("Inspection").assertIsDisplayed()
    }
}
