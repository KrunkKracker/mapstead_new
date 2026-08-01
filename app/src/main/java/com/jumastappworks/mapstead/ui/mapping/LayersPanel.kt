package com.jumastappworks.mapstead.ui.mapping

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.db.entities.LayerEntity
import com.jumastappworks.mapstead.data.help.HelpTopicId
import com.jumastappworks.mapstead.ui.components.ContextHelpLink
import com.jumastappworks.mapstead.ui.components.KeyboardPolicy
import com.jumastappworks.mapstead.ui.components.TextFieldSemanticType
import com.jumastappworks.mapstead.ui.components.bringIntoViewOnFocus
import com.jumastappworks.mapstead.ui.components.rememberFormFieldVisibilityController
import java.util.UUID

@Composable
fun LayersPanel(
    layers: List<LayerEntity>,
    activeLayerId: UUID?,
    onSelectLayer: (UUID) -> Unit,
    onToggleVisibility: (UUID) -> Unit,
    onToggleLock: (UUID) -> Unit,
    onAddLayer: (String, String) -> Unit,
    onDeleteLayer: (UUID) -> Unit,
    onRenameLayer: (UUID, String) -> Unit,
    onChangeLayerOpacity: (UUID, Float) -> Unit,
    onMoveLayerUp: (UUID) -> Unit,
    onMoveLayerDown: (UUID) -> Unit,
    onClose: () -> Unit = {},
    onHelpClick: (HelpTopicId) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var showAddDialog by remember { mutableStateOf(false) }
    var newLayerName by remember { mutableStateOf("") }
    var newLayerCategory by remember { mutableStateOf("Structure") }

    var editingLayer by remember { mutableStateOf<LayerEntity?>(null) }
    var editName by remember { mutableStateOf("") }
    var editOpacity by remember { mutableStateOf(1.0f) }
    var showDeleteConfirmForLayerId by remember { mutableStateOf<UUID?>(null) }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.map_layers), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_layers))
                }
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_layer))
                }
            }

            ContextHelpLink(
                text = stringResource(R.string.what_is_this),
                onClick = { onHelpClick(HelpTopicId.LAYERS) }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(layers) { layer ->
                    val isActive = layer.id == activeLayerId
                    LayerItem(
                        layer = layer,
                        isActive = isActive,
                        onClick = { onSelectLayer(layer.id) },
                        onToggleVisibility = { onToggleVisibility(layer.id) },
                        onToggleLock = { onToggleLock(layer.id) },
                        onEditClick = {
                            editingLayer = layer
                            editName = layer.name
                            editOpacity = layer.opacity
                        },
                        onMoveUp = { onMoveLayerUp(layer.id) },
                        onMoveDown = { onMoveLayerDown(layer.id) },
                        onDelete = { showDeleteConfirmForLayerId = layer.id }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        val nameController = rememberFormFieldVisibilityController()
        val catController = rememberFormFieldVisibilityController()
        val scope = rememberCoroutineScope()

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.add_new_layer_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.imePadding()) {
                    Text("Layers keep related map items together, such as Buildings, Utilities, or Boundaries.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = newLayerName,
                        onValueChange = { newLayerName = it },
                        label = { Text(stringResource(R.string.layer_name_label)) },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(nameController),
                        keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.PROPER_NAME),
                        keyboardActions = KeyboardPolicy.getActions(focusManager),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newLayerCategory,
                        onValueChange = { newLayerCategory = it },
                        label = { Text(stringResource(R.string.layer_category_label)) },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(catController),
                        keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.PROPER_NAME),
                        keyboardActions = KeyboardPolicy.getActions(focusManager, onDone = {
                            if (newLayerName.isNotBlank()) {
                                onAddLayer(newLayerName, newLayerCategory)
                                newLayerName = ""
                                showAddDialog = false
                            }
                        }),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newLayerName.isNotBlank()) {
                            onAddLayer(newLayerName, newLayerCategory)
                            newLayerName = ""
                            showAddDialog = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.add))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    editingLayer?.let { layer ->
        val nameController = rememberFormFieldVisibilityController()
        AlertDialog(
            onDismissRequest = { editingLayer = null },
            title = { Text(stringResource(R.string.edit_layer_settings_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.imePadding()) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text(stringResource(R.string.layer_name_label)) },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(nameController),
                        keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.PROPER_NAME),
                        keyboardActions = KeyboardPolicy.getActions(focusManager, onDone = {
                            if (editName.isNotBlank()) {
                                onRenameLayer(layer.id, editName)
                                onChangeLayerOpacity(layer.id, editOpacity)
                                editingLayer = null
                            }
                        }),
                        singleLine = true
                    )
                    Text(stringResource(R.string.opacity_label, (editOpacity * 100).toInt()))
                    Slider(
                        value = editOpacity,
                        onValueChange = { editOpacity = it },
                        valueRange = 0.0f..1.0f
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editName.isNotBlank()) {
                            onRenameLayer(layer.id, editName)
                            onChangeLayerOpacity(layer.id, editOpacity)
                            editingLayer = null
                        }
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingLayer = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    showDeleteConfirmForLayerId?.let { id ->
        val layer = layers.find { it.id == id }
        AlertDialog(
            onDismissRequest = { showDeleteConfirmForLayerId = null },
            title = { Text(stringResource(R.string.delete_layer_title)) },
            text = { Text("Are you sure you want to delete \"${layer?.name}\"? All items in this layer will be removed. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteLayer(id)
                        showDeleteConfirmForLayerId = null
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmForLayerId = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun LayerItem(
    layer: LayerEntity,
    isActive: Boolean,
    onClick: () -> Unit,
    onToggleVisibility: () -> Unit,
    onToggleLock: () -> Unit,
    onEditClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                RadioButton(selected = isActive, onClick = onClick)
                Text(
                    layer.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                )
                IconButton(onClick = onToggleVisibility) {
                    Icon(
                        if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = stringResource(R.string.toggle_visibility)
                    )
                }
                IconButton(onClick = onToggleLock) {
                    Icon(
                        if (layer.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = stringResource(R.string.toggle_lock)
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onMoveUp) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.move_layer_up))
                }
                IconButton(onClick = onMoveDown) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = stringResource(R.string.move_layer_down))
                }
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.layer_settings))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
