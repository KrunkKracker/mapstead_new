package com.jumastappworks.mapstead.ui.settings

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.prefs.MeasurementSystem
import com.jumastappworks.mapstead.data.prefs.ThemeSelection
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.launch

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    userPreferencesRepository: UserPreferencesRepository,
    isBackupEnabled: Boolean,
    onNavigateToHelp: () -> Unit,
    onNavigateToHelpTopic: (com.jumastappworks.mapstead.data.help.HelpTopicId) -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToSafety: () -> Unit,
    onNavigateToGettingStarted: () -> Unit,
    onNavigateToOrientation: () -> Unit
) {
    val prefs by userPreferencesRepository.userPreferencesFlow.collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    var showBoundaryResetConfirm by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.Bold) }
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
            // APPEARANCE
            SettingsSection(title = stringResource(R.string.settings_appearance)) {
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.app_theme), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeSelection.entries.forEach { selection ->
                                FilterChip(
                                    selected = prefs?.themeSelection == selection,
                                    onClick = { coroutineScope.launch { userPreferencesRepository.updateThemeSelection(selection) } },
                                    label = { Text(getThemeLabel(selection)) }
                                )
                            }
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.dynamic_color), style = MaterialTheme.typography.labelLarge)
                                Text(stringResource(R.string.use_android_12_colors), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = prefs?.useDynamicColor == true,
                                onCheckedChange = { checked -> coroutineScope.launch { userPreferencesRepository.updateUseDynamicColor(checked) } }
                            )
                        }
                    }
                }
            }

            // MEASUREMENTS
            SettingsSection(title = stringResource(R.string.settings_measurements)) {
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.display_units), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MeasurementSystem.entries.forEach { system ->
                                FilterChip(
                                    selected = prefs?.measurementSystem == system,
                                    onClick = { coroutineScope.launch { userPreferencesRepository.updateMeasurementSystem(system) } },
                                    label = { Text(getUnitLabel(system)) }
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.measurement_explanation),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // MAP & LOCATION
            SettingsSection(title = stringResource(R.string.settings_map_location)) {
                SettingsCard {
                    Column {
                        SettingsRow(
                            title = stringResource(R.string.help_topic_gps),
                            icon = Icons.Default.GpsFixed,
                            onClick = { onNavigateToHelpTopic(com.jumastappworks.mapstead.data.help.HelpTopicId.GPS_AND_ACCURACY) }
                        )
                        HorizontalDivider()
                        SettingsRow(
                            title = stringResource(R.string.map_help_title),
                            icon = Icons.Default.Map,
                            onClick = { onNavigateToHelpTopic(com.jumastappworks.mapstead.data.help.HelpTopicId.ADD_TO_MAP) }
                        )
                        HorizontalDivider()
                        SettingsActionRow(
                            title = stringResource(R.string.reset_map_guidance_label),
                            description = stringResource(R.string.reset_map_guidance_desc),
                            icon = Icons.Default.TipsAndUpdates,
                            actionLabel = stringResource(R.string.reset_label),
                            onClick = {
                                coroutineScope.launch { 
                                    userPreferencesRepository.resetMapGuidance()
                                    snackbarHostState.showSnackbar(context.getString(R.string.reset_complete_map))
                                }
                            }
                        )
                        HorizontalDivider()
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.basemap_limitations_title), style = MaterialTheme.typography.labelLarge)
                            Text(stringResource(R.string.basemap_limitations_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // GUIDANCE & HELP
            SettingsSection(title = stringResource(R.string.settings_guidance_help)) {
                SettingsCard {
                    Column {
                        SettingsRow(
                            title = stringResource(R.string.help_center_title),
                            icon = Icons.AutoMirrored.Filled.HelpOutline,
                            onClick = onNavigateToHelp
                        )
                        HorizontalDivider()
                        SettingsRow(
                            title = stringResource(R.string.gs_checklist_title),
                            icon = Icons.AutoMirrored.Filled.ListAlt,
                            onClick = onNavigateToGettingStarted
                        )
                        HorizontalDivider()
                        SettingsActionRow(
                            title = stringResource(R.string.reset_welcome_label),
                            description = stringResource(R.string.reset_welcome_desc),
                            icon = Icons.Default.Celebration,
                            actionLabel = stringResource(R.string.reset_label),
                            onClick = {
                                coroutineScope.launch { 
                                    userPreferencesRepository.resetWelcomeGuidance()
                                    snackbarHostState.showSnackbar(context.getString(R.string.welcome_guidance_reset))
                                }
                            }
                        )
                        HorizontalDivider()
                        SettingsActionRow(
                            title = stringResource(R.string.reset_guidance_label),
                            description = stringResource(R.string.settings_guidance_desc),
                            icon = Icons.Default.RestartAlt,
                            actionLabel = stringResource(R.string.reset_label),
                            onClick = {
                                coroutineScope.launch { 
                                    userPreferencesRepository.resetOnboardingGuidance()
                                    snackbarHostState.showSnackbar(context.getString(R.string.reset_complete_gs))
                                    onNavigateToGettingStarted()
                                }
                            }
                        )
                        HorizontalDivider()
                        SettingsRow(
                            title = stringResource(R.string.welcome_title),
                            icon = Icons.Default.Celebration,
                            onClick = onNavigateToOrientation
                        )
                    }
                }
            }

            // DATA & BACKUP
            SettingsSection(title = stringResource(R.string.settings_data_backup)) {
                SettingsCard {
                    Column {
                        if (isBackupEnabled) {
                            SettingsRow(
                                title = stringResource(R.string.backup_restore),
                                icon = Icons.Default.Backup,
                                onClick = onNavigateToBackup
                            )
                            HorizontalDivider()
                        }
                        
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Text(stringResource(R.string.local_storage_info), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // ABOUT & SAFETY
            SettingsSection(title = stringResource(R.string.settings_about_safety)) {
                SettingsCard {
                    Column {
                        SettingsRow(
                            title = stringResource(R.string.app_info),
                            icon = Icons.Default.Info,
                            onClick = onNavigateToAbout
                        )
                        HorizontalDivider()
                        SettingsRow(
                            title = stringResource(R.string.safety_limitations),
                            icon = Icons.Default.Shield,
                            onClick = onNavigateToSafety
                        )
                        HorizontalDivider()
                        SettingsRow(
                            title = stringResource(R.string.privacy_policy),
                            icon = Icons.Default.Lock,
                            onClick = onNavigateToPrivacy
                        )
                        HorizontalDivider()
                        SettingsActionRow(
                            title = stringResource(R.string.reset_boundary_ack),
                            icon = Icons.Default.Gavel,
                            actionLabel = stringResource(R.string.reset_label),
                            onClick = {
                                showBoundaryResetConfirm = true
                            }
                        )
                    }
                }
            }

            if (showBoundaryResetConfirm) {
                val context = androidx.compose.ui.platform.LocalContext.current
                AlertDialog(
                    onDismissRequest = { showBoundaryResetConfirm = false },
                    title = { Text(stringResource(R.string.reset_boundary_ack)) },
                    text = { Text(stringResource(R.string.reset_boundary_ack_confirm)) },
                    confirmButton = {
                        TextButton(onClick = {
                            coroutineScope.launch {
                                userPreferencesRepository.resetBoundaryAcknowledgment()
                                showBoundaryResetConfirm = false
                                snackbarHostState.showSnackbar(context.getString(R.string.reset_complete_boundary))
                            }
                        }) {
                            Text(stringResource(R.string.reset_label), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBoundaryResetConfirm = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            Text(
                text = stringResource(R.string.app_version, com.jumastappworks.mapstead.BuildConfig.VERSION_NAME, com.jumastappworks.mapstead.BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp)
        )
        content()
    }
}

@Composable
private fun SettingsCard(
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        content()
    }
}

@Composable
private fun SettingsRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    icon: ImageVector,
    actionLabel: String,
    onClick: () -> Unit,
    description: String? = null
) {
    Row(
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = onClick) {
            Text(actionLabel)
        }
    }
}

@Composable
private fun getThemeLabel(selection: ThemeSelection): String = when (selection) {
    ThemeSelection.SYSTEM -> stringResource(R.string.theme_system)
    ThemeSelection.LIGHT -> stringResource(R.string.theme_light)
    ThemeSelection.DARK -> stringResource(R.string.theme_dark)
}

@Composable
private fun getUnitLabel(system: MeasurementSystem): String = when (system) {
    MeasurementSystem.IMPERIAL -> stringResource(R.string.unit_imperial)
    MeasurementSystem.METRIC -> stringResource(R.string.unit_metric)
}
