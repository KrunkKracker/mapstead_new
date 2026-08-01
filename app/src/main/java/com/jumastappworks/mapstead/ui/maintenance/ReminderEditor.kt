package com.jumastappworks.mapstead.ui.maintenance

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.ui.components.*
import com.jumastappworks.mapstead.util.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderEditor(
    propertyId: UUID,
    reminderId: UUID?,
    maintenanceRecordId: UUID? = null,
    infrastructureItemId: UUID? = null,
    viewModel: MaintenanceViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.reminderEditorState.collectAsState()

    LaunchedEffect(propertyId, reminderId) {
        viewModel.startReminderEditing(propertyId, reminderId, maintenanceRecordId, infrastructureItemId)
    }

    val uiState = state
    if (uiState is ReminderEditorUiState.Loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (uiState is ReminderEditorUiState.Saved) {
        LaunchedEffect(Unit) { onNavigateBack() }
        return
    }

    if (uiState is ReminderEditorUiState.Ready) {
        ReminderEditorReady(
            uiState = uiState,
            viewModel = viewModel,
            onNavigateBack = onNavigateBack
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderEditorReady(
    uiState: ReminderEditorUiState.Ready,
    viewModel: MaintenanceViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    
    val titleController = rememberFormFieldVisibilityController()
    val descController = rememberFormFieldVisibilityController()
    val titleFocusRequester = remember { FocusRequester() }
    val descFocusRequester = remember { FocusRequester() }

    var showPermissionRationale by remember { mutableStateOf(false) }
    var permissionStatusToHandle by remember { mutableStateOf<NotificationPermissionStatus?>(null) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var settingsLaunchError by remember { mutableStateOf(false) }

    val isSaving = uiState.isSaving
    val isDirty = uiState.isDirty()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.saveReminder()
        } else {
            val activity = context.findComponentActivity()
            val status = NotificationPermissionPolicy.determineStatus(
                context = context,
                hasBeenRequested = true,
                shouldShowRationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
            )
            permissionStatusToHandle = status
        }
    }

    val onSaveClick = {
        if (uiState.title.isBlank()) {
            titleFocusRequester.requestFocus()
            titleController.bringIntoView(coroutineScope)
        } else if (uiState.enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val activity = context.findComponentActivity()
            val status = NotificationPermissionPolicy.determineStatus(
                context = context,
                hasBeenRequested = viewModel.notificationPermissionRequested,
                shouldShowRationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
            )
            
            when (status) {
                NotificationPermissionStatus.Granted,
                NotificationPermissionStatus.NotRequired -> viewModel.saveReminder()
                NotificationPermissionStatus.NotRequested,
                NotificationPermissionStatus.DeniedRetryable -> {
                    showPermissionRationale = true
                }
                NotificationPermissionStatus.DeniedPermanently -> {
                    permissionStatusToHandle = status
                }
            }
        } else {
            viewModel.saveReminder()
        }
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

    if (settingsLaunchError) {
        AlertDialog(
            onDismissRequest = { settingsLaunchError = false },
            title = { Text(stringResource(R.string.handoff_error_title)) },
            text = { Text(stringResource(R.string.notification_settings_failed)) },
            confirmButton = {
                TextButton(onClick = { settingsLaunchError = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    permissionStatusToHandle?.let { status ->
        AlertDialog(
            onDismissRequest = { permissionStatusToHandle = null },
            title = { Text(stringResource(R.string.notification_permission_title)) },
            text = { 
                Text(
                    if (status == NotificationPermissionStatus.DeniedPermanently)
                        stringResource(R.string.notification_denied_permanent)
                    else
                        stringResource(R.string.notification_denied_retryable)
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (status == NotificationPermissionStatus.DeniedRetryable) {
                        TextButton(onClick = {
                            permissionStatusToHandle = null
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }) {
                            Text(stringResource(R.string.retry))
                        }
                    } else if (status == NotificationPermissionStatus.DeniedPermanently) {
                        TextButton(onClick = { 
                            if (!PermissionUtils.openAppSettings(context)) {
                                settingsLaunchError = true
                            }
                            permissionStatusToHandle = null 
                        }) {
                            Text(stringResource(R.string.open_settings))
                        }
                    }
                    TextButton(onClick = {
                        permissionStatusToHandle = null
                        viewModel.saveReminderWithoutNotifications()
                    }) {
                        Text(stringResource(R.string.save_without_notifications))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { permissionStatusToHandle = null }) {
                    Text(stringResource(R.string.keep_editing))
                }
            }
        )
    }

    MapsteadFormScaffold(
        title = if (uiState.reminderId == null) stringResource(R.string.add_reminder_title) else stringResource(R.string.edit_reminder),
        onBack = { if (isDirty) showDiscardConfirm = true else onNavigateBack() },
        isLoading = isSaving,
        primaryActionLabel = stringResource(R.string.save_label),
        onPrimaryAction = { onSaveClick() }
    ) {
        item {
            OutlinedTextField(
                value = uiState.title,
                onValueChange = { viewModel.updateReminderTitle(it) },
                label = { Text(stringResource(R.string.reminder_title_label)) },
                modifier = Modifier.fillMaxWidth()
                    .testTag("Reminder_Title")
                    .focusRequester(titleFocusRequester)
                    .bringIntoViewOnFocus(titleController),
                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.PROPER_NAME),
                keyboardActions = KeyboardPolicy.getActions(focusManager),
                singleLine = true,
                enabled = !isSaving
            )
        }

        item {
            DatePickerField(
                label = stringResource(R.string.due_date_label),
                date = uiState.dueDate,
                onDateChange = { viewModel.updateReminderDueDate(it) },
                enabled = !isSaving
            )
        }

        item {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                Checkbox(checked = uiState.enabled, onCheckedChange = { viewModel.updateReminderEnabled(it) }, enabled = !isSaving)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.enable_notification_label))
            }
        }

        item {
            OutlinedTextField(
                value = uiState.description,
                onValueChange = { viewModel.updateReminderDescription(it) },
                label = { Text(stringResource(R.string.reminder_description_label)) },
                modifier = Modifier.fillMaxWidth()
                    .testTag("Reminder_Description")
                    .focusRequester(descFocusRequester)
                    .bringIntoViewOnFocus(descController),
                minLines = 3,
                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.MULTILINE_PROSE),
                keyboardActions = KeyboardPolicy.getActions(focusManager),
                enabled = !isSaving
            )
        }
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text(stringResource(R.string.notification_permission_title)) },
            text = { Text(stringResource(R.string.notification_permission_rationale)) },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionRationale = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) { Text(stringResource(R.string.allow)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPermissionRationale = false
                    permissionStatusToHandle = NotificationPermissionStatus.DeniedRetryable
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
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
