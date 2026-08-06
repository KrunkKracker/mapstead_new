package com.jumastappworks.mapstead.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
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
import java.time.format.FormatStyle

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

    val selectedProperty = remember(properties, selectedPropertyId) {
        properties.find { it.id == selectedPropertyId }
    }
    val propertyName = selectedProperty?.name ?: stringResource(R.string.app_name)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable(
                                onClickLabel = stringResource(R.string.home_switch_property),
                                role = Role.Button,
                                onClick = { showPropertySwitcher = true }
                            )
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
                        Icon(Icons.Default.ExpandMore, contentDescription = stringResource(R.string.home_switch_property), modifier = Modifier.padding(start = 4.dp))
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                    IconButton(onClick = { onNavigateToHelp(HelpTopicId.GETTING_STARTED) }) {
                        Icon(Icons.Default.HelpOutline, contentDescription = stringResource(R.string.help_center_title))
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
                        Text(stringResource(R.string.home_property_not_found), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onManageProperties) {
                            Text(stringResource(R.string.desc_open_properties))
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
                        TextButton(onClick = onManageProperties) {
                            Text(stringResource(R.string.back))
                        }
                    }
                }
            }
            is HomeUiState.Ready -> {
                if (s.property.id != selectedPropertyId) {
                    Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    HomeContent(
                        modifier = Modifier.padding(padding),
                        state = s,
                        onAddSomething = onAddSomething,
                        onFindSomething = onFindSomething,
                        onOpenEmergency = onOpenEmergency,
                        onOpenTasks = onOpenTasks,
                        onOpenItemDetails = onOpenItemDetails,
                        onEditProperty = { onEditProperty(s.property.id) },
                        onNavigateToHelp = onNavigateToHelp,
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
internal fun HomeContent(
    modifier: Modifier = Modifier,
    state: HomeUiState.Ready,
    onAddSomething: () -> Unit,
    onFindSomething: () -> Unit,
    onOpenEmergency: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenItemDetails: (UUID) -> Unit,
    onEditProperty: () -> Unit,
    onNavigateToHelp: (HelpTopicId) -> Unit,
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

        // 3. Primary Grid (Find / Emergency) - Adaptive
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        val isNarrow = configuration.screenWidthDp < 400 || density.fontScale >= 1.5f

        if (isNarrow) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeActionCard(
                    title = stringResource(R.string.home_find_something),
                    icon = Icons.Default.Search,
                    onClick = onFindSomething,
                    modifier = Modifier.fillMaxWidth()
                )
                HomeActionCard(
                    title = stringResource(R.string.home_emergency_guide),
                    icon = Icons.Default.Warning,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    onClick = onOpenEmergency,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HomeActionCard(
                    title = stringResource(R.string.home_find_something),
                    icon = Icons.Default.Search,
                    onClick = onFindSomething,
                    modifier = Modifier.weight(1f)
                )
                HomeActionCard(
                    title = stringResource(R.string.home_emergency_guide),
                    icon = Icons.Default.Warning,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    onClick = onOpenEmergency,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Orientation / First Item Guidance
        if (state.showOrientationCard) {
            OrientationCard(onAddSomething, { onNavigateToHelp(HelpTopicId.GETTING_STARTED) })
        }

        // Checklist (Manual or deferred)
        if (state.showChecklist) {
            GettingStartedChecklist(
                steps = state.checklist,
                onStepClick = onStepClick,
                onDismiss = onDismissChecklist,
                onHelpClick = {}
            )
        }

        // 4. Needs Attention
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(stringResource(R.string.home_needs_attention), onOpenTasks)
            if (state.needsAttentionTasks.isNotEmpty()) {
                state.needsAttentionTasks.forEach { task ->
                    HomeTaskRow(task) { onOpenTasks() }
                }
            } else {
                NeedsAttentionEmptyState()
            }
        }

        // Map Only Content Warning
        if (state.hasMapFeaturesOnly) {
            MapOnlyGuidanceCard(onAddSomething)
        }

        // 5. Upcoming
        if (state.upcomingTasks.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(stringResource(R.string.home_upcoming), onOpenTasks)
                state.upcomingTasks.forEach { task ->
                    HomeTaskRow(task) { onOpenTasks() }
                }
            }
        }

        // 6. Recently Added or Truthful Empty State
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.home_recently_added), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            if (state.recentlyAddedItems.isEmpty()) {
                if (!state.hasAnyPropertyContent) {
                    WelcomeGuideCard(onAddSomething)
                } else {
                    Text(
                        stringResource(R.string.home_add_more_guidance),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                state.recentlyAddedItems.forEach { item ->
                    RecentItemRow(item, onClick = { onOpenItemDetails(item.itemId) })
                }
            }
        }
        
        // Secondary Actions
        SecondaryActions(
            onEditProperty = onEditProperty,
            onHelp = { onNavigateToHelp(HelpTopicId.GETTING_STARTED) }
        )

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun HomeActionCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(title)
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
        Text(stringResource(R.string.view_all), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
                Text(formatDueDateLocalized(task.dueDate), style = MaterialTheme.typography.bodySmall, color = color)
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
                    Icon(
                        if (item.isEmergency) Icons.Default.Warning else Icons.Default.Foundation, 
                        contentDescription = null, 
                        modifier = Modifier.size(24.dp), 
                        tint = if (item.isEmergency) Color(0xFFF97316) else MaterialTheme.colorScheme.primary
                    )
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

@Composable
private fun OrientationCard(onAddSomething: () -> Unit, onHelp: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.home_add_first_item_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.home_add_first_item_body),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddSomething, shape = RoundedCornerShape(8.dp)) {
                    Text(stringResource(R.string.home_add_something))
                }
                TextButton(onClick = onHelp) {
                    Text(stringResource(R.string.home_how_mapstead_works))
                }
            }
        }
    }
}

