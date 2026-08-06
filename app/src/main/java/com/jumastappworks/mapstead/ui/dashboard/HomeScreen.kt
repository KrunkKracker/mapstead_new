package com.jumastappworks.mapstead.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.help.GettingStartedStepId
import com.jumastappworks.mapstead.data.help.HelpTopicId
import com.jumastappworks.mapstead.ui.components.GettingStartedChecklist
import java.util.UUID
import java.time.format.DateTimeFormatter
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    properties: List<PropertyEntity>,
    selectedPropertyId: UUID?,
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
    val state by viewModel.uiState.collectAsState()
    var showPropertySwitcher by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val propertyName = (state as? HomeUiState.Ready)?.property?.name ?: "Mapstead"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { showPropertySwitcher = true }
                            .minimumInteractiveComponentSize()
                    ) {
                        Text(
                            text = propertyName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Icon(Icons.Default.ExpandMore, contentDescription = "Switch Property", modifier = Modifier.padding(start = 4.dp))
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                    IconButton(onClick = { onNavigateToHelp(HelpTopicId.GETTING_STARTED) }) {
                        Icon(Icons.Default.HelpOutline, contentDescription = "Help")
                    }
                }
            )
        }
    ) { padding ->
        when (val s = state) {
            HomeUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            HomeUiState.NotFound -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text(stringResource(R.string.property_not_found), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onManageProperties) {
                            Text("Manage Properties")
                        }
                    }
                }
            }
            is HomeUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text(stringResource(s.messageRes), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.retry() }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
            is HomeUiState.Ready -> {
                HomeContent(
                    modifier = Modifier.padding(padding),
                    state = s,
                    onAddSomething = onAddSomething,
                    onFindSomething = onFindSomething,
                    onOpenEmergency = onOpenEmergency,
                    onOpenTasks = onOpenTasks,
                    onOpenItemDetails = onOpenItemDetails,
                    onDismissChecklist = { viewModel.dismissChecklist() },
                    onStepClick = { stepId ->
                        when (stepId) {
                            GettingStartedStepId.CREATE_PROPERTY -> onEditProperty(s.property.id)
                            else -> onAddSomething()
                        }
                    }
                )
            }
        }
    }

    if (showPropertySwitcher) {
        PropertySwitcherSheet(
            properties = properties,
            selectedPropertyId = selectedPropertyId,
            onSelect = { onSelectProperty(it); showPropertySwitcher = false },
            onAddProperty = { onAddProperty(); showPropertySwitcher = false },
            onManageProperties = { onManageProperties(); showPropertySwitcher = false },
            onDismiss = { showPropertySwitcher = false }
        )
    }
}

@Composable
fun HomeContent(
    modifier: Modifier = Modifier,
    state: HomeUiState.Ready,
    onAddSomething: () -> Unit,
    onFindSomething: () -> Unit,
    onOpenEmergency: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenItemDetails: (UUID) -> Unit,
    onDismissChecklist: () -> Unit,
    onStepClick: (GettingStartedStepId) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 1. Current Property Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(state.property.propertyType, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(state.property.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (state.formattedAddress.isNotBlank()) {
                    Text(state.formattedAddress, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // 2. Add Something (Strongest Action)
        Button(
            onClick = onAddSomething,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.home_add_something), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        // 3. Primary Grid (Find / Emergency)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(
                onClick = onFindSomething,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_find_something))
            }
            
            FilledTonalButton(
                onClick = onOpenEmergency,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.Default.Warning, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_emergency_guide))
            }
        }

        // Checklist
        if (state.showChecklist) {
            GettingStartedChecklist(
                steps = state.checklist,
                onStepClick = onStepClick,
                onDismiss = onDismissChecklist,
                onHelpClick = {}
            )
        }

        // 4. Needs Attention
        if (state.needsAttentionTasks.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(stringResource(R.string.home_needs_attention), onOpenTasks)
                state.needsAttentionTasks.forEach { task ->
                    HomeTaskRow(task) { onOpenTasks() }
                }
            }
        }

        // 5. Upcoming
        if (state.upcomingTasks.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("Upcoming", onOpenTasks)
                state.upcomingTasks.forEach { task ->
                    HomeTaskRow(task) { onOpenTasks() }
                }
            }
        }

        // 6. Recently Added or Truthful Empty State
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Recently Added", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            if (state.recentlyAddedItems.isEmpty()) {
                if (!state.hasAnyPropertyContent) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.nothing_added_yet_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.nothing_added_yet_supporting), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = onAddSomething, modifier = Modifier.align(Alignment.End)) {
                                Text("Add Something")
                            }
                        }
                    }
                } else {
                    // Property has map-only content but no infrastructure items yet
                    Text("Add more equipment and systems to see them here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                }
            } else {
                state.recentlyAddedItems.forEach { item ->
                    RecentItemRow(item, onClick = { onOpenItemDetails(item.itemId) })
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SectionHeader(title: String, onAction: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAction() }
            .minimumInteractiveComponentSize()
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text("View All", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun HomeTaskRow(task: HomeTaskSummary, onClick: () -> Unit) {
    val color = when (task.dueState) {
        HomeTaskDueState.OVERDUE -> MaterialTheme.colorScheme.error
        HomeTaskDueState.TODAY -> Color(0xFFF57C00)
        HomeTaskDueState.UPCOMING -> MaterialTheme.colorScheme.primary
    }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatDueDate(task.dueDate), style = MaterialTheme.typography.bodySmall, color = color)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun RecentItemRow(item: HomePropertyItemSummary, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (item.isEmergency) Icons.Default.Warning else Icons.Default.Foundation, contentDescription = null, modifier = Modifier.size(24.dp), tint = if (item.isEmergency) Color(0xFFF97316) else MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertySwitcherSheet(
    properties: List<PropertyEntity>,
    selectedPropertyId: UUID?,
    onSelect: (UUID) -> Unit,
    onAddProperty: () -> Unit,
    onManageProperties: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Switch Property", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            
            properties.forEach { p ->
                val isSelected = p.id == selectedPropertyId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(p.id) }
                        .padding(vertical = 12.dp)
                        .semantics(mergeDescendants = true) {
                            if (isSelected) {
                                stateDescription = "Selected"
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Home, contentDescription = null, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(16.dp))
                    Text(p.name, modifier = Modifier.weight(1f), fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            
            HorizontalDivider()
            
            ListItem(
                headlineContent = { Text(stringResource(R.string.add_property)) },
                leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                modifier = Modifier.clickable { onAddProperty() }
            )
            
            ListItem(
                headlineContent = { Text("Manage Properties") },
                leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
                modifier = Modifier.clickable { onManageProperties() }
            )
        }
    }
}

fun formatDueDate(date: LocalDate?): String {
    if (date == null) return "No due date"
    val today = LocalDate.now()
    return when {
        date.isBefore(today) -> "Overdue"
        date.isEqual(today) -> "Due today"
        date.isEqual(today.plusDays(1)) -> "Due tomorrow"
        else -> "Due ${date.format(DateTimeFormatter.ofPattern("MMM d"))}"
    }
}
