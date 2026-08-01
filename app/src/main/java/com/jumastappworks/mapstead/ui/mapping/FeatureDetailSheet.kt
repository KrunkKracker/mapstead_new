package com.jumastappworks.mapstead.ui.mapping

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.db.entities.LayerEntity
import com.jumastappworks.mapstead.data.db.entities.MapFeatureEntity
import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import com.jumastappworks.mapstead.data.prefs.MeasurementSystem
import com.jumastappworks.mapstead.data.attachments.*
import com.jumastappworks.mapstead.data.mapping.*
import com.jumastappworks.mapstead.ui.components.*
import com.jumastappworks.mapstead.util.MeasurementFormatter
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureDetailSheet(
    feature: MapFeatureEntity,
    layers: List<LayerEntity>,
    infrastructureItems: List<InfrastructureItemEntity>,
    isSaving: Boolean,
    isDeleting: Boolean,
    labelError: Int?,
    accuracyError: Int?,
    errorMsg: String?,
    onSave: (MapFeatureEntity) -> Unit,
    onDelete: (UUID) -> Unit,
    onDismiss: () -> Unit,
    onCancel: () -> Unit = {},
    onSaveNewSystemItem: (PendingSystemItemInput) -> UUID,
    onMovePointClick: (UUID) -> Unit,
    onEditShapeClick: (UUID) -> Unit,
    canEditShape: Boolean = true,
    editShapeBlockReason: String? = null,
    isNewUnsavedFeature: Boolean = false,
    attachmentCount: Int = 0,
    photoCount: Int = 0,
    coverThumbnailUri: android.net.Uri? = null,
    onViewAttachments: (UUID) -> Unit = {},
    onTakePhoto: (UUID) -> Unit = {},
    onChoosePhoto: (UUID) -> Unit = {},
    onChooseDocument: (UUID) -> Unit = {},
    measurementSystem: MeasurementSystem = MeasurementSystem.IMPERIAL,
    guidedPrefill: GuidedFeaturePrefill? = null,
    systemItemDraft: PendingSystemItemDraft? = null,
    linkSelection: SystemItemLinkSelection = SystemItemLinkSelection.None,
    onLinkSelectionChange: (SystemItemLinkSelection) -> Unit = {},
    onClearSystemItemDraft: () -> Unit = {},
    stagedPhoto: StagedCreationPhotoState = StagedCreationPhotoState.None,
    onRemoveStagedPhoto: () -> Unit = {},
    onTakePhotoCreation: (UUID) -> Unit = {},
    onChoosePhotoCreation: (UUID) -> Unit = {},
    saveOutcome: GuidedSaveOutcome? = null,
    onRetryPhoto: (UUID, UUID) -> Unit = { _, _ -> },
    onContinueWithoutPhoto: (UUID) -> Unit = {}
) {
    var label by remember(feature.id) { mutableStateOf(feature.label ?: "") }
    var selectedLayerId by remember(feature.id) { mutableStateOf(feature.layerId) }
    
    var category by remember(feature.id) { 
        mutableStateOf(try { JSONObject(feature.styleJson).optString("category", "Structure") } catch (e: Exception) { "Structure" })
    }
    var notes by remember(feature.id) { 
        mutableStateOf(try { JSONObject(feature.styleJson).optString("notes", "") } catch (e: Exception) { "" })
    }

    // Prefill logic
    val currentPrefillId = guidedPrefill?.sessionId
    var lastAppliedPrefillId by remember(feature.id) { mutableStateOf<UUID?>(null) }
    val suggestedLabel = guidedPrefill?.suggestedLabel ?: guidedPrefill?.suggestedLabelRes?.let { stringResource(it) }
    
    LaunchedEffect(feature.id, currentPrefillId, guidedPrefill?.draftId) {
        if (guidedPrefill != null && guidedPrefill.draftId == feature.id && lastAppliedPrefillId != currentPrefillId) {
            if (label.isBlank() && suggestedLabel != null) {
                label = suggestedLabel
            }
            if (guidedPrefill.suggestedLayerId != null && selectedLayerId == feature.layerId) {
                selectedLayerId = guidedPrefill.suggestedLayerId
            }
            if (category == "Structure" && guidedPrefill.suggestedCategory != null) {
                category = guidedPrefill.suggestedCategory
            }
            lastAppliedPrefillId = currentPrefillId
        }
    }

    var accuracySource by remember(feature.id) { mutableStateOf(feature.accuracySource) }
    
    var horizontalAccuracyText by remember(feature.id) { 
        mutableStateOf(feature.horizontalAccuracyMeters?.let { 
            MeasurementFormatter.displayAccuracyInput(it, measurementSystem)
        } ?: "")
    }
    var isAccuracyEdited by remember(feature.id) { mutableStateOf(false) }
    var localAccuracyError by remember { mutableStateOf<Int?>(null) }
    var lastSystem by remember(feature.id) { mutableStateOf(measurementSystem) }

    LaunchedEffect(measurementSystem) {
        if (lastSystem != measurementSystem) {
            if (!isAccuracyEdited) {
                horizontalAccuracyText = feature.horizontalAccuracyMeters?.let { 
                    MeasurementFormatter.displayAccuracyInput(it, measurementSystem)
                } ?: ""
            } else if (horizontalAccuracyText.isNotBlank()) {
                val previousResult = MeasurementFormatter.parseAccuracyInputToMeters(horizontalAccuracyText, lastSystem)
                if (previousResult.isSuccess) {
                    val meters = previousResult.getOrNull()
                    if (meters != null) {
                        horizontalAccuracyText = MeasurementFormatter.displayAccuracyInput(meters, measurementSystem)
                    }
                }
            }
            lastSystem = measurementSystem
        }
    }

    var showDeleteConfirmDialog by remember(feature.id) { mutableStateOf(false) }
    var showAddInfraDialog by remember(feature.id) { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val currentLayer = layers.find { it.id == selectedLayerId }
    val isLayerLocked = currentLayer?.isLocked == true

    val labelController = rememberFormFieldVisibilityController()
    val accuracyController = rememberFormFieldVisibilityController()
    val notesController = rememberFormFieldVisibilityController()

    val labelFocusRequester = remember { FocusRequester() }
    val accuracyFocusRequester = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    LaunchedEffect(labelError) {
        if (labelError != null) {
            labelFocusRequester.requestFocus()
            labelController.bringIntoView(coroutineScope)
        }
    }
    LaunchedEffect(accuracyError ?: localAccuracyError) {
        if ((accuracyError ?: localAccuracyError) != null) {
            accuracyFocusRequester.requestFocus()
            accuracyController.bringIntoView(coroutineScope)
        }
    }

    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isNewUnsavedFeature && guidedPrefill != null) {
                val presetTitle = suggestedLabel ?: "Item"
                Text(
                    text = stringResource(R.string.setup_review_new_title, presetTitle),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.item_name_label)) },
                    modifier = Modifier.fillMaxWidth().focusRequester(labelFocusRequester).bringIntoViewOnFocus(labelController),
                    isError = labelError != null,
                    supportingText = labelError?.let { { Text(stringResource(it)) } },
                    keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.PROPER_NAME, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardPolicy.getActions(focusManager),
                    singleLine = true,
                    enabled = !isSaving
                )

                Text(
                    text = category,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                GeometrySummary(feature, measurementSystem)

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.notes_label)) },
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(notesController),
                    minLines = 3,
                    keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.MULTILINE_PROSE),
                    keyboardActions = KeyboardPolicy.getActions(focusManager),
                    enabled = !isSaving
                )

                errorMsg?.let { err ->
                    Text(text = err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }

                when (guidedPrefill.systemItemPolicy) {
                    SystemItemPolicy.AUTOMATIC -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.tracking_records_included), style = MaterialTheme.typography.titleSmall)
                                }
                                Text(stringResource(R.string.tracking_records_included_desc), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    SystemItemPolicy.OPTIONAL -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            onClick = { 
                                if (!isSaving) {
                                    val next = if (linkSelection == SystemItemLinkSelection.CreateSuggested) SystemItemLinkSelection.None else SystemItemLinkSelection.CreateSuggested
                                    onLinkSelectionChange(next)
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = linkSelection == SystemItemLinkSelection.CreateSuggested,
                                    onCheckedChange = null, // Handled by Card onClick
                                    enabled = !isSaving
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        stringResource(R.string.tracking_keep_records), 
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        stringResource(R.string.tracking_keep_records_desc), 
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                    SystemItemPolicy.MAP_ONLY -> { /* No tracking section */ }
                }

                CreationPhotoSection(
                    stagedPhoto = stagedPhoto,
                    onTakePhoto = { onTakePhotoCreation(feature.id) },
                    onChoosePhoto = { onChoosePhotoCreation(feature.id) },
                    onRemovePhoto = onRemoveStagedPhoto,
                    onRetryPreview = { /* AsyncImage re-renders on model change */ }
                )
            } else {
                // Existing feature or unguided new feature
                Text(
                    text = if (isLayerLocked) stringResource(R.string.feature_details_locked) 
                           else if (feature.geometryType == "LINESTRING") stringResource(R.string.feature_details_line) 
                           else if (feature.geometryType == "POLYGON") stringResource(R.string.area_details_title) 
                           else stringResource(R.string.feature_details_point),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                if (isLayerLocked) {
                    Text(stringResource(R.string.locked_layer_read_only), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }

                GeometrySummary(feature, measurementSystem)

                errorMsg?.let { err ->
                    Text(text = err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.point_label)) },
                    modifier = Modifier.testTag("label_field").fillMaxWidth().focusRequester(labelFocusRequester).bringIntoViewOnFocus(labelController),
                    readOnly = isLayerLocked || isSaving || isDeleting,
                    enabled = !isLayerLocked && !isSaving && !isDeleting,
                    isError = labelError != null,
                    supportingText = labelError?.let { { Text(stringResource(it)) } },
                    keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.PROPER_NAME),
                    keyboardActions = KeyboardPolicy.getActions(focusManager),
                    singleLine = true
                )

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(stringResource(R.string.item_category)) },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = isLayerLocked || isSaving || isDeleting,
                    enabled = !isLayerLocked && !isSaving && !isDeleting,
                    keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.PROPER_NAME),
                    keyboardActions = KeyboardPolicy.getActions(focusManager),
                    singleLine = true
                )

                AccuracySection(
                    accuracySource = accuracySource,
                    onSourceChange = { accuracySource = it },
                    horizontalAccuracyText = horizontalAccuracyText,
                    onAccuracyTextChange = { horizontalAccuracyText = it; isAccuracyEdited = true; localAccuracyError = null },
                    accuracyError = accuracyError ?: localAccuracyError,
                    measurementSystem = measurementSystem,
                    isReadOnly = isLayerLocked || isSaving || isDeleting,
                    focusRequester = accuracyFocusRequester,
                    visibilityController = accuracyController
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.notes_label)) },
                    modifier = Modifier.testTag("notes_field").fillMaxWidth().bringIntoViewOnFocus(notesController),
                    minLines = 3,
                    readOnly = isLayerLocked || isSaving || isDeleting,
                    enabled = !isLayerLocked && !isSaving && !isDeleting,
                    keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.MULTILINE_PROSE),
                    keyboardActions = KeyboardPolicy.getActions(focusManager)
                )

                if (isNewUnsavedFeature || saveOutcome is GuidedSaveOutcome.FeatureSavedPhotoFailed) {
                    CreationPhotoSection(
                        stagedPhoto = stagedPhoto,
                        onTakePhoto = { onTakePhotoCreation(feature.id) },
                        onChoosePhoto = { onChoosePhotoCreation(feature.id) },
                        onRemovePhoto = onRemoveStagedPhoto,
                        onRetryPreview = { /* AsyncImage re-renders on model change */ }
                    )
                } else {
                    PhotosAndFilesSection(
                        attachmentCount = attachmentCount, photoCount = photoCount,
                        coverThumbnailUri = coverThumbnailUri, isReadOnly = isLayerLocked || isSaving || isDeleting,
                        onViewAll = { onViewAttachments(feature.id) }, onTakePhoto = { onTakePhoto(feature.id) },
                        onChoosePhoto = { onChoosePhoto(feature.id) }, onChooseDocument = { onChooseDocument(feature.id) }
                    )
                }

                Spacer(Modifier.height(8.dp))
                LinkSection(
                    linkSelection = linkSelection,
                    onLinkSelectionChange = onLinkSelectionChange,
                    infrastructureItems = infrastructureItems,
                    systemItemDraft = systemItemDraft,
                    onClearSystemItemDraft = onClearSystemItemDraft,
                    isReadOnly = isLayerLocked || isSaving || isDeleting,
                    onCreateNewClick = { showAddInfraDialog = true }
                )

                if (!isLayerLocked && !isSaving && !isDeleting) {
                    if (feature.geometryType == "POINT") {
                        Button(onClick = { onMovePointClick(feature.id) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary), modifier = Modifier.testTag("move_point_button").fillMaxWidth(), enabled = canEditShape) { Text(stringResource(R.string.move_point)) }
                    } else {
                        Button(onClick = { onEditShapeClick(feature.id) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary), modifier = Modifier.testTag("edit_shape_button").fillMaxWidth(), enabled = canEditShape) { Text(stringResource(R.string.edit_shape)) }
                    }
                    editShapeBlockReason?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Sticky Footer
        Surface(tonalElevation = 8.dp, shadowElevation = 8.dp) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (saveOutcome is GuidedSaveOutcome.FeatureSavedPhotoFailed) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.feature_saved_with_photo_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onRetryPhoto(saveOutcome.propertyId, saveOutcome.featureId) },
                                modifier = Modifier.weight(1f),
                                enabled = !isSaving
                            ) {
                                Text(stringResource(R.string.retry_photo))
                            }
                            OutlinedButton(
                                onClick = { onContinueWithoutPhoto(saveOutcome.featureId) },
                                modifier = Modifier.weight(1f),
                                enabled = !isSaving
                            ) {
                                Text(stringResource(R.string.continue_without_photo))
                            }
                        }
                    }
                } else if (isSaving || isDeleting) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterVertically))
                } else if (!isLayerLocked) {
                    if (!isNewUnsavedFeature) {
                        OutlinedButton(onClick = { showDeleteConfirmDialog = true }, modifier = Modifier.weight(1.0f), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.delete)) }
                    } else {
                        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1.0f)) { Text(stringResource(R.string.cancel)) }
                    }
                    Button(
                        onClick = {
                            val accuracyResult = if (horizontalAccuracyText.isBlank()) Result.success(null) else MeasurementFormatter.parseAccuracyInputToMeters(horizontalAccuracyText, measurementSystem)
                            if (accuracyResult.isFailure) { localAccuracyError = R.string.error_invalid_number; return@Button }
                            val accuracyMeters = accuracyResult.getOrNull()
                            val updatedStyle = try {
                                val current = JSONObject(feature.styleJson); current.put("category", category); current.put("notes", notes); current.toString()
                            } catch (e: Exception) {
                                JSONObject().apply { put("category", category); put("notes", notes) }.toString()
                            }
                            
                            onSave(feature.copy(label = label, layerId = selectedLayerId, styleJson = updatedStyle, accuracySource = accuracySource, horizontalAccuracyMeters = accuracyMeters))
                        },
                        modifier = Modifier.testTag("save_button").weight(1.0f),
                        enabled = label.isNotBlank()
                    ) { Text(stringResource(R.string.save)) }
                } else {
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.dismiss)) }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(onDismissRequest = { showDeleteConfirmDialog = false }, title = { Text(stringResource(R.string.confirm_delete_item_title)) }, text = { Text(stringResource(R.string.delete_feature_confirm)) }, confirmButton = { TextButton(onClick = { showDeleteConfirmDialog = false; onDelete(feature.id) }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { showDeleteConfirmDialog = false }) { Text(stringResource(R.string.cancel)) } })
    }

    if (showAddInfraDialog) {
        CreateSystemItemDialog(
            isCreating = false,
            onDismiss = { showAddInfraDialog = false },
            onSave = { onSaveNewSystemItem(it); showAddInfraDialog = false }
        )
    }
}

