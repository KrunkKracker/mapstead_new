package com.jumastappworks.mapstead.ui.relationships

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.relationships.ItemRelationshipType
import com.jumastappworks.mapstead.ui.components.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelationshipEditorScreen(
    propertyId: UUID,
    currentItemId: UUID,
    relationshipId: UUID?,
    viewModel: RelationshipEditorViewModel,
    onNavigateBack: () -> Unit,
    onHelpClick: (com.jumastappworks.mapstead.data.help.HelpTopicId) -> Unit
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val state by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val descController = rememberFormFieldVisibilityController()
    val relatedItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(propertyId, currentItemId, relationshipId) {
        viewModel.init(propertyId, currentItemId, relationshipId)
    }

    when (val uiState = state) {
        is RelationshipEditorUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is RelationshipEditorUiState.NotFound -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.property_not_found))
            }
        }
        is RelationshipEditorUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(uiState.message, color = MaterialTheme.colorScheme.error)
            }
        }
        is RelationshipEditorUiState.Ready -> {
            if (uiState.saved) {
                LaunchedEffect(Unit) { onNavigateBack() }
            }

            var showDeleteConfirm by remember { mutableStateOf(false) }
            var showDiscardConfirm by remember { mutableStateOf(false) }
            
            // Refined dirty check
            val isDirty = uiState.relatedItemId != null || uiState.description.isNotBlank()

            BackHandler(enabled = isDirty && !uiState.saved) {
                showDiscardConfirm = true
            }

            if (showDiscardConfirm) {
                AlertDialog(
                    onDismissRequest = { showDiscardConfirm = false },
                    title = { Text(stringResource(R.string.discard_changes_title)) },
                    text = { Text(stringResource(R.string.discard_changes_message)) },
                    confirmButton = {
                        TextButton(onClick = { showDiscardConfirm = false; onNavigateBack() }) {
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
                title = if (relationshipId == null) stringResource(R.string.add_label) + " " + stringResource(R.string.connections_count_label, 1) else stringResource(R.string.edit_item_title),
                onBack = { if (isDirty && !uiState.saved) showDiscardConfirm = true else onNavigateBack() },
                isLoading = uiState.isSaving,
                primaryActionLabel = stringResource(R.string.save_label),
                onPrimaryAction = {
                    if (uiState.relatedItemId == null) {
                        relatedItemFocusRequester.requestFocus()
                    } else {
                        viewModel.save()
                    }
                },
                secondaryAction = {
                    if (relationshipId != null) {
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isSaving
                        ) {
                            Text(stringResource(R.string.delete_connection_title))
                        }
                    }
                }
            ) {
                if (uiState.availableItems.isEmpty()) {
                    item {
                        EmptyState(
                            title = stringResource(R.string.empty_infra_title),
                            description = stringResource(R.string.relationship_no_available_items),
                            icon = Icons.Default.Foundation,
                            helpTopicId = com.jumastappworks.mapstead.data.help.HelpTopicId.CONNECTIONS,
                            onHelpClick = onHelpClick,
                            useFullHeight = false
                        )
                    }
                }

                item {
                    Text(stringResource(R.string.linked_item_header) + ": ${uiState.currentItemName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                item {
                    Text(stringResource(R.string.select_item), style = MaterialTheme.typography.labelLarge)
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { if (!uiState.isSaving) expanded = !expanded }
                    ) {
                        val selectedItem = uiState.availableItems.find { it.id == uiState.relatedItemId }
                        OutlinedTextField(
                            value = selectedItem?.name ?: stringResource(R.string.select_item),
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, !uiState.isSaving).fillMaxWidth().focusRequester(relatedItemFocusRequester),
                            enabled = !uiState.isSaving
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            uiState.availableItems.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text("${item.name} (${item.category})") },
                                    onClick = {
                                        viewModel.onRelatedItemChange(item.id)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    Text(stringResource(R.string.relationships_header), style = MaterialTheme.typography.labelLarge)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ItemRelationshipType.entries.forEach { type ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                                RadioButton(
                                    selected = uiState.selectedType == type,
                                    onClick = { if (!uiState.isSaving) viewModel.onTypeChange(type) },
                                    enabled = !uiState.isSaving
                                )
                                Text(stringResource(type.labelRes))
                            }
                        }
                    }
                }

                item {
                    Text(stringResource(R.string.direction_label), style = MaterialTheme.typography.labelLarge)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                            RadioButton(selected = uiState.isOutgoing, onClick = { if (!uiState.isSaving) viewModel.onDirectionChange(true) }, enabled = !uiState.isSaving)
                            Text(stringResource(R.string.direction_outgoing, uiState.currentItemName))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                            RadioButton(selected = !uiState.isOutgoing, onClick = { if (!uiState.isSaving) viewModel.onDirectionChange(false) }, enabled = !uiState.isSaving)
                            Text(stringResource(R.string.direction_incoming, uiState.currentItemName))
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = uiState.description,
                        onValueChange = { viewModel.onDescriptionChange(it) },
                        label = { Text(stringResource(R.string.description_label) + " " + stringResource(R.string.optional)) },
                        modifier = Modifier.fillMaxWidth().testTag("Relationship_Description").bringIntoViewOnFocus(descController),
                        minLines = 3,
                        keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.MULTILINE_PROSE),
                        keyboardActions = KeyboardPolicy.getActions(focusManager),
                        enabled = !uiState.isSaving
                    )
                }

                if (uiState.errorRes != null) {
                    item {
                        Text(text = stringResource(uiState.errorRes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text(stringResource(R.string.delete_connection_title)) },
                    text = { Text(stringResource(R.string.delete_connection_message)) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.deleteRelationship(); showDeleteConfirm = false }) {
                            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
                    }
                )
            }
        }
    }
}

@Composable
fun getRelationshipDisplayName(type: ItemRelationshipType): String {
    return when (type) {
        ItemRelationshipType.FEEDS -> "Feeds"
        ItemRelationshipType.CONTROLS -> "Controls"
        ItemRelationshipType.PROTECTS -> "Protects"
        ItemRelationshipType.DRAINS_TO -> "Drains To"
        ItemRelationshipType.SERVES -> "Serves"
        ItemRelationshipType.DEPENDS_ON -> "Depends On"
        ItemRelationshipType.CONNECTED_TO -> "Connected To"
        ItemRelationshipType.OTHER -> "Other"
    }
}
