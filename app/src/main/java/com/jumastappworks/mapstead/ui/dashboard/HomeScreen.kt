package com.jumastappworks.mapstead.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.help.HelpTopicId
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    properties: List<PropertyEntity>,
    selectedPropertyId: UUID,
    onSelectProperty: (UUID) -> Unit,
    onAddProperty: () -> Unit,
    onManageProperties: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHelp: (HelpTopicId) -> Unit,
    onAddSomething: () -> Unit,
    onFindSomething: () -> Unit,
    onOpenEmergency: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenItemDetails: (UUID) -> Unit,
    onEditProperty: (UUID) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    PropertySelector(
                        currentProperty = uiState.property,
                        properties = properties,
                        onSelectProperty = onSelectProperty,
                        onAddProperty = onAddProperty,
                        onManageProperties = onManageProperties
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Primary Actions
            PrimaryActionsRow(
                onAddSomething = onAddSomething,
                onFindSomething = onFindSomething,
                onOpenEmergency = onOpenEmergency
            )

            // Needs Attention
            if (uiState.needsAttentionTasks.isNotEmpty()) {
                SectionHeader(
                    title = stringResource(R.string.home_needs_attention),
                    onViewAll = onOpenTasks
                )
                uiState.needsAttentionTasks.take(3).forEach { task ->
                    TaskCard(task.title, task.serviceDate.toString(), onClick = onOpenTasks)
                }
            } else if (!uiState.isLoading && uiState.property != null) {
                NeedsAttentionEmptyState()
            }

            // Recently Added
            if (uiState.recentlyAddedItems.isNotEmpty()) {
                SectionHeader(
                    title = stringResource(R.string.home_recently_updated),
                    onViewAll = onFindSomething
                )
                uiState.recentlyAddedItems.forEach { item ->
                    ItemRow(item.name, item.category, onClick = { onOpenItemDetails(item.id) })
                }
            } else if (!uiState.isLoading) {
                // Guide customer to add first item
                WelcomeGuideCard(onAddSomething)
            }

            // Secondary Actions / Quick Links
            SecondaryActions(
                onEditProperty = { onEditProperty(selectedPropertyId) },
                onHelp = { onNavigateToHelp(HelpTopicId.GETTING_STARTED) }
            )
        }
    }
}

@Composable
private fun PropertySelector(
    currentProperty: PropertyEntity?,
    properties: List<PropertyEntity>,
    onSelectProperty: (UUID) -> Unit,
    onAddProperty: () -> Unit,
    onManageProperties: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .clickable { expanded = true }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currentProperty?.name ?: stringResource(R.string.loading),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            properties.forEach { property ->
                DropdownMenuItem(
                    text = { Text(property.name) },
                    onClick = {
                        expanded = false
                        onSelectProperty(property.id)
                    },
                    trailingIcon = {
                        if (property.id == currentProperty?.id) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_property)) },
                onClick = {
                    expanded = false
                    onAddProperty()
                },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.desc_open_properties)) },
                onClick = {
                    expanded = false
                    onManageProperties()
                },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) }
            )
        }
    }
}

@Composable
private fun PrimaryActionsRow(
    onAddSomething: () -> Unit,
    onFindSomething: () -> Unit,
    onOpenEmergency: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PrimaryActionCard(
            title = stringResource(R.string.home_add_something),
            icon = Icons.Default.AddCircle,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
            onClick = onAddSomething
        )
        PrimaryActionCard(
            title = stringResource(R.string.home_find_something),
            icon = Icons.Default.Search,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
            onClick = onFindSomething
        )
        PrimaryActionCard(
            title = stringResource(R.string.home_emergency_guide),
            icon = Icons.Default.Warning,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
            onClick = onOpenEmergency
        )
    }
}

@Composable
private fun PrimaryActionCard(
    title: String,
    icon: ImageVector,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(100.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionHeader(title: String, onViewAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        TextButton(onClick = onViewAll) {
            Text(stringResource(R.string.view_all))
        }
    }
}

@Composable
private fun TaskCard(title: String, dueDate: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(dueDate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun ItemRow(name: String, category: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(name) },
        supportingContent = { Text(category) },
        leadingContent = { Icon(Icons.Default.Foundation, contentDescription = null) },
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun NeedsAttentionEmptyState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.no_tasks_attention_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.no_tasks_attention_supporting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun WelcomeGuideCard(onAddSomething: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.nothing_added_yet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.nothing_added_yet_supporting),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAddSomething) {
                Text(stringResource(R.string.add_something_now_action))
            }
        }
    }
}

@Composable
private fun SecondaryActions(
    onEditProperty: () -> Unit,
    onHelp: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onEditProperty,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Edit, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.edit_property))
        }
        OutlinedButton(
            onClick = onHelp,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.help_center_title))
        }
    }
}
