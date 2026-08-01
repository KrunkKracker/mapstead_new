package com.jumastappworks.mapstead.ui.mapping

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.data.prefs.MeasurementSystem
import com.jumastappworks.mapstead.data.help.HelpTopicId
import com.jumastappworks.mapstead.ui.components.ContextHelpLink
import com.jumastappworks.mapstead.util.MeasurementFormatter
import com.jumastappworks.mapstead.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationDetailsSheet(
    location: LocationResult.Success,
    quality: LocationAccuracyQuality,
    measurementSystem: MeasurementSystem,
    onRetry: () -> Unit,
    onHide: () -> Unit,
    onDismiss: () -> Unit,
    onHelpClick: (HelpTopicId) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(16.dp)
            .padding(bottom = 32.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.gps_details_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.dismiss))
            }
        }

        LocationAttribute(
            icon = Icons.Default.SignalCellularAlt,
            label = stringResource(R.string.gps_quality),
            value = when (quality) {
                LocationAccuracyQuality.Good -> stringResource(R.string.quality_good)
                LocationAccuracyQuality.Moderate -> stringResource(R.string.quality_moderate)
                LocationAccuracyQuality.Poor -> stringResource(R.string.quality_poor)
            },
            color = when (quality) {
                LocationAccuracyQuality.Good -> MaterialTheme.colorScheme.primary
                LocationAccuracyQuality.Moderate -> androidx.compose.ui.graphics.Color(0xFFFBC02D)
                LocationAccuracyQuality.Poor -> MaterialTheme.colorScheme.error
            }
        )

        Spacer(Modifier.height(16.dp))

        val isLive = location.source == LocationResult.Success.Source.Fresh
        LocationAttribute(
            icon = if (isLive) Icons.Default.GpsFixed else Icons.Default.History,
            label = stringResource(R.string.location_status),
            value = if (isLive) stringResource(R.string.status_live) else stringResource(R.string.status_cached)
        )

        if (!isLive) {
            Spacer(Modifier.height(16.dp))
            LocationAttribute(
                icon = Icons.Default.AccessTime,
                label = stringResource(R.string.last_updated),
                value = TimeUtils.formatRelativeTime(context, location.timestampMillis)
            )
        }

        Spacer(Modifier.height(16.dp))

        LocationAttribute(
            icon = Icons.Default.Straighten,
            label = stringResource(R.string.estimated_accuracy),
            value = MeasurementFormatter.formatAccuracy(location.accuracyMeters.toDouble(), measurementSystem)
        )

        Spacer(Modifier.height(16.dp))

        LocationAttribute(
            icon = Icons.Default.Settings,
            label = stringResource(R.string.display_units),
            value = if (measurementSystem == MeasurementSystem.IMPERIAL) stringResource(R.string.unit_imperial) else stringResource(R.string.unit_metric)
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.accuracy_explanation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        ContextHelpLink(
            text = stringResource(R.string.what_is_this),
            onClick = { onHelpClick(HelpTopicId.GPS_AND_ACCURACY) }
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onHide,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.VisibilityOff, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.hide_location))
            }
            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun LocationAttribute(
    icon: ImageVector,
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
