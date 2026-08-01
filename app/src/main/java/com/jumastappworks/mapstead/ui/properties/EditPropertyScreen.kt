package com.jumastappworks.mapstead.ui.properties

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
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
import com.jumastappworks.mapstead.util.rememberAdaptiveLayoutInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPropertyScreen(
    viewModel: EditPropertyViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showOverwriteConfirm by remember { mutableStateOf(false) }
    
    val layoutInfo = rememberAdaptiveLayoutInfo()
    val coroutineScope = rememberCoroutineScope()
    val locationState by viewModel.locationState.collectAsState()
    val addressLookupState by viewModel.addressLookupState.collectAsState()
    val isLocating by viewModel.isLocating.collectAsState()

    val nameController = rememberFormFieldVisibilityController()
    val typeController = rememberFormFieldVisibilityController()
    val addr1Controller = rememberFormFieldVisibilityController()
    val addr2Controller = rememberFormFieldVisibilityController()
    val cityController = rememberFormFieldVisibilityController()
    val stateController = rememberFormFieldVisibilityController()
    val zipController = rememberFormFieldVisibilityController()
    val countryController = rememberFormFieldVisibilityController()
    val parcelController = rememberFormFieldVisibilityController()
    val acreageController = rememberFormFieldVisibilityController()
    val latController = rememberFormFieldVisibilityController()
    val lngController = rememberFormFieldVisibilityController()
    val descController = rememberFormFieldVisibilityController()

    val nameFocusRequester = remember { FocusRequester() }
    val acreageFocusRequester = remember { FocusRequester() }
    val latFocusRequester = remember { FocusRequester() }
    val lngFocusRequester = remember { FocusRequester() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms.values.any { it }
        viewModel.onPermissionResult(granted)
    }

    val onMyLocationClick = {
        val hasCoords = viewModel.latitude.isNotBlank() || viewModel.longitude.isNotBlank()
        if (hasCoords) {
            showOverwriteConfirm = true
        } else {
            val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (fineGranted) {
                viewModel.requestLocation()
            } else {
                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
            }
        }
    }

    if (showOverwriteConfirm) {
        AlertDialog(
            onDismissRequest = { showOverwriteConfirm = false },
            title = { Text(stringResource(R.string.overwrite_coords_title)) },
            text = { Text(stringResource(R.string.overwrite_coords_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showOverwriteConfirm = false
                    val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (fineGranted) {
                        viewModel.requestLocation()
                    } else {
                        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                    }
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

    LaunchedEffect(viewModel.nameError, viewModel.acreageError, viewModel.latitudeError, viewModel.longitudeError) {
        when {
            viewModel.nameError != null -> { nameFocusRequester.requestFocus(); nameController.bringIntoView(coroutineScope) }
            viewModel.acreageError != null -> { acreageFocusRequester.requestFocus(); acreageController.bringIntoView(coroutineScope) }
            viewModel.latitudeError != null -> { latFocusRequester.requestFocus(); latController.bringIntoView(coroutineScope) }
            viewModel.longitudeError != null -> { lngFocusRequester.requestFocus(); lngController.bringIntoView(coroutineScope) }
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
        title = stringResource(R.string.edit_property),
        onBack = { if (viewModel.isDirty()) showDiscardConfirm = true else onBack() },
        isLoading = viewModel.isSaving,
        primaryActionLabel = stringResource(R.string.save_property),
        onPrimaryAction = { viewModel.saveProperty(onBack) },
        secondaryAction = {
            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
                enabled = !viewModel.isSaving
            ) {
                Text(stringResource(R.string.delete_property))
            }
        }
    ) {
        item {
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                label = { Text(stringResource(R.string.property_name_label)) },
                placeholder = { Text(stringResource(R.string.property_name_hint)) },
                modifier = Modifier.fillMaxWidth().testTag("AddProperty_Name").focusRequester(nameFocusRequester).bringIntoViewOnFocus(nameController),
                isError = viewModel.nameError != null,
                supportingText = viewModel.nameError?.let { { Text(stringResource(it)) } },
                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.PROPER_NAME),
                keyboardActions = KeyboardPolicy.getActions(focusManager),
                singleLine = true,
                enabled = !viewModel.isSaving
            )
        }
        item {
            OutlinedTextField(
                value = viewModel.type,
                onValueChange = { viewModel.type = it },
                label = { Text(stringResource(R.string.property_type_label)) },
                placeholder = { Text(stringResource(R.string.property_type_hint)) },
                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(typeController),
                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.PROPER_NAME),
                keyboardActions = KeyboardPolicy.getActions(focusManager),
                singleLine = true,
                enabled = !viewModel.isSaving
            )
        }
        item {
            OutlinedTextField(
                value = viewModel.addressLine1,
                onValueChange = { viewModel.addressLine1 = it },
                label = { Text(stringResource(R.string.address_line_1_label)) },
                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(addr1Controller),
                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.ADDRESS),
                keyboardActions = KeyboardPolicy.getActions(focusManager),
                singleLine = true,
                enabled = !viewModel.isSaving
            )
        }
        item {
            OutlinedTextField(
                value = viewModel.addressLine2,
                onValueChange = { viewModel.addressLine2 = it },
                label = { Text(stringResource(R.string.address_line_2_label)) },
                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(addr2Controller),
                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.ADDRESS),
                keyboardActions = KeyboardPolicy.getActions(focusManager),
                singleLine = true,
                enabled = !viewModel.isSaving
            )
        }

        if (layoutInfo.isWidthCompact) {
            item {
                OutlinedTextField(
                    value = viewModel.city,
                    onValueChange = { viewModel.city = it },
                    label = { Text(stringResource(R.string.city_label)) },
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(cityController),
                    keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.ADDRESS),
                    keyboardActions = KeyboardPolicy.getActions(focusManager),
                    singleLine = true,
                    enabled = !viewModel.isSaving
                )
            }
            item {
                OutlinedTextField(
                    value = viewModel.state,
                    onValueChange = { viewModel.state = it },
                    label = { Text(stringResource(R.string.state_region_label)) },
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(stateController),
                    keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.ADDRESS),
                    keyboardActions = KeyboardPolicy.getActions(focusManager),
                    singleLine = true,
                    enabled = !viewModel.isSaving
                )
            }
            item {
                OutlinedTextField(
                    value = viewModel.postalCode,
                    onValueChange = { viewModel.postalCode = it },
                    label = { Text(stringResource(R.string.postal_code_label)) },
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(zipController),
                    keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.POSTAL_CODE),
                    keyboardActions = KeyboardPolicy.getActions(focusManager),
                    singleLine = true,
                    enabled = !viewModel.isSaving
                )
            }
            item {
                OutlinedTextField(
                    value = viewModel.countryCode,
                    onValueChange = { viewModel.countryCode = it },
                    label = { Text(stringResource(R.string.country_code_label)) },
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(countryController),
                    keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.ADDRESS),
                    keyboardActions = KeyboardPolicy.getActions(focusManager),
                    singleLine = true,
                    enabled = !viewModel.isSaving
                )
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = viewModel.city,
                        onValueChange = { viewModel.city = it },
                        label = { Text(stringResource(R.string.city_label)) },
                        modifier = Modifier.weight(1f).bringIntoViewOnFocus(cityController),
                        keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.ADDRESS),
                        keyboardActions = KeyboardPolicy.getActions(focusManager),
                        singleLine = true,
                        enabled = !viewModel.isSaving
                    )
                    OutlinedTextField(
                        value = viewModel.state,
                        onValueChange = { viewModel.state = it },
                        label = { Text(stringResource(R.string.state_region_label)) },
                        modifier = Modifier.weight(1f).bringIntoViewOnFocus(stateController),
                        keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.ADDRESS),
                        keyboardActions = KeyboardPolicy.getActions(focusManager),
                        singleLine = true,
                        enabled = !viewModel.isSaving
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = viewModel.postalCode,
                        onValueChange = { viewModel.postalCode = it },
                        label = { Text(stringResource(R.string.postal_code_label)) },
                        modifier = Modifier.weight(1f).bringIntoViewOnFocus(zipController),
                        keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.POSTAL_CODE),
                        keyboardActions = KeyboardPolicy.getActions(focusManager),
                        singleLine = true,
                        enabled = !viewModel.isSaving
                    )
                    OutlinedTextField(
                        value = viewModel.countryCode,
                        onValueChange = { viewModel.countryCode = it },
                        label = { Text(stringResource(R.string.country_code_label)) },
                        modifier = Modifier.weight(1f).bringIntoViewOnFocus(countryController),
                        keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.ADDRESS),
                        keyboardActions = KeyboardPolicy.getActions(focusManager),
                        singleLine = true,
                        enabled = !viewModel.isSaving
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = viewModel.parcelNumber,
                onValueChange = { viewModel.parcelNumber = it },
                label = { Text(stringResource(R.string.parcel_number_label)) },
                placeholder = { Text(stringResource(R.string.parcel_number_hint)) },
                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(parcelController),
                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.IDENTIFIER),
                keyboardActions = KeyboardPolicy.getActions(focusManager),
                singleLine = true,
                enabled = !viewModel.isSaving
            )
        }
        item {
            OutlinedTextField(
                value = viewModel.acreage,
                onValueChange = { viewModel.acreage = it },
                label = { Text(stringResource(R.string.acreage_label)) },
                modifier = Modifier.fillMaxWidth().focusRequester(acreageFocusRequester).bringIntoViewOnFocus(acreageController),
                isError = viewModel.acreageError != null,
                supportingText = viewModel.acreageError?.let { { Text(stringResource(it)) } },
                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.NUMERIC),
                keyboardActions = KeyboardPolicy.getActions(focusManager),
                singleLine = true,
                enabled = !viewModel.isSaving
            )
        }

        item {
            Text(stringResource(R.string.property_location_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onMyLocationClick,
                    enabled = !viewModel.isSaving && !isLocating,
                    modifier = Modifier.weight(1f)
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
                    onClick = { viewModel.clearLocation() },
                    enabled = !viewModel.isSaving && !isLocating && (viewModel.latitude.isNotBlank() || viewModel.longitude.isNotBlank()),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.clear_location))
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { viewModel.findCoordinatesFromAddress() },
                enabled = !viewModel.isSaving && !isLocating && addressLookupState !is AddressLookupStateLegacy.Searching,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (addressLookupState is AddressLookupStateLegacy.Searching) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Search, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.find_from_address))
            }
        }

        if (addressLookupState is AddressLookupStateLegacy.Error) {
            item {
                Text(
                    text = stringResource((addressLookupState as AddressLookupStateLegacy.Error).messageRes),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = viewModel.latitude,
                    onValueChange = { viewModel.latitude = it },
                    label = { Text(stringResource(R.string.latitude_label)) },
                    modifier = Modifier.weight(1f).testTag("AddProperty_Latitude").focusRequester(latFocusRequester).bringIntoViewOnFocus(latController),
                    isError = viewModel.latitudeError != null,
                    supportingText = viewModel.latitudeError?.let { { Text(stringResource(it)) } },
                    keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.COORDINATE),
                    keyboardActions = KeyboardPolicy.getActions(focusManager),
                    singleLine = true,
                    enabled = !viewModel.isSaving && !isLocating
                )
                OutlinedTextField(
                    value = viewModel.longitude,
                    onValueChange = { viewModel.longitude = it },
                    label = { Text(stringResource(R.string.longitude_label)) },
                    modifier = Modifier.weight(1f).testTag("AddProperty_Longitude").focusRequester(lngFocusRequester).bringIntoViewOnFocus(lngController),
                    isError = viewModel.longitudeError != null,
                    supportingText = viewModel.longitudeError?.let { { Text(stringResource(it)) } },
                    keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.COORDINATE, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardPolicy.getActions(focusManager, onDone = { focusManager.clearFocus() }),
                    singleLine = true,
                    enabled = !viewModel.isSaving && !isLocating
                )
            }
        }
        
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val activity = context.findComponentActivity()
        val status = PermissionUtils.determineLocationPermissionStatus(
            isGranted = fineGranted,
            hasBeenRequested = viewModel.permissionRequested,
            shouldShowRationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
        )

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

        locationState?.let { result ->
            val msgRes = LocationPresentation.getMessageRes(result)
            item {
                Text(
                    text = stringResource(msgRes) + if (result is LocationResult.Success) " (\u00b1${String.format(java.util.Locale.US, "%.1f", result.accuracyMeters)}m)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result is LocationResult.Success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }

        item {
            OutlinedTextField(
                value = viewModel.description,
                onValueChange = { viewModel.description = it },
                label = { Text(stringResource(R.string.description_label)) },
                modifier = Modifier.fillMaxWidth().testTag("AddProperty_Description").bringIntoViewOnFocus(descController),
                minLines = 3,
                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.MULTILINE_PROSE),
                keyboardActions = KeyboardPolicy.getActions(focusManager),
                enabled = !viewModel.isSaving
            )
        }

        if (viewModel.saveErrorRes != null) {
            item {
                Text(text = stringResource(viewModel.saveErrorRes!!), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.confirm_delete_property_title)) },
            text = { Text(stringResource(R.string.delete_property_confirm, viewModel.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProperty {
                            showDeleteConfirm = false
                            onBack()
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    when (val s = addressLookupState) {
        is AddressLookupStateLegacy.Results -> {
            AlertDialog(
                onDismissRequest = { viewModel.cancelAddressLookup() },
                title = { Text(stringResource(R.string.select_address)) },
                text = {
                    androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(s.matches) { match ->
                            ListItem(
                                headlineContent = { Text(match.displayAddress) },
                                supportingContent = { Text("${match.latitude}, ${match.longitude}") },
                                modifier = Modifier.clickable { viewModel.selectAddressMatch(match) }
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { viewModel.cancelAddressLookup() }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
        is AddressLookupStateLegacy.ConfirmingSelection -> {
            AlertDialog(
                onDismissRequest = { viewModel.cancelAddressLookup() },
                title = { Text(stringResource(R.string.overwrite_coords_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.overwrite_coords_address_message))
                        Spacer(Modifier.height(8.dp))
                        Text(s.match.displayAddress, fontWeight = FontWeight.Bold)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmAddressSelection() }) {
                        Text(stringResource(R.string.overwrite_label))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.cancelAddressLookup() }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
        else -> {}
    }
}