@Composable
private fun GeometrySummary(feature: MapFeatureEntity, measurementSystem: MeasurementSystem) {
    val isPolygon = feature.geometryType == "POLYGON"
    val isLine = feature.geometryType == "LINESTRING"

    if (isLine || isPolygon) {
        val verticesCount = remember(feature.id) {
            try {
                val obj = org.json.JSONObject(feature.geometryJson)
                val coords = if (isPolygon) obj.getJSONArray("coordinates").getJSONArray(0) else obj.getJSONArray("coordinates")
                if (isPolygon) coords.length() - 1 else coords.length()
            } catch (e: Exception) { 0 }
        }
        val pathLengthMeters = remember(feature.id) {
            try {
                val obj = org.json.JSONObject(feature.geometryJson)
                val coords = if (isPolygon) obj.getJSONArray("coordinates").getJSONArray(0) else obj.getJSONArray("coordinates")
                val list = mutableListOf<Pair<Double, Double>>()
                for (i in 0 until coords.length()) {
                    val pt = coords.getJSONArray(i)
                    list.add(Pair(pt.getDouble(0), pt.getDouble(1)))
                }
                if (isPolygon) com.jumastappworks.mapstead.util.GeometryUtils.calculatePolygonPerimeter(list.dropLast(1))
                else com.jumastappworks.mapstead.util.GeometryUtils.calculatePathLength(list)
            } catch (e: Exception) { 0.0 }
        }
        val formattedLength = MeasurementFormatter.formatDistance(pathLengthMeters, measurementSystem)
        
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (isPolygon) {
                val areaMeters = remember(feature.id) {
                    val result = com.jumastappworks.mapstead.util.GeometryUtils.parsePolygonGeoJson(feature.geometryJson)
                    if (result is com.jumastappworks.mapstead.util.PolygonParseResult.Success) {
                        com.jumastappworks.mapstead.util.GeometryUtils.calculateSphericalArea(result.vertices)
                    } else 0.0
                }
                Text("${stringResource(R.string.approx_area_label)}: ${MeasurementFormatter.formatArea(areaMeters, measurementSystem)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.perimeter_label, formattedLength), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(stringResource(R.string.length_label, formattedLength), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            Text(stringResource(R.string.vertices_count, verticesCount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun AccuracySection(
    accuracySource: String,
    onSourceChange: (String) -> Unit,
    horizontalAccuracyText: String,
    onAccuracyTextChange: (String) -> Unit,
    accuracyError: Int?,
    measurementSystem: MeasurementSystem,
    isReadOnly: Boolean,
    focusRequester: FocusRequester,
    visibilityController: FormFieldVisibilityController
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val accuracyChoices = listOf(
        R.string.accuracy_source_user_estimated to "User Estimated",
        R.string.accuracy_source_phone_gps to "Phone GPS",
        R.string.accuracy_source_measured_from_reference to "Measured From Reference",
        R.string.accuracy_source_imported_plan to "Imported Plan",
        R.string.accuracy_source_imported_survey to "Imported Survey",
        R.string.accuracy_source_professional_locate to "Professional Utility Locate",
        R.string.accuracy_source_professional_surveyed to "Professionally Surveyed",
        R.string.accuracy_source_unknown to "Unknown"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var showAccuracyMenu by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(
                onClick = { if (!isReadOnly) showAccuracyMenu = true },
                enabled = !isReadOnly,
                modifier = Modifier.fillMaxWidth()
            ) {
                val currentAccuracySourceRes = accuracyChoices.find { it.second == accuracySource }?.first ?: R.string.accuracy_source_unknown
                Text(stringResource(R.string.accuracy_source_label) + ": " + stringResource(currentAccuracySourceRes))
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = showAccuracyMenu, onDismissRequest = { showAccuracyMenu = false }) {
                accuracyChoices.forEach { (res, value) ->
                    DropdownMenuItem(text = { Text(stringResource(res)) }, onClick = { onSourceChange(value); showAccuracyMenu = false })
                }
            }
        }

        val accuracyUnit = if (measurementSystem == MeasurementSystem.IMPERIAL) stringResource(R.string.unit_feet) else stringResource(R.string.unit_meters)
        OutlinedTextField(
            value = horizontalAccuracyText,
            onValueChange = onAccuracyTextChange,
            label = { Text(stringResource(R.string.accuracy_label_unit, accuracyUnit)) },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).bringIntoViewOnFocus(visibilityController),
            readOnly = isReadOnly,
            enabled = !isReadOnly,
            isError = accuracyError != null,
            supportingText = accuracyError?.let { { Text(stringResource(it)) } },
            keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.COORDINATE),
            keyboardActions = KeyboardPolicy.getActions(focusManager),
            singleLine = true
        )
    }
}

