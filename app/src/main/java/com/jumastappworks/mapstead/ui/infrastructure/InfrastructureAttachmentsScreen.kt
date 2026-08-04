package com.jumastappworks.mapstead.ui.infrastructure

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.attachments.AttachmentNavigationOrigin
import com.jumastappworks.mapstead.data.backup.TemporaryCameraCapture
import com.jumastappworks.mapstead.data.help.HelpTopicId
import com.jumastappworks.mapstead.ui.attachments.FileFilter
import com.jumastappworks.mapstead.ui.components.EmptyState
import com.jumastappworks.mapstead.ui.mapping.FeatureAttachmentCard
import com.jumastappworks.mapstead.ui.mapping.FilterTabs
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfrastructureAttachmentsScreen(
    propertyId: UUID,
    itemId: UUID,
    viewModel: InfrastructureAttachmentsViewModel,
    onBack: () -> Unit,
    onAttachmentClick: (UUID) -> Unit,
    onNavigateToEditor: (UUID, String, UUID, String?, String?, AttachmentNavigationOrigin) -> Unit,
    onHelpClick: (HelpTopicId) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var currentFilter by remember { mutableStateOf(FileFilter.All) }

    LaunchedEffect(propertyId, itemId) {
        viewModel.loadAttachments(propertyId, itemId)
    }

    // Pickers
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { onNavigateToEditor(propertyId, "INFRASTRUCTURE", itemId, it.toString(), null, AttachmentNavigationOrigin.INFRASTRUCTURE) }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onNavigateToEditor(propertyId, "INFRASTRUCTURE", itemId, it.toString(), null, AttachmentNavigationOrigin.INFRASTRUCTURE) }
    }

    var tempCapture by remember { mutableStateOf<TemporaryCameraCapture?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capture = tempCapture
        if (success && capture != null) {
            onNavigateToEditor(propertyId, "INFRASTRUCTURE", itemId, capture.uri.toString(), capture.token, AttachmentNavigationOrigin.INFRASTRUCTURE)
        } else {
            capture?.token?.let { viewModel.deleteCameraCapture(it) }
        }
        tempCapture = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        val readyState = uiState as? InfrastructureAttachmentsUiState.Ready
                        val title = when (uiState) {
                            is InfrastructureAttachmentsUiState.Ready -> (uiState as InfrastructureAttachmentsUiState.Ready).item.name
                            else -> "Photos & Files"
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() }
                        )
                        if (uiState is InfrastructureAttachmentsUiState.Ready) { 
                            Text("Infrastructure Item", style = MaterialTheme.typography.bodySmall) 
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            var showAddMenu by remember { mutableStateOf(false) }
            if (uiState is InfrastructureAttachmentsUiState.Ready) {
                Box {
                    FloatingActionButton(onClick = { showAddMenu = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Attachment")
                    }
                    DropdownMenu(
                        expanded = showAddMenu,
                        onDismissRequest = { showAddMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Take Photo") },
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
                            text = { Text("Choose Photo") },
                            leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                            onClick = {
                                showAddMenu = false
                                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Choose Document") },
                            leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                            onClick = {
                                showAddMenu = false
                                documentPickerLauncher.launch(arrayOf("application/pdf", "text/plain", "image/*"))
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        when (val s = uiState) {
            is InfrastructureAttachmentsUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is InfrastructureAttachmentsUiState.Ready -> {
                val filtered = when (currentFilter) {
                    FileFilter.All -> s.attachments
                    FileFilter.Photos -> s.attachments.filter { it.attachment.mimeType?.startsWith("image/") == true }
                    FileFilter.Documents -> s.attachments.filter { it.attachment.mimeType?.startsWith("image/") != true }
                    else -> s.attachments
                }

                Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                    FilterTabs(selectedFilter = currentFilter, onFilterChange = { currentFilter = it })
                    
                    if (filtered.isEmpty()) {
                        EmptyState(
                            title = stringResource(R.string.empty_attachments_title),
                            description = "No ${currentFilter.name.lowercase()} found for this item.",
                            icon = Icons.Default.FolderOpen,
                            primaryActionLabel = stringResource(R.string.add_attachment),
                            onPrimaryAction = {
                                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            helpTopicId = HelpTopicId.PHOTOS_AND_FILES,
                            onHelpClick = onHelpClick
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filtered) { model ->
                                FeatureAttachmentCard(
                                    model = model,
                                    onClick = { onAttachmentClick(model.attachment.id) }
                                )
                            }
                        }
                    }
                }
            }
            is InfrastructureAttachmentsUiState.NotFound -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Item not found.")
                }
            }
            is InfrastructureAttachmentsUiState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                    Text(stringResource(s.messageRes), textAlign = TextAlign.Center)
                    Button(onClick = { viewModel.retryAttachments() }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
    }
}
