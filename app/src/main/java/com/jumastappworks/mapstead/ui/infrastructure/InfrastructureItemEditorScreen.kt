package com.jumastappworks.mapstead.ui.infrastructure

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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.help.HelpTopicId
import com.jumastappworks.mapstead.ui.components.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfrastructureItemEditorScreen(
    propertyId: UUID,
    itemId: UUID?,
    viewModel: InfrastructureItemEditorViewModel,
    onNavigateBack: () -> Unit,
    onSaveSuccess: (UUID) -> Unit,
    onHelpClick: (HelpTopicId) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val state by viewModel.uiState.collectAsState()

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
            viewModel.saveItem { onSaveSuccess(it) }
        }
    }

    val uiState = state as? InfrastructureItemEditorUiState.Ready
    val isWorking = uiState?.isSaving == true

    var showDiscardConfirm by remember { mutableStateOf(false) }

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
        onPrimaryAction = { onSaveClick() }
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
        
        uiState?.saveErrorRes?.let {
            item { Text(text = stringResource(it), color = MaterialTheme.colorScheme.error) }
        }
    }
}
