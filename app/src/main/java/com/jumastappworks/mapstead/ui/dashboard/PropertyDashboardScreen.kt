package com.jumastappworks.mapstead.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.db.entities.PropertyEntity
import com.jumastappworks.mapstead.data.help.GettingStartedStep
import com.jumastappworks.mapstead.data.help.GettingStartedStepId
import com.jumastappworks.mapstead.data.help.HelpTopicId
import com.jumastappworks.mapstead.ui.components.GettingStartedChecklist
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertyDashboardScreen(
    viewModel: PropertyDashboardViewModel,
    onBack: () -> Unit,
    onNavigateToPlans: () -> Unit,
    onNavigateToMaintenance: () -> Unit,
    onNavigateToEmergency: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onEditPropertyClick: () -> Unit,
    onAddLocationClick: () -> Unit,
    onNavigateToInfrastructureList: () -> Unit,
    onQuickAddMaintenance: () -> Unit,
    onNavigateToFiles: (UUID) -> Unit,
    onNavigateToRelationships: (UUID) -> Unit,
    onNavigateToReports: (UUID) -> Unit,
    onHelpClick: (HelpTopicId) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        when (val s = state) {
                            is PropertyDashboardUiState.Ready -> s.property.name
                            else -> stringResource(R.string.property_dashboard)
                        }
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    IconButton(onClick = onEditPropertyClick, enabled = state is PropertyDashboardUiState.Ready) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_property))
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            )
        }
    ) { padding ->
        when (val s = state) {
            is PropertyDashboardUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is PropertyDashboardUiState.NotFound -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.property_not_found))
                }
            }
            is PropertyDashboardUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(s.messageRes), color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.retry() }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
            is PropertyDashboardUiState.Ready -> {
                DashboardContent(
                    modifier = Modifier.padding(padding),
                    property = s.property,
                    formattedAddress = s.formattedAddress,
                    planCount = s.planCount,
                    itemCount = s.itemCount,
                    emergencyCount = s.emergencyCount,
                    dueCount = s.dueMaintenanceCount,
                    attachmentCount = s.attachmentCount,
                    checklist = s.checklist,
                    showChecklist = s.showChecklist,
                    onDismissChecklist = { viewModel.dismissChecklist() },
                    onStepClick = { stepId: GettingStartedStepId ->
                        when (stepId) {
                            GettingStartedStepId.CREATE_PROPERTY -> onEditPropertyClick()
                            GettingStartedStepId.CREATE_MAP -> onNavigateToPlans()
                            GettingStartedStepId.ADD_FEATURE -> onNavigateToPlans() // usually start there
                            GettingStartedStepId.ADD_INFRA -> onNavigateToInfrastructureList()
                            GettingStartedStepId.ADD_MAINT -> onQuickAddMaintenance()
                            GettingStartedStepId.ADD_PHOTO -> onNavigateToFiles(s.property.id)
                            GettingStartedStepId.REVIEW_EMERGENCY -> {
                                viewModel.markEmergencyReviewed()
                                onNavigateToEmergency()
                            }
                        }
                    },
                    onNavigateToPlans = onNavigateToPlans,
                    onNavigateToMaintenance = onNavigateToMaintenance,
                    onNavigateToEmergency = onNavigateToEmergency,
                    onNavigateToInfrastructureList = onNavigateToInfrastructureList,
                    onQuickAddMaintenance = onQuickAddMaintenance,
                    onAddLocationClick = onAddLocationClick,
                    onNavigateToFiles = { onNavigateToFiles(s.property.id) },
                    onNavigateToRelationships = { onNavigateToRelationships(s.property.id) },
                    onNavigateToReports = { onNavigateToReports(s.property.id) },
                    onEditPropertyClick = onEditPropertyClick,
                    onHelpClick = onHelpClick
                )
            }
        }
    }
}

