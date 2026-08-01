package com.jumastappworks.mapstead.ui.mapping

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R

@Composable
fun BoundaryAcknowledgmentDialog(
    isSaving: Boolean = false,
    errorRes: Int? = null,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onLearnMore: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* No-op, require explicit action */ },
        title = { Text(stringResource(R.string.boundary_ack_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.boundary_ack_message))
                TextButton(onClick = onLearnMore, enabled = !isSaving) {
                    Text(stringResource(R.string.learn_more))
                }
                if (errorRes != null) {
                    Text(stringResource(errorRes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isSaving) {
                if (isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                else Text(stringResource(R.string.continue_label))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !isSaving) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
