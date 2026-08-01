package com.jumastappworks.mapstead.ui.attachments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.attachments.AttachmentType
import com.jumastappworks.mapstead.data.attachments.AttachmentOwner
import com.jumastappworks.mapstead.data.attachments.AttachmentSaveResult
import com.jumastappworks.mapstead.ui.components.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentEditorScreen(
    propertyId: UUID,
    ownerType: String,
    ownerId: UUID?,
    attachmentId: UUID?,
    stagedFileUri: String?,
    cameraCaptureToken: String?,
    viewModel: AttachmentEditorViewModel,
    onNavigateBack: () -> Unit
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val state by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    val nameController = rememberFormFieldVisibilityController()
    val captionController = rememberFormFieldVisibilityController()
    val nameFocusRequester = remember { FocusRequester() }

    LaunchedEffect(propertyId, ownerType, ownerId, attachmentId, stagedFileUri, cameraCaptureToken) {
        viewModel.init(propertyId, ownerType, ownerId, attachmentId, stagedFileUri, cameraCaptureToken)
    }

    val uiState = state
    if (uiState == null || uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (uiState.saveResult is AttachmentSaveResult.Saved) {
        LaunchedEffect(Unit) { onNavigateBack() }
    }

    LaunchedEffect(uiState.errorRes) {
        if (uiState.errorRes == R.string.attachment_display_name_required) {
            nameFocusRequester.requestFocus()
            nameController.bringIntoView(coroutineScope)
        }
    }

    var showDiscardConfirm by remember { mutableStateOf(false) }
    val isDirty = uiState.isDirty()

    BackHandler(enabled = isDirty) {
        showDiscardConfirm = true
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text(stringResource(R.string.discard_changes_title)) },
            text = { Text(stringResource(R.string.discard_changes_message)) },
            confirmButton = {
                TextButton(onClick = { showDiscardConfirm = false; viewModel.onCancel(); onNavigateBack() }) {
                    Text(stringResource(R.string.discard_changes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text(stringResource(R.string.keep_editing))
                }
            }
        )
    }

    MapsteadFormScaffold(
        title = if (attachmentId == null) stringResource(R.string.add_attachment_title) else stringResource(R.string.edit_attachment_title),
        onBack = { if (isDirty) showDiscardConfirm = true else { viewModel.onCancel(); onNavigateBack() } },
        isLoading = uiState.isSaving,
        primaryActionLabel = stringResource(R.string.save_label),
        onPrimaryAction = { viewModel.save() }
    ) {
        if (uiState.saveResult is AttachmentSaveResult.SavedWithCoverWarning) {
            item {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                    Text(
                        text = stringResource(R.string.cover_warning_message),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        uiState.errorRes?.let { err ->
            item {
                Text(text = stringResource(err), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                if (uiState.isImage && uiState.stagedFileUri != null) {
                    AsyncImage(
                        model = uiState.stagedFileUri,
                        contentDescription = "Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            if (uiState.attachmentType == AttachmentType.Photo) Icons.Default.Image else Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        item {
            Text(stringResource(R.string.attachment_owner_label) + " " + uiState.ownerDisplayName, style = MaterialTheme.typography.labelLarge)
        }

        item {
            OutlinedTextField(
                value = uiState.displayName,
                onValueChange = { viewModel.onDisplayNameChange(it) },
                label = { Text(stringResource(R.string.attachment_display_name)) },
                modifier = Modifier.fillMaxWidth().focusRequester(nameFocusRequester).bringIntoViewOnFocus(nameController),
                isError = uiState.errorRes == R.string.attachment_display_name_required,
                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.PROPER_NAME),
                keyboardActions = KeyboardPolicy.getActions(focusManager),
                singleLine = true,
                enabled = !uiState.isSaving
            )
        }

        item {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { if (!uiState.isSaving) expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = uiState.attachmentType.canonicalName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.attachment_type)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, !uiState.isSaving).fillMaxWidth(),
                    enabled = !uiState.isSaving
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    AttachmentType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.canonicalName) },
                            onClick = {
                                viewModel.onTypeChange(type)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        if (uiState.owner is AttachmentOwner.MapFeature && uiState.isImage) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                    Checkbox(checked = uiState.isCover, onCheckedChange = { viewModel.onCoverChange(it) }, enabled = !uiState.isSaving)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.cover_photo_checkbox))
                }
            }
        }

        item {
            OutlinedTextField(
                value = uiState.caption,
                onValueChange = { viewModel.onCaptionChange(it) },
                label = { Text(stringResource(R.string.caption_optional)) },
                modifier = Modifier.fillMaxWidth().testTag("Attachment_Caption").bringIntoViewOnFocus(captionController),
                minLines = 2,
                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.MULTILINE_PROSE),
                keyboardActions = KeyboardPolicy.getActions(focusManager),
                enabled = !uiState.isSaving
            )
        }
    }
}
