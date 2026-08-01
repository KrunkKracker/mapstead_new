package com.jumastappworks.mapstead.ui.infrastructure

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.attachments.AttachmentNavigationOrigin
import com.jumastappworks.mapstead.data.help.HelpTopicId
import com.jumastappworks.mapstead.data.relationships.RelationshipWriteResult
import com.jumastappworks.mapstead.ui.components.*
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfrastructureItemScreen(
    propertyId: UUID,
    itemId: UUID?,
    viewModel: InfrastructureItemViewModel,
    onNavigateBack: () -> Unit,
    onAddMaintenance: (UUID, UUID) -> Unit,
    onNavigateToFiles: (UUID) -> Unit,
    onNavigateToRelationships: (UUID) -> Unit,
    onNavigateToMaintenance: (UUID) -> Unit,
    onNavigateToEditor: (UUID, String, UUID, String?, String?, AttachmentNavigationOrigin) -> Unit,
    onNavigateToAttachmentDetails: (UUID, UUID, AttachmentNavigationOrigin) -> Unit,
    onNavigateToParentEditor: (UUID, UUID) -> Unit,
    onNavigateToRelationshipEditor: (UUID, UUID, UUID?) -> Unit,
    onNavigateToInfrastructureItem: (UUID, UUID) -> Unit,
    onHelpClick: (HelpTopicId) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val state by viewModel.uiState.collectAsState()
    val attachments by viewModel.attachments.collectAsState()
    val maintenanceCount by viewModel.maintenanceCount.collectAsState()
    val nextDueDate by viewModel.nextDueDate.collectAsState()
    val parentItem by viewModel.parentItem.collectAsState()
    val childrenItems by viewModel.childrenItems.collectAsState()
    val relationships by viewModel.relationships.collectAsState()
    val actionState by viewModel.actionState.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val nameController = rememberFormFieldVisibilityController()
    val categoryController = rememberFormFieldVisibilityController()
    val subtypeController = rememberFormFieldVisibilityController()
    val manufacturerController = rememberFormFieldVisibilityController()
    val modelController = rememberFormFieldVisibilityController()
    val serialController = rememberFormFieldVisibilityController()
    val providerController = rememberFormFieldVisibilityController()
    val phoneController = rememberFormFieldVisibilityController()
    val webController = rememberFormFieldVisibilityController()
    val instructionsController = rememberFormFieldVisibilityController()
    val emergencyController = rememberFormFieldVisibilityController()
    val notesController = rememberFormFieldVisibilityController()

    val nameFocusRequester = remember { FocusRequester() }
    val categoryFocusRequester = remember { FocusRequester() }

    LaunchedEffect(propertyId, itemId) {
        viewModel.loadItem(propertyId, itemId)
    }

    LaunchedEffect(viewModel.nameError) { 
        if (viewModel.nameError != null) { 
            nameFocusRequester.requestFocus()
            nameController.bringIntoView(coroutineScope) 
        } 
    }
    LaunchedEffect(viewModel.categoryError) { 
        if (viewModel.categoryError != null) { 
            categoryFocusRequester.requestFocus()
            categoryController.bringIntoView(coroutineScope) 
        } 
    }

    val onSaveClick = {
        if (viewModel.name.isBlank()) {
            nameFocusRequester.requestFocus()
            nameController.bringIntoView(coroutineScope)
        } else if (viewModel.category.isBlank()) {
            categoryFocusRequester.requestFocus()
            categoryController.bringIntoView(coroutineScope)
        } else {
            viewModel.saveItem { onNavigateBack() }
        }
    }

    val uiState = state as? InfrastructureItemUiState.Ready
    val isWorking = uiState?.isSaving == true || uiState?.isDeleting == true

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showRemoveParentConfirm by remember { mutableStateOf(false) }
    var relationshipToDelete by remember { mutableStateOf<UUID?>(null) }

    BackHandler(enabled = viewModel.isDirty()) {
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
        title = if (itemId == null) stringResource(R.string.add_item_title) else stringResource(R.string.edit_item_title),
        onBack = { if (viewModel.isDirty()) showDiscardConfirm = true else onNavigateBack() },
        isLoading = isWorking,
        primaryActionLabel = stringResource(R.string.save_label),
        onPrimaryAction = { onSaveClick() },
        secondaryAction = {
            if (itemId != null) {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isWorking
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.delete_item))
                }
            }
        }
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.general_info_header), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                ContextHelpLink(
                    text = stringResource(R.string.what_is_this),
                    onClick = { onHelpClick(HelpTopicId.INFRASTRUCTURE) },
                    modifier = Modifier.width(140.dp)
                )
            }
        }

        item {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { if (!isWorking) expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = stringResource(InfrastructureStatus.fromDatabaseValue(viewModel.status).labelRes),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.item_status_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, !isWorking).fillMaxWidth(),
                    enabled = !isWorking
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    InfrastructureStatus.entries.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(stringResource(s.labelRes)) },
                            onClick = { viewModel.status = s.databaseValue; expanded = false }
                        )
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                label = { Text(stringResource(R.string.item_name_label)) },
                placeholder = { Text(stringResource(R.string.item_name_hint)) },
                modifier = Modifier.fillMaxWidth().focusRequester(nameFocusRequester).bringIntoViewOnFocus(nameController),
                isError = viewModel.nameError != null,
                supportingText = viewModel.nameError?.let { { Text(stringResource(it)) } },
                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.PROPER_NAME),
                keyboardActions = KeyboardPolicy.getActions(focusManager),
                singleLine = true,
                enabled = !isWorking
            )
        }
        item {
            OutlinedTextField(
                value = viewModel.category,
                onValueChange = { viewModel.category = it },
                label = { Text(stringResource(R.string.item_category)) },
                placeholder = { Text(stringResource(R.string.item_category_hint)) },
                modifier = Modifier.fillMaxWidth().focusRequester(categoryFocusRequester).bringIntoViewOnFocus(categoryController),
                isError = viewModel.categoryError != null,
                supportingText = viewModel.categoryError?.let { { Text(stringResource(it)) } },
                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.PROPER_NAME),
                keyboardActions = KeyboardPolicy.getActions(focusManager),
                singleLine = true,
                enabled = !isWorking
            )
        }
        item {
            OutlinedTextField(
                value = viewModel.subtype,
                onValueChange = { viewModel.subtype = it },
                label = { Text(stringResource(R.string.item_subtype_label)) },
                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(subtypeController),
                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.PROPER_NAME),
                keyboardActions = KeyboardPolicy.getActions(focusManager),
                singleLine = true,
                enabled = !isWorking
            )
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                Checkbox(checked = viewModel.isEmergencyItem, onCheckedChange = { viewModel.isEmergencyItem = it }, enabled = !isWorking)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.emergency_item_label), style = MaterialTheme.typography.bodyLarge)
            }
        }

        item {
            Text(stringResource(R.string.equipment_details_header), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        item {
            OutlinedTextField(
                value = viewModel.manufacturer,
                onValueChange = { viewModel.manufacturer = it },
                label = { Text(stringResource(R.string.manufacturer_label)) },
                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(manufacturerController),
                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.PROPER_NAME),
                keyboardActions = KeyboardPolicy.getActions(focusManager),
                singleLine = true,
                enabled = !isWorking
            )
        }
        item {
            OutlinedTextField(
                value = viewModel.model,
                onValueChange = { viewModel.model = it },
                label = { Text(stringResource(R.string.model_label)) },
                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(modelController),
                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.IDENTIFIER),
                keyboardActions = KeyboardPolicy.getActions(focusManager),
                singleLine = true,
                enabled = !isWorking
            )
        }
        item {
            OutlinedTextField(
                value = viewModel.serialNumber,
                onValueChange = { viewModel.serialNumber = it },
                label = { Text(stringResource(R.string.serial_number_label)) },
                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(serialController),
                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.IDENTIFIER),
                keyboardActions = KeyboardPolicy.getActions(focusManager),
                singleLine = true,
                enabled = !isWorking
            )
        }

        item {
            Text(stringResource(R.string.service_support_header), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        item {
            OutlinedTextField(
                value = viewModel.serviceProvider,
                onValueChange = { viewModel.serviceProvider = it },
                label = { Text(stringResource(R.string.service_provider_label)) },
                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(providerController),
                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.PROPER_NAME),
                keyboardActions = KeyboardPolicy.getActions(focusManager),
                singleLine = true,
                enabled = !isWorking
            )
        }
        item {
            OutlinedTextField(
                value = viewModel.phoneNumber,
                onValueChange = { viewModel.phoneNumber = it },
                label = { Text(stringResource(R.string.support_phone_label)) },
                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(phoneController),
                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.IDENTIFIER),
                keyboardActions = KeyboardPolicy.getActions(focusManager),
                singleLine = true,
                enabled = !isWorking
            )
        }
        item {
            OutlinedTextField(
                value = viewModel.website,
                onValueChange = { viewModel.website = it },
                label = { Text(stringResource(R.string.support_website_label)) },
                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(webController),
                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.URL),
                keyboardActions = KeyboardPolicy.getActions(focusManager),
                singleLine = true,
                enabled = !isWorking
            )
        }

        item {
            Text(stringResource(R.string.instructions_header), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        item {
            OutlinedTextField(
                value = viewModel.instructions,
                onValueChange = { viewModel.instructions = it },
                label = { Text(stringResource(R.string.operating_instructions_label)) },
                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(instructionsController),
                minLines = 3,
                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.MULTILINE_PROSE),
                keyboardActions = KeyboardPolicy.getActions(focusManager),
                enabled = !isWorking
            )
        }
        item {
            OutlinedTextField(
                value = viewModel.emergencyInstructions,
                onValueChange = { viewModel.emergencyInstructions = it },
                label = { Text(stringResource(R.string.emergency_instructions_label)) },
                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(emergencyController),
                minLines = 3,
                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.MULTILINE_PROSE),
                keyboardActions = KeyboardPolicy.getActions(focusManager),
                enabled = !isWorking
            )
        }
        item {
            OutlinedTextField(
                value = viewModel.notes,
                onValueChange = { viewModel.notes = it },
                label = { Text(stringResource(R.string.notes_label)) },
                modifier = Modifier.fillMaxWidth().testTag("Infrastructure_Notes").bringIntoViewOnFocus(notesController),
                minLines = 3,
                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.MULTILINE_PROSE),
                keyboardActions = KeyboardPolicy.getActions(focusManager),
                enabled = !isWorking
            )
        }

        if (itemId != null) {
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            
            item {
                DetailSection(title = stringResource(R.string.attachments_header)) {
                    AttachmentsSection(
                        attachments = attachments,
                        onAddPhoto = { onNavigateToEditor(propertyId, "INFRASTRUCTURE", itemId, null, null, AttachmentNavigationOrigin.INFRASTRUCTURE) },
                        onTakeExtentPhoto = {
                            coroutineScope.launch {
                                viewModel.createCameraCapture()?.let { capture ->
                                    onNavigateToEditor(propertyId, "INFRASTRUCTURE", itemId, capture.uri.toString(), capture.token, AttachmentNavigationOrigin.INFRASTRUCTURE)
                                }
                            }
                        },
                        onAddDocument = { onNavigateToEditor(propertyId, "INFRASTRUCTURE", itemId, null, null, AttachmentNavigationOrigin.INFRASTRUCTURE) },
                        onViewAll = { onNavigateToFiles(propertyId) },
                        onAttachmentClick = { onNavigateToAttachmentDetails(propertyId, it, AttachmentNavigationOrigin.INFRASTRUCTURE) }
                    )
                }
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.parent_item_header)) },
                    supportingContent = { Text(parentItem?.name ?: stringResource(R.string.no_linked_item)) },
                    trailingContent = { 
                        Row {
                            if (parentItem != null && !isWorking) {
                                IconButton(onClick = { showRemoveParentConfirm = true }) { Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.remove_parent)) }
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null) 
                        }
                    },
                    modifier = Modifier.clickable(enabled = !isWorking) { onNavigateToParentEditor(propertyId, itemId) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.maintenance_history_section)) },
                    supportingContent = { Text(stringResource(R.string.record_count_label, maintenanceCount) + (nextDueDate?.let { " • Next due: $it" } ?: "")) },
                    trailingContent = { 
                        Row {
                            IconButton(onClick = { onNavigateToMaintenance(propertyId) }) { Icon(Icons.Default.History, contentDescription = stringResource(R.string.view_maintenance_button)) }
                            IconButton(onClick = { onAddMaintenance(propertyId, itemId) }) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_maintenance_record_button)) }
                        }
                    },
                    modifier = Modifier.clickable(enabled = !isWorking) { onNavigateToMaintenance(propertyId) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.relationships_header)) },
                    supportingContent = { Text(stringResource(R.string.connections_count_label, relationships.size)) },
                    trailingContent = { 
                        Row {
                            IconButton(onClick = { onNavigateToRelationshipEditor(propertyId, itemId, null) }) { Icon(Icons.Default.Add, contentDescription = "Add Connection") }
                            Icon(Icons.Default.ChevronRight, contentDescription = null) 
                        }
                    },
                    modifier = Modifier.clickable(enabled = !isWorking) { onNavigateToRelationships(propertyId) }
                )
            }

            if (relationships.isNotEmpty()) {
                items(relationships) { rel ->
                    ListItem(
                        headlineContent = { Text(rel.relatedItemName) },
                        supportingContent = { Text(rel.displayLabel) },
                        trailingContent = {
                             Row {
                                IconButton(onClick = { onNavigateToRelationshipEditor(propertyId, itemId, rel.relationshipId) }) { Icon(Icons.Default.Edit, contentDescription = "Edit Connection") }
                                IconButton(onClick = { relationshipToDelete = rel.relationshipId }) { Icon(Icons.Default.Delete, contentDescription = "Delete Connection") }
                             }
                        }
                    )
                }
            }
            
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.child_items_header), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    ContextHelpLink(
                        text = stringResource(R.string.what_is_this),
                        onClick = { onHelpClick(HelpTopicId.CONNECTIONS) },
                        modifier = Modifier.width(140.dp)
                    )
                }
            }
            
            if (childrenItems.isEmpty()) {
                item { Text(stringResource(R.string.no_child_components), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
            } else {
                items(childrenItems) { child ->
                    ListItem(
                        headlineContent = { Text(child.name) },
                        supportingContent = { Text(child.category) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                        modifier = Modifier.clickable(enabled = !isWorking) { onNavigateToInfrastructureItem(propertyId, child.id) }
                    )
                }
            }
        }
        
        uiState?.saveErrorRes?.let {
            item { Text(text = stringResource(it), color = MaterialTheme.colorScheme.error) }
        }
        uiState?.deleteErrorRes?.let {
            item { Text(text = stringResource(it), color = MaterialTheme.colorScheme.error) }
        }
        val relErrorRes = actionState?.let { RelationshipPresentation.getErrorRes(it) }
        if (relErrorRes != null) {
             item { Text(text = stringResource(relErrorRes), color = MaterialTheme.colorScheme.error) }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.confirm_delete_item_title)) },
            text = { Text(stringResource(R.string.delete_item_confirm, viewModel.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteItem(onNavigateBack)
                        showDeleteConfirm = false
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showRemoveParentConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveParentConfirm = false },
            title = { Text("Remove Parent?") },
            text = { Text("Are you sure you want to disconnect this item from its parent?") },
            confirmButton = {
                TextButton(onClick = { viewModel.removeParent(); showRemoveParentConfirm = false }) {
                    Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveParentConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (relationshipToDelete != null) {
        AlertDialog(
            onDismissRequest = { relationshipToDelete = null },
            title = { Text(stringResource(R.string.delete_connection_title)) },
            text = { Text(stringResource(R.string.delete_connection_message)) },
            confirmButton = {
                TextButton(onClick = { relationshipToDelete?.let { viewModel.removeRelationship(it) }; relationshipToDelete = null }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { relationshipToDelete = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
        content()
    }
}
