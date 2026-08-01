package com.jumastappworks.mapstead.ui.plans

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.db.entities.PlanEntity
import com.jumastappworks.mapstead.data.help.HelpTopicId
import com.jumastappworks.mapstead.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlansScreen(
    viewModel: PlansViewModel,
    onBack: () -> Unit,
    onPlanClick: (PlanEntity) -> Unit,
    onCreatePlanClick: () -> Unit,
    onHelpClick: (HelpTopicId) -> Unit
) {
    val plans by viewModel.plans.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plans)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            if (plans.isNotEmpty()) {
                FloatingActionButton(
                    onClick = onCreatePlanClick,
                    modifier = Modifier.testTag("CreatePlanButton")
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_label))
                }
            }
        }
    ) { padding ->
        if (plans.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.empty_maps_title),
                description = stringResource(R.string.empty_maps_desc),
                icon = Icons.Default.Map,
                primaryActionLabel = stringResource(R.string.create_property_map_title),
                onPrimaryAction = onCreatePlanClick,
                helpTopicId = HelpTopicId.PROPERTY_MAPS,
                onHelpClick = onHelpClick,
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(plans) { plan ->
                    PlanCard(plan = plan, onClick = { onPlanClick(plan) })
                }
            }
        }
    }
}

@Composable
fun PlanCard(plan: PlanEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("PlanCard_${plan.name}"),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Layers, contentDescription = null)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(plan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(plan.planType, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
