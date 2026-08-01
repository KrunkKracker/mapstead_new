package com.jumastappworks.mapstead.ui.mapping

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.mapping.SuggestedMapLayer

@Composable
fun StarterLayersDialog(
    operation: StarterLayerOperation = StarterLayerOperation.Idle,
    errorRes: Int? = null,
    onConfirm: (Set<SuggestedMapLayer>, String, String, String, String) -> Unit,
    onSkip: () -> Unit
) {
    var selected by remember { mutableStateOf(SuggestedMapLayer.entries.toSet()) }
    val isProcessing = operation != StarterLayerOperation.Idle

    val buildingsName = stringResource(R.string.layer_buildings)
    val utilitiesName = stringResource(R.string.layer_utilities)
    val outdoorName = stringResource(R.string.layer_outdoor)
    val safetyName = stringResource(R.string.layer_safety)

    AlertDialog(
        onDismissRequest = { /* No-op, require explicit Confirm or Skip */ },
        title = { Text(stringResource(R.string.starter_layers_title)) },
        text = {
            Column {
                Text(stringResource(R.string.starter_layers_desc))
                Spacer(Modifier.height(16.dp))
                SuggestedMapLayer.entries.forEach { type ->
                    val name = when (type) {
                        SuggestedMapLayer.BUILDINGS_BOUNDARIES -> buildingsName
                        SuggestedMapLayer.UTILITIES -> utilitiesName
                        SuggestedMapLayer.OUTDOOR_FEATURES -> outdoorName
                        SuggestedMapLayer.SAFETY_EMERGENCY -> safetyName
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = selected.contains(type),
                            onCheckedChange = { checked ->
                                if (!isProcessing) {
                                    selected = if (checked) selected + type else selected - type
                                }
                            },
                            enabled = !isProcessing
                        )
                        Text(text = name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (errorRes != null) {
                    Text(
                        text = stringResource(errorRes),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selected, buildingsName, utilitiesName, outdoorName, safetyName) },
                enabled = !isProcessing
            ) {
                if (operation == StarterLayerOperation.Creating) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                else Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip, enabled = !isProcessing) {
                if (operation == StarterLayerOperation.Skipping) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                else Text(stringResource(R.string.skip))
            }
        }
    )
}
