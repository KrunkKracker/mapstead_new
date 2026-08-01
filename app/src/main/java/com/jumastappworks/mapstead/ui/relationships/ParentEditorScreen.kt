package com.jumastappworks.mapstead.ui.relationships

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Foundation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.ui.components.EmptyState
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentEditorScreen(
    propertyId: UUID,
    itemId: UUID,
    viewModel: ParentEditorViewModel,
    onNavigateBack: () -> Unit,
    onHelpClick: (com.jumastappworks.mapstead.data.help.HelpTopicId) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(propertyId, itemId) {
        viewModel.init(propertyId, itemId)
    }

    when (val uiState = state) {
        is ParentEditorUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is ParentEditorUiState.NotFound -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Item not found.")
            }
        }
        is ParentEditorUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(uiState.message, color = MaterialTheme.colorScheme.error)
            }
        }
        is ParentEditorUiState.Ready -> {
            if (uiState.saved) {
                LaunchedEffect(Unit) {
                    onNavigateBack()
                }
            }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { 
                            Column {
                                Text("Set Parent Item")
                                Text(uiState.itemName, style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack, enabled = !uiState.isSaving) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel))
                            }
                        }
                    )
                }
            ) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    if (uiState.isSaving) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    
                    uiState.errorRes?.let { errRes ->
                        Surface(color = MaterialTheme.colorScheme.errorContainer) {
                            Text(
                                text = stringResource(errRes),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    if (uiState.availableParents.isEmpty()) {
                        EmptyState(
                            title = stringResource(R.string.empty_infra_title),
                            description = "No other items available to link as a parent.",
                            icon = Icons.Default.Foundation,
                            helpTopicId = com.jumastappworks.mapstead.data.help.HelpTopicId.CONNECTIONS,
                            onHelpClick = onHelpClick,
                            useFullHeight = false
                        )
                    }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            ListItem(
                                headlineContent = { Text("No Parent", fontWeight = FontWeight.Bold) },
                                supportingContent = { Text("Item is a top-level component") },
                                trailingContent = {
                                    if (uiState.currentParentId == null) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                modifier = Modifier.clickable(enabled = !uiState.isSaving) { viewModel.setParent(null) }
                            )
                            HorizontalDivider()
                        }
                        
                        items(uiState.availableParents) { parent ->
                            ListItem(
                                headlineContent = { Text(parent.name) },
                                supportingContent = { Text("${parent.category}${parent.subtype?.let { " • $it" } ?: ""}") },
                                trailingContent = {
                                    if (uiState.currentParentId == parent.id) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                modifier = Modifier.clickable(enabled = !uiState.isSaving) { viewModel.setParent(parent.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}
