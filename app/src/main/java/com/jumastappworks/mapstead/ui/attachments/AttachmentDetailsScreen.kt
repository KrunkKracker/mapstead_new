package com.jumastappworks.mapstead.ui.attachments

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.attachments.AttachmentDeleteState
import com.jumastappworks.mapstead.data.db.entities.AttachmentEntity
import kotlinx.coroutines.launch
import java.util.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentDetailsScreen(
    propertyId: UUID,
    attachmentId: UUID,
    viewModel: AttachmentDetailsViewModel,
    onNavigateBack: () -> Unit,
    onEditMetadata: (UUID) -> Unit,
    onOpenOwner: (AttachmentEntity) -> Unit,
    onDeleted: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val deleteState by viewModel.deleteState.collectAsState()
    val resources = androidx.compose.ui.platform.LocalResources.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chooserTitle = stringResource(R.string.open_file)
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(propertyId, attachmentId) {
        viewModel.init(propertyId, attachmentId)
    }

    LaunchedEffect(deleteState) {
        when (deleteState) {
            AttachmentDeleteState.Deleted -> onDeleted()
            is AttachmentDeleteState.DeletedWithCleanupWarning -> {
                snackbarHostState.showSnackbar(resources.getString((deleteState as AttachmentDeleteState.DeletedWithCleanupWarning).messageRes))
                onDeleted()
            }
            is AttachmentDeleteState.Error -> {
                snackbarHostState.showSnackbar(resources.getString((deleteState as AttachmentDeleteState.Error).messageRes))
                viewModel.clearDeleteState()
            }
            else -> {}
        }
    }

    val readyState = state as? AttachmentDetailsUiState.Ready
    LaunchedEffect(readyState?.coverActionErrorRes) {
        readyState?.coverActionErrorRes?.let { errorRes ->
            snackbarHostState.showSnackbar(resources.getString(errorRes))
            viewModel.clearCoverActionError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.attachment_details)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, enabled = deleteState == AttachmentDeleteState.Idle) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.dismiss))
                    }
                },
                actions = {
                    val current = state
                    if (current is AttachmentDetailsUiState.Ready || current is AttachmentDetailsUiState.MissingFile || current is AttachmentDetailsUiState.DamagedFile) {
                        val attachment = when (current) {
                            is AttachmentDetailsUiState.Ready -> current.attachment
                            is AttachmentDetailsUiState.MissingFile -> current.attachment
                            is AttachmentDetailsUiState.DamagedFile -> current.attachment
                            else -> null
                        }
                        attachment?.let {
                            IconButton(onClick = { onOpenOwner(it) }, enabled = deleteState == AttachmentDeleteState.Idle) {
                                Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = stringResource(R.string.open_owner))
                            }
                            IconButton(onClick = { onEditMetadata(attachmentId) }, enabled = deleteState == AttachmentDeleteState.Idle) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_attachment_metadata))
                            }
                            IconButton(onClick = { showDeleteConfirm = true }, enabled = deleteState == AttachmentDeleteState.Idle) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_attachment))
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (deleteState == AttachmentDeleteState.Deleting || readyState?.coverActionLoading == true) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            
            when (val uiState = state) {
                is AttachmentDetailsUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is AttachmentDetailsUiState.NotFound -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.error_attachment_not_found))
                    }
                }
                is AttachmentDetailsUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(uiState.messageRes), color = MaterialTheme.colorScheme.error)
                    }
                }
                is AttachmentDetailsUiState.Ready, is AttachmentDetailsUiState.MissingFile, is AttachmentDetailsUiState.DamagedFile -> {
                    val attachment = when (uiState) {
                        is AttachmentDetailsUiState.Ready -> uiState.attachment
                        is AttachmentDetailsUiState.MissingFile -> uiState.attachment
                        is AttachmentDetailsUiState.DamagedFile -> uiState.attachment
                        else -> throw IllegalStateException()
                    }
                    val ownerName = when (uiState) {
                        is AttachmentDetailsUiState.Ready -> uiState.ownerDisplayName
                        is AttachmentDetailsUiState.MissingFile -> uiState.ownerDisplayName
                        is AttachmentDetailsUiState.DamagedFile -> uiState.ownerDisplayName
                        else -> ""
                    }
                    
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Visual Preview Area
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp, max = 500.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            when (uiState) {
                                is AttachmentDetailsUiState.Ready -> {
                                    if (uiState.isImage) {
                                        AsyncImage(
                                            model = uiState.fileUri,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxWidth(),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Button(onClick = {
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(uiState.fileUri, uiState.attachment.mimeType)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                try {
                                                    context.startActivity(Intent.createChooser(intent, chooserTitle))
                                                } catch (e: ActivityNotFoundException) {
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar(resources.getString(R.string.error_no_viewer))
                                                    }
                                                }
                                            }) {
                                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                                                Spacer(Modifier.width(8.dp))
                                                Text(stringResource(R.string.open_file))
                                            }
                                        }
                                    }
                                }
                                is AttachmentDetailsUiState.MissingFile -> {
                                    IntegrityPlaceholder(
                                        icon = Icons.Default.LinkOff,
                                        title = stringResource(R.string.missing_file_title),
                                        message = stringResource(R.string.missing_file_message)
                                    )
                                }
                                is AttachmentDetailsUiState.DamagedFile -> {
                                    IntegrityPlaceholder(
                                        icon = Icons.Default.Warning,
                                        title = stringResource(R.string.damaged_file_title),
                                        message = stringResource(R.string.damaged_file_message)
                                    )
                                }
                                else -> {}
                            }
                        }

                        // Details
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            DetailItem(label = stringResource(R.string.attachment_display_name), value = attachment.displayName)
                            DetailItem(label = stringResource(R.string.attachment_type), value = attachment.attachmentType)
                            attachment.caption?.let {
                                DetailItem(label = stringResource(R.string.caption_optional), value = it)
                            }
                            DetailItem(label = stringResource(R.string.attachment_owner_label), value = ownerName)
                            
                            HorizontalDivider()
                            
                            DetailItem(label = "MIME Type", value = attachment.mimeType ?: "Unknown")
                            DetailItem(label = "Size", value = formatFileSize(attachment.fileSizeBytes))

                            if (attachment.mapFeatureId != null && attachment.mimeType?.startsWith("image/") == true) {
                                Spacer(Modifier.height(8.dp))
                                val isBusy = deleteState != AttachmentDeleteState.Idle || uiState is AttachmentDetailsUiState.Ready && uiState.coverActionLoading
                                if (attachment.isCover) {
                                    OutlinedButton(
                                        onClick = { viewModel.removeCover() },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !isBusy
                                    ) {
                                        Icon(Icons.Default.ImageNotSupported, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Remove as Cover Photo")
                                    }
                                } else if (uiState is AttachmentDetailsUiState.Ready) {
                                    Button(
                                        onClick = { viewModel.setAsCover() },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !isBusy
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Set as Cover Photo")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_attachment)) },
            text = { Text(stringResource(R.string.delete_attachment_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteAttachment()
                }) {
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
}

@Composable
private fun IntegrityPlaceholder(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            Text(message, style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun formatFileSize(bytes: Long?): String {
    if (bytes == null) return "Unknown size"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        String.format(Locale.US, "%.0f KB", kb)
    }
}
