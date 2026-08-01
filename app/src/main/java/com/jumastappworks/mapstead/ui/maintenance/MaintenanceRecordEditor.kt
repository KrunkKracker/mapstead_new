package com.jumastappworks.mapstead.ui.maintenance

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.ui.components.*
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceRecordEditor(
    propertyId: UUID,
    recordId: UUID?,
    infrastructureItemId: UUID? = null,
    viewModel: MaintenanceViewModel,
    onNavigateBack: () -> Unit
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val state by viewModel.editorState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    val titleController = rememberFormFieldVisibilityController()
    val categoryController = rememberFormFieldVisibilityController()
    val providerController = rememberFormFieldVisibilityController()
    val costController = rememberFormFieldVisibilityController()
    val descController = rememberFormFieldVisibilityController()

    val titleFocusRequester = remember { FocusRequester() }
    val categoryFocusRequester = remember { FocusRequester() }
    val costFocusRequester = remember { FocusRequester() }

    LaunchedEffect(propertyId, recordId) {
        viewModel.startEditing(propertyId, recordId, infrastructureItemId)
    }

    val uiState = state
    if (uiState is MaintenanceRecordEditorUiState.Loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (uiState is MaintenanceRecordEditorUiState.Saved) {
        LaunchedEffect(Unit) { onNavigateBack() }
        return
    }

    if (uiState !is MaintenanceRecordEditorUiState.Ready) return

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

    LaunchedEffect(uiState.validationErrors) {
        if (uiState.validationErrors.isNotEmpty()) {
            val firstErrorField = when {
                uiState.validationErrors.containsKey("title") -> "title"
                uiState.validationErrors.containsKey("category") -> "category"
                uiState.validationErrors.containsKey("cost") -> "cost"
                else -> null
            }
            when (firstErrorField) {
                "title" -> { titleFocusRequester.requestFocus(); titleController.bringIntoView(coroutineScope) }
                "category" -> { categoryFocusRequester.requestFocus(); categoryController.bringIntoView(coroutineScope) }
                "cost" -> { costFocusRequester.requestFocus(); costController.bringIntoView(coroutineScope) }
            }
        }
    }

    MapsteadFormScaffold(
        title = if (recordId == null) stringResource(R.string.add_maintenance_title) else stringResource(R.string.edit_maintenance_title),
        onBack = { if (isDirty) showDiscardConfirm = true else onNavigateBack() },
        isLoading = uiState.isSaving,
        primaryActionLabel = stringResource(R.string.save_label),
        onPrimaryAction = { viewModel.saveEditorRecord() }
    ) {
        item {
            Text(stringResource(R.string.linked_item_header), style = MaterialTheme.typography.labelLarge)
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { if (!uiState.isSaving) expanded = !expanded }
            ) {
                val selectedItem = uiState.infrastructureItems.find { it.id == uiState.selectedInfrastructureItemId }
                OutlinedTextField(
                    value = selectedItem?.name ?: stringResource(R.string.no_linked_item),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.item_name_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    enabled = !uiState.isSaving
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.no_linked_item)) },
                        onClick = { viewModel.updateEditorInfrastructureItem(null); expanded = false }
                    )
                    uiState.infrastructureItems.forEach { item ->
                        DropdownMenuItem(
                            text = { Text("${item.name} (${item.category})") },
                            onClick = { viewModel.updateEditorInfrastructureItem(item.id); expanded = false }
                        )
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = uiState.title,
                onValueChange = { viewModel.updateEditorTitle(it) },
                label = { Text(stringResource(R.string.maintenance_title_label)) },
                placeholder = { Text(stringResource(R.string.maintenance_title_hint)) },
                modifier = Modifier.fillMaxWidth()
                    .focusRequester(titleFocusRequester)
                    .bringIntoViewOnFocus(titleController),
                isError = uiState.validationErrors.containsKey("title"),
                supportingText = uiState.validationErrors["title"]?.let { { Text(stringResource(it)) } },
                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.PROPER_NAME),
                keyboardActions = KeyboardPolicy.getActions(focusManager),
                singleLine = true,
                enabled = !uiState.isSaving
            )
        }

        item {
            OutlinedTextField(
                value = uiState.category,
                onValueChange = { viewModel.updateEditorCategory(it) },
                label = { Text(stringResource(R.string.maintenance_category_label)) },
                placeholder = { Text(stringResource(R.string.maintenance_category_hint)) },
                modifier = Modifier.fillMaxWidth()
                    .focusRequester(categoryFocusRequester)
                    .bringIntoViewOnFocus(categoryController),
                isError = uiState.validationErrors.containsKey("category"),
                supportingText = uiState.validationErrors["category"]?.let { { Text(stringResource(it)) } },
                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.PROPER_NAME),
                keyboardActions = KeyboardPolicy.getActions(focusManager),
                singleLine = true,
                enabled = !uiState.isSaving
            )
        }

        item {
            DatePickerField(
                label = stringResource(R.string.service_date_label),
                date = uiState.serviceDate,
                onDateChange = { viewModel.updateEditorServiceDate(it) },
                enabled = !uiState.isSaving
            )
        }

        item {
            DatePickerField(
                label = stringResource(R.string.maintenance_next_due_label),
                date = uiState.nextDueDate,
                onDateChange = { viewModel.updateEditorNextDueDate(it) },
                isOptional = true,
                onClear = { viewModel.updateEditorNextDueDate(null) },
                enabled = !uiState.isSaving
            )
        }

        item {
            OutlinedTextField(
                value = uiState.provider,
                onValueChange = { viewModel.updateEditorProvider(it) },
                label = { Text(stringResource(R.string.maintenance_provider_label)) },
                placeholder = { Text(stringResource(R.string.service_provider_hint)) },
                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(providerController),
                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.PROPER_NAME),
                keyboardActions = KeyboardPolicy.getActions(focusManager),
                singleLine = true,
                enabled = !uiState.isSaving
            )
        }

        item {
            OutlinedTextField(
                value = uiState.cost,
                onValueChange = { viewModel.updateEditorCost(it) },
                label = { Text(stringResource(R.string.maintenance_cost_label)) },
                placeholder = { Text(stringResource(R.string.maintenance_cost_hint)) },
                modifier = Modifier.fillMaxWidth()
                    .focusRequester(costFocusRequester)
                    .bringIntoViewOnFocus(costController),
                isError = uiState.validationErrors.containsKey("cost"),
                supportingText = uiState.validationErrors["cost"]?.let { { Text(stringResource(it)) } },
                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.COORDINATE),
                keyboardActions = KeyboardPolicy.getActions(focusManager),
                singleLine = true,
                enabled = !uiState.isSaving
            )
        }

        item {
            OutlinedTextField(
                value = uiState.description,
                onValueChange = { viewModel.updateEditorDescription(it) },
                label = { Text(stringResource(R.string.maintenance_description_label)) },
                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(descController),
                minLines = 3,
                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.MULTILINE_PROSE),
                keyboardActions = KeyboardPolicy.getActions(focusManager),
                enabled = !uiState.isSaving
            )
        }
        
        if (uiState.saveErrorRes != null) {
            item {
                Text(text = stringResource(uiState.saveErrorRes!!), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate) -> Unit,
    isOptional: Boolean = false,
    onClear: () -> Unit = {},
    enabled: Boolean = true
) {
    var showPicker by remember { mutableStateOf(false) }
    
    OutlinedTextField(
        value = date?.toString() ?: if (isOptional) stringResource(R.string.optional) else stringResource(R.string.select_date),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        trailingIcon = {
            Row {
                if (isOptional && date != null && enabled) {
                    IconButton(onClick = onClear) { Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear)) }
                }
                IconButton(onClick = { if (enabled) showPicker = true }) { Icon(Icons.Default.CalendarMonth, contentDescription = stringResource(R.string.select_date)) }
            }
        }
    )

    if (showPicker && enabled) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        onDateChange(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate())
                    }
                    showPicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
