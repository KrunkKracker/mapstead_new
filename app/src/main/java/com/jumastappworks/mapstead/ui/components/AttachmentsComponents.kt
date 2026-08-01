package com.jumastappworks.mapstead.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.ui.attachments.AttachmentListItemUiModel
import java.util.UUID

@Composable
fun AttachmentsSection(
    attachments: List<AttachmentListItemUiModel>,
    onAddPhoto: () -> Unit,
    onTakeExtentPhoto: () -> Unit,
    onAddDocument: () -> Unit,
    onViewAll: () -> Unit,
    onAttachmentClick: (UUID) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.recent_attachments),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (attachments.isNotEmpty()) {
                TextButton(onClick = onViewAll) {
                    Text(stringResource(R.string.view_all))
                }
            }
        }

        if (attachments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_files_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(attachments.take(5)) { model ->
                    ThumbnailCard(
                        model = model,
                        onClick = { onAttachmentClick(model.attachment.id) }
                    )
                }
            }
        }

        Box {
            Button(
                onClick = { showAddMenu = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_attachment))
            }
            
            DropdownMenu(
                expanded = showAddMenu,
                onDismissRequest = { showAddMenu = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.take_photo)) },
                    leadingIcon = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                    onClick = {
                        showAddMenu = false
                        onTakeExtentPhoto()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.choose_photo)) },
                    leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                    onClick = {
                        showAddMenu = false
                        onAddPhoto()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.choose_document)) },
                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                    onClick = {
                        showAddMenu = false
                        onAddDocument()
                    }
                )
            }
        }
    }
}

@Composable
fun ThumbnailCard(
    model: AttachmentListItemUiModel,
    onClick: () -> Unit
) {
    val attachment = model.attachment
    val isImage = attachment.mimeType?.startsWith("image/") == true
    
    Card(
        modifier = Modifier
            .size(100.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                model.isMissing -> {
                    Icon(
                        Icons.Default.LinkOff,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp).align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                model.isDamaged -> {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp).align(Alignment.Center),
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
                            .size(40.dp)
                            .align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // Tiny badge for type
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
            ) {
                Text(
                    text = attachment.attachmentType,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(2.dp),
                    maxLines = 1,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
