package com.jumastappworks.mapstead.ui.relationships

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.jumastappworks.mapstead.data.help.HelpTopicId
import com.jumastappworks.mapstead.ui.components.EmptyState
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfrastructureRelationshipsScreen(
    propertyId: UUID,
    viewModel: InfrastructureRelationshipsViewModel,
    onNavigateBack: () -> Unit,
    onOpenItem: (UUID) -> Unit,
    onAddRelationship: (UUID) -> Unit,
    onSetParent: (UUID) -> Unit,
    onHelpClick: (HelpTopicId) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(propertyId) {
        viewModel.init(propertyId)
    }

    state?.let { uiState ->
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.relationships_header))
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
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Summary Stats
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(stringResource(R.string.items_stat), uiState.counts.totalItems)
                        StatItem(stringResource(R.string.links_stat), uiState.counts.systemRelationships)
                        StatItem(stringResource(R.string.hierarchy_stat), uiState.counts.hierarchyLinks)
                        StatItem(stringResource(R.string.unconnected_stat), uiState.counts.unconnected)
                    }
                }

                // Filter Tabs
                PrimaryTabRow(
                    selectedTabIndex = RelationshipViewFilter.entries.indexOf(uiState.currentFilter),
                    containerColor = MaterialTheme.colorScheme.surface,
                    divider = {}
                ) {
                    RelationshipViewFilter.entries.forEach { filter ->
                        Tab(
                            selected = uiState.currentFilter == filter,
                            onClick = { viewModel.setFilter(filter) },
                            text = { Text(stringResource(filter.labelRes)) }
                        )
                    }
                }

                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        when (uiState.currentFilter) {
                            RelationshipViewFilter.Hierarchy -> {
                                items(uiState.flattenedHierarchy) { node ->
                                    HierarchyItemRow(node, onOpenItem)
                                }
                            }
                            RelationshipViewFilter.Connections -> {
                                items(uiState.connections) { rel ->
                                    RelationshipRow(rel, onOpenItem, viewModel)
                                }
                            }
                            RelationshipViewFilter.Unconnected -> {
                                if (uiState.unconnectedItems.isEmpty()) {
                                    item {
                                        EmptyState(
                                            title = stringResource(R.string.empty_relationships_title),
                                            description = stringResource(R.string.empty_relationships_desc),
                                            icon = Icons.Default.AccountTree,
                                            helpTopicId = HelpTopicId.CONNECTIONS,
                                            onHelpClick = onHelpClick
                                        )
                                    }
                                } else {
                                    items(uiState.unconnectedItems) { item ->
                                        UnconnectedItemRow(item, onOpenItem, onAddRelationship, onSetParent)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun HierarchyItemRow(node: HierarchyNode, onOpenItem: (UUID) -> Unit) {
    ListItem(
        headlineContent = { Text(node.item.name) },
        supportingContent = { Text(node.item.category) },
        leadingContent = { 
            if (node.depth > 0) {
                Row {
                    Spacer(Modifier.width((node.depth * 16).dp))
                    Icon(Icons.Default.SubdirectoryArrowRight, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            } else {
                Icon(Icons.Default.Foundation, contentDescription = null)
            }
        },
        modifier = Modifier.clickable { onOpenItem(node.item.id) }
    )
}

@Composable
private fun RelationshipRow(
    rel: PropertyRelationshipUiModel, 
    onOpenItem: (UUID) -> Unit,
    viewModel: InfrastructureRelationshipsViewModel
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rel.sourceItemName, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onOpenItem(rel.sourceItemId) })
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.padding(horizontal = 8.dp).size(16.dp))
                Text(rel.targetItemName, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onOpenItem(rel.targetItemId) })
            }
        },
        supportingContent = { 
            Column {
                Text(rel.displayLabel + " (" + rel.canonicalType.canonicalName + ")")
                rel.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        trailingContent = {
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Remove")
            }
        }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.remove_relationship_title)) },
            text = { Text(stringResource(R.string.remove_relationship_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.removeRelationship(rel.relationshipId)
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
private fun UnconnectedItemRow(
    item: InfrastructureItemEntity,
    onOpenItem: (UUID) -> Unit,
    onAddRelationship: (UUID) -> Unit,
    onSetParent: (UUID) -> Unit
) {
    ListItem(
        headlineContent = { Text(item.name) },
        supportingContent = { Text(item.category) },
        trailingContent = {
            Row {
                IconButton(onClick = { onSetParent(item.id) }) {
                    Icon(Icons.Default.AccountTree, contentDescription = "Set Parent")
                }
                IconButton(onClick = { onAddRelationship(item.id) }) {
                    Icon(Icons.Default.Link, contentDescription = "Add Relationship")
                }
            }
        },
        modifier = Modifier.clickable { onOpenItem(item.id) }
    )
}
