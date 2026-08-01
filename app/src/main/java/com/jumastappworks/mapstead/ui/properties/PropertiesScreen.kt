package com.jumastappworks.mapstead.ui.properties

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.help.HelpTopicId
import com.jumastappworks.mapstead.ui.components.EmptyState
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertiesScreen(
    viewModel: PropertiesViewModel,
    onPropertyClick: (String) -> Unit,
    onAddPropertyClick: () -> Unit,
    onHelpClick: (HelpTopicId) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    var showArchivedDialog by remember { mutableStateOf(false) }
    var deleteConfirmProperty by remember { mutableStateOf<PropertyEntity?>(null) }
    var archiveConfirmProperty by remember { mutableStateOf<PropertyEntity?>(null) }

    val s = uiState as? PropertiesUiState.Loaded
    val isWorking = s?.let { it.exampleOperation != ExampleOperation.Idle || it.isArchiving } ?: false
    val errorMsg = s?.errorRes?.let { stringResource(it) }

    LaunchedEffect(errorMsg) {
        errorMsg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.your_properties), fontWeight = FontWeight.Bold) },
                actions = {
                    val s = uiState
                    if (s is PropertiesUiState.Loaded && s.archivedProperties.isNotEmpty()) {
                        IconButton(onClick = { showArchivedDialog = true }) {
                            Icon(Icons.Default.Archive, contentDescription = stringResource(R.string.archived_properties))
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            val s = uiState
            if (s is PropertiesUiState.Loaded && s.properties.isNotEmpty()) {
                FloatingActionButton(onClick = onAddPropertyClick) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_property))
                }
            }
        }
    ) { padding ->
        when (val s = uiState) {
            is PropertiesUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is PropertiesUiState.Loaded -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    if (s.isDemoInstalled) {
                        Button(
                            onClick = { viewModel.removeDemoData() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            enabled = !isWorking
                        ) {
                            if (s.exampleOperation == ExampleOperation.Removing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onErrorContainer)
                            } else {
                                Icon(Icons.Default.DeleteForever, contentDescription = null)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.remove_example_property_action))
                        }
                    }

                    if (s.showWelcome) {
                        WelcomeGuidanceCard(
                            onCreateProperty = onAddPropertyClick,
                            onExploreHelp = { onHelpClick(HelpTopicId.GETTING_STARTED) },
                            onDismiss = { viewModel.dismissWelcome() },
                            modifier = Modifier.padding(16.dp),
                            enabled = !isWorking
                        )
                    } else if (s.properties.isEmpty()) {
                        EmptyState(
                            title = stringResource(R.string.empty_properties_title),
                            description = stringResource(R.string.empty_properties_desc),
                            icon = Icons.Default.Home,
                            primaryActionLabel = stringResource(R.string.add_property),
                            onPrimaryAction = onAddPropertyClick,
                            secondaryActionLabel = if (s.exampleOperation == ExampleOperation.Installing) stringResource(R.string.loading) else stringResource(R.string.explore_example_property),
                            onSecondaryAction = { viewModel.installDemoData() },
                            primaryActionEnabled = !isWorking,
                            secondaryActionEnabled = !isWorking,
                            helpTopicId = HelpTopicId.PROPERTIES,
                            onHelpClick = onHelpClick,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(s.properties) { property ->
                                PropertyCard(
                                    property = property,
                                    onClick = { onPropertyClick(property.id.toString()) },
                                    onArchive = { if (!s.isArchiving) archiveConfirmProperty = property },
                                    enabled = !s.isArchiving && !isWorking
                                )
                            }
                        }
                    }
                }

                if (showArchivedDialog) {
                    ArchivedPropertiesDialog(
                        properties = s.archivedProperties,
                        onDismiss = { showArchivedDialog = false },
                        onRestore = { viewModel.restoreProperty(it) },
                        onDelete = { deleteConfirmProperty = it }
                    )
                }
            }
        }
    }

    deleteConfirmProperty?.let { prop ->
        AlertDialog(
            onDismissRequest = { deleteConfirmProperty = null },
            title = { Text(stringResource(R.string.remove_property_title)) },
            text = { Text(stringResource(R.string.remove_property_message, prop.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.softDeleteProperty(prop.id)
                        deleteConfirmProperty = null
                    }
                ) {
                    Text(stringResource(R.string.remove_property_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmProperty = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    archiveConfirmProperty?.let { prop ->
        AlertDialog(
            onDismissRequest = { archiveConfirmProperty = null },
            title = { Text(stringResource(R.string.archive_property_title)) },
            text = { Text(stringResource(R.string.archive_property_message, prop.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.archiveProperty(prop.id)
                        archiveConfirmProperty = null
                    }
                ) {
                    Text(stringResource(R.string.archive_label))
                }
            },
            dismissButton = {
                TextButton(onClick = { archiveConfirmProperty = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ArchivedPropertiesDialog(
    properties: List<PropertyEntity>,
    onDismiss: () -> Unit,
    onRestore: (UUID) -> Unit,
    onDelete: (PropertyEntity) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.archived_properties)) },
        text = {
            if (properties.isEmpty()) {
                Text(stringResource(R.string.no_archived_properties))
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                ) {
                    items(properties) { prop ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(prop.name, fontWeight = FontWeight.Bold)
                                    Text(prop.propertyType, style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(onClick = { onRestore(prop.id) }) {
                                    Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.restore_label))
                                }
                                IconButton(onClick = { onDelete(prop) }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close_label))
            }
        }
    )
}

@Composable
private fun WelcomeGuidanceCard(
    onCreateProperty: () -> Unit,
    onExploreHelp: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Celebration,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    stringResource(R.string.welcome_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, enabled = enabled) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.dismiss))
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                stringResource(R.string.welcome_desc),
                style = MaterialTheme.typography.bodyLarge
            )
            
            Spacer(Modifier.height(12.dp))
            
            Text(
                stringResource(R.string.welcome_storage_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            
            Spacer(Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onCreateProperty,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    enabled = enabled
                ) {
                    Text(stringResource(R.string.welcome_action_create))
                }
                
                OutlinedButton(
                    onClick = onExploreHelp,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    enabled = enabled
                ) {
                    Text(stringResource(R.string.welcome_action_help))
                }
            }
            
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                enabled = enabled
            ) {
                Text(stringResource(R.string.not_now))
            }
        }
    }
}

@Composable
fun PropertyCard(
    property: PropertyEntity,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    enabled: Boolean = true
) {
    Card(
        onClick = if (enabled) onClick else ({}),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(property.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    property.propertyType,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!property.city.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${property.city}, ${property.stateOrRegion ?: ""}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            IconButton(onClick = onArchive, enabled = enabled) {
                Icon(Icons.Default.Archive, contentDescription = stringResource(R.string.archive_property_desc))
            }
        }
    }
}
