package com.jumastappworks.mapstead.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.util.AdaptiveLayoutInfo

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager

@Composable
fun SearchBarExpressive(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    active: Boolean,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.search_hint),
    propertyName: String? = null,
    planName: String? = null,
    onClearQuery: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 2.dp
    ) {
        Column {
            if (propertyName != null || planName != null) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = propertyName ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (propertyName != null && planName != null) {
                        Text(" \u2022 ", style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        text = planName ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 4.dp)
            ) {
                if (active) {
                    IconButton(
                        onClick = { 
                            onSearchActiveChange(false)
                            focusManager.clearFocus()
                        },
                        modifier = Modifier.testTag("MapSearchCloseButton")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                } else {
                    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                TextField(
                    value = query,
                    onValueChange = {
                        onQueryChange(it)
                        if (!active && it.isNotEmpty()) {
                            onSearchActiveChange(true)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("MapSearchField")
                        .onFocusChanged { 
                            if (it.isFocused && !active) {
                                onSearchActiveChange(true)
                            }
                        },
                    placeholder = { Text(placeholder) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.SEARCH),
                    keyboardActions = KeyboardPolicy.getActions(focusManager)
                )

                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = onClearQuery,
                        modifier = Modifier.testTag("MapSearchClearButton")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.dismiss))
                    }
                }
                
                if (!active && query.isEmpty()) {
                    Spacer(Modifier.width(12.dp))
                }
            }
        }
    }
}

@Composable
fun MapActionButtons(
    modifier: Modifier = Modifier,
    layoutInfo: AdaptiveLayoutInfo,
    isBasemapActive: Boolean,
    isPhoneLocationVisible: Boolean,
    isLayerPanelOpen: Boolean,
    onEmergencyClick: () -> Unit,
    onLayersClick: () -> Unit = {},
    onAddClick: () -> Unit = {},
    onBasemapClick: () -> Unit = {},
    onMyLocationClick: () -> Unit = {},
    onHelpClick: () -> Unit = {},
    isAddEnabled: Boolean = true
) {
    val isLandscape = layoutInfo.isHeightCompact
    
    if (isLandscape) {
        Row(
            modifier = modifier.testTag("MapActionButtons"),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            EmergencyButton(onEmergencyClick, iconOnly = true, modifier = Modifier.testTag("EmergencyButton"))
            ActionButton(Icons.Default.Layers, stringResource(R.string.map_layers), isLayerPanelOpen, true, onLayersClick, Modifier.testTag("LayersButton"))
            ActionButton(Icons.Default.Map, stringResource(R.string.basemap), isBasemapActive, true, onBasemapClick, Modifier.testTag("BasemapButton"))
            ActionButton(Icons.Default.Info, stringResource(R.string.map_help_title), false, true, onHelpClick, Modifier.testTag("HelpButton"))
            
            AddActionButton(
                isEnabled = isAddEnabled,
                onClick = onAddClick,
                modifier = Modifier.testTag("AddMenuButton")
            )

            ActionButton(Icons.Default.MyLocation, stringResource(R.string.my_location), isPhoneLocationVisible, true, onMyLocationClick, Modifier.testTag("MyLocationButton"))
        }
    } else {
        Column(
            modifier = modifier.testTag("MapActionButtons"),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ActionButton(Icons.Default.MyLocation, stringResource(R.string.my_location), isPhoneLocationVisible, true, onMyLocationClick, Modifier.testTag("MyLocationButton"))
            
            AddActionButton(
                isEnabled = isAddEnabled,
                onClick = onAddClick,
                modifier = Modifier.testTag("AddMenuButton")
            )

            ActionButton(Icons.Default.Info, stringResource(R.string.map_help_title), false, true, onHelpClick, Modifier.testTag("HelpButton"))
            ActionButton(Icons.Default.Map, stringResource(R.string.basemap), isBasemapActive, true, onBasemapClick, Modifier.testTag("BasemapButton"))
            ActionButton(Icons.Default.Layers, stringResource(R.string.map_layers), isLayerPanelOpen, true, onLayersClick, Modifier.testTag("LayersButton"))
            EmergencyButton(onEmergencyClick, iconOnly = false, modifier = Modifier.testTag("EmergencyButton"))
        }
    }
}

@Composable
private fun AddActionButton(
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = isEnabled,
        shape = androidx.compose.foundation.shape.CircleShape,
        color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (isEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        tonalElevation = if (isEnabled) 6.dp else 0.dp,
        shadowElevation = if (isEnabled) 6.dp else 0.dp,
        modifier = modifier
            .size(56.dp)
            .semantics { 
                role = Role.Button
                if (!isEnabled) disabled() 
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_to_map))
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = isEnabled,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = modifier.size(56.dp)
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@Composable
private fun MiniActionButton(
    icon: ImageVector,
    contentDescription: String,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = isEnabled,
        modifier = modifier.size(48.dp)
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@Composable
private fun EmergencyButton(onClick: () -> Unit, iconOnly: Boolean, modifier: Modifier = Modifier) {
    if (iconOnly) {
        IconButton(
            onClick = onClick,
            colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White),
            modifier = modifier.size(56.dp)
        ) {
            Icon(Icons.Default.Warning, contentDescription = stringResource(R.string.emergency_mode))
        }
    } else {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = modifier.heightIn(min = 56.dp),
            contentPadding = PaddingValues(horizontal = 20.dp)
        ) {
            Icon(Icons.Default.Warning, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.emergency_mode), style = MaterialTheme.typography.labelLarge)
        }
    }
}
