package com.jumastappworks.mapstead.ui.mapping

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.data.mapping.GuidedFeaturePrefill
import com.jumastappworks.mapstead.data.mapping.SystemItemPolicy
import com.jumastappworks.mapstead.data.attachments.StagedCreationPhotoState
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class FeatureDetailSheetDeterministicTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val dummyFeature = MapFeatureEntity(
        id = UUID.randomUUID(),
        propertyId = UUID.randomUUID(),
        planId = UUID.randomUUID(),
        layerId = UUID.randomUUID(),
        geometryType = "POINT",
        geometryJson = "{}",
        label = "Test Item",
        coordinateSpace = "G",
        styleJson = "{}",
        accuracySource = "M"
    )

    @Test
    fun guided_new_feature_shows_creation_photo_section() {
        val prefill = GuidedFeaturePrefill(
            sessionId = UUID.randomUUID(),
            draftId = dummyFeature.id,
            suggestedLabelRes = R.string.label_well,
            suggestedLabel = "Well 1",
            suggestedCategory = "Utility",
            suggestedLayerId = UUID.randomUUID(),
            systemItemPolicy = SystemItemPolicy.OPTIONAL,
            presetStyle = "well"
        )

        composeTestRule.setContent {
            FeatureDetailSheet(
                feature = dummyFeature,
                layers = emptyList(),
                infrastructureItems = emptyList(),
                isSaving = false,
                isDeleting = false,
                labelError = null,
                accuracyError = null,
                errorMsg = null,
                onSave = {},
                onDelete = {},
                onDismiss = {},
                onSaveNewSystemItem = { UUID.randomUUID() },
                onMovePointClick = {},
                onEditShapeClick = {},
                isNewUnsavedFeature = true,
                guidedPrefill = prefill,
                stagedPhoto = StagedCreationPhotoState.None
            )
        }

        // Verify "Add a photo" action exists (CreationPhotoSection)
        // We look for the text of R.string.setup_add_photo_action
        composeTestRule.onNodeWithText("Add a photo", ignoreCase = true).assertExists()
        
        // Verify standard attachments section does NOT exist
        composeTestRule.onNodeWithText("Recent attachments", ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun existing_feature_shows_photos_and_files_section() {
        composeTestRule.setContent {
            FeatureDetailSheet(
                feature = dummyFeature,
                layers = emptyList(),
                infrastructureItems = emptyList(),
                isSaving = false,
                isDeleting = false,
                labelError = null,
                accuracyError = null,
                errorMsg = null,
                onSave = {},
                onDelete = {},
                onDismiss = {},
                onSaveNewSystemItem = { UUID.randomUUID() },
                onMovePointClick = {},
                onEditShapeClick = {},
                isNewUnsavedFeature = false,
                guidedPrefill = null,
                stagedPhoto = StagedCreationPhotoState.None
            )
        }

        // Verify "Recent attachments" exists
        composeTestRule.onNodeWithText("Recent attachments", ignoreCase = true).assertExists()
        
        // Verify "Add a photo" action does NOT exist
        composeTestRule.onNodeWithText("Add a photo", ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun photo_failure_shows_retry_ui() {
        val outcome = GuidedSaveOutcome.FeatureSavedPhotoFailed(UUID.randomUUID(), dummyFeature.id)
        
        composeTestRule.setContent {
            FeatureDetailSheet(
                feature = dummyFeature,
                layers = emptyList(),
                infrastructureItems = emptyList(),
                isSaving = false,
                isDeleting = false,
                labelError = null,
                accuracyError = null,
                errorMsg = null,
                onSave = {},
                onDelete = {},
                onDismiss = {},
                onSaveNewSystemItem = { UUID.randomUUID() },
                onMovePointClick = {},
                onEditShapeClick = {},
                isNewUnsavedFeature = false,
                saveOutcome = outcome,
                stagedPhoto = StagedCreationPhotoState.Ready("content://photo", "token")
            )
        }

        composeTestRule.onNodeWithText("Retry Photo").assertExists()
        composeTestRule.onNodeWithText("Continue Without Photo").assertExists()
    }
}
