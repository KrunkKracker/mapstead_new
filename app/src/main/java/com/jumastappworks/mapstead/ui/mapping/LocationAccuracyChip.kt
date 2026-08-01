package com.jumastappworks.mapstead.ui.mapping

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.mapping.LocationResult
import com.jumastappworks.mapstead.data.mapping.LocationAccuracyQuality
import com.jumastappworks.mapstead.data.prefs.MeasurementSystem
import com.jumastappworks.mapstead.util.MeasurementFormatter

@Composable
fun LocationAccuracyChip(
    location: LocationResult.Success,
    quality: LocationAccuracyQuality,
    measurementSystem: MeasurementSystem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCached = location.source == LocationResult.Success.Source.LastKnown
    
    val qualityLabel = if (isCached) {
        stringResource(R.string.location_cached)
    } else {
        when (quality) {
            LocationAccuracyQuality.Good -> stringResource(R.string.gps_good)
            LocationAccuracyQuality.Moderate -> stringResource(R.string.gps_moderate)
            LocationAccuracyQuality.Poor -> stringResource(R.string.gps_poor)
        }
    }

    val accuracyStr = MeasurementFormatter.displayAccuracyInput(location.accuracyMeters.toDouble(), measurementSystem)
    val unitSuffix = if (measurementSystem == MeasurementSystem.IMPERIAL) stringResource(R.string.unit_feet) else stringResource(R.string.unit_meters)
    val prefix = stringResource(R.string.gps_accuracy_prefix)
    val fullLabel = "$qualityLabel \u00b7 $prefix$accuracyStr $unitSuffix"

    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 40.dp)
            .widthIn(max = 260.dp)
            .semantics { contentDescription = fullLabel },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 6.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (isCached) Icons.Default.History else Icons.Default.GpsFixed,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (quality == LocationAccuracyQuality.Poor && !isCached) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = fullLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
