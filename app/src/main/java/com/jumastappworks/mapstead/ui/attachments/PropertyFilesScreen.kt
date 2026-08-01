package com.jumastappworks.mapstead.ui.attachments

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.backup.TemporaryCameraCapture
import com.jumastappworks.mapstead.data.help.HelpTopicId
import com.jumastappworks.mapstead.ui.components.EmptyState
import java.util.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertyFilesScreen(
    propertyId: UUID,
    viewModel: PropertyFilesViewModel,
    onNavigateBack: () -> Unit,
    onAttachmentClick: (UUID) -> Unit,
    onNavigateToEditor: (UUID, String, UUID?, String, String?) -> Unit,
    onHelpClick: (HelpTopicId) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { onNavigateToEditor(propertyId, "PROPERTY", null, it.toString(), null) }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onNavigateToEditor(propertyId, "PROPERTY", null, it.toString(), null) }
    }

    var tempCapture by remember { mutableStateOf<TemporaryCameraCapture?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capture = tempCapture
        if (success && capture != null) {
            onNavigateToEditor(propertyId, "PROPERTY", null, capture.uri.toString(), capture.token)
        } else {
            capture?.token?.let { viewModel.deleteCameraCapture(it) }
        }
        tempCapture = null
    }

    LaunchedEffect(propertyId) {
        viewModel.init(propertyId)
    }

    state?.let { uiState ->
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.files))
                            Text(
                                text = uiState.propertyName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.dismiss))
                        }
                    }
                )
            },
            floatingActionButton = {
                var showAddMenu by remember { mutableStateOf(false) }
                Box {
                    FloatingActionButton(onClick = { showAddMenu = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_attachment))
                    }
                    DropdownMenu(
                        expanded = showAddMenu,
                        onDismissRequest = { showAddMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.take_photo)) },
                            leadingIcon = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                            onClick = {
                                showAddMenu = false
                                viewModel.createCameraCapture()?.let { capture ->
                                    tempCapture = capture
                                    cameraLauncher.launch(capture.uri)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.choose_photo)) },
                            leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                            onClick = {
                                showAddMenu = false
                                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.choose_document)) },
                            leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                            onClick = {
                                showAddMenu = false
                                documentPickerLauncher.launch(arrayOf("application/pdf", "text/plain", "image/*"))
                            }
                        )
                    }
                }
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Filters
                ScrollableTabRow(
                    selectedTabIndex = FileFilter.entries.indexOf(uiState.currentFilter),
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    divider = {}
                ) {
                    FileFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = uiState.currentFilter == filter,
                            onClick = { viewModel.setFilter(filter) },
                            label = { Text(filter.name) },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.attachments.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.empty_attachments_title),
                        description = stringResource(R.string.empty_attachments_desc),
                        icon = Icons.Default.FolderOpen,
                        primaryActionLabel = stringResource(R.string.add_attachment),
                        onPrimaryAction = {
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        helpTopicId = HelpTopicId.PHOTOS_AND_FILES,
                        onHelpClick = onHelpClick,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.filteredAttachments) { model ->
                            AttachmentCard(
                                model = model,
                                onClick = { onAttachmentClick(model.attachment.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttachmentCard(
    model: AttachmentListItemUiModel,
    onClick: () -> Unit
) {
    val attachment = model.attachment
    val isImage = attachment.mimeType?.startsWith("image/") == true
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                when {
                    model.isMissing -> {
                        Icon(
                            Icons.Default.LinkOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).align(Alignment.Center),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    model.isDamaged -> {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).align(Alignment.Center),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    isImage && model.previewUri != null -> {
                        AsyncImage(
                            model = model.previewUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    else -> {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .align(Alignment.Center),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                // Type badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = attachment.attachmentType,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = attachment.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatFileSize(attachment.fileSizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (model.isMissing || model.isDamaged) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Removed EmptyFilesState in favor of common EmptyState

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
