package com.jumastappworks.mapstead.ui.help

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.help.GettingStartedPropertyContext
import com.jumastappworks.mapstead.data.help.GettingStartedStepId
import com.jumastappworks.mapstead.ui.components.GettingStartedChecklist
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GettingStartedScreen(
    viewModel: GettingStartedViewModel,
    onBack: () -> Unit,
    onNavigateToCreateProperty: () -> Unit,
    onNavigateToCreateMap: (UUID) -> Unit,
    onNavigateToMap: (UUID, UUID) -> Unit,
    onNavigateToPlans: (UUID) -> Unit,
    onNavigateToAddInfrastructure: (UUID) -> Unit,
    onNavigateToAddMaintenance: (UUID) -> Unit,
    onNavigateToFiles: (UUID) -> Unit,
    onNavigateToEmergency: (UUID) -> Unit,
    onHelpClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gs_checklist_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        when (val s = state) {
            is GettingStartedUiState.LoadingProperties -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is GettingStartedUiState.LoadingProgress -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is GettingStartedUiState.NeedsProperty -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val context = s.context
                    val activeProps = when (context) {
                        is GettingStartedPropertyContext.NeedsSelection -> context.activeProperties
                        is GettingStartedPropertyContext.Selected -> context.activeProperties
                        else -> emptyList()
                    }

                    if (activeProps.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.gs_select_property_hint),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                value = stringResource(R.string.select_property),
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                activeProps.forEach { prop ->
                                    DropdownMenuItem(
                                        text = { Text(prop.name) },
                                        onClick = {
                                            viewModel.selectProperty(prop.id)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(8.dp))
                    }

                    GettingStartedChecklist(
                        steps = s.steps,
                        onStepClick = { stepId: GettingStartedStepId ->
                            when (stepId) {
                                GettingStartedStepId.CREATE_PROPERTY -> onNavigateToCreateProperty()
                                else -> {} // others disabled
                            }
                        },
                        onDismiss = null,
                        onHelpClick = onHelpClick
                    )
                }
            }
            is GettingStartedUiState.Ready -> {
                val context = s.context
                val selectedId = context.property.id
                val activeProps = context.activeProperties

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (activeProps.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.gs_select_property_hint),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            val selectedProp = activeProps.find { it.id == selectedId }
                            OutlinedTextField(
                                value = selectedProp?.name ?: stringResource(R.string.select_property),
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                activeProps.forEach { prop ->
                                    DropdownMenuItem(
                                        text = { Text(prop.name) },
                                        onClick = {
                                            viewModel.selectProperty(prop.id)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(8.dp))
                    }

                    GettingStartedChecklist(
                        steps = s.steps,
                        onStepClick = { stepId: GettingStartedStepId ->
                            when (stepId) {
                                GettingStartedStepId.CREATE_PROPERTY -> onNavigateToCreateProperty()
                                GettingStartedStepId.CREATE_MAP -> selectedId?.let { onNavigateToCreateMap(it) }
                                GettingStartedStepId.ADD_FEATURE -> selectedId?.let { pid ->
                                    if (s.plans.isEmpty()) {
                                        onNavigateToCreateMap(pid)
                                    } else if (s.plans.size == 1) {
                                        onNavigateToMap(pid, s.plans.first().id)
                                    } else {
                                        onNavigateToPlans(pid)
                                    }
                                }
                                GettingStartedStepId.ADD_INFRA -> selectedId?.let { onNavigateToAddInfrastructure(it) }
                                GettingStartedStepId.ADD_MAINT -> selectedId?.let { onNavigateToAddMaintenance(it) }
                                GettingStartedStepId.ADD_PHOTO -> selectedId?.let { onNavigateToFiles(it) }
                                GettingStartedStepId.REVIEW_EMERGENCY -> selectedId?.let { pid ->
                                    viewModel.markEmergencyReviewed(pid)
                                    onNavigateToEmergency(pid)
                                }
                            }
                        },
                        onDismiss = null,
                        onHelpClick = onHelpClick
                    )
                    
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}