@Composable
fun DashboardContent(
    modifier: Modifier = Modifier,
    property: PropertyEntity,
    formattedAddress: String,
    planCount: Int,
    itemCount: Int,
    emergencyCount: Int,
    dueCount: Int,
    attachmentCount: Int,
    checklist: List<GettingStartedStep>,
    showChecklist: Boolean,
    onDismissChecklist: () -> Unit,
    onStepClick: (GettingStartedStepId) -> Unit,
    onEditPropertyClick: () -> Unit,
    onAddLocationClick: () -> Unit,
    onNavigateToPlans: () -> Unit,
    onNavigateToMaintenance: () -> Unit,
    onNavigateToEmergency: () -> Unit,
    onNavigateToInfrastructureList: () -> Unit,
    onQuickAddMaintenance: () -> Unit,
    onNavigateToFiles: () -> Unit,
    onNavigateToRelationships: () -> Unit,
    onNavigateToReports: () -> Unit,
    onHelpClick: (HelpTopicId) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    property.propertyType,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    property.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (formattedAddress.isNotBlank()) {
                    Text(
                        formattedAddress,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                property.description?.let { desc ->
                    Spacer(Modifier.height(8.dp))
                    Text(desc, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        val hasLocation = property.latitude != null && property.longitude != null
        val hasMap = planCount > 0

        if (showChecklist) {
            GettingStartedChecklist(
                steps = checklist,
                onStepClick = onStepClick,
                onDismiss = onDismissChecklist,
                onHelpClick = { onHelpClick(HelpTopicId.GETTING_STARTED) }
            )
        }

        if (!hasLocation) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.setup_add_location_cta), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onAddLocationClick) {
                        Text(stringResource(R.string.setup_checklist_add_location))
                    }
                }
            }
        } else if (!hasMap) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("This property has a location, but no map yet.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onNavigateToPlans) {
                        Text(stringResource(R.string.setup_checklist_create_map))
                    }
                }
            }
        }

        Text(stringResource(R.string.property_status), style = MaterialTheme.typography.titleMedium)

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.heightIn(max = 600.dp)
        ) {
            item {
                StatusCard(
                    title = stringResource(R.string.plans),
                    count = planCount.toString(),
                    icon = Icons.Default.Map,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    onClick = onNavigateToPlans
                )
            }
            item {
                StatusCard(
                    title = stringResource(R.string.infrastructure),
                    count = itemCount.toString(),
                    icon = Icons.Default.Foundation,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = onNavigateToInfrastructureList
                )
            }
            item {
                StatusCard(
                    title = stringResource(R.string.systems_label),
                    count = stringResource(R.string.connected_label),
                    icon = Icons.Default.AccountTree,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = onNavigateToRelationships
                )
            }
            item {
                StatusCard(
                    title = stringResource(R.string.emergency_items),
                    count = emergencyCount.toString(),
                    icon = Icons.Default.Warning,
                    color = if (emergencyCount > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (emergencyCount > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onNavigateToEmergency
                )
            }
            item {
                StatusCard(
                    title = stringResource(R.string.maintenance),
                    count = if (dueCount > 0) stringResource(R.string.due_count, dueCount) else stringResource(R.string.no_due_tasks),
                    icon = Icons.Default.Build,
                    color = if (dueCount > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
                    onClick = onNavigateToMaintenance
                )
            }
            item {
                StatusCard(
                    title = stringResource(R.string.files_card_title),
                    count = attachmentCount.toString(),
                    icon = Icons.Default.Folder,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = onNavigateToFiles
                )
            }
        }

        Button(
            onClick = onNavigateToPlans,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("OpenMapPlansButton"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.open_map_plans))
        }

        OutlinedButton(
            onClick = onQuickAddMaintenance,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Build, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.add_maintenance))
        }

        OutlinedButton(
            onClick = onNavigateToReports,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Description, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.reports_handoff_title))
        }
    }
}

@Composable
fun StatusCard(
    title: String,
    count: String,
    icon: ImageVector,
    color: Color,
    contentColor: Color = contentColorFor(color),
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color, contentColor = contentColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = contentColor)
            Spacer(Modifier.weight(1f))
            Text(
                count, 
                style = MaterialTheme.typography.headlineSmall, 
                fontWeight = FontWeight.Bold, 
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                color = contentColor
            )
            Text(title, style = MaterialTheme.typography.labelLarge, color = contentColor)
        }
    }
}
