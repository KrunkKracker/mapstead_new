package com.jumastappworks.mapstead.ui.backup

import android.app.Activity
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.backup.BackupOperationPhase
import com.jumastappworks.mapstead.data.backup.DriveBackupFile
import com.jumastappworks.mapstead.data.backup.RestoreRecoveryManager
import com.jumastappworks.mapstead.data.backup.SafetyBackupReference
import com.jumastappworks.mapstead.data.db.entities.BackupRecordEntity
import com.jumastappworks.mapstead.data.help.HelpTopicId
import com.jumastappworks.mapstead.ui.components.ContextHelpLink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    viewModel: BackupViewModel,
    onBack: () -> Unit,
    onHelpClick: (HelpTopicId) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as Activity
    
    var showDeleteConfirm by remember { mutableStateOf<DriveBackupFile?>(null) }
    var showSafetyDeleteConfirm by remember { mutableStateOf<SafetyBackupReference?>(null) }
    var showSafetyRestoreConfirm by remember { mutableStateOf<SafetyBackupReference?>(null) }
    var confirmIncompleteRestore by remember { mutableStateOf(false) }

    val startAuthorizationIntent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        viewModel.onAuthorizationResult(activity, activityResult.data)
    }

    LaunchedEffect(state.resolutionIntent) {
        state.resolutionIntent?.let {
            startAuthorizationIntent.launch(IntentSenderRequest.Builder(it).build())
            viewModel.clearResolutionIntent()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_restore)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.backupHistory.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onClearHistory() }) {
                            Icon(Icons.Default.ClearAll, contentDescription = stringResource(R.string.clear_history))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            // Recovery Banner
            if (state.recoveryStatus is RestoreRecoveryManager.RecoveryStatus.RecoveryRequired || 
                state.recoveryStatus is RestoreRecoveryManager.RecoveryStatus.Failed) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.recovery_required_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (state.recoveryStatus is RestoreRecoveryManager.RecoveryStatus.Failed) (state.recoveryStatus as RestoreRecoveryManager.RecoveryStatus.Failed).error else stringResource(R.string.recovery_required_text),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { viewModel.onAcknowledgeRecovery() }) {
                                Text(stringResource(R.string.acknowledge))
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { viewModel.onRetryRecovery() }) {
                                Text(stringResource(R.string.retry_recovery))
                            }
                        }
                    }
                }
            }

            // Authorization State
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val icon = if (state.isDriveAuthorized) Icons.Default.CloudDone else Icons.Default.CloudOff
                        val tint = if (state.isDriveAuthorized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        Icon(icon, contentDescription = null, tint = tint)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (state.isDriveAuthorized) stringResource(R.string.drive_authorized) else stringResource(R.string.drive_not_authorized),
                                style = MaterialTheme.typography.titleMedium
                            )
                            state.authorizedEmail?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        
                        ContextHelpLink(
                            text = stringResource(R.string.what_is_this),
                            onClick = { onHelpClick(HelpTopicId.BACKUP_AND_RESTORE) },
                            modifier = Modifier.width(140.dp)
                        )
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    if (!state.isDriveAuthorized) {
                        Button(onClick = { viewModel.onConnectDrive(activity) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.connect_google_drive))
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.onBackupNow(activity) }, modifier = Modifier.fillMaxWidth(), enabled = state.currentOperation == null) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.backup_now))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Current Operation
            state.currentOperation?.let { op ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "${op.operationType}: ${op.status}", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { op.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Drive Backups
            Text(stringResource(R.string.restore_from_drive), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            
            if (state.isLoadingDriveBackups) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else if (state.isDriveAuthorized && state.driveBackups.isEmpty()) {
                Text(stringResource(R.string.no_backups_found), style = MaterialTheme.typography.bodyMedium)
            } else if (!state.isDriveAuthorized) {
                Text("Connect to view backups", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.driveBackups) { backup ->
                        BackupFileItem(
                            backup = backup,
                            onRestore = { viewModel.onRestoreClick(activity, backup) },
                            onDelete = { showDeleteConfirm = backup }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Safety Backups
            if (state.safetyBackups.isNotEmpty()) {
                Text(stringResource(R.string.safety_backups), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(state.safetyBackups) { safety ->
                        SafetyBackupItem(
                            safety = safety,
                            onRestore = { showSafetyRestoreConfirm = safety },
                            onDelete = { showSafetyDeleteConfirm = safety }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Local History
            if (state.backupHistory.isNotEmpty()) {
                Text(stringResource(R.string.backup_history), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                    items(state.backupHistory) { record ->
                        HistoryItem(record)
                    }
                }
            }
        }
    }

    // Reset checkbox state whenever pendingRestore changes
    LaunchedEffect(state.pendingRestore) {
        confirmIncompleteRestore = false
    }

    // Incomplete Backup Warning/Confirmation Dialog
    state.pendingIncompleteUpload?.let { pendingUpload ->
        AlertDialog(
            onDismissRequest = { viewModel.onCancelIncompleteUpload() },
            title = { Text("Incomplete Backup Created") },
            text = {
                Column {
                    Text("The created backup contains warnings because some attachments are missing or corrupted:")
                    Spacer(Modifier.height(8.dp))
                    pendingUpload.manifest.warnings.forEach { warning ->
                        Text("- $warning", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Do you explicitly confirm that you want to upload this incomplete backup to Google Drive anyway?")
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onConfirmIncompleteUpload(activity) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Confirm Upload")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onCancelIncompleteUpload() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Restore Preview Dialog
    state.pendingRestore?.let { pending ->
        val manifest = pending.validationReport.manifest
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { viewModel.onCancelRestore() },
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.restore_confirm_text))
                    Spacer(Modifier.height(16.dp))
                    
                    // Group: Metadata
                    InfoGroup(title = "Metadata") {
                        InfoRow(stringResource(R.string.drive_filename_label, pending.driveFile.name))
                        InfoRow(stringResource(R.string.backup_id_label, manifest.backupId))
                        InfoRow(stringResource(R.string.created_label, manifest.createdAt))
                        InfoRow(stringResource(R.string.device_info_label, "${manifest.deviceManufacturer} ${manifest.deviceModel}"))
                        InfoRow(stringResource(R.string.version_info_label, manifest.appVersionName, manifest.appVersionCode))
                        InfoRow(stringResource(R.string.schema_version_label, manifest.databaseSchemaVersion))
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    
                    // Group: Entity Counts
                    InfoGroup(title = "Content") {
                        InfoRow(stringResource(R.string.properties_count_label, manifest.propertyCount))
                        InfoRow(stringResource(R.string.plans_count_label, manifest.planCount))
                        InfoRow(stringResource(R.string.layers_count_label, manifest.layerCount))
                        InfoRow(stringResource(R.string.features_count_label, manifest.mapFeatureCount))
                        InfoRow(stringResource(R.string.infrastructure_count_label, manifest.infrastructureCount))
                        InfoRow(stringResource(R.string.maintenance_count_label, manifest.maintenanceCount))
                        InfoRow(stringResource(R.string.reminders_count_label, manifest.reminderCount))
                        InfoRow(stringResource(R.string.attachments_count_label, manifest.attachmentCount))
                        InfoRow(stringResource(R.string.relationships_count_label, manifest.relationshipCount))
                    }

                    Spacer(Modifier.height(12.dp))

                    // Group: Storage
                    InfoGroup(title = "Storage") {
                        val attachmentSizeStr = Formatter.formatShortFileSize(context, manifest.includedAttachmentBytes)
                        InfoRow(stringResource(R.string.attachments_size_label, attachmentSizeStr))
                        // Note: Real storage checks would come from coordinator report, but here we show manifest values
                    }

                    if (manifest.warnings.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.manifest_warnings_label, manifest.warnings.size), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        manifest.warnings.forEach {
                            Text("- $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = confirmIncompleteRestore,
                                onCheckedChange = { confirmIncompleteRestore = it }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "I understand that this backup is incomplete and some attachments are missing. Proceed with restore.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    if (manifest.appVersionName != com.jumastappworks.mapstead.BuildConfig.VERSION_NAME) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.restore_compatibility_warning, manifest.appVersionName),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text(
                            text = stringResource(R.string.safety_backup_explanation),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                val isEnabled = manifest.warnings.isEmpty() || confirmIncompleteRestore
                Button(
                    onClick = { viewModel.onConfirmRestore(activity) },
                    enabled = isEnabled,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.restore_now))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onCancelRestore() }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    showDeleteConfirm?.let { backup ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text(stringResource(R.string.delete_backup_title)) },
            text = { Text(stringResource(R.string.delete_backup_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDeleteClick(activity, backup.driveFileId)
                    showDeleteConfirm = null
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    showSafetyDeleteConfirm?.let { safety ->
        AlertDialog(
            onDismissRequest = { showSafetyDeleteConfirm = null },
            title = { Text(stringResource(R.string.delete_safety_backup_title)) },
            text = { Text(stringResource(R.string.delete_safety_backup_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDeleteSafetyBackup(safety.backupId)
                    showSafetyDeleteConfirm = null
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showSafetyDeleteConfirm = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    showSafetyRestoreConfirm?.let { safety ->
        AlertDialog(
            onDismissRequest = { showSafetyRestoreConfirm = null },
            title = { Text(stringResource(R.string.restore_safety_title)) },
            text = { Text(stringResource(R.string.restore_safety_confirm)) },
            confirmButton = {
                Button(onClick = {
                    viewModel.onRestoreSafetyBackup(safety.backupId)
                    showSafetyRestoreConfirm = null
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(R.string.restore_now))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSafetyRestoreConfirm = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    state.error?.let { err ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error") },
            text = { Text(err) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) { Text("OK") }
            }
        )
    }
}

@Composable
fun InfoGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(8.dp),
            content = content
        )
    }
}

@Composable
fun InfoRow(text: String, icon: ImageVector? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun BackupFileItem(backup: DriveBackupFile, onRestore: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(backup.name, style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.created_label, backup.createdAt), style = MaterialTheme.typography.bodySmall)
                Text("Size: ${Formatter.formatShortFileSize(LocalContext.current, backup.size)} | v${backup.formatVersion}", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onRestore) {
                Icon(Icons.Default.SettingsBackupRestore, contentDescription = "Restore")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun SafetyBackupItem(safety: SafetyBackupReference, onRestore: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = if (safety.validationStatus.name == "VALID") null else BorderStroke(1.dp, MaterialTheme.colorScheme.error)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (safety.validationStatus.name == "VALID") Icons.Default.VerifiedUser else Icons.Default.GppBad,
                contentDescription = null,
                tint = if (safety.validationStatus.name == "VALID") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.safety_backup_label, safety.backupId), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.created_label, safety.createdAt.toString()), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.size_label, safety.sizeBytes / 1024), style = MaterialTheme.typography.bodySmall)
                Text(
                    text = stringResource(R.string.status_label, safety.validationStatus.name),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (safety.validationStatus.name == "VALID") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            Row {
                IconButton(onClick = onRestore) {
                    Icon(Icons.Default.SettingsBackupRestore, contentDescription = "Restore")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun HistoryItem(record: BackupRecordEntity) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.1f))
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val (icon, tint) = when (record.status) {
                BackupOperationPhase.SUCCESS.name -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
                BackupOperationPhase.FAILED.name -> Icons.Default.ErrorOutline to MaterialTheme.colorScheme.error
                BackupOperationPhase.CANCELLED.name -> Icons.Default.Block to MaterialTheme.colorScheme.onSurfaceVariant
                else -> Icons.Default.History to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("${record.operationType}: ${record.status}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(record.startedAt.toString(), style = MaterialTheme.typography.labelSmall)
                record.fileSize?.let {
                    Text("Size: ${Formatter.formatShortFileSize(context, it)}", style = MaterialTheme.typography.labelSmall)
                }
                record.userSafeErrorMessage?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
