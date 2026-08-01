package com.jumastappworks.mapstead.ui.mapping

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R

@Composable
fun MapControlsHelpSheet(
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = stringResource(R.string.map_help_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        HelpItem(
            title = stringResource(R.string.help_my_location_title),
            description = stringResource(R.string.help_my_location_desc),
            icon = Icons.Default.MyLocation
        )

        HelpItem(
            title = stringResource(R.string.help_add_to_map_title),
            description = stringResource(R.string.help_add_to_map_desc),
            icon = Icons.Default.AddLocation
        )

        HelpItem(
            title = stringResource(R.string.help_search_title),
            description = stringResource(R.string.help_search_desc),
            icon = Icons.Default.Search
        )

        HelpItem(
            title = stringResource(R.string.help_basemap_title),
            description = stringResource(R.string.help_basemap_desc),
            icon = Icons.Default.Map
        )

        HelpItem(
            title = stringResource(R.string.help_layers_title),
            description = stringResource(R.string.help_layers_desc),
            icon = Icons.Default.Layers
        )

        HelpItem(
            title = stringResource(R.string.help_emergency_title),
            description = stringResource(R.string.help_emergency_desc),
            icon = Icons.Default.Warning,
            iconTint = MaterialTheme.colorScheme.error
        )

        Button(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(stringResource(R.string.dismiss))
        }
    }
}

@Composable
private fun HelpItem(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
