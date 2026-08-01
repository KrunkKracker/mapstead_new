package com.jumastappworks.mapstead.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import com.jumastappworks.mapstead.data.relationships.ItemRelationshipUiModel
import com.jumastappworks.mapstead.data.relationships.RelationshipDirection
import java.util.UUID

@Composable
fun ConnectedSystemSection(
    parent: InfrastructureItemEntity?,
    children: List<InfrastructureItemEntity>,
    relationships: List<ItemRelationshipUiModel>,
    onSetParent: () -> Unit,
    onRemoveParent: () -> Unit,
    onAddRelationship: () -> Unit,
    onOpenItem: (UUID) -> Unit,
    onOpenOnMap: (UUID) -> Unit,
    onEditRelationship: (UUID) -> Unit,
    onRemoveRelationship: (UUID) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirmByRelId by remember { mutableStateOf<UUID?>(null) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Connected System",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // Hierarchy
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Hierarchy", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                
                if (parent == null && children.isEmpty()) {
                    Text("No hierarchy links.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                }

                parent?.let {
                    RelationshipItemRow(
                        label = "Parent",
                        itemName = it.name,
                        onOpen = { onOpenItem(it.id) },
                        onRemove = onRemoveParent
                    )
                }

                children.forEach { child ->
                    RelationshipItemRow(
                        label = "Child",
                        itemName = child.name,
                        onOpen = { onOpenItem(child.id) },
                        onRemove = null
                    )
                }

                TextButton(
                    onClick = onSetParent,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Icon(Icons.Default.AccountTree, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (parent == null) "Set Parent" else "Change Parent")
                }
            }
        }

        // Operational Relationships
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Connections", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                
                if (relationships.isEmpty()) {
                    Text("No operational connections.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                }

                relationships.forEach { rel ->
                    RelationshipOperationalRow(
                        rel = rel,
                        onOpen = { onOpenItem(rel.relatedItemId) },
                        onOpenOnMap = { onOpenOnMap(rel.relatedItemId) },
                        onEdit = { onEditRelationship(rel.relationshipId) },
                        onRemove = { showDeleteConfirmByRelId = rel.relationshipId }
                    )
                }

                TextButton(
                    onClick = onAddRelationship,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Icon(Icons.Default.Link, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Relationship")
                }
            }
        }
    }

    if (showDeleteConfirmByRelId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmByRelId = null },
            title = { Text("Remove Relationship") },
            text = { Text("Are you sure you want to remove this connection? Both items will be kept.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveRelationship(showDeleteConfirmByRelId!!)
                    showDeleteConfirmByRelId = null
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmByRelId = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun RelationshipItemRow(
    label: String,
    itemName: String,
    onOpen: () -> Unit,
    onRemove: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(itemName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = onOpen) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open")
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.LinkOff, contentDescription = "Remove")
            }
        }
    }
}

@Composable
private fun RelationshipOperationalRow(
    rel: ItemRelationshipUiModel,
    onOpen: () -> Unit,
    onOpenOnMap: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(rel.displayLabel, style = MaterialTheme.typography.labelSmall)
            Text(rel.relatedItemName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            rel.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        
        var expanded by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Options")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("Open Item") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                    onClick = { expanded = false; onOpen() }
                )
                if (rel.hasMappedFeature) {
                    DropdownMenuItem(
                        text = { Text("View on Map") },
                        leadingIcon = { Icon(Icons.Default.Map, contentDescription = null) },
                        onClick = { expanded = false; onOpenOnMap() }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = { expanded = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text("Remove") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = { expanded = false; onRemove() }
                )
            }
        }
    }
}
