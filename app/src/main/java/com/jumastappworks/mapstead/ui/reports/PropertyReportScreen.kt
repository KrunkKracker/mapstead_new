package com.jumastappworks.mapstead.ui.reports

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.handoff.*
import com.jumastappworks.mapstead.data.help.HelpTopicId
import com.jumastappworks.mapstead.data.reports.*
import com.jumastappworks.mapstead.ui.components.ContextHelpLink
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertyReportScreen(
    propertyId: UUID,
    viewModel: PropertyReportViewModel,
    onBack: () -> Unit,
    onHelpClick: (HelpTopicId) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    
    LaunchedEffect(propertyId) {
        viewModel.setPropertyId(propertyId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reports_handoff_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = state.reportData?.propertyName ?: stringResource(R.string.loading),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // PDF SECTION
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pdf_report_section_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                ContextHelpLink(
                    text = stringResource(R.string.what_is_this),
                    onClick = { onHelpClick(HelpTopicId.REPORTS_AND_HANDOFF) },
                    modifier = Modifier.width(140.dp)
                )
            }
            Text(
                text = stringResource(R.string.report_description),
                style = MaterialTheme.typography.bodyMedium
            )

            HorizontalDivider()

            Text(stringResource(R.string.include_sections_title), style = MaterialTheme.typography.titleMedium)
            
            state.options?.let { options ->
                SectionToggle(stringResource(R.string.property_profile_section), options.includes(PropertyReportSection.PROPERTY_PROFILE)) { 
                    viewModel.toggleSection(PropertyReportSection.PROPERTY_PROFILE, it) 
                }
                SectionToggle(stringResource(R.string.infrastructure_section), options.includes(PropertyReportSection.INFRASTRUCTURE)) { 
                    viewModel.toggleSection(PropertyReportSection.INFRASTRUCTURE, it) 
                }
                SectionToggle(stringResource(R.string.maintenance_history_section), options.includes(PropertyReportSection.MAINTENANCE_HISTORY)) { 
                    viewModel.toggleSection(PropertyReportSection.MAINTENANCE_HISTORY, it) 
                }
                
                if (options.includes(PropertyReportSection.MAINTENANCE_HISTORY)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
                        Text(stringResource(R.string.range_label), style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.width(8.dp))
                        RangeChip(stringResource(R.string.range_all), options.maintenanceRange == MaintenanceHistoryRange.ALL_TIME) { viewModel.setMaintenanceRange(MaintenanceHistoryRange.ALL_TIME) }
                        Spacer(Modifier.width(4.dp))
                        RangeChip(stringResource(R.string.range_last_12m), options.maintenanceRange == MaintenanceHistoryRange.LAST_12_MONTHS) { viewModel.setMaintenanceRange(MaintenanceHistoryRange.LAST_12_MONTHS) }
                        Spacer(Modifier.width(4.dp))
                        RangeChip(stringResource(R.string.range_this_year), options.maintenanceRange == MaintenanceHistoryRange.CURRENT_YEAR) { viewModel.setMaintenanceRange(MaintenanceHistoryRange.CURRENT_YEAR) }
                    }
                }

                SectionToggle(stringResource(R.string.upcoming_tasks_section), options.includes(PropertyReportSection.UPCOMING_MAINTENANCE)) { 
                    viewModel.toggleSection(PropertyReportSection.UPCOMING_MAINTENANCE, it) 
                }
                SectionToggle(stringResource(R.string.map_gis_summary_section), options.includes(PropertyReportSection.MAP_SUMMARY)) { 
                    viewModel.toggleSection(PropertyReportSection.MAP_SUMMARY, it) 
                }
                SectionToggle(stringResource(R.string.attachment_summary_section), options.includes(PropertyReportSection.ATTACHMENT_SUMMARY)) { 
                    viewModel.toggleSection(PropertyReportSection.ATTACHMENT_SUMMARY, it) 
                }
            }

            state.reportData?.let { data ->
                Text(stringResource(R.string.report_preview_title), style = MaterialTheme.typography.titleMedium)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.item_count_label, data.infrastructureItems.size))
                        Text(stringResource(R.string.record_count_label, data.maintenanceRecords.size))
                        Text(stringResource(R.string.task_count_label, data.upcomingMaintenance.size))
                        Text(stringResource(R.string.feature_count_label_simple, data.pointCount + data.lineCount + data.areaCount))
                        Text(stringResource(R.string.attachment_count_label_simple, data.attachmentSummary.totalCount))
                    }
                }
            }

            if (state.error == PropertyReportError.NO_SECTIONS_SELECTED) {
                Text(
                    text = stringResource(R.string.error_no_sections_selected),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            if (state.generatedFile != null) {
                Button(onClick = { viewModel.shareReport() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.share_report_button))
                }
            } else {
                Button(
                    onClick = { viewModel.generatePdf() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isGenerating && state.options?.enabledSections?.isNotEmpty() == true
                ) {
                    if (state.isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.Description, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.generate_report_button))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(thickness = 2.dp)
            Spacer(Modifier.height(16.dp))

            // HANDOFF SECTION
            Text(stringResource(R.string.handoff_package_section_title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.handoff_description),
                style = MaterialTheme.typography.bodyMedium
            )

            state.handoffOptions?.let { hOptions ->
                SectionToggle(stringResource(R.string.handoff_include_pdf_label), hOptions.includes(PropertyHandoffComponent.PDF_REPORT)) { 
                    viewModel.toggleHandoffComponent(PropertyHandoffComponent.PDF_REPORT, it) 
                }
                SectionToggle(stringResource(R.string.handoff_include_property_label), hOptions.includes(PropertyHandoffComponent.PROPERTY_ATTACHMENTS)) { 
                    viewModel.toggleHandoffComponent(PropertyHandoffComponent.PROPERTY_ATTACHMENTS, it) 
                }
                SectionToggle(stringResource(R.string.handoff_include_infrastructure_label), hOptions.includes(PropertyHandoffComponent.INFRASTRUCTURE_ATTACHMENTS)) { 
                    viewModel.toggleHandoffComponent(PropertyHandoffComponent.INFRASTRUCTURE_ATTACHMENTS, it) 
                }
                SectionToggle(stringResource(R.string.handoff_include_maintenance_label), hOptions.includes(PropertyHandoffComponent.MAINTENANCE_ATTACHMENTS)) { 
                    viewModel.toggleHandoffComponent(PropertyHandoffComponent.MAINTENANCE_ATTACHMENTS, it) 
                }
                SectionToggle(stringResource(R.string.handoff_include_map_label), hOptions.includes(PropertyHandoffComponent.MAP_FEATURE_ATTACHMENTS)) { 
                    viewModel.toggleHandoffComponent(PropertyHandoffComponent.MAP_FEATURE_ATTACHMENTS, it) 
                }
            }

            if (state.handoffError == PropertyHandoffError.NOTHING_SELECTED) {
                Text(
                    text = stringResource(R.string.error_handoff_nothing_selected),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            if (state.isGeneratingHandoff) {
                HandoffProgressUI(state.handoffProgress)
            }

            if (state.generatedHandoffFile != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.handoff_package_ready), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.handoff_included_attachments_label, state.handoffIncludedCount))
                        if (state.handoffSkippedCount > 0) {
                            Text(stringResource(R.string.handoff_skipped_attachments_label, state.handoffSkippedCount), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Button(onClick = { viewModel.shareHandoff() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.handoff_share_button))
                }
            } else {
                Button(
                    onClick = { viewModel.generateHandoff() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isGeneratingHandoff && state.handoffOptions?.enabledComponents?.isNotEmpty() == true
                ) {
                    Icon(Icons.Default.Inventory, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.handoff_generate_button))
                }
            }

            if (state.handoffWarnings.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                TextButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                    Text(stringResource(R.string.handoff_show_warnings_label, state.handoffWarnings.size))
                }
                AnimatedVisibility(visible = expanded) {
                    Column {
                        state.handoffWarnings.forEach { warning ->
                            Text("${warning.category}: ${warning.displayName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (state.error != null && state.error != PropertyReportError.NO_SECTIONS_SELECTED) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text(stringResource(R.string.report_error_title)) },
            text = { Text(stringResource(when (state.error) {
                PropertyReportError.PROPERTY_NOT_FOUND -> R.string.error_property_not_found
                PropertyReportError.PDF_CREATION_FAILED -> R.string.error_pdf_creation_failed
                PropertyReportError.STORAGE_UNAVAILABLE -> R.string.error_storage_unavailable
                PropertyReportError.GENERATED_FILE_MISSING -> R.string.error_generated_file_missing
                PropertyReportError.SHARE_UNAVAILABLE -> R.string.error_share_unavailable
                PropertyReportError.SHARE_PERMISSION_FAILED -> R.string.error_share_permission_failed
                else -> R.string.error_unexpected_report
            })) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) { Text(stringResource(R.string.ok)) }
            }
        )
    }

    if (state.handoffError != null && state.handoffError != PropertyHandoffError.NOTHING_SELECTED) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text(stringResource(R.string.handoff_error_title)) },
            text = { Text(stringResource(when (state.handoffError) {
                PropertyHandoffError.PROPERTY_NOT_FOUND -> R.string.error_property_not_found
                PropertyHandoffError.REPORT_GENERATION_FAILED -> R.string.error_handoff_report_failed
                PropertyHandoffError.STORAGE_UNAVAILABLE -> R.string.error_handoff_storage_failed
                PropertyHandoffError.PACKAGE_CREATION_FAILED -> R.string.error_handoff_package_failed
                PropertyHandoffError.GENERATED_FILE_MISSING -> R.string.error_generated_file_missing
                PropertyHandoffError.SHARE_UNAVAILABLE -> R.string.error_share_unavailable
                PropertyHandoffError.SHARE_PERMISSION_FAILED -> R.string.error_share_permission_failed
                else -> R.string.error_unexpected_handoff
            })) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) { Text(stringResource(R.string.ok)) }
            }
        )
    }
}

@Composable
fun HandoffProgressUI(progress: PropertyHandoffProgress?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val stageText = when (progress?.stage) {
            PropertyHandoffStage.LOADING_PROPERTY -> stringResource(R.string.handoff_stage_loading)
            PropertyHandoffStage.GENERATING_REPORT -> stringResource(R.string.handoff_stage_generating_report)
            PropertyHandoffStage.VALIDATING_ATTACHMENTS -> stringResource(R.string.handoff_stage_validating)
            PropertyHandoffStage.COPYING_ATTACHMENTS -> stringResource(R.string.handoff_stage_copying, progress.completedItems, progress.totalItems)
            PropertyHandoffStage.WRITING_MANIFEST -> stringResource(R.string.handoff_stage_manifest)
            PropertyHandoffStage.CREATING_ARCHIVE -> stringResource(R.string.handoff_stage_finalizing)
            else -> stringResource(R.string.handoff_stage_preparing)
        }
        Text(stageText, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        if (progress?.stage == PropertyHandoffStage.COPYING_ATTACHMENTS && progress.totalItems > 0) {
            LinearProgressIndicator(
                progress = { progress.completedItems.toFloat() / progress.totalItems.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun RangeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
    )
}

@Composable
fun SectionToggle(label: String, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}
