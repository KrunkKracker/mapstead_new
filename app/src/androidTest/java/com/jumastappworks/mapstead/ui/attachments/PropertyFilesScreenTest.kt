package com.jumastappworks.mapstead.ui.attachments

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jumastappworks.mapstead.data.db.entities.AttachmentEntity
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class PropertyFilesScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel = mockk<PropertyFilesViewModel>(relaxed = true)

    @Test
    fun testEmptyStateRendering() {
        val propertyId = UUID.randomUUID()
        val state = PropertyFilesUiState(
            propertyId = propertyId,
            propertyName = "My Property",
            attachments = emptyList(),
            filteredAttachments = emptyList(),
            isLoading = false
        )
        
        every { viewModel.uiState } returns MutableStateFlow(state)

        composeTestRule.setContent {
            PropertyFilesScreen(
                propertyId = propertyId,
                viewModel = viewModel,
                onNavigateBack = {},
                onAttachmentClick = {},
                onNavigateToEditor = { _, _, _, _, _ -> },
                onHelpClick = {}
            )
        }

        composeTestRule.onNodeWithText("No files yet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add Attachment").assertIsDisplayed()
    }

    @Test
    fun testPopulatedListRendering() {
        val propertyId = UUID.randomUUID()
        val attachment = AttachmentEntity(
            id = UUID.randomUUID(),
            propertyId = propertyId,
            attachmentType = "Photo",
            localUri = "content://dummy",
            displayName = "Test Photo",
            mimeType = "image/jpeg",
            fileSizeBytes = 1024 * 1024L
        )
        val model = AttachmentListItemUiModel(
            attachment = attachment,
            previewUri = null
        )
        
        val state = PropertyFilesUiState(
            propertyId = propertyId,
            propertyName = "My Property",
            attachments = listOf(model),
            filteredAttachments = listOf(model),
            isLoading = false
        )
        
        every { viewModel.uiState } returns MutableStateFlow(state)

        composeTestRule.setContent {
            PropertyFilesScreen(
                propertyId = propertyId,
                viewModel = viewModel,
                onNavigateBack = {},
                onAttachmentClick = {},
                onNavigateToEditor = { _, _, _, _, _ -> },
                onHelpClick = {}
            )
        }

        composeTestRule.onNodeWithText("Test Photo").assertIsDisplayed()
        composeTestRule.onNodeWithText("1.0 MB").assertIsDisplayed()
        composeTestRule.onNodeWithText("Photo").assertIsDisplayed()
    }

    @Test
    fun testMissingFilePlaceholder() {
        val propertyId = UUID.randomUUID()
        val attachment = AttachmentEntity(
            id = UUID.randomUUID(),
            propertyId = propertyId,
            attachmentType = "Photo",
            localUri = "content://dummy",
            displayName = "Missing Photo",
            mimeType = "image/jpeg",
            fileSizeBytes = 100L
        )
        val model = AttachmentListItemUiModel(
            attachment = attachment,
            previewUri = null,
            isMissing = true
        )
        
        val state = PropertyFilesUiState(
            propertyId = propertyId,
            attachments = listOf(model),
            filteredAttachments = listOf(model),
            isLoading = false
        )
        
        every { viewModel.uiState } returns MutableStateFlow(state)

        composeTestRule.setContent {
            PropertyFilesScreen(
                propertyId = propertyId,
                viewModel = viewModel,
                onNavigateBack = {},
                onAttachmentClick = {},
                onNavigateToEditor = { _, _, _, _, _ -> },
                onHelpClick = {}
            )
        }

        // We use Icons.Default.LinkOff for missing files
        // We can check for the error color or just absence of the image if we had a tag
        // For now, check if metadata is still there
        composeTestRule.onNodeWithText("Missing Photo").assertIsDisplayed()
    }

    @Test
    fun testDamagedFilePlaceholder() {
        val propertyId = UUID.randomUUID()
        val attachment = AttachmentEntity(
            id = UUID.randomUUID(),
            propertyId = propertyId,
            attachmentType = "Document",
            localUri = "content://dummy",
            displayName = "Damaged Doc",
            mimeType = "application/pdf",
            fileSizeBytes = 100L
        )
        val model = AttachmentListItemUiModel(
            attachment = attachment,
            previewUri = null,
            isDamaged = true
        )
        
        val state = PropertyFilesUiState(
            propertyId = propertyId,
            attachments = listOf(model),
            filteredAttachments = listOf(model),
            isLoading = false
        )
        
        every { viewModel.uiState } returns MutableStateFlow(state)

        composeTestRule.setContent {
            PropertyFilesScreen(
                propertyId = propertyId,
                viewModel = viewModel,
                onNavigateBack = {},
                onAttachmentClick = {},
                onNavigateToEditor = { _, _, _, _, _ -> },
                onHelpClick = {}
            )
        }

        composeTestRule.onNodeWithText("Damaged Doc").assertIsDisplayed()
    }
}
