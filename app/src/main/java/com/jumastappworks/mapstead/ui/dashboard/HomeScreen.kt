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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.db.entities.MaintenanceRecordEntity
import com.jumastappworks.mapstead.data.help.HelpTopicId
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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
    val state by viewModel.uiState.collectAsState()

    when (val uiState = state) {
        is HomeUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is HomeUiState.Error -> {
            ErrorScreen(
                message = stringResource(uiState.messageRes),
                onRetry = { viewModel.retry() },
                onBack = onManageProperties // Fallback to list
            )
        }
        is HomeUiState.NotFound -> {
            ErrorScreen(
                message = stringResource(R.string.home_property_not_found),
                onRetry = { viewModel.retry() },
                onBack = onManageProperties
            )
        }
        is HomeUiState.Ready -> {
            HomeScreenContent(
                uiState = uiState,
                properties = properties,
                selectedPropertyId = selectedPropertyId,
                onSelectProperty = onSelectProperty,
                onAddProperty = onAddProperty,
                onManageProperties = onManageProperties,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToHelp = onNavigateToHelp,
                onAddSomething = onAddSomething,
                onFindSomething = onFindSomething,
                onOpenEmergency = onOpenEmergency,
                onOpenTasks = onOpenTasks,
                onOpenItemDetails = onOpenItemDetails,
                onEditProperty = onEditProperty
            )
        }
        HomeUiState.NoProperties -> {
            // Handled by NavGraph usually, but safety fallback
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.empty_properties_title))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreenContent(
    uiState: HomeUiState.Ready,
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
            PrimaryActionsSection(
                onAddSomething = onAddSomething,
                onFindSomething = onFindSomething,
                onOpenEmergency = onOpenEmergency
            )

            // Needs Attention
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(
                    title = stringResource(R.string.home_needs_attention),
                    onViewAll = onOpenTasks
                )
                if (uiState.needsAttentionTasks.isNotEmpty()) {
                    uiState.needsAttentionTasks.forEach { task ->
                        TaskCard(task, onClick = onOpenTasks)
                    }
                } else {
                    NeedsAttentionEmptyState()
                }
            }

            // Map Only Content Warning
            if (uiState.hasMapFeaturesOnly) {
                MapOnlyGuidanceCard(onAddSomething)
            }

            // Upcoming
            if (uiState.upcomingTasks.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(
                        title = stringResource(R.string.home_upcoming),
                        onViewAll = onOpenTasks
                    )
                    uiState.upcomingTasks.forEach { task ->
                        TaskCard(task, onClick = onOpenTasks)
                    }
                }
            }

            // Recently Added
            if (uiState.recentlyAddedItems.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(
                        title = stringResource(R.string.home_recently_added),
                        onViewAll = onFindSomething
                    )
                    uiState.recentlyAddedItems.forEach { item ->
                        ItemRow(item.name, item.category, onClick = { onOpenItemDetails(item.id) })
                    }
                }
            } else if (uiState.needsAttentionTasks.isEmpty() && !uiState.hasMapFeaturesOnly && uiState.upcomingTasks.isEmpty()) {
                // Total empty state for first items
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
                .clickable(
                    onClickLabel = stringResource(R.string.home_switch_property),
                    role = Role.Button,
                    onClick = { expanded = true }
                )
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

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            properties.forEach { property ->
                val isSelected = property.id == currentProperty?.id
                DropdownMenuItem(
                    text = { Text(property.name) },
                    onClick = {
                        expanded = false
                        onSelectProperty(property.id)
                    },
                trailingIcon = {
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.state_selected))
                    }
                },
                    modifier = Modifier.semantics {
                        role = Role.RadioButton
                        selected = isSelected
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
private fun PrimaryActionsSection(
    onAddSomething: () -> Unit,
    onFindSomething: () -> Unit,
    onOpenEmergency: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val screenWidth = configuration.screenWidthDp.dp
    
    val stackSecondary = screenWidth < 400.dp || fontScale >= 1.5f

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PrimaryActionCard(
            title = stringResource(R.string.home_add_something),
            icon = Icons.Default.AddCircle,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            isFullWidth = true,
            onClick = onAddSomething
        )
        
        if (stackSecondary) {
            PrimaryActionCard(
                title = stringResource(R.string.home_find_something),
                icon = Icons.Default.Search,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                isFullWidth = true,
                onClick = onFindSomething
            )
            PrimaryActionCard(
                title = stringResource(R.string.home_emergency_guide),
                icon = Icons.Default.Warning,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                isFullWidth = true,
                onClick = onOpenEmergency
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
    }
}

@Composable
private fun PrimaryActionCard(
    title: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFullWidth: Boolean = false
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (isFullWidth) 80.dp else 100.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor)
    ) {
        if (isFullWidth) {
            Row(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(16.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    title, 
                    style = MaterialTheme.typography.labelLarge, 
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
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
private fun TaskCard(task: MaintenanceRecordEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Assignment, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(task.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(
                    formatDueDate(task.nextDueDate), 
                    style = MaterialTheme.typography.bodySmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

@Composable
private fun ErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.ErrorOutline, 
            contentDescription = null, 
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(24.dp))
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.retry))
        }
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.back))
        }
    }
}

@Composable
private fun formatDueDate(date: LocalDate?): String {
    if (date == null) return stringResource(R.string.maint_no_due_date)
    val now = LocalDate.now()
    return when (date) {
        now -> stringResource(R.string.maint_due_today)
        now.plusDays(1) -> stringResource(R.string.maint_due_tomorrow)
        else -> {
            if (date.isBefore(now)) {
                stringResource(R.string.maint_overdue) + " (" + date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) + ")"
            } else {
                stringResource(R.string.maint_due_on, date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
            }
        }
    }
}
