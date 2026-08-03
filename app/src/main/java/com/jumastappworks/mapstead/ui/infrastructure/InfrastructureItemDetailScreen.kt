package com.jumastappworks.mapstead.ui.infrastructure

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.attachments.AttachmentNavigationOrigin
import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.ui.components.AttachmentsSection
import com.jumastappworks.mapstead.ui.components.details.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfrastructureItemDetailScreen(
    propertyId: UUID,
    itemId: UUID,
    viewModel: InfrastructureItemDetailViewModel,
    onNavigateBack: () -> Unit,
    onEditClick: (UUID, UUID) -> Unit,
    onShowOnMap: (UUID, UUID, String) -> Unit,
    onAddMaintenance: (UUID, UUID) -> Unit,
    onViewMaintenance: (UUID) -> Unit,
    onAddPhoto: (UUID, UUID) -> Unit,
    onAddFile: (UUID, UUID) -> Unit,
    onViewAllAttachments: (UUID) -> Unit,
    onAttachmentClick: (UUID, UUID) -> Unit,
    onManageRelationships: (UUID) -> Unit,
    onOpenRelatedItem: (UUID, UUID) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(propertyId, itemId) {
        viewModel.init(propertyId, itemId)
    }

    val readyState = state as? InfrastructureItemDetailUiState.Ready
    
    // Deletion Error handling
    readyState?.deleteErrorRes?.let { errorRes ->
        AlertDialog(
            onDismissRequest = { viewModel.clearDeleteError() },
            title = { Text(stringResource(R.string.error_occurred)) },
            text = { Text(stringResource(errorRes)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearDeleteError() }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    when (val uiState = state) {
        is InfrastructureItemDetailUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is InfrastructureItemDetailUiState.NotFound -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.error_feature_not_found)) },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                        }
                    )
                }
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        text = "This item could not be found or has been deleted.",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            }
        }
        is InfrastructureItemDetailUiState.Error -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.error_occurred)) },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                        }
                    )
                }
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(uiState.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
            }
        }
        is InfrastructureItemDetailUiState.Ready -> {
            InfrastructureItemDetailContent(
                uiState = uiState,
                onBack = onNavigateBack,
                onEdit = { onEditClick(propertyId, itemId) },
                onDelete = { showDeleteConfirm = true },
                onShowOnMap = { planId, fid -> onShowOnMap(propertyId, planId, fid) },
                onAddMaintenance = { onAddMaintenance(propertyId, itemId) },
                onViewMaintenance = { onViewMaintenance(itemId) },
                onAddPhoto = { onAddPhoto(propertyId, itemId) },
                onAddFile = { onAddFile(propertyId, itemId) },
                onViewAllAttachments = { onViewAllAttachments(propertyId) },
                onAttachmentClick = { onAttachmentClick(propertyId, it) },
                onManageRelationships = { onManageRelationships(propertyId) },
                onOpenRelatedItem = { onOpenRelatedItem(propertyId, it) }
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.confirm_delete_item_title)) },
            text = { Text(stringResource(R.string.delete_item_confirm, readyState?.item?.name ?: "")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteItem(
                            onSuccess = { onNavigateBack() }
                        )
                    }
                ) {
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
private fun InfrastructureItemDetailContent(
    uiState: InfrastructureItemDetailUiState.Ready,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShowOnMap: (UUID, String) -> Unit,
    onAddMaintenance: () -> Unit,
    onViewMaintenance: () -> Unit,
    onAddPhoto: () -> Unit,
    onAddFile: () -> Unit,
    onViewAllAttachments: () -> Unit,
    onAttachmentClick: (UUID) -> Unit,
    onManageRelationships: () -> Unit,
    onOpenRelatedItem: (UUID) -> Unit
) {
    val item = uiState.item
    
    UnifiedItemDetailScaffold(
        title = item.name,
        onBack = onBack,
        actions = {
            var expanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { expanded = true }, enabled = !uiState.isDeleting) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More Actions")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                        onClick = { expanded = false; onDelete() },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    ) {
        if (uiState.isDeleting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        UnifiedItemHeader(
            name = item.name,
            category = item.category,
            subtype = item.subtype,
            icon = Icons.Default.Foundation,
            statusLabel = stringResource(InfrastructureStatus.fromDatabaseValue(item.status).labelRes),
            propertyContext = uiState.propertyName,
            onEditClick = onEdit
        )

        if (item.isEmergencyItem || !item.emergencyInstructions.isNullOrBlank()) {
            EmergencyInstructionCard(
                instructions = item.emergencyInstructions,
                isEmergencyDesignated = item.isEmergencyItem
            )
        }

        DetailSection(title = "Map Locations") {
            if (uiState.mapLocations.isEmpty()) {
                SectionEmptyState(text = "Not shown on a map.")
            } else {
                uiState.mapLocations.forEach { feature ->
                    MapLocationCard(
                        feature = feature,
                        onClick = { onShowOnMap(feature.planId, feature.id.toString()) }
                    )
                }
            }
        }

        DetailSection(title = "Overview") {
            item.notes?.takeIf { it.isNotBlank() }?.let {
                KeyValueRow(label = stringResource(R.string.notes_label), value = it)
            }
            item.instructions?.takeIf { it.isNotBlank() }?.let {
                KeyValueRow(label = stringResource(R.string.operating_instructions_label), value = it)
            }
        }

        if (!item.manufacturer.isNullOrBlank() || !item.model.isNullOrBlank() || !item.serialNumber.isNullOrBlank()) {
            DetailSection(title = "Equipment Details") {
                item.manufacturer?.takeIf { it.isNotBlank() }?.let { KeyValueRow(label = stringResource(R.string.manufacturer_label), value = it) }
                item.model?.takeIf { it.isNotBlank() }?.let { KeyValueRow(label = stringResource(R.string.model_label), value = it) }
                item.serialNumber?.takeIf { it.isNotBlank() }?.let { KeyValueRow(label = stringResource(R.string.serial_number_label), value = it) }
            }
        }

        if (!item.serviceProvider.isNullOrBlank() || !item.phoneNumber.isNullOrBlank() || !item.website.isNullOrBlank()) {
            DetailSection(title = "Service Information") {
                item.serviceProvider?.takeIf { it.isNotBlank() }?.let { KeyValueRow(label = stringResource(R.string.service_provider_label), value = it) }
                item.phoneNumber?.takeIf { it.isNotBlank() }?.let { KeyValueRow(label = stringResource(R.string.support_phone_label), value = it) }
                item.website?.takeIf { it.isNotBlank() }?.let { KeyValueRow(label = stringResource(R.string.support_website_label), value = it) }
            }
        }

        DetailSection(title = stringResource(R.string.attachments_header)) {
            AttachmentsSection(
                attachments = uiState.attachments,
                onAddPhoto = onAddPhoto,
                onTakeExtentPhoto = onAddPhoto,
                onAddDocument = onAddFile,
                onViewAll = onViewAllAttachments,
                onAttachmentClick = onAttachmentClick
            )
        }

        DetailSection(title = "Maintenance") {
            ListItem(
                headlineContent = { Text("Maintenance History") },
                supportingContent = { 
                    Text("${uiState.maintenanceCount} records" + (uiState.nextDueDate?.let { " • Next due: $it" } ?: ""))
                },
                trailingContent = { 
                    Row {
                        IconButton(onClick = onViewMaintenance) { Icon(Icons.Default.History, contentDescription = "View History") }
                        IconButton(onClick = onAddMaintenance) { Icon(Icons.Default.Add, contentDescription = "Add Record") }
                    }
                },
                modifier = Modifier.clickable { onViewMaintenance() }
            )
        }

        DetailSection(title = "Relationships") {
            if (uiState.parentItem != null) {
                ListItem(
                    headlineContent = { Text("Parent Item") },
                    supportingContent = { Text(uiState.parentItem.name) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                    modifier = Modifier.clickable { onOpenRelatedItem(uiState.parentItem.id) }
                )
            }
            
            if (uiState.childrenItems.isNotEmpty()) {
                Text(
                    text = "Child Items",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                uiState.childrenItems.forEach { child ->
                    ListItem(
                        headlineContent = { Text(child.name) },
                        supportingContent = { Text(child.category) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                        modifier = Modifier.clickable { onOpenRelatedItem(child.id) }
                    )
                }
            }
            
            ListItem(
                headlineContent = { Text("Connections") },
                supportingContent = { Text("${uiState.relationshipSummary.size} connections") },
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable { onManageRelationships() }
            )
        }
    }
}

@Composable
private fun MapLocationCard(
    feature: MapFeatureEntity,
    onClick: () -> Unit
) {
    val geometryLabel = when (feature.geometryType) {
        "POINT" -> "Marked Location"
        "LINESTRING" -> "Drawn Route"
        "POLYGON" -> "Outlined Area"
        else -> "Map Item"
    }
    
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.Map,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = feature.label ?: "Unnamed Location",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = geometryLabel,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onClick) {
                Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Show on Map")
            }
        }
    }
}
