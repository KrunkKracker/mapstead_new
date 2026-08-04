package com.jumastappworks.mapstead.ui.maintenance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.db.entities.MaintenanceRecordEntity
import com.jumastappworks.mapstead.data.help.HelpTopicId
import com.jumastappworks.mapstead.ui.components.EmptyState
import com.jumastappworks.mapstead.util.MaintenanceStatus
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceScreen(
    viewModel: MaintenanceViewModel,
    onBack: () -> Unit,
    onAddRecord: (UUID) -> Unit,
    onOpenRecord: (UUID, UUID) -> Unit,
    onHelpClick: (HelpTopicId) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { 
                    val propertyName = (uiState as? MaintenanceUiState.Ready)?.property?.name ?: ""
                    Column {
                        Text(propertyName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.maintenance), fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    if (uiState is MaintenanceUiState.Ready) {
                        val propertyId = (uiState as MaintenanceUiState.Ready).property.id
                        IconButton(onClick = { onAddRecord(propertyId) }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
                        }
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is MaintenanceUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is MaintenanceUiState.NotFound -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.property_not_found))
                }
            }
            is MaintenanceUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is MaintenanceUiState.Ready -> {
                MaintenanceHubContent(
                    modifier = Modifier.padding(padding),
                    state = state,
                    onFilterSelect = { viewModel.setFilter(it) },
                    onRecordClick = { onOpenRecord(state.property.id, it) },
                    onAddRecord = { onAddRecord(state.property.id) },
                    onHelpClick = onHelpClick,
                    onClearInfrastructureFilter = { viewModel.setInfrastructureFilter(null) }
                )
            }
        }
    }
}

@Composable
fun MaintenanceHubContent(
    modifier: Modifier = Modifier,
    state: MaintenanceUiState.Ready,
    onFilterSelect: (MaintenanceFilter) -> Unit,
    onRecordClick: (UUID) -> Unit,
    onAddRecord: () -> Unit,
    onHelpClick: (HelpTopicId) -> Unit,
    onClearInfrastructureFilter: () -> Unit = {}
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            MaintenanceSummaryRow(state.counts)
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterTabs(state.selectedFilter, onFilterSelect)
                
                if (state.filteredInfrastructureItemId != null) {
                    val itemName = state.infrastructureItems.find { it.id == state.filteredInfrastructureItemId }?.name ?: "Selected Item"
                    AssistChip(
                        onClick = onClearInfrastructureFilter,
                        label = { Text("Filtered to: $itemName") },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear Filter", modifier = Modifier.size(18.dp)) }
                    )
                }
            }
        }

        if (state.filteredRecords.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.empty_maint_title),
                    description = stringResource(R.string.empty_maint_desc),
                    icon = Icons.Default.Build,
                    primaryActionLabel = stringResource(R.string.add_maintenance_title),
                    onPrimaryAction = onAddRecord,
                    helpTopicId = HelpTopicId.MAINTENANCE,
                    onHelpClick = onHelpClick
                )
            }
        } else {
            items(state.filteredRecords) { record ->
                MaintenanceTaskCard(record, onClick = { onRecordClick(record.id) })
            }
        }
        
        item {
            Spacer(Modifier.height(80.dp)) // space for FAB if needed
        }
    }
}

@Composable
fun MaintenanceSummaryRow(counts: MaintenanceCounts) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            SummaryChip(
                label = "Overdue",
                count = counts.overdue,
                color = MaterialTheme.colorScheme.errorContainer,
                icon = Icons.Default.Warning
            )
        }
        item {
            SummaryChip(
                label = "Due Today",
                count = counts.dueToday,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                icon = Icons.Default.Today
            )
        }
        item {
            SummaryChip(
                label = "Due Soon",
                count = counts.dueSoon,
                color = MaterialTheme.colorScheme.secondaryContainer,
                icon = Icons.Default.Schedule
            )
        }
    }
}

@Composable
fun SummaryChip(label: String, count: Int, color: Color, icon: ImageVector) {
    Surface(
        color = color,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("$count $label", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun FilterTabs(selected: MaintenanceFilter, onSelect: (MaintenanceFilter) -> Unit) {
    ScrollableTabRow(
        selectedTabIndex = selected.ordinal,
        edgePadding = 0.dp,
        containerColor = Color.Transparent,
        divider = {}
    ) {
        MaintenanceFilter.entries.forEach { filter ->
            Tab(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                text = { Text(filter.name) }
            )
        }
    }
}

@Composable
fun MaintenanceTaskCard(record: MaintenanceRecordEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Build, contentDescription = null)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(record.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(record.category, style = MaterialTheme.typography.bodySmall)
                }
                if (MaintenanceStatus.isCompleted(record.status)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                val dateLabel = if (MaintenanceStatus.isCompleted(record.status)) "Completed: " else "Next Due: "
                val dateValue = if (MaintenanceStatus.isCompleted(record.status)) record.serviceDate else record.nextDueDate ?: "Unscheduled"
                Text(
                    text = "$dateLabel$dateValue",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

// Removed EmptyMaintenanceState in favor of common EmptyState