@Composable
private fun NeedsAttentionEmptyState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.home_no_tasks_attention),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MapOnlyGuidanceCard(onAddSomething: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.home_map_only_guidance),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = onAddSomething,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(stringResource(R.string.home_add_something))
            }
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
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.nothing_added_yet_supporting),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onAddSomething,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.home_add_something))
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
            Icon(Icons.Default.HelpOutline, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.help_center_title))
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
            Text(stringResource(R.string.home_switch_property), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            
            properties.forEach { p ->
                val isSelected = p.id == selectedPropertyId
                val selectedLabel = stringResource(R.string.state_selected)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(p.id) }
                        .padding(vertical = 12.dp)
                        .semantics(mergeDescendants = true) {
                            role = Role.RadioButton
                            selected = isSelected
                            stateDescription = if (isSelected) selectedLabel else ""
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
            
            val addPropertyLabel = if (properties.size == 1) {
                stringResource(R.string.home_add_another_property)
            } else {
                stringResource(R.string.add_property)
            }
            
            ListItem(
                headlineContent = { Text(addPropertyLabel) },
                leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                modifier = Modifier.clickable { onAddProperty() }
            )
            
            ListItem(
                headlineContent = { Text(stringResource(R.string.desc_open_properties)) },
                leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
                modifier = Modifier.clickable { onManageProperties() }
            )
        }
    }
}

@Composable
private fun formatDueDateLocalized(date: LocalDate?): String {
    if (date == null) return stringResource(R.string.maint_no_due_date)
    val today = LocalDate.now()
    return when {
        date.isBefore(today) -> stringResource(R.string.maint_overdue) + " (" + date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) + ")"
        date.isEqual(today) -> stringResource(R.string.maint_due_today)
        date.isEqual(today.plusDays(1)) -> stringResource(R.string.maint_due_tomorrow)
        else -> stringResource(R.string.maint_due_on, date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
    }
}
