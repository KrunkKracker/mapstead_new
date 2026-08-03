package com.jumastappworks.mapstead.ui.mapping

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.ui.components.AttachmentsSection
import com.jumastappworks.mapstead.ui.components.details.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedFeatureDetailSheet(
    uiState: FeatureDetailUiState.Ready,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onTakePhoto: () -> Unit,
    onChoosePhoto: () -> Unit,
    onAddFile: () -> Unit,
    onViewAllAttachments: () -> Unit,
    onAttachmentClick: (UUID) -> Unit,
    onOpenLinkedRecord: (UUID) -> Unit,
    onDismiss: () -> Unit,
    onClearDeleteError: () -> Unit
) {
    val feature = uiState.feature
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Delete Failure Dialog
    uiState.deleteErrorRes?.let { errorRes ->
        AlertDialog(
            onDismissRequest = onClearDeleteError,
            title = { Text(stringResource(R.string.error_occurred)) },
            text = { Text(stringResource(errorRes)) },
            confirmButton = {
                TextButton(onClick = onClearDeleteError) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!uiState.isDeleting) showDeleteConfirm = false },
            title = { Text(stringResource(R.string.confirm_delete_feature_title)) },
            text = { 
                Text(stringResource(R.string.delete_feature_confirm_message, feature.label ?: stringResource(R.string.label_custom))) 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteClick()
                    },
                    enabled = !uiState.isDeleting,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    enabled = !uiState.isDeleting
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Sticky Header with Edit and Overflow
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = uiState.geometryLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                var expanded by remember { mutableStateOf(false) }
                
                IconButton(onClick = { expanded = true }, enabled = !uiState.isDeleting) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More Actions")
                }
                
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                        onClick = { expanded = false; showDeleteConfirm = true },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (uiState.isDeleting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            UnifiedItemHeader(
                name = feature.label ?: "Unnamed Location",
                category = uiState.category,
                subtype = uiState.layerName?.let { "Layer: $it" },
                icon = Icons.Default.Place, 
                statusLabel = "Saved",
                propertyContext = null,
                onEditClick = onEditClick
            )

            // Measurement Summary
            DetailSection(title = "Location Details") {
                val measurements = uiState.measurementSummary
                measurements.length?.let { KeyValueRow(label = "Length", value = it) }
                measurements.area?.let { KeyValueRow(label = "Area", value = it) }
                measurements.perimeter?.let { KeyValueRow(label = "Perimeter", value = it) }
                
                uiState.pointCoordinates?.let {
                    KeyValueRow(label = "Coordinates", value = it)
                }
            }

            // Accuracy Summary
            if (uiState.accuracySummary.accuracy != null || uiState.accuracySummary.sourceRes != null) {
                DetailSection(title = "Estimated Accuracy") {
                    uiState.accuracySummary.accuracy?.let {
                        KeyValueRow(label = "Location Accuracy", value = it)
                    }
                    uiState.accuracySummary.sourceRes?.let {
                        KeyValueRow(label = "Recorded Using", value = stringResource(it))
                    }
                }
            }

            // Linked Record
            DetailSection(title = "Documentation Record") {
                when (val link = uiState.linkedRecord) {
                    is LinkedRecordState.Available -> {
                        LinkedRecordCard(
                            name = link.item.name,
                            category = link.item.category,
                            onClick = { onOpenLinkedRecord(link.item.id) }
                        )
                    }
                    is LinkedRecordState.Unavailable -> {
                        SectionEmptyState(text = "Documentation record unavailable.")
                    }
                    LinkedRecordState.None -> {
                        SectionEmptyState(text = "No documentation record linked.")
                    }
                }
            }

            // Overview / Notes
            uiState.notes?.let {
                DetailSection(title = "Notes") {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Attachments
            DetailSection(title = stringResource(R.string.attachments_header)) {
                AttachmentsSection(
                    attachments = uiState.attachments,
                    onAddPhoto = onChoosePhoto,
                    onTakeExtentPhoto = onTakePhoto,
                    onAddDocument = onAddFile,
                    onViewAll = onViewAllAttachments,
                    onAttachmentClick = onAttachmentClick
                )
            }
            
            Spacer(Modifier.height(16.dp))
        }

        // Action Bar for dismissal
        Surface(tonalElevation = 8.dp, shadowElevation = 8.dp) {
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(stringResource(R.string.dismiss))
            }
        }
    }
}

@Composable
private fun LinkedRecordCard(
    name: String,
    category: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.Foundation,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = category,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onClick) {
                Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open Record")
            }
        }
    }
}