@Composable
private fun LinkSection(
    linkSelection: SystemItemLinkSelection,
    onLinkSelectionChange: (SystemItemLinkSelection) -> Unit,
    infrastructureItems: List<InfrastructureItemEntity>,
    systemItemDraft: PendingSystemItemDraft?,
    onClearSystemItemDraft: () -> Unit,
    isReadOnly: Boolean,
    onCreateNewClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.system_item_link_label), style = MaterialTheme.typography.labelMedium)

        var showInfraMenu by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(
                onClick = { if (!isReadOnly) showInfraMenu = true },
                enabled = !isReadOnly,
                modifier = Modifier.fillMaxWidth()
            ) {
                val linkedName = when (linkSelection) {
                    is SystemItemLinkSelection.Existing -> {
                        infrastructureItems.find { it.id == linkSelection.itemId }?.name ?: stringResource(R.string.linked_system_item_unavailable)
                    }
                    is SystemItemLinkSelection.PendingDraft -> {
                        if (systemItemDraft != null && linkSelection.draftId == systemItemDraft.id) systemItemDraft.name
                        else stringResource(R.string.no_linked_item)
                    }
                    SystemItemLinkSelection.CreateSuggested -> stringResource(R.string.suggested_item_label)
                    SystemItemLinkSelection.None -> stringResource(R.string.no_linked_item)
                }
                Text("${stringResource(R.string.linked_label)}: $linkedName")
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = showInfraMenu, onDismissRequest = { showInfraMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.no_linked_item)) },
                    onClick = { 
                        onLinkSelectionChange(SystemItemLinkSelection.None)
                        onClearSystemItemDraft()
                        showInfraMenu = false 
                    }
                )
                infrastructureItems.forEach { item ->
                    DropdownMenuItem(
                        text = { Text("${item.name} (${item.category})") },
                        onClick = { 
                            onLinkSelectionChange(SystemItemLinkSelection.Existing(item.id))
                            onClearSystemItemDraft()
                            showInfraMenu = false 
                        }
                    )
                }
                if (systemItemDraft != null) {
                    DropdownMenuItem(
                        text = { Text("${systemItemDraft.name} (Draft)") },
                        onClick = {
                            onLinkSelectionChange(SystemItemLinkSelection.PendingDraft(systemItemDraft.id))
                            showInfraMenu = false
                        }
                    )
                }
            }
        }

        if (!isReadOnly) {
            Button(onClick = onCreateNewClick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.create_system_item))
            }
        }
    }
}

