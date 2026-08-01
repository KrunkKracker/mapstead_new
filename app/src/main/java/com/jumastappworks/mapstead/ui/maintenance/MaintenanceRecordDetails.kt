package com.jumastappworks.mapstead.ui.maintenance

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.backup.TemporaryCameraCapture
import com.jumastappworks.mapstead.data.db.entities.MaintenanceRecordEntity
import com.jumastappworks.mapstead.data.db.entities.ReminderEntity
import com.jumastappworks.mapstead.data.help.HelpTopicId
import com.jumastappworks.mapstead.ui.attachments.AttachmentListItemUiModel
import com.jumastappworks.mapstead.ui.components.AttachmentsSection
import com.jumastappworks.mapstead.ui.components.ContextHelpLink
import com.jumastappworks.mapstead.util.MaintenanceStatus
import java.time.LocalDate
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceRecordDetails(
    propertyId: UUID,
    recordId: UUID,
    viewModel: MaintenanceViewModel,
    onBack: () -> Unit,
    onEdit: (UUID, UUID) -> Unit,
    onOpenInfrastructure: (UUID, UUID) -> Unit,
    onAddReminder: (UUID, UUID?, UUID?) -> Unit,
    onEditReminder: (UUID, UUID, UUID?, UUID?) -> Unit,
    onOpenOnMap: (UUID, UUID, String) -> Unit,
    onOpenRecord: (UUID, UUID) -> Unit,
    onAttachmentClick: (UUID, UUID) -> Unit,
    onViewAllAttachments: (UUID, UUID) -> Unit,
    onNavigateToEditor: (UUID, String, UUID, String, String?) -> Unit,
    onHelpClick: (HelpTopicId) -> Unit
) {
    val uiState by viewModel.detailsState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Pickers
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { onNavigateToEditor(propertyId, "MAINTENANCE", recordId, it.toString(), null) }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onNavigateToEditor(propertyId, "MAINTENANCE", recordId, it.toString(), null) }
    }

    var tempCapture by remember { mutableStateOf<TemporaryCameraCapture?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capture = tempCapture
        if (success && capture != null) {
            onNavigateToEditor(propertyId, "MAINTENANCE", recordId, capture.uri.toString(), capture.token)
        } else {
            capture?.token?.let { viewModel.deleteCameraCapture(it) }
        }
        tempCapture = null
    }

    LaunchedEffect(recordId) {
        viewModel.openRecordDetails(recordId)
    }

    LaunchedEffect(Unit) {
        viewModel.detailsEvents.collect { event ->
            when (event) {
                is MaintenanceDetailsEvent.NavigateBackAfterDelete -> {
                    onBack()
                }
                is MaintenanceDetailsEvent.NavigateToRecord -> {
                    onOpenRecord(propertyId, event.recordId)
                }
                is MaintenanceDetailsEvent.ShowSchedulingWarning -> {
                    snackbarHostState.showSnackbar(
                        message = "Scheduling issue occurred.", // Simplified for now
                        duration = SnackbarDuration.Long
                    )
                }
            }
        }
    }

    when (val s = uiState) {
        is MaintenanceRecordDetailsUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is MaintenanceRecordDetailsUiState.NotFound -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.property_not_found))
            }
        }
        is MaintenanceRecordDetailsUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(s.message, color = MaterialTheme.colorScheme.error)
            }
        }
        is MaintenanceRecordDetailsUiState.Ready -> {
            MaintenanceRecordDetailsContent(
                state = s,
                onBack = onBack,
                onEdit = { onEdit(propertyId, recordId) },
                onDelete = {
                    viewModel.deleteRecord(recordId)
                },
                onMarkComplete = { date ->
                    viewModel.markRecordComplete(s.record, date)
                },
                onReschedule = { date, nextDue ->
                    viewModel.rescheduleRecord(s.record, date, nextDue)
                },
                onAddReminder = { onAddReminder(propertyId, recordId, s.record.infrastructureItemId) },
                onEditReminder = { rid -> onEditReminder(propertyId, rid, recordId, s.record.infrastructureItemId) },
                onOpenInfrastructure = { onOpenInfrastructure(propertyId, s.record.infrastructureItemId!!) },
                onOpenOnMap = { viewModel.openLinkedMapFeature(recordId, onOpenOnMap) },
                onClearError = { viewModel.clearActionState() },
                onAddPhoto = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onTakePhoto = {
                    viewModel.createCameraCapture()?.let { capture ->
                        tempCapture = capture
                        cameraLauncher.launch(capture.uri)
                    }
                },
                onAddDocument = { documentPickerLauncher.launch(arrayOf("application/pdf", "text/plain", "image/*")) },
                onViewAllAttachments = { onViewAllAttachments(propertyId, recordId) },
                onAttachmentClick = { onAttachmentClick(propertyId, it) },
                onHelpClick = onHelpClick,
                snackbarHostState = snackbarHostState
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceRecordDetailsContent(
    state: MaintenanceRecordDetailsUiState.Ready,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMarkComplete: (LocalDate) -> Unit,
    onReschedule: (LocalDate, LocalDate?) -> Unit,
    onAddReminder: () -> Unit,
    onEditReminder: (UUID) -> Unit,
    onOpenInfrastructure: () -> Unit,
    onOpenOnMap: () -> Unit,
    onClearError: () -> Unit,
    onAddPhoto: () -> Unit,
    onTakePhoto: () -> Unit,
    onAddDocument: () -> Unit,
    onViewAllAttachments: () -> Unit,
    onAttachmentClick: (UUID) -> Unit,
    onHelpClick: (HelpTopicId) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCompleteDialog by remember { mutableStateOf(false) }
    var showRescheduleDialog by remember { mutableStateOf(false) }
    
    val isWorking = state.actionState is MaintenanceDetailsActionState.Working
    val errorRes = (state.actionState as? MaintenanceDetailsActionState.Error)?.messageRes

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.maintenance_details_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isWorking) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    IconButton(onClick = onEdit, enabled = !isWorking) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_maintenance))
                    }
                    IconButton(onClick = { showDeleteDialog = true }, enabled = !isWorking) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                DetailHeader(state.record)

                DetailSection(title = stringResource(R.string.description)) {
                    Text(state.record.description ?: "No description provided.", style = MaterialTheme.typography.bodyMedium)
                }

                DetailSection(title = "Schedule") {
                    DetailRow(icon = Icons.Default.Event, label = stringResource(R.string.service_date_label), value = state.record.serviceDate.toString())
                    state.record.nextDueDate?.let { 
                        DetailRow(icon = Icons.Default.EventRepeat, label = stringResource(R.string.maintenance_next_due_label), value = it.toString())
                    }
                }

                if (state.record.provider != null || state.record.cost != null) {
                    DetailSection(title = "Service Info") {
                        state.record.provider?.let { DetailRow(icon = Icons.Default.Business, label = stringResource(R.string.maintenance_provider_label), value = it) }
                        state.record.cost?.let { DetailRow(icon = Icons.Default.Payments, label = stringResource(R.string.maintenance_cost_label), value = "$it ${state.record.currencyCode ?: ""}") }
                    }
                }
                
                DetailSection(title = stringResource(R.string.reminders_label)) {
                    if (state.reminder != null) {
                        ReminderCard(state.reminder, onClick = { if (!isWorking) onEditReminder(state.reminder.id) })
                    } else {
                        Column {
                            OutlinedButton(
                                onClick = onAddReminder,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isWorking
                            ) {
                                Icon(Icons.Default.AddAlert, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.add_reminder))
                            }
                            ContextHelpLink(
                                text = stringResource(R.string.what_is_this),
                                onClick = { onHelpClick(HelpTopicId.REMINDERS) }
                            )
                        }
                    }
                }

                if (state.infrastructureItem != null) {
                    DetailSection(title = stringResource(R.string.linked_item_header)) {
                        Card(
                            onClick = { if (!isWorking) onOpenInfrastructure() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Foundation, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                                Text(state.infrastructureItem.name, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Default.ChevronRight, contentDescription = null)
                            }
                        }
                        
                        if (state.hasMappedFeature) {
                            TextButton(
                                onClick = { if (!isWorking) onOpenOnMap() },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.open_owner_feature))
                            }
                        }
                    }
                }

                HorizontalDivider()
                AttachmentsSection(
                    attachments = state.attachments,
                    onAddPhoto = onAddPhoto,
                    onTakeExtentPhoto = onTakePhoto,
                    onAddDocument = onAddDocument,
                    onViewAll = onViewAllAttachments,
                    onAttachmentClick = onAttachmentClick
                )

                if (!MaintenanceStatus.isCompleted(state.record.status)) {
                    Button(
                        onClick = { showCompleteDialog = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isWorking
                    ) {
                        if (state.actionState is MaintenanceDetailsActionState.Working && state.actionState.action == "complete") {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.mark_complete))
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { showRescheduleDialog = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isWorking
                    ) {
                        if (state.actionState is MaintenanceDetailsActionState.Working && state.actionState.action == "reschedule") {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Default.EventRepeat, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.reschedule))
                        }
                    }
                }
            }
            
            if (isWorking) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            
            errorRes?.let {
                AlertDialog(
                    onDismissRequest = onClearError,
                    title = { Text("Action Failed") },
                    text = { Text(stringResource(it)) },
                    confirmButton = {
                        TextButton(onClick = onClearError) { Text(stringResource(R.string.dismiss)) }
                    }
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_record_title)) },
            text = { Text(stringResource(R.string.delete_record_message)) },
            confirmButton = {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showCompleteDialog) {
        AlertDialog(
            onDismissRequest = { showCompleteDialog = false },
            title = { Text(stringResource(R.string.complete_confirmation_title)) },
            text = { Text(stringResource(R.string.complete_confirmation_message)) },
            confirmButton = {
                TextButton(onClick = { 
                    onMarkComplete(LocalDate.now())
                    showCompleteDialog = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showCompleteDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showRescheduleDialog) {
        RescheduleDialog(
            onDismiss = { showRescheduleDialog = false },
            onConfirm = { date, nextDue ->
                onReschedule(date, nextDue)
                showRescheduleDialog = false
            }
        )
    }
}

@Composable
fun ReminderCard(reminder: ReminderEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (reminder.enabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(reminder.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Due: ${reminder.dueDate}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun DetailHeader(record: MaintenanceRecordEntity) {
    Column {
        Text(record.category.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(record.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Surface(
            color = if (MaintenanceStatus.isCompleted(record.status)) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                record.status,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                color = if (MaintenanceStatus.isCompleted(record.status)) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
        content()
    }
}

@Composable
fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Text(value, style = MaterialTheme.typography.bodyLarge)
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
    onClear: () -> Unit = {}
) {
    var showPicker by remember { mutableStateOf(false) }
    
    OutlinedTextField(
        value = date?.toString() ?: if (isOptional) "Optional" else "Select Date",
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            Row {
                if (isOptional && date != null) {
                    IconButton(onClick = onClear) { Icon(Icons.Default.Clear, contentDescription = "Clear") }
                }
                IconButton(onClick = { showPicker = true }) { Icon(Icons.Default.CalendarMonth, contentDescription = "Select Date") }
            }
        }
    )

    if (showPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        onDateChange(Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate())
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

@Composable
private fun RescheduleDialog(
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate?) -> Unit
) {
    var serviceDate by remember { mutableStateOf(LocalDate.now()) }
    var nextDueDate by remember { mutableStateOf<LocalDate?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reschedule)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Schedule a new future instance of this task.")
                DatePickerField(
                    label = stringResource(R.string.maintenance_service_date_label),
                    date = serviceDate,
                    onDateChange = { serviceDate = it }
                )
                DatePickerField(
                    label = stringResource(R.string.maintenance_next_due_label),
                    date = nextDueDate,
                    onDateChange = { nextDueDate = it },
                    isOptional = true,
                    onClear = { nextDueDate = null }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(serviceDate, nextDueDate) },
                enabled = true
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
