package com.jumastappworks.mapstead.ui.mapping

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.attachments.AttachmentNavigationOrigin
import com.jumastappworks.mapstead.data.backup.TemporaryCameraCapture
import com.jumastappworks.mapstead.data.help.HelpTopicId
import com.jumastappworks.mapstead.ui.attachments.AttachmentListItemUiModel
import com.jumastappworks.mapstead.ui.attachments.FileFilter
import com.jumastappworks.mapstead.ui.components.EmptyState
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureAttachmentsScreen(
    propertyId: UUID,
    planId: UUID,
    featureId: UUID,
    viewModel: MapFeatureAttachmentsViewModel,
    onBack: () -> Unit,
    onAttachmentClick: (UUID) -> Unit,
    onNavigateToEditor: (UUID, String, UUID, String?, String?, AttachmentNavigationOrigin) -> Unit,
    onReturnToMap: (UUID) -> Unit,
    onHelpClick: (HelpTopicId) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var currentFilter by remember { mutableStateOf(FileFilter.All) }

    // Pickers
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { onNavigateToEditor(propertyId, "MAP_FEATURE", featureId, it.toString(), null, AttachmentNavigationOrigin.MAP_FEATURE) }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onNavigateToEditor(propertyId, "MAP_FEATURE", featureId, it.toString(), null, AttachmentNavigationOrigin.MAP_FEATURE) }
    }

    var tempCapture by remember { mutableStateOf<TemporaryCameraCapture?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capture = tempCapture
        if (success && capture != null) {
            onNavigateToEditor(propertyId, "MAP_FEATURE", featureId, capture.uri.toString(), capture.token, AttachmentNavigationOrigin.MAP_FEATURE)
        } else {
            capture?.token?.let { viewModel.deleteCameraCapture(it) }
        }
        tempCapture = null
    }

    LaunchedEffect(propertyId, planId, featureId) {
        viewModel.loadAttachments(propertyId, planId, featureId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        val readyState = uiState as? MapFeatureAttachmentsUiState.Ready
                        Text(readyState?.feature?.label ?: "Feature Attachments", style = MaterialTheme.typography.titleMedium)
                        readyState?.let { 
                            Text("${it.feature.geometryType} • Layer: ${it.layer.name}", style = MaterialTheme.typography.bodySmall) 
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { (uiState as? MapFeatureAttachmentsUiState.Ready)?.feature?.id?.let(onReturnToMap) }) {
                        Icon(Icons.Default.Map, contentDescription = "Show on Map")
                    }
                }
            )
        },
        floatingActionButton = {
            var showAddMenu by remember { mutableStateOf(false) }
            if (uiState is MapFeatureAttachmentsUiState.Ready) {
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
            is MapFeatureAttachmentsUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is MapFeatureAttachmentsUiState.Ready -> {
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
                            description = "No ${currentFilter.name.lowercase()} found for this feature.",
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
            is MapFeatureAttachmentsUiState.NotFound -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Feature not found.")
                }
            }
            is MapFeatureAttachmentsUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(s.messageRes), color = MaterialTheme.colorScheme.error)
                }
            }
            else -> {}
        }
    }
}

@Composable
fun FilterTabs(selectedFilter: FileFilter, onFilterChange: (FileFilter) -> Unit) {
    val filters = listOf(FileFilter.All, FileFilter.Photos, FileFilter.Documents)
    ScrollableTabRow(
        selectedTabIndex = filters.indexOf(selectedFilter).coerceAtLeast(0),
        edgePadding = 16.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        divider = {}
    ) {
        filters.forEach { filter ->
            Tab(
                selected = selectedFilter == filter,
                onClick = { onFilterChange(filter) },
                text = { Text(filter.name) }
            )
        }
    }
}

@Composable
fun FeatureAttachmentCard(
    model: AttachmentListItemUiModel,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (model.attachment.mimeType?.startsWith("image/") == true && model.previewUri != null) {
                AsyncImage(
                    model = model.previewUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.InsertDriveFile, contentDescription = null, modifier = Modifier.size(48.dp))
                }
            }
            
            if (model.attachment.isCover) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                ) {
                    Text("Cover", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(8.dp)
            ) {
                Text(
                    model.attachment.displayName,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Removed old EmptyState in favor of common EmptyState
