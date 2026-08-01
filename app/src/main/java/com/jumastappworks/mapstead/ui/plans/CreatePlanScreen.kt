package com.jumastappworks.mapstead.ui.plans

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.mapping.LocationResult
import com.jumastappworks.mapstead.ui.components.*
import com.jumastappworks.mapstead.util.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlanScreen(
    viewModel: CreatePlanViewModel,
    onBack: () -> Unit,
    onPlanCreated: (UUID) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val layoutInfo = rememberAdaptiveLayoutInfo()
    val nameController = rememberFormFieldVisibilityController()
    val addrController = rememberFormFieldVisibilityController()
    val coroutineScope = rememberCoroutineScope()
    val nameFocusRequester = remember { FocusRequester() }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    
    val property by viewModel.property.collectAsState()
    val locationState by viewModel.locationState.collectAsState()
    val addressMatches by viewModel.addressSearchResults.collectAsState()
    val isLocating by viewModel.isLocating.collectAsState()
    
    var addressQuery by remember { mutableStateOf("") }
    var manualMode by remember { mutableStateOf(false) }

    val latController = rememberFormFieldVisibilityController()
    val lngController = rememberFormFieldVisibilityController()
    val applyController = rememberFormFieldVisibilityController()

    val latFocusRequester = remember { FocusRequester() }
    val lngFocusRequester = remember { FocusRequester() }
    var showOverwriteConfirm by remember { mutableStateOf(false) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onPermissionResult(granted)
    }

    val onMyLocationClick = {
        if (locationState is CreatePlanLocationState.Success) {
            showOverwriteConfirm = true
        } else {
            val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (fineGranted) {
                viewModel.useMyLocation()
            } else {
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val activity = context.findComponentActivity()
    val status = PermissionUtils.determineLocationPermissionStatus(
        isGranted = fineGranted,
        hasBeenRequested = viewModel.permissionRequested,
        shouldShowRationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
    )

    if (showOverwriteConfirm) {
        AlertDialog(
            onDismissRequest = { showOverwriteConfirm = false },
            title = { Text(stringResource(R.string.overwrite_coords_title)) },
            text = { Text(stringResource(R.string.overwrite_coords_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showOverwriteConfirm = false
                    val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (fineGranted) viewModel.useMyLocation()
                    else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }) {
                    Text(stringResource(R.string.overwrite_label))
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverwriteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    LaunchedEffect(property) {
        if (addressQuery.isBlank()) {
            addressQuery = property?.addressLine1 ?: ""
        }
    }

    LaunchedEffect(viewModel.nameError) {
        if (viewModel.nameError != null) {
            nameFocusRequester.requestFocus()
            nameController.bringIntoView(coroutineScope)
        }
    }

    LaunchedEffect(viewModel.latitudeError) {
        if (viewModel.latitudeError != null) {
            latFocusRequester.requestFocus()
            latController.bringIntoView(coroutineScope)
        }
    }

    LaunchedEffect(viewModel.longitudeError) {
        if (viewModel.longitudeError != null) {
            lngFocusRequester.requestFocus()
            lngController.bringIntoView(coroutineScope)
        }
    }

    BackHandler(enabled = viewModel.isDirty()) {
        showDiscardConfirm = true
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text(stringResource(R.string.discard_changes_title)) },
            text = { Text(stringResource(R.string.discard_changes_message)) },
            confirmButton = {
                TextButton(onClick = { showDiscardConfirm = false; onBack() }) {
                    Text(stringResource(R.string.discard_changes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text(stringResource(R.string.keep_editing))
                }
            }
        )
    }

    MapsteadFormScaffold(
        title = stringResource(R.string.create_property_map_title),
        onBack = { if (viewModel.isDirty()) showDiscardConfirm = true else onBack() },
        isLoading = viewModel.isSaving,
        primaryActionLabel = stringResource(R.string.create_label),
        onPrimaryAction = { viewModel.savePlan(onPlanCreated) },
        primaryActionEnabled = locationState is CreatePlanLocationState.Success
    ) {
        item {
            Text(
                text = stringResource(R.string.map_description_text),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        item {
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                label = { Text(stringResource(R.string.map_name_label)) },
                placeholder = { Text(stringResource(R.string.map_name_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("CreatePlan_Name")
                    .focusRequester(nameFocusRequester)
                    .bringIntoViewOnFocus(nameController),
                isError = viewModel.nameError != null,
                supportingText = {
                    if (viewModel.nameError != null) {
                        Text(stringResource(viewModel.nameError!!))
                    } else {
                        Text(stringResource(R.string.default_layer_info))
                    }
                },
                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.PROPER_NAME),
                keyboardActions = KeyboardPolicy.getActions(focusManager),
                singleLine = true,
                enabled = !viewModel.isSaving
            )
        }

        if (locationState is CreatePlanLocationState.Success) {
            item {
                val loc = locationState as CreatePlanLocationState.Success
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = when (loc.method) {
                                    PlanLocationMethod.PROPERTY_LOCATION -> stringResource(R.string.source_property_location)
                                    PlanLocationMethod.ADDRESS_SEARCH -> stringResource(R.string.source_address)
                                    PlanLocationMethod.PHONE_LOCATION -> stringResource(R.string.source_phone_location)
                                    PlanLocationMethod.MANUAL_COORDINATES -> stringResource(R.string.source_entered_coordinates)
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { viewModel.changeLocation() }) {
                                Text(stringResource(R.string.change_location))
                            }
                        }
                        if (loc.addressLabel != null) {
                            Text(loc.addressLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        Text("${String.format(java.util.Locale.US, "%.5f", loc.latitude)}, ${String.format(java.util.Locale.US, "%.5f", loc.longitude)}", style = MaterialTheme.typography.bodySmall)
                        if (loc.accuracyMeters != null) {
                            Text(stringResource(R.string.accuracy_label_unit, String.format(java.util.Locale.US, "%.1f", loc.accuracyMeters)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        } else {
            item {
                Text(stringResource(R.string.map_center_location_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (property?.latitude != null && property?.longitude != null) {
                item {
                    OutlinedButton(
                        onClick = { viewModel.usePropertyLocation() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !viewModel.isSaving
                    ) {
                        Icon(Icons.Default.Home, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.use_property_location))
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onMyLocationClick,
                        modifier = Modifier.weight(1f),
                        enabled = !viewModel.isSaving && !isLocating
                    ) {
                        if (isLocating) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Default.MyLocation, contentDescription = null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.use_my_location))
                    }
                    
                    OutlinedButton(
                        onClick = { manualMode = !manualMode },
                        modifier = Modifier.weight(1f),
                        enabled = !viewModel.isSaving
                    ) {
                        Icon(Icons.Default.EditLocation, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (manualMode) stringResource(R.string.cancel) else stringResource(R.string.manual_coordinates))
                    }
                }
            }

            if (status is PermissionStatus.DeniedRetryable) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(stringResource(R.string.location_issue_permission_denied), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onMyLocationClick) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            } else if (status is PermissionStatus.DeniedPermanently) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(stringResource(R.string.location_issue_permission_permanently_denied), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { 
                            if (!PermissionUtils.openAppSettings(context)) {
                                viewModel.setManualError(R.string.error_settings_launch_failed)
                            }
                        }) {
                            Text(stringResource(R.string.open_settings))
                        }
                    }
                }
            }

            if (manualMode) {
                item {
                    val isStacked = layoutInfo.isWidthCompact
                    if (isStacked) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            OutlinedTextField(
                                value = viewModel.manualLatitude,
                                onValueChange = { viewModel.manualLatitude = it },
                                label = { Text(stringResource(R.string.latitude_label_center)) },
                                modifier = Modifier.fillMaxWidth().testTag("CreatePlan_Latitude").focusRequester(latFocusRequester).bringIntoViewOnFocus(latController),
                                isError = viewModel.latitudeError != null,
                                supportingText = viewModel.latitudeError?.let { { Text(stringResource(it)) } },
                                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.COORDINATE),
                                keyboardActions = KeyboardPolicy.getActions(focusManager),
                                singleLine = true,
                                enabled = !viewModel.isSaving
                            )
                            OutlinedTextField(
                                value = viewModel.manualLongitude,
                                onValueChange = { viewModel.manualLongitude = it },
                                label = { Text(stringResource(R.string.longitude_label_center)) },
                                modifier = Modifier.fillMaxWidth().testTag("CreatePlan_Longitude").focusRequester(lngFocusRequester).bringIntoViewOnFocus(lngController),
                                isError = viewModel.longitudeError != null,
                                supportingText = viewModel.longitudeError?.let { { Text(stringResource(it)) } },
                                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.COORDINATE, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardPolicy.getActions(focusManager, onDone = { viewModel.applyManualCoordinates() }),
                                singleLine = true,
                                enabled = !viewModel.isSaving
                            )
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            OutlinedTextField(
                                value = viewModel.manualLatitude,
                                onValueChange = { viewModel.manualLatitude = it },
                                label = { Text(stringResource(R.string.latitude_label_center)) },
                                modifier = Modifier.weight(1f).testTag("CreatePlan_Latitude").focusRequester(latFocusRequester).bringIntoViewOnFocus(latController),
                                isError = viewModel.latitudeError != null,
                                supportingText = viewModel.latitudeError?.let { { Text(stringResource(it)) } },
                                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.COORDINATE),
                                keyboardActions = KeyboardPolicy.getActions(focusManager),
                                singleLine = true,
                                enabled = !viewModel.isSaving
                            )
                            OutlinedTextField(
                                value = viewModel.manualLongitude,
                                onValueChange = { viewModel.manualLongitude = it },
                                label = { Text(stringResource(R.string.longitude_label_center)) },
                                modifier = Modifier.weight(1f).testTag("CreatePlan_Longitude").focusRequester(lngFocusRequester).bringIntoViewOnFocus(lngController),
                                isError = viewModel.longitudeError != null,
                                supportingText = viewModel.longitudeError?.let { { Text(stringResource(it)) } },
                                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.COORDINATE, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardPolicy.getActions(focusManager, onDone = { viewModel.applyManualCoordinates() }),
                                singleLine = true,
                                enabled = !viewModel.isSaving
                            )
                        }
                    }
                }
                item {
                    Button(
                        onClick = { viewModel.applyManualCoordinates() },
                        modifier = Modifier.fillMaxWidth().testTag("CreatePlan_Apply").bringIntoViewOnFocus(applyController),
                        enabled = !viewModel.isSaving && viewModel.manualLatitude.isNotBlank() && viewModel.manualLongitude.isNotBlank()
                    ) {
                        Text(stringResource(R.string.apply_coordinates))
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = addressQuery,
                    onValueChange = { addressQuery = it },
                    label = { Text(stringResource(R.string.find_by_address)) },
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(addrController),
                    trailingIcon = {
                        IconButton(onClick = { viewModel.searchAddress(addressQuery) }, enabled = addressQuery.isNotBlank() && !viewModel.isSaving) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.desc_map_search))
                        }
                    },
                    keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.ADDRESS, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardPolicy.getActions(focusManager, onSearch = { viewModel.searchAddress(addressQuery) }),
                    singleLine = true,
                    enabled = !viewModel.isSaving
                )
            }

            if (addressMatches.isNotEmpty()) {
                items(addressMatches) { match ->
                    ListItem(
                        headlineContent = { Text(match.displayAddress) },
                        supportingContent = { Text("${match.latitude}, ${match.longitude}") },
                        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                        modifier = Modifier.clickable { viewModel.selectAddressMatch(match) }
                    )
                    HorizontalDivider()
                }
            }
        }

        if (locationState is CreatePlanLocationState.Loading) {
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        }
        
        if (locationState is CreatePlanLocationState.Error) {
            item { Text(stringResource((locationState as CreatePlanLocationState.Error).messageRes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }

        if (viewModel.saveErrorRes != null) {
            item {
                Text(text = stringResource(viewModel.saveErrorRes!!), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
