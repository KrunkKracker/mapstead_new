package com.jumastappworks.mapstead.ui.properties

import android.Manifest
import android.content.pm.PackageManager
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.mapping.AddressLocationMatch
import com.jumastappworks.mapstead.data.mapping.BasemapProvider
import com.jumastappworks.mapstead.data.attachments.*
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.ui.components.*
import com.jumastappworks.mapstead.util.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPropertyScreen(
    viewModel: AddPropertyViewModel,
    basemapProvider: BasemapProvider,
    onBack: () -> Unit,
    onFinish: (UUID) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    fun clearFocusAndHideKeyboard() {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        viewModel.setLocationPermissionLaunchInProgress(false)
        val fineGranted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            viewModel.requestGpsLocation()
        } else {
            val act = context.findComponentActivity()
            val status = PermissionUtils.determineLocationPermissionStatus(
                isGranted = false,
                hasBeenRequested = true,
                shouldShowRationale = act?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
            )
            
            when (status) {
                PermissionStatus.DeniedPermanently -> viewModel.handlePermanentDenial()
                else -> viewModel.handleTransientDenial()
            }
        }
    }

    val requestPropertyLocationWithPermission = {
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            viewModel.clearPendingLocationRequest()
            viewModel.setPendingLocationPurpose(LocationRequestPurpose.LocateOnly)
            viewModel.requestGpsLocation()
        } else if (!state.locationPermissionLaunchInProgress) {
            viewModel.setPendingLocationPurpose(LocationRequestPurpose.LocateOnly)
            viewModel.markLocationPermissionRequested()
            viewModel.setLocationPermissionLaunchInProgress(true)
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    var showDiscardConfirm by remember { mutableStateOf(false) }

    val backHandler = {
        if (viewModel.isDirty()) {
            showDiscardConfirm = true
        } else {
            onBack()
        }
    }

    BackHandler(onBack = backHandler)

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text(stringResource(R.string.discard_changes_title)) },
            text = { Text(stringResource(R.string.discard_changes_message)) },
            confirmButton = {
                TextButton(onClick = { 
                    showDiscardConfirm = false
                    clearFocusAndHideKeyboard()
                    onBack() 
                }) {
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

    Scaffold(
        topBar = {
            if (state.currentStep != SetupStep.REVIEW) {
                TopAppBar(
                    title = {
                        Text(stringResource(when(state.currentStep) {
                            SetupStep.NAME_AND_TYPE -> R.string.setup_step_1_title
                            SetupStep.LOCATE -> R.string.setup_step_2_title
                            else -> R.string.setup_step_3_title
                        }))
                    },
                    navigationIcon = {
                        IconButton(onClick = backHandler) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                )
            }
        },
        contentWindowInsets = if (state.currentStep == SetupStep.REVIEW) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets
    ) { padding ->
        Box(modifier = Modifier.padding(if (state.currentStep == SetupStep.REVIEW) PaddingValues(0.dp) else padding).fillMaxSize()) {
            AnimatedContent(
                targetState = state.currentStep,
                transitionSpec = {
                    slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                },
                label = "SetupStepAnimation"
            ) { step ->
                when (step) {
                    SetupStep.NAME_AND_TYPE -> StepNameAndType(viewModel, state)
                    SetupStep.LOCATE -> StepLocate(viewModel, state, basemapProvider, backHandler, requestPropertyLocationWithPermission)
                    SetupStep.REVIEW -> StepReview(viewModel, state, basemapProvider, onBack = backHandler, onFinish = onFinish)
                }
            }
        }
    }
}

@Composable
private fun StepNameAndType(viewModel: AddPropertyViewModel, state: PropertySetupState) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = stringResource(R.string.setup_basics_header),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.setup_name_question), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = viewModel.nameInput,
                onValueChange = { viewModel.setName(it) },
                modifier = Modifier.fillMaxWidth().testTag("Setup_NameInput"),
                placeholder = { Text(stringResource(R.string.property_name_hint)) },
                isError = state.nameErrorRes != null,
                supportingText = state.nameErrorRes?.let { { Text(stringResource(it)) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.setup_type_question), style = MaterialTheme.typography.titleMedium)
            val types = listOf(
                stringResource(R.string.prop_type_home),
                stringResource(R.string.prop_type_farm),
                stringResource(R.string.prop_type_rental),
                stringResource(R.string.prop_type_vacant),
                stringResource(R.string.prop_type_workshop),
                stringResource(R.string.prop_type_other)
            )
            
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                types.forEach { type ->
                    FilterChip(
                        selected = state.propertyType == type,
                        onClick = { viewModel.setType(type) },
                        label = { Text(type) }
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = { 
                focusManager.clearFocus()
                keyboardController?.hide()
                viewModel.proceedToLocate() 
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("Setup_ContinueToLocate")
        ) {
            Text(stringResource(R.string.continue_label))
        }
    }
}

@Composable
private fun StepLocate(viewModel: AddPropertyViewModel, state: PropertySetupState, basemapProvider: BasemapProvider, onBack: () -> Unit, requestPropertyLocationWithPermission: () -> Unit) {
    val focusManager = LocalFocusManager.current
    Box(modifier = Modifier.fillMaxSize()) {
        when (state.locationMethodScreen) {
            PropertyLocationMethod.NONE -> LocateMenu(viewModel, state, onBack, requestPropertyLocationWithPermission)
            PropertyLocationMethod.ADDRESS -> LocateAddress(viewModel, state)
            PropertyLocationMethod.MAP -> LocateOnMap(viewModel, state, basemapProvider)
            PropertyLocationMethod.MANUAL -> LocateManual(viewModel, state)
            PropertyLocationMethod.GPS -> { /* GPS is handled as a quick action in LocateMenu */ }
        }

        state.candidateLocation?.let { cand ->
            LocationConfirmationOverlay(
                candidate = cand,
                measurementSystem = state.measurementSystem,
                onConfirm = { 
                    focusManager.clearFocus()
                    viewModel.confirmCandidate() 
                },
                onCancel = { viewModel.clearCandidate() }
            )
        }
    }
}

@Composable
private fun LocateMenu(
    viewModel: AddPropertyViewModel, 
    state: PropertySetupState, 
    onBack: () -> Unit,
    onRequestLocation: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    fun clearFocusAndHideKeyboard() {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.setup_locate_desc),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        LocationChoiceCard(
            title = stringResource(R.string.setup_address_search),
            icon = Icons.Default.Search,
            onClick = { 
                clearFocusAndHideKeyboard()
                viewModel.openAddressSearch() 
            }
        )

        LocationChoiceCard(
            title = stringResource(R.string.setup_current_location),
            icon = Icons.Default.MyLocation,
            loading = state.isLocatingGps,
            onClick = { 
                clearFocusAndHideKeyboard()
                onRequestLocation() 
            }
        )

        LocationChoiceCard(
            title = stringResource(R.string.setup_choose_on_map),
            icon = Icons.Default.Map,
            onClick = { 
                clearFocusAndHideKeyboard()
                viewModel.openMapPicker() 
            }
        )

        TextButton(
            onClick = { 
                clearFocusAndHideKeyboard()
                viewModel.openManualEntry() 
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(stringResource(R.string.setup_more_options))
        }

        Spacer(Modifier.weight(1f))

        if (state.target is PropertySetupTarget.New) {
            OutlinedButton(
                onClick = { 
                    clearFocusAndHideKeyboard()
                    viewModel.deferLocation() 
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) {
                Text(stringResource(R.string.setup_add_location_later))
            }
        } else {
            OutlinedButton(
                onClick = { 
                    clearFocusAndHideKeyboard()
                    onBack() 
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) {
                Text(stringResource(R.string.cancel))
            }
        }

        state.locationIssue?.let { issue ->
            AlertDialog(
                onDismissRequest = { viewModel.cancelLocationIssue() },
                title = { Text(stringResource(R.string.location_issue_title)) },
                text = { Text(stringResource(issue.messageRes)) },
                confirmButton = {
                    Column(horizontalAlignment = Alignment.End) {
                        if (issue.canRetry) { 
                            val retryLabel = stringResource(R.string.retry)
                            TextButton(onClick = { 
                                if (issue.type == LocationIssueType.PermissionDenied) {
                                    onRequestLocation()
                                    viewModel.cancelLocationIssue()
                                } else {
                                    viewModel.retryLocationIssue() 
                                }
                            }) { 
                                Text(retryLabel) 
                            } 
                        }
                        if (issue.canOpenAppSettings) { 
                            TextButton(onClick = { 
                                if (!PermissionUtils.openAppSettings(context)) {
                                    viewModel.setGeneralError(R.string.error_occurred)
                                }
                                viewModel.cancelLocationIssue()
                            }) { Text(stringResource(R.string.open_settings)) } 
                        }
                        if (issue.canOpenLocationSettings) { 
                            TextButton(onClick = { 
                                try {
                                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                } catch (e: Exception) {
                                    viewModel.setGeneralError(R.string.error_occurred)
                                }
                                viewModel.cancelLocationIssue()
                            }) { Text(stringResource(R.string.open_location_settings)) } 
                        }
                    }
                },
                dismissButton = { 
                    TextButton(onClick = { viewModel.cancelLocationIssue() }) { 
                        Text(stringResource(R.string.cancel)) 
                    } 
                }
            )
        }

        state.errorRes?.let {
            Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun LocationChoiceCard(title: String, icon: ImageVector, loading: Boolean = false, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = !loading
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun LocateAddress(viewModel: AddPropertyViewModel, state: PropertySetupState) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.openLocationMenu() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Text(stringResource(R.string.setup_address_search), style = MaterialTheme.typography.titleLarge)
        }

        OutlinedTextField(
            value = state.addressQuery,
            onValueChange = { viewModel.searchAddress(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Enter address...") },
            trailingIcon = {
                if (state.isSearchingAddress) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else IconButton(onClick = { 
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    viewModel.searchAddress(state.addressQuery) 
                }) { Icon(Icons.Default.Search, contentDescription = null) }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                viewModel.searchAddress(state.addressQuery)
                focusManager.clearFocus()
                keyboardController?.hide()
            })
        )

        state.errorRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.addressResults) { match ->
                ListItem(
                    headlineContent = { Text(match.displayAddress) },
                    modifier = Modifier.clickable { 
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        viewModel.selectAddressCandidate(match) 
                    },
                    leadingContent = { Icon(Icons.Default.LocationOn, contentDescription = null) }
                )
            }
        }
    }
}

@Composable
private fun LocateOnMap(viewModel: AddPropertyViewModel, state: PropertySetupState, basemapProvider: BasemapProvider) {
    val focusManager = LocalFocusManager.current
    Box(modifier = Modifier.fillMaxSize()) {
        PropertyLocationPickerMap(
            initialLat = state.pickerLat,
            initialLng = state.pickerLng,
            initialZoom = state.pickerZoom,
            basemapProvider = basemapProvider,
            onCameraMoved = { lat, lng, zoom -> viewModel.setPickerCamera(lat, lng, zoom) },
            onConfirm = { lat, lng -> 
                focusManager.clearFocus()
                viewModel.setMapCandidate(lat, lng) 
            }
        )
        
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            tonalElevation = 4.dp
        ) {
            Text(stringResource(R.string.setup_map_selection_hint), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LocateManual(viewModel: AddPropertyViewModel, state: PropertySetupState) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.openLocationMenu() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Text(stringResource(R.string.setup_manual_coords), style = MaterialTheme.typography.titleLarge)
        }

        OutlinedTextField(
            value = state.manualLatInput,
            onValueChange = { viewModel.setManualInputs(it, state.manualLngInput) },
            label = { Text(stringResource(R.string.latitude_label)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
        )

        OutlinedTextField(
            value = state.manualLngInput,
            onValueChange = { viewModel.setManualInputs(state.manualLatInput, it) },
            label = { Text(stringResource(R.string.longitude_label)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                focusManager.clearFocus()
                keyboardController?.hide()
                viewModel.validateAndSetManualCandidate()
            })
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick = { 
                focusManager.clearFocus()
                keyboardController?.hide()
                viewModel.validateAndSetManualCandidate() 
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            enabled = state.manualLatInput.isNotBlank() && state.manualLngInput.isNotBlank()
        ) {
            Text(stringResource(R.string.confirm))
        }
    }
}

@Composable
private fun LocationConfirmationOverlay(candidate: PropertyLocationCandidate, measurementSystem: com.jumastappworks.mapstead.data.prefs.MeasurementSystem, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.setup_confirm_location), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(candidate.displayLabel, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            
            if (candidate.accuracyMeters != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = MeasurementFormatter.formatDistance(candidate.accuracyMeters, measurementSystem),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(48.dp))

            Button(onClick = { 
                onConfirm() 
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.confirm))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.cancel))
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.approximate_disclaimer), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepReview(viewModel: AddPropertyViewModel, state: PropertySetupState, basemapProvider: BasemapProvider, onBack: () -> Unit, onFinish: (UUID) -> Unit) {
    val scrollState = rememberScrollState()
    val isExisting = state.target is PropertySetupTarget.Existing
    val mainScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.setStagedPhoto(it.toString(), null) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        viewModel.handleCameraResult(success)
    }

    LaunchedEffect(state.outcome) {
        if (state.outcome is PropertySetupOutcome.PropertyCreated) {
            onFinish(state.outcome.propertyId)
            viewModel.clearOutcome()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(24.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(
                text = stringResource(R.string.setup_step_3_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReviewItem(stringResource(R.string.property_name_label), state.propertyName)
                if (!isExisting) {
                    state.propertyType?.let { ReviewItem(stringResource(R.string.property_type_label), it) }
                }
                
                HorizontalDivider()
                
                val locLabel = if (state.isLocationDeferred) {
                    stringResource(R.string.setup_location_deferred_label)
                } else if (state.confirmedLocation != null) {
                    state.confirmedLocation.displayLabel
                } else {
                    "None"
                }
                ReviewItem(stringResource(R.string.property_location_title), locLabel)
            }
        }

        if (!state.isLocationDeferred && state.confirmedLocation != null) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp))) {
                PropertyLocationPreviewMap(
                    latitude = state.confirmedLocation.latitude,
                    longitude = state.confirmedLocation.longitude,
                    basemapProvider = basemapProvider
                )
            }
        }

        CreationPhotoSection(
            stagedPhoto = state.stagedPhoto,
            onTakePhoto = {
                mainScope.launch {
                    viewModel.createCameraCapture().onSuccess { capture ->
                        viewModel.setInFlightCapture(capture.uri.toString(), capture.token)
                        cameraLauncher.launch(capture.uri)
                    }
                }
            },
            onChoosePhoto = {
                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onRemovePhoto = { viewModel.clearStagedPhoto() },
            onRetryPreview = { /* AsyncImage will re-evaluate on recompose if model changes or key changes. */ }
        )

        if (state.outcome is PropertySetupOutcome.PropertyCreatedWithPhotoWarning) {
            val pid = state.outcome.propertyId
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.property_created_with_photo_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.retryPropertyPhoto(pid) },
                        modifier = Modifier.weight(1f),
                        enabled = !state.isSaving
                    ) {
                        Text(stringResource(R.string.retry_photo))
                    }
                    OutlinedButton(
                        onClick = { viewModel.continueWithoutPhoto(pid) },
                        modifier = Modifier.weight(1f),
                        enabled = !state.isSaving
                    ) {
                        Text(stringResource(R.string.continue_without_photo))
                    }
                }
            }
        } else {
            Button(
                onClick = { 
                    focusManager.clearFocus()
                    viewModel.createProperty() 
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("Setup_FinalCreate"),
                enabled = !state.isSaving
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    val label = if (isExisting) "Save Location" else stringResource(R.string.setup_confirm_create)
                    Text(label)
                }
            }
        }
        
        state.errorRes?.let {
            if (state.outcome !is PropertySetupOutcome.PropertyCreatedWithPhotoWarning) {
                Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ReviewItem(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}