@Composable
private fun CreateSystemItemDialog(
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onSave: (PendingSystemItemInput) -> Unit
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val newNameController = rememberFormFieldVisibilityController()
    val newCatController = rememberFormFieldVisibilityController()
    val newInstController = rememberFormFieldVisibilityController()
    val newNameFocusRequester = remember { FocusRequester() }
    val newCatFocusRequester = remember { FocusRequester() }

    var newInfraName by remember { mutableStateOf("") }
    var newInfraCategory by remember { mutableStateOf("Utility") }
    var newInfraIsEmergency by remember { mutableStateOf(false) }
    var newInfraEmergencyInstructions by remember { mutableStateOf("") }
    var nameErrorRes by remember { mutableStateOf<Int?>(null) }
    var categoryErrorRes by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = { Text(stringResource(R.string.create_system_item)) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newInfraName,
                    onValueChange = { newInfraName = it; nameErrorRes = null },
                    label = { Text(stringResource(R.string.item_name_label)) },
                    modifier = Modifier.focusRequester(newNameFocusRequester).bringIntoViewOnFocus(newNameController),
                    isError = nameErrorRes != null,
                    supportingText = nameErrorRes?.let { { Text(stringResource(it)) } },
                    keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.PROPER_NAME),
                    keyboardActions = KeyboardPolicy.getActions(focusManager),
                    singleLine = true,
                    enabled = !isCreating
                )
                OutlinedTextField(
                    value = newInfraCategory,
                    onValueChange = { newInfraCategory = it; categoryErrorRes = null },
                    label = { Text(stringResource(R.string.item_category)) },
                    modifier = Modifier.focusRequester(newCatFocusRequester).bringIntoViewOnFocus(newCatController),
                    isError = categoryErrorRes != null,
                    supportingText = categoryErrorRes?.let { { Text(stringResource(it)) } },
                    keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.PROPER_NAME),
                    keyboardActions = KeyboardPolicy.getActions(focusManager),
                    singleLine = true,
                    enabled = !isCreating
                )
                OutlinedTextField(
                    value = newInfraEmergencyInstructions,
                    onValueChange = { newInfraEmergencyInstructions = it },
                    label = { Text(stringResource(R.string.emergency_instructions_label)) },
                    modifier = Modifier.bringIntoViewOnFocus(newInstController),
                    minLines = 2,
                    keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.MULTILINE_PROSE),
                    keyboardActions = KeyboardPolicy.getActions(focusManager),
                    enabled = !isCreating
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                    Checkbox(checked = newInfraIsEmergency, onCheckedChange = { newInfraIsEmergency = it }, enabled = !isCreating)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.emergency_item))
                }
                if (isCreating) { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (newInfraName.trim().isBlank()) {
                    nameErrorRes = R.string.error_name_required
                    newNameFocusRequester.requestFocus()
                } else if (newInfraCategory.trim().isBlank()) {
                    categoryErrorRes = R.string.error_category_required
                    newCatFocusRequester.requestFocus()
                } else {
                    onSave(
                        PendingSystemItemInput(
                            name = newInfraName.trim(),
                            category = newInfraCategory.trim(),
                            subtype = null,
                            isEmergencyItem = newInfraIsEmergency,
                            emergencyInstructions = newInfraEmergencyInstructions.trim()
                        )
                    )
                }
            }) { Text(stringResource(R.string.create_and_link)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun PhotosAndFilesSection(
    attachmentCount: Int, photoCount: Int, coverThumbnailUri: android.net.Uri?,
    isReadOnly: Boolean, onViewAll: () -> Unit, onTakePhoto: () -> Unit,
    onChoosePhoto: () -> Unit, onChooseDocument: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.recent_attachments), style = MaterialTheme.typography.titleSmall); if (attachmentCount > 0) { Surface(modifier = Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.primary, shape = CircleShape) { Text(text = attachmentCount.toString(), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary) } } }
                TextButton(onClick = onViewAll) { Text(stringResource(R.string.view_all)) }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (coverThumbnailUri != null) { Card(modifier = Modifier.size(80.dp), shape = RoundedCornerShape(8.dp)) { AsyncImage(model = coverThumbnailUri, contentDescription = "Cover photo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
                else if (photoCount > 0) { Box(modifier = Modifier.size(80.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.outline) } }
                Column(modifier = Modifier.weight(1f)) { if (!isReadOnly) { Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { IconButton(onClick = onTakePhoto) { Icon(Icons.Default.AddAPhoto, contentDescription = stringResource(R.string.take_photo)) }; IconButton(onClick = onChoosePhoto) { Icon(Icons.Default.Photo, contentDescription = stringResource(R.string.choose_photo)) }; IconButton(onClick = onChooseDocument) { Icon(Icons.Default.Description, contentDescription = stringResource(R.string.choose_document)) } } } else { Text(text = if (attachmentCount == 0) stringResource(R.string.no_attachments) else "$attachmentCount attachments", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) } }
            }
        }
    }
}
