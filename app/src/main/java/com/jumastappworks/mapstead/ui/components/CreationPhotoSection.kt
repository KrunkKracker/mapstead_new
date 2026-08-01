package com.jumastappworks.mapstead.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.attachments.StagedCreationPhotoState

@Composable
fun CreationPhotoSection(
    stagedPhoto: StagedCreationPhotoState,
    onTakePhoto: () -> Unit,
    onChoosePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onRetryPreview: () -> Unit = {}
) {
    var showOptions by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var previewRevision by remember { mutableIntStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.setup_add_photo_label),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        when (stagedPhoto) {
            is StagedCreationPhotoState.Ready -> {
                val model = remember(stagedPhoto.uri, stagedPhoto.cameraCaptureToken, previewRevision) {
                    ImageRequest.Builder(context)
                        .data(stagedPhoto.uri)
                        .crossfade(true)
                        .setParameter("revision", previewRevision)
                        .build()
                }
                
                var isError by remember(stagedPhoto.uri, previewRevision) { mutableStateOf(false) }
                var isLoading by remember(stagedPhoto.uri, previewRevision) { mutableStateOf(true) }

                Box(modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp))) {
                    if (!isError) {
                        AsyncImage(
                            model = model,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            onState = { state ->
                                isLoading = state is AsyncImagePainter.State.Loading
                                isError = state is AsyncImagePainter.State.Error
                            }
                        )
                    }

                    if (isLoading && !isError) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }

                    if (isError) {
                        Column(
                            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.BrokenImage, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(stringResource(R.string.error_preview_load), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { 
                                previewRevision++
                                onRetryPreview() 
                            }) {
                                Text(stringResource(R.string.retry_preview))
                            }
                        }
                    }
                    
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(8.dp)).clickable { onRemovePhoto() }
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.remove),
                            modifier = Modifier.padding(4.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(8.dp)).clickable { showOptions = true }
                    ) {
                        Text(
                            stringResource(R.string.replace),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            StagedCreationPhotoState.Loading -> {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is StagedCreationPhotoState.Failed -> {
                OutlinedCard(
                    onClick = { showOptions = true },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f))
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(stringResource(stagedPhoto.messageRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            Text(stringResource(R.string.retry), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            StagedCreationPhotoState.None -> {
                OutlinedCard(
                    onClick = { showOptions = true },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null)
                            Text(stringResource(R.string.setup_add_photo_action), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }

    if (showOptions) {
        AlertDialog(
            onDismissRequest = { showOptions = false },
            title = { Text(stringResource(R.string.setup_add_photo_action)) },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showOptions = false }) {
                    Text(stringResource(R.string.cancel_label))
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showOptions = false; onTakePhoto() }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Text(stringResource(R.string.take_photo))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showOptions = false; onChoosePhoto() }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Text(stringResource(R.string.choose_photo))
                    }
                }
            }
        )
    }
}
