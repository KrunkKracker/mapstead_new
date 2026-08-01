package com.jumastappworks.mapstead.ui.mapping

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.jumastappworks.mapstead.data.mapping.*

import androidx.compose.runtime.saveable.rememberSaveable

@Composable
fun GuidedAddMenu(
    onPresetSelected: (GuidedMapPreset) -> Unit,
    onDismiss: () -> Unit
) {
    var currentGroup by rememberSaveable { mutableStateOf<PresetGroup?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (currentGroup == null) {
            Text(
                text = stringResource(R.string.guided_add_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            GroupItem(
                title = stringResource(R.string.group_locations),
                description = stringResource(R.string.group_locations_desc),
                icon = Icons.Default.Place,
                onClick = { currentGroup = PresetGroup.LOCATIONS }
            )
            GroupItem(
                title = stringResource(R.string.group_routes),
                description = stringResource(R.string.group_routes_desc),
                icon = Icons.Default.Timeline,
                onClick = { currentGroup = PresetGroup.ROUTES }
            )
            GroupItem(
                title = stringResource(R.string.group_areas),
                description = stringResource(R.string.group_areas_desc),
                icon = Icons.Default.Category,
                onClick = { currentGroup = PresetGroup.AREAS }
            )

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(stringResource(R.string.cancel))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { currentGroup = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Text(
                    text = when(currentGroup) {
                        PresetGroup.LOCATIONS -> stringResource(R.string.group_locations)
                        PresetGroup.ROUTES -> stringResource(R.string.group_routes)
                        PresetGroup.AREAS -> stringResource(R.string.group_areas)
                        else -> ""
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }

            val presets = when(currentGroup) {
                PresetGroup.LOCATIONS -> GuidedMapPresets.LOCATIONS
                PresetGroup.ROUTES -> GuidedMapPresets.ROUTES
                PresetGroup.AREAS -> GuidedMapPresets.AREAS
                else -> emptyList()
            }

            // Adaptive Grid for presets
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 500.dp)
            ) {
                items(presets) { preset ->
                    PresetItem(preset, onClick = { onPresetSelected(preset) })
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

private enum class PresetGroup { LOCATIONS, ROUTES, AREAS }

@Composable
private fun GroupItem(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun PresetItem(
    preset: GuidedMapPreset,
    onClick: () -> Unit
) {
    val icon = when (preset.id) {
        GuidedMapPresetId.WELL -> Icons.Default.WaterDrop
        GuidedMapPresetId.WATER_VALVE -> Icons.Default.Settings
        GuidedMapPresetId.ELECTRICAL_PANEL -> Icons.Default.Bolt
        GuidedMapPresetId.UTILITY_METER -> Icons.Default.Speed
        GuidedMapPresetId.SEPTIC_ACCESS -> Icons.Default.Engineering
        GuidedMapPresetId.GATE -> Icons.Default.DoorFront
        GuidedMapPresetId.FIRE_EXTINGUISHER -> Icons.Default.FireExtinguisher
        GuidedMapPresetId.TREE -> Icons.Default.Park
        GuidedMapPresetId.FENCE -> Icons.Default.Straight
        GuidedMapPresetId.WATER_LINE -> Icons.Default.Water
        GuidedMapPresetId.ELECTRICAL_LINE -> Icons.Default.Bolt
        GuidedMapPresetId.GAS_LINE -> Icons.Default.LocalGasStation
        GuidedMapPresetId.DRAINAGE_ROUTE -> Icons.Default.Waves
        GuidedMapPresetId.IRRIGATION_LINE -> Icons.Default.Opacity
        GuidedMapPresetId.DRIVEWAY_EDGE -> Icons.Default.EditRoad
        GuidedMapPresetId.PROPERTY_EDGE -> Icons.Default.Straight
        GuidedMapPresetId.HOUSE -> Icons.Default.Home
        GuidedMapPresetId.SHED -> Icons.Default.HomeWork
        GuidedMapPresetId.PROPERTY_BOUNDARY -> Icons.Default.SquareFoot
        GuidedMapPresetId.SEPTIC_FIELD -> Icons.Default.Agriculture
        GuidedMapPresetId.POND -> Icons.Default.Waves
        GuidedMapPresetId.DRIVEWAY_AREA -> Icons.Default.EditRoad
        GuidedMapPresetId.GARDEN -> Icons.Default.Park
        GuidedMapPresetId.POOL -> Icons.Default.Pool
        GuidedMapPresetId.CUSTOM_LOCATION -> Icons.Default.Place
        GuidedMapPresetId.CUSTOM_ROUTE -> Icons.Default.Timeline
        GuidedMapPresetId.CUSTOM_AREA -> Icons.Default.Category
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(preset.titleRes), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(
                text = stringResource(preset.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
