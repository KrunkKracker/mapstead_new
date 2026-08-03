package com.jumastappworks.mapstead.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.ui.mapping.MapScreen
import com.jumastappworks.mapstead.ui.mapping.MapViewModel
import com.jumastappworks.mapstead.ui.emergency.EmergencyScreen
import com.jumastappworks.mapstead.ui.emergency.EmergencyViewModel
import com.jumastappworks.mapstead.ui.maintenance.MaintenanceScreen
import com.jumastappworks.mapstead.ui.maintenance.MaintenanceViewModel
import com.jumastappworks.mapstead.ui.maintenance.MaintenanceRecordEditor
import com.jumastappworks.mapstead.ui.maintenance.MaintenanceRecordDetails
import com.jumastappworks.mapstead.ui.maintenance.ReminderEditor
import com.jumastappworks.mapstead.ui.mapping.*
import com.jumastappworks.mapstead.ui.help.*
import com.jumastappworks.mapstead.data.help.HelpTopicId
import com.jumastappworks.mapstead.ui.settings.*
import com.jumastappworks.mapstead.ui.attachments.*
import com.jumastappworks.mapstead.ui.properties.PropertiesScreen
import com.jumastappworks.mapstead.ui.properties.PropertiesViewModel
import com.jumastappworks.mapstead.ui.properties.AddPropertyScreen
import com.jumastappworks.mapstead.ui.properties.AddPropertyViewModel
import com.jumastappworks.mapstead.ui.properties.EditPropertyScreen
import com.jumastappworks.mapstead.ui.properties.EditPropertyViewModel
import com.jumastappworks.mapstead.ui.dashboard.PropertyDashboardScreen
import com.jumastappworks.mapstead.ui.dashboard.PropertyDashboardViewModel
import com.jumastappworks.mapstead.ui.plans.PlansScreen
import com.jumastappworks.mapstead.ui.plans.PlansViewModel
import com.jumastappworks.mapstead.ui.plans.CreatePlanScreen
import com.jumastappworks.mapstead.data.db.entities.*
import com.jumastappworks.mapstead.data.help.*
import com.jumastappworks.mapstead.data.repository.*
import com.jumastappworks.mapstead.data.mapping.BasemapProvider
import com.jumastappworks.mapstead.data.prefs.UserPreferencesRepository
import com.jumastappworks.mapstead.ui.plans.CreatePlanViewModel
import com.jumastappworks.mapstead.ui.infrastructure.InfrastructureItemEditorScreen
import com.jumastappworks.mapstead.ui.infrastructure.InfrastructureItemEditorViewModel
import com.jumastappworks.mapstead.ui.infrastructure.InfrastructureItemDetailScreen
import com.jumastappworks.mapstead.ui.infrastructure.InfrastructureItemDetailViewModel
import com.jumastappworks.mapstead.ui.backup.BackupScreen
import com.jumastappworks.mapstead.ui.backup.BackupViewModel
import com.jumastappworks.mapstead.ui.relationships.*
import com.jumastappworks.mapstead.data.attachments.AttachmentNavigationOrigin
import com.jumastappworks.mapstead.util.UuidHelper
import com.jumastappworks.mapstead.util.rememberAdaptiveLayoutInfo
import kotlinx.coroutines.launch
import java.util.UUID

enum class MainDestination {
    Properties, Map, Maintenance, Emergency, Settings
}

enum class NavigationPresentation {
    CompactLabels,
    IconOnly,
    NavigationRail
}

fun navigationPresentation(
    widthDp: Int,
    fontScale: Float,
    useNavigationRail: Boolean
): NavigationPresentation {
    return when {
        useNavigationRail -> NavigationPresentation.NavigationRail
        widthDp < 360 -> NavigationPresentation.IconOnly
        widthDp < 600 && fontScale >= 1.3f -> NavigationPresentation.IconOnly
        else -> NavigationPresentation.CompactLabels
    }
}

data class MapsteadNavigationItem(
    val destination: MainDestination,
    val fullLabelRes: Int,
    val compactLabelRes: Int,
    val contentDescriptionRes: Int,
    val icon: ImageVector
)

val NavItems = listOf(
    MapsteadNavigationItem(MainDestination.Properties, R.string.nav_properties_full, R.string.nav_properties_compact, R.string.desc_open_properties, Icons.Default.Home),
    MapsteadNavigationItem(MainDestination.Map, R.string.nav_map_full, R.string.nav_map_compact, R.string.desc_open_map, Icons.Default.Map),
    MapsteadNavigationItem(MainDestination.Maintenance, R.string.nav_maintenance_full, R.string.nav_maintenance_compact, R.string.desc_open_maintenance, Icons.Default.Build),
    MapsteadNavigationItem(MainDestination.Emergency, R.string.nav_emergency_full, R.string.nav_emergency_compact, R.string.desc_open_emergency, Icons.Default.Warning),
    MapsteadNavigationItem(MainDestination.Settings, R.string.nav_settings_full, R.string.nav_settings_compact, R.string.desc_open_settings, Icons.Default.Settings)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MapsteadNavGraph(
    basemapProvider: BasemapProvider,
    userPreferencesRepository: UserPreferencesRepository,
    relationshipRepository: InfrastructureRelationshipRepository,
    propertyRepository: PropertyRepository,
    selectionManager: PropertySelectionManager,
    initialPropertyId: UUID? = null,
    initialItemId: UUID? = null,
    initialRecordId: UUID? = null,
    initialReminderId: UUID? = null,
    isBackupEnabled: Boolean
) {
    val mainScope = rememberCoroutineScope()
    val initialRoute = initialPropertyId?.let { Route.PropertyDashboard(it) } ?: Route.Properties
    val backStack = remember {
        val initialList = mutableStateListOf<Route>(initialRoute)
        if (initialPropertyId != null) {
            if (initialItemId != null) { 
                initialList.add(Route.InfrastructureItemDetails(initialPropertyId, initialItemId)) 
            }
            if (initialRecordId != null) {
                initialList.add(Route.MaintenanceRecordDetails(initialPropertyId, initialRecordId))
            }
            if (initialReminderId != null && initialRecordId == null) {
                initialList.add(Route.Maintenance(initialPropertyId))
            }
        }
        NavBackStack(initialList)
    }
    
    val selectionState by selectionManager.selectionState.collectAsState()
    val selectedPropId = (selectionState as? PropertySelectionState.Selected)?.selectedProperty?.id

    LaunchedEffect(selectedPropId) {
        if (selectedPropId == null) {
            val last = backStack.lastOrNull()
            if (last != null && last !is Route.Properties && last !is Route.AddProperty && last !is Route.Settings && 
                last !is Route.HelpCenter && last !is Route.HelpTopic && last !is Route.About && 
                last !is Route.Privacy && last !is Route.Orientation && last !is Route.GettingStarted) {
                // Return to properties if current route requires a selected property
                while (backStack.size > 1) {
                    backStack.removeAt(backStack.size - 1)
                }
                if (backStack.firstOrNull() != Route.Properties) {
                    backStack.clear()
                    backStack.add(Route.Properties)
                }
            }
        }
    }
    val layoutInfo = rememberAdaptiveLayoutInfo()
    val configuration = LocalConfiguration.current
    val fontScale = configuration.fontScale
    val screenWidthDp = configuration.screenWidthDp

    val presentation = navigationPresentation(
        widthDp = screenWidthDp,
        fontScale = fontScale,
        useNavigationRail = layoutInfo.useNavigationRail
    )
    val isIconOnly = presentation == NavigationPresentation.IconOnly
    val currentRoute = backStack.lastOrNull()

    val onNavItemClick: (MainDestination) -> Unit = { dest ->
        val route = when (dest) {
            MainDestination.Properties -> Route.Properties
            MainDestination.Map -> selectedPropId?.let { Route.Plans(it) }
            MainDestination.Maintenance -> selectedPropId?.let { Route.Maintenance(it) }
            MainDestination.Emergency -> selectedPropId?.let { Route.Emergency(it) }
            MainDestination.Settings -> Route.Settings
        }
        
        if (route != null) {
            if (currentRoute != route) {
                val existingIndex = backStack.indexOfFirst { it.matchesTopLevelRoot(dest, selectedPropId) }
                if (existingIndex != -1) {
                    while (backStack.size > existingIndex + 1) {
                        backStack.removeAt(backStack.size - 1)
                    }
                } else {
                    val staleIndex = backStack.indexOfFirst { it.topLevelDestination() == dest }
                    if (staleIndex != -1) {
                        while (backStack.size > staleIndex) {
                            backStack.removeAt(backStack.size - 1)
                        }
                    }
                    backStack.add(route)
                }
            }
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        if (layoutInfo.useNavigationRail) {
            MapsteadNavigationRail(
                currentRoute = currentRoute,
                selectedPropId = selectedPropId,
                onNavItemClick = onNavItemClick
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            Scaffold(
                bottomBar = {
                    if (layoutInfo.useBottomNavigation) {
                        MapsteadBottomBar(
                            currentRoute = currentRoute,
                            selectedPropId = selectedPropId,
                            isIconOnly = isIconOnly,
                            onNavItemClick = onNavItemClick
                        )
                    }
                }
            ) { innerPadding ->
                val entryProvider = entryProvider<Route> {
                    entry<Route.Properties> {
                        val vm: PropertiesViewModel = viewModel()
                        PropertiesScreen(
                            viewModel = vm,
                            onPropertyClick = { id ->
                                mainScope.launch { 
                                    userPreferencesRepository.updateSelectedProperty(id)
                                    backStack.add(Route.PropertyDashboard(UUID.fromString(id)))
                                }
                            },
                            onAddPropertyClick = { backStack.add(Route.AddProperty) },
                            onHelpClick = { topicId -> backStack.add(Route.HelpTopic(topicId)) }
                        )
                    }
                    entry<Route.PropertyDashboard> { key ->
                        val vm: PropertyDashboardViewModel = viewModel(); LaunchedEffect(key.propertyId) { vm.setPropertyId(key.propertyId) }
                        PropertyDashboardScreen(
                            viewModel = vm,
                            onBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) },
                            onNavigateToPlans = { backStack.add(Route.Plans(key.propertyId)) },
                            onNavigateToMaintenance = { backStack.add(Route.Maintenance(key.propertyId)) },
                            onNavigateToEmergency = { backStack.add(Route.Emergency(key.propertyId)) },
                            onNavigateToSettings = { backStack.add(Route.Settings) },
                            onEditPropertyClick = { backStack.add(Route.EditProperty(key.propertyId)) },
                            onAddLocationClick = { backStack.add(Route.AddPropertyLocation(key.propertyId)) },
                            onNavigateToInfrastructureList = { backStack.add(Route.InfrastructureList(key.propertyId)) },
                            onQuickAddMaintenance = { backStack.add(Route.MaintenanceRecordEditor(key.propertyId)) },
                            onNavigateToFiles = { backStack.add(Route.PropertyFiles(it)) },
                            onNavigateToRelationships = { backStack.add(Route.InfrastructureRelationships(it)) },
                            onNavigateToReports = { backStack.add(Route.PropertyReports(it)) },
                            onHelpClick = { topicId -> backStack.add(Route.HelpTopic(topicId)) }
                        )
                    }
                    entry<Route.PropertyReports> { key ->
                        val vm: com.jumastappworks.mapstead.ui.reports.PropertyReportViewModel = viewModel()
                        com.jumastappworks.mapstead.ui.reports.PropertyReportScreen(
                            propertyId = key.propertyId,
                            viewModel = vm,
                            onBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) },
                            onHelpClick = { topicId -> backStack.add(Route.HelpTopic(topicId)) }
                        )
                    }
                    entry<Route.AddProperty> {
                        val addVm: AddPropertyViewModel = viewModel()
                        AddPropertyScreen(
                            viewModel = addVm, 
                            basemapProvider = basemapProvider,
                            onBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) },
                            onFinish = { id ->
                                if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1)
                                mainScope.launch {
                                    userPreferencesRepository.updateSelectedProperty(id.toString())
                                    backStack.add(Route.PropertyDashboard(id))
                                }
                            }
                        )
                    }
                    entry<Route.EditProperty> { key ->
                        val editVm: EditPropertyViewModel = viewModel(); LaunchedEffect(key.propertyId) { editVm.loadProperty(key.propertyId) }
                        EditPropertyScreen(viewModel = editVm, onBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) })
                    }
                    entry<Route.AddPropertyLocation> { key ->
                        val addVm: AddPropertyViewModel = viewModel()
                        LaunchedEffect(key.propertyId) { addVm.loadExistingProperty(key.propertyId) }
                        AddPropertyScreen(
                            viewModel = addVm,
                            basemapProvider = basemapProvider,
                            onBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) },
                            onFinish = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) }
                        )
                    }
                    entry<Route.Plans> { key ->
                        val vm: PlansViewModel = viewModel(); LaunchedEffect(key.propertyId) { vm.setPropertyId(key.propertyId) }
                        PlansScreen(
                            viewModel = vm,
                            onBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) },
                            onPlanClick = { plan -> backStack.add(Route.MapEditor(key.propertyId, plan.id)) },
                            onCreatePlanClick = { backStack.add(Route.CreatePlan(key.propertyId)) },
                            onHelpClick = { topicId -> backStack.add(Route.HelpTopic(topicId)) }
                        )
                    }
                    entry<Route.CreatePlan> { key ->
                        val vm: CreatePlanViewModel = viewModel(); LaunchedEffect(key.propertyId) { vm.setPid(key.propertyId) }
                        CreatePlanScreen(viewModel = vm, onBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) }, onPlanCreated = { planId -> if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1); backStack.add(Route.MapEditor(key.propertyId, planId)) })
                    }
                    entry<Route.MapEditor> { key ->
                        val vm: MapViewModel = viewModel()
                        val openingToken = remember(key.propertyId, key.planId) { UUID.randomUUID().toString() }
                        LaunchedEffect(key.propertyId, key.planId, openingToken) { 
                            vm.openMapContext(key.propertyId, key.planId, openingToken) 
                        }
                        val attachmentsVm: MapFeatureAttachmentsViewModel = viewModel()
                        val state by vm.uiState.collectAsState()
                        val selectedFeatureId = state.selectedFeature?.id
                        
                        LaunchedEffect(selectedFeatureId) {
                            if (selectedFeatureId != null) {
                                attachmentsVm.loadAttachments(key.propertyId, key.planId, selectedFeatureId)
                            } else {
                                attachmentsVm.clear()
                            }
                        }

                        val attachmentsState by attachmentsVm.uiState.collectAsState()

                        MapScreen(
                            basemapProvider = basemapProvider,
                            userPreferencesRepository = userPreferencesRepository,
                            onEmergencyClick = { backStack.add(Route.Emergency(key.propertyId)) },
                            onBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) },
                            propertyId = key.propertyId,
                            planId = key.planId,
                            featureId = key.featureId,
                            viewModel = vm,
                            attachmentCount = (attachmentsState as? MapFeatureAttachmentsUiState.Ready)?.attachments?.size ?: 0,
                            photoCount = (attachmentsState as? MapFeatureAttachmentsUiState.Ready)?.photoCount ?: 0,
                            coverThumbnailUri = (attachmentsState as? MapFeatureAttachmentsUiState.Ready)?.coverAttachment?.previewUri,
                            onViewAttachments = { fid -> backStack.add(Route.FeatureAttachments(key.propertyId, key.planId, fid)) },
                            onNavigateToEditor = { pid, ot, oid, u, t, origin ->
                                backStack.add(Route.AttachmentEditor(pid, ot, oid, null, u, t, origin))
                            },
                            onHelpRequest = { topicId -> backStack.add(Route.HelpTopic(topicId)) }
                        )
                    }
                    entry<Route.FeatureAttachments> { key ->
                        val attachmentsVm: MapFeatureAttachmentsViewModel = viewModel()
                        FeatureAttachmentsScreen(
                            propertyId = key.propertyId,
                            planId = key.planId,
                            featureId = key.featureId,
                            viewModel = attachmentsVm,
                            onBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) },
                            onAttachmentClick = { aid -> backStack.add(Route.AttachmentDetails(key.propertyId, aid, AttachmentNavigationOrigin.MAP_FEATURE)) },
                            onNavigateToEditor = { pid, ot, oid, u, t, origin -> backStack.add(Route.AttachmentEditor(pid, ot, oid, null, u, t, origin)) },
                            onReturnToMap = { fid -> 
                                if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1)
                            },
                            onHelpClick = { topicId -> backStack.add(Route.HelpTopic(topicId)) }
                        )
                    }
                    entry<Route.InfrastructureList> { key ->
                        val vm: MapViewModel = viewModel(); LaunchedEffect(key.propertyId) { vm.setProperty(key.propertyId) }
                        com.jumastappworks.mapstead.ui.infrastructure.InfrastructureListScreen(
                            viewModel = vm,
                            onBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) },
                            onAddItemClick = { backStack.add(Route.InfrastructureItemEditor(key.propertyId, null)) },
                            onEditItemClick = { itemId -> backStack.add(Route.InfrastructureItemDetails(key.propertyId, itemId)) },
                            onHelpClick = { topicId -> backStack.add(Route.HelpTopic(topicId)) }
                        )
                    }
                    entry<Route.InfrastructureItemDetails> { key ->
                        val vm: InfrastructureItemDetailViewModel = viewModel()
                        InfrastructureItemDetailScreen(
                            propertyId = key.propertyId,
                            itemId = key.itemId,
                            viewModel = vm,
                            onNavigateBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) },
                            onEditClick = { pid, iid -> backStack.add(Route.InfrastructureItemEditor(pid, iid)) },
                            onShowOnMap = { pid: UUID, mid: UUID, fid: String -> backStack.add(Route.MapEditor(pid, mid, fid)) },
                            onAddMaintenance = { pid, iid -> backStack.add(Route.MaintenanceRecordEditor(pid, null, iid)) },
                            onViewMaintenance = { iid -> backStack.add(Route.Maintenance(key.propertyId)) }, // Simplified for now
                            onAddPhoto = { pid, iid -> backStack.add(Route.AttachmentEditor(pid, "INFRASTRUCTURE", iid, null, null, null, AttachmentNavigationOrigin.INFRASTRUCTURE)) },
                            onAddFile = { pid, iid -> backStack.add(Route.AttachmentEditor(pid, "INFRASTRUCTURE", iid, null, null, null, AttachmentNavigationOrigin.INFRASTRUCTURE)) },
                            onViewAllAttachments = { pid -> backStack.add(Route.PropertyFiles(pid)) },
                            onAttachmentClick = { pid, aid -> backStack.add(Route.AttachmentDetails(pid, aid, AttachmentNavigationOrigin.INFRASTRUCTURE)) },
                            onManageRelationships = { pid -> backStack.add(Route.InfrastructureRelationships(pid)) },
                            onOpenRelatedItem = { pid, iid -> backStack.add(Route.InfrastructureItemDetails(pid, iid)) }
                        )
                    }
                    entry<Route.InfrastructureItemEditor> { key ->
                        val vm: InfrastructureItemEditorViewModel = viewModel()
                        InfrastructureItemEditorScreen(
                            propertyId = key.propertyId,
                            itemId = key.itemId,
                            viewModel = vm,
                            onNavigateBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) },
                            onSaveSuccess = { iid ->
                                // Pop editor, add details for the item
                                if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1)
                                backStack.add(Route.InfrastructureItemDetails(key.propertyId, iid))
                            },
                            onHelpClick = { topicId -> backStack.add(Route.HelpTopic(topicId)) }
                        )
                    }
                    entry<Route.Emergency> { key ->
                        val emergencyVm: EmergencyViewModel = viewModel(); LaunchedEffect(key.propertyId) { emergencyVm.setPropertyId(key.propertyId) }
                        EmergencyScreen(
                            viewModel = emergencyVm,
                            onBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) },
                            onOpenMap = { planId, featureId -> backStack.add(Route.MapEditor(key.propertyId, planId, featureId.toString())) },
                            onEditItem = { itemId -> backStack.add(Route.InfrastructureItemDetails(key.propertyId, itemId)) },
                            onHelpClick = { topicId -> backStack.add(Route.HelpTopic(topicId)) }
                        )
                    }
                    entry<Route.Maintenance> { key ->
                        val maintenanceVm: MaintenanceViewModel = viewModel(); LaunchedEffect(key.propertyId) { maintenanceVm.setPropertyId(key.propertyId) }
                        MaintenanceScreen(
                            viewModel = maintenanceVm,
                            onBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) },
                            onAddRecord = { backStack.add(Route.MaintenanceRecordEditor(key.propertyId)) },
                            onOpenRecord = { propId, recordId -> backStack.add(Route.MaintenanceRecordDetails(propId, recordId)) },
                            onHelpClick = { topicId -> backStack.add(Route.HelpTopic(topicId)) }
                        )
                    }
                    entry<Route.MaintenanceRecordDetails> { key ->
                        val vm: MaintenanceViewModel = viewModel(); LaunchedEffect(key.propertyId) { vm.setPropertyId(key.propertyId) }
                        MaintenanceRecordDetails(
                            propertyId = key.propertyId,
                            recordId = key.recordId,
                            viewModel = vm,
                            onBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) },
                            onEdit = { propId, recordId -> backStack.add(Route.MaintenanceRecordEditor(propId, recordId)) },
                            onOpenInfrastructure = { propId, itemId -> backStack.add(Route.InfrastructureItemDetails(propId, itemId)) },
                            onAddReminder = { propId, recordId, itemId -> backStack.add(Route.ReminderEditor(propId, null, recordId, itemId)) },
                            onEditReminder = { propId, rid, recordId, iid -> backStack.add(Route.ReminderEditor(propId, rid, recordId, iid)) },
                            onOpenOnMap = { propId, planId, featId -> backStack.add(Route.MapEditor(propId, planId, featId)) },
                            onOpenRecord = { propId, recordId -> backStack.add(Route.MaintenanceRecordDetails(propId, recordId)) },
                            onAttachmentClick = { pid, aid -> backStack.add(Route.AttachmentDetails(pid, aid, AttachmentNavigationOrigin.MAINTENANCE)) },
                            onViewAllAttachments = { pid, _ -> backStack.add(Route.PropertyFiles(pid)) },
                            onNavigateToEditor = { pid, ot, oid, u, t -> backStack.add(Route.AttachmentEditor(pid, ot, oid, null, u, t, AttachmentNavigationOrigin.MAINTENANCE)) },
                            onHelpClick = { topicId -> backStack.add(Route.HelpTopic(topicId)) }
                        )
                    }
                    entry<Route.MaintenanceRecordEditor> { key ->
                        val vm: MaintenanceViewModel = viewModel(); LaunchedEffect(key.propertyId) { vm.setPropertyId(key.propertyId) }
                        MaintenanceRecordEditor(
                            propertyId = key.propertyId,
                            recordId = key.recordId,
                            infrastructureItemId = key.infrastructureItemId,
                            viewModel = vm,
                            onNavigateBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) }
                        )
                    }
                    entry<Route.ReminderEditor> { key ->
                        val vm: MaintenanceViewModel = viewModel(); LaunchedEffect(key.propertyId) { vm.setPropertyId(key.propertyId) }
                        ReminderEditor(
                            propertyId = key.propertyId,
                            reminderId = key.reminderId,
                            maintenanceRecordId = key.maintenanceRecordId,
                            infrastructureItemId = key.infrastructureItemId,
                            viewModel = vm,
                            onNavigateBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) }
                        )
                    }
                    entry<Route.PropertyFiles> { key ->
                        val vm: PropertyFilesViewModel = viewModel()
                        PropertyFilesScreen(
                            propertyId = key.propertyId,
                            viewModel = vm,
                            onNavigateBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) },
                            onAttachmentClick = { backStack.add(Route.AttachmentDetails(key.propertyId, it, AttachmentNavigationOrigin.PROPERTY_FILES)) },
                            onNavigateToEditor = { pid: UUID, ot: String, oid: UUID?, u: String, t: String? -> backStack.add(Route.AttachmentEditor(pid, ot, oid, null, u, t, AttachmentNavigationOrigin.PROPERTY_FILES)) },
                            onHelpClick = { topicId -> backStack.add(Route.HelpTopic(topicId)) }
                        )
                    }
                    entry<Route.AttachmentEditor> { key ->
                        val vm: AttachmentEditorViewModel = viewModel()
                        AttachmentEditorScreen(
                            propertyId = key.propertyId,
                            ownerType = key.ownerType,
                            ownerId = key.ownerId,
                            attachmentId = key.attachmentId,
                            stagedFileUri = key.stagedFileUri,
                            cameraCaptureToken = key.cameraCaptureToken,
                            viewModel = vm,
                            onNavigateBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) }
                        )
                    }
                    entry<Route.AttachmentDetails> { key ->
                        val vm: AttachmentDetailsViewModel = viewModel()
                        AttachmentDetailsScreen(
                            propertyId = key.propertyId,
                            attachmentId = key.attachmentId,
                            viewModel = vm,
                            onNavigateBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) },
                            onEditMetadata = { backStack.add(Route.AttachmentEditor(key.propertyId, "PROPERTY", null, it, null, null, key.navigationOrigin)) },
                            onOpenOwner = { 
                                val state = vm.uiState.value
                                if (state is AttachmentDetailsUiState.Ready) {
                                    when (val dest = state.ownerDestination) {
                                        is AttachmentOwnerDestination.Property -> backStack.add(Route.PropertyDashboard(dest.propertyId))
                                        is AttachmentOwnerDestination.InfrastructureItem -> backStack.add(Route.InfrastructureItemDetails(dest.propertyId, dest.itemId))
                                        is AttachmentOwnerDestination.MaintenanceRecord -> backStack.add(Route.MaintenanceRecordDetails(dest.propertyId, dest.recordId))
                                        is AttachmentOwnerDestination.MapFeature -> backStack.add(Route.MapEditor(dest.propertyId, dest.planId, dest.featureId.toString()))
                                    }
                                }
                            },
                            onDeleted = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) }
                        )
                    }
                    entry<Route.InfrastructureRelationships> { key ->
                        val vm: InfrastructureRelationshipsViewModel = viewModel()
                        InfrastructureRelationshipsScreen(
                            propertyId = key.propertyId,
                            viewModel = vm,
                            onNavigateBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) },
                            onOpenItem = { iid -> backStack.add(Route.InfrastructureItemDetails(key.propertyId, iid)) },
                            onAddRelationship = { iid -> backStack.add(Route.InfrastructureRelationshipEditor(key.propertyId, iid, null)) },
                            onSetParent = { iid -> backStack.add(Route.InfrastructureParentEditor(key.propertyId, iid)) },
                            onHelpClick = { topicId -> backStack.add(Route.HelpTopic(topicId)) }
                        )
                    }
                    entry<Route.InfrastructureRelationshipEditor> { key ->
                        val vm: RelationshipEditorViewModel = viewModel()
                        RelationshipEditorScreen(
                            propertyId = key.propertyId,
                            currentItemId = key.currentItemId,
                            relationshipId = key.relationshipId,
                            viewModel = vm,
                            onNavigateBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) },
                            onHelpClick = { topicId -> backStack.add(Route.HelpTopic(topicId)) }
                        )
                    }
                    entry<Route.InfrastructureParentEditor> { key ->
                        val vm: ParentEditorViewModel = viewModel()
                        ParentEditorScreen(
                            propertyId = key.propertyId,
                            itemId = key.itemId,
                            viewModel = vm,
                            onNavigateBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) },
                            onHelpClick = { topicId -> backStack.add(Route.HelpTopic(topicId)) }
                        )
                    }
                    entry<Route.HelpCenter> {
                        HelpCenterScreen(
                            onTopicClick = { topicId -> backStack.add(Route.HelpTopic(topicId)) },
                            onBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) }
                        )
                    }
                    entry<Route.HelpTopic> { key ->
                        HelpTopicScreen(
                            topicId = key.topicId,
                            onBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) }
                        )
                    }
                    entry<Route.GettingStarted> {
                        val vm: GettingStartedViewModel = viewModel()
                        GettingStartedScreen(
                            viewModel = vm,
                            onBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) },
                            onNavigateToCreateProperty = { backStack.add(Route.AddProperty) },
                            onNavigateToCreateMap = { pid -> backStack.add(Route.CreatePlan(pid)) },
                            onNavigateToMap = { pid, mid -> backStack.add(Route.MapEditor(pid, mid, null)) },
                            onNavigateToPlans = { pid -> backStack.add(Route.Plans(pid)) },
                            onNavigateToAddInfrastructure = { pid -> backStack.add(Route.InfrastructureItemEditor(pid, null)) },
                            onNavigateToAddMaintenance = { pid -> backStack.add(Route.MaintenanceRecordEditor(pid, null, null)) },
                            onNavigateToFiles = { pid -> backStack.add(Route.PropertyFiles(pid)) },
                            onNavigateToEmergency = { pid -> backStack.add(Route.Emergency(pid)) },
                            onHelpClick = { backStack.add(Route.HelpTopic(HelpTopicId.GETTING_STARTED)) }
                        )
                    }
                    entry<Route.Privacy> {
                        PrivacyScreen(onBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) })
                    }
                    entry<Route.About> {
                        AboutScreen(
                            onBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) },
                            onNavigateToPrivacy = { backStack.add(Route.Privacy) },
                            onNavigateToSafety = { backStack.add(Route.HelpTopic(HelpTopicId.SAFETY_AND_LIMITATIONS)) }
                        )
                    }
                    entry<Route.Orientation> {
                        OrientationScreen(
                            onBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) },
                            onCreateProperty = { backStack.add(Route.AddProperty) },
                            onExploreHelp = { backStack.add(Route.HelpCenter) }
                        )
                    }
                    entry<Route.Backup> {
                        val resolution = resolveBackupDestination(isBackupEnabled)
                        if (resolution == BackupRouteResolution.REDIRECT_TO_SETTINGS) {
                            LaunchedEffect(Unit) {
                                val sanitized = sanitizeDisabledBackupRoute(backStack)
                                backStack.clear()
                                backStack.addAll(sanitized)
                            }
                        } else {
                            val backupVm: BackupViewModel = viewModel()
                            BackupScreen(
                                viewModel = backupVm,
                                onBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) },
                                onHelpClick = { topicId -> backStack.add(Route.HelpTopic(topicId)) }
                            )
                        }
                    }
                    entry<Route.Settings> {
                        SettingsScreen(
                            userPreferencesRepository = userPreferencesRepository,
                            isBackupEnabled = isBackupEnabled,
                            onNavigateToHelp = { backStack.add(Route.HelpCenter) },
                            onNavigateToHelpTopic = { topicId -> backStack.add(Route.HelpTopic(topicId)) },
                            onNavigateToBackup = { backStack.add(Route.Backup) },
                            onNavigateToAbout = { backStack.add(Route.About) },
                            onNavigateToPrivacy = { backStack.add(Route.Privacy) },
                            onNavigateToSafety = { backStack.add(Route.HelpTopic(HelpTopicId.SAFETY_AND_LIMITATIONS)) },
                            onNavigateToGettingStarted = { backStack.add(Route.GettingStarted) },
                            onNavigateToOrientation = { backStack.add(Route.Orientation) }
                        )
                    }
                }
                val contentModifier = if (currentRoute is Route.MapEditor) {
                    Modifier.padding(bottom = innerPadding.calculateBottomPadding())
                } else {
                    Modifier.padding(innerPadding)
                }

                NavDisplay(
                    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
                    backStack = backStack, entryProvider = entryProvider, modifier = contentModifier,
                    onBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) }
                )
            }
        }
    }
}

fun availableMainDestinations(hasValidProperty: Boolean): List<MainDestination> {
    return if (hasValidProperty) {
        listOf(
            MainDestination.Properties,
            MainDestination.Map,
            MainDestination.Maintenance,
            MainDestination.Emergency,
            MainDestination.Settings
        )
    } else {
        listOf(
            MainDestination.Properties,
            MainDestination.Settings
        )
    }
}

@Composable
fun MapsteadBottomBar(
    currentRoute: Route?,
    selectedPropId: UUID?,
    isIconOnly: Boolean,
    onNavItemClick: (MainDestination) -> Unit
) {
    val destinations = availableMainDestinations(selectedPropId != null)
    NavigationBar {
        NavItems.filter { it.destination in destinations }.forEach { item ->
            val isSelected = currentRoute?.topLevelDestination() == item.destination

            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavItemClick(item.destination) },
                modifier = Modifier.testTag("NavTab_${item.destination.name}"),
                icon = { Icon(item.icon, contentDescription = stringResource(item.contentDescriptionRes)) },
                label = if (isIconOnly) null else {
                    {
                        Text(
                            text = stringResource(item.compactLabelRes),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun MapsteadNavigationRail(
    currentRoute: Route?,
    selectedPropId: UUID?,
    onNavItemClick: (MainDestination) -> Unit
) {
    val destinations = availableMainDestinations(selectedPropId != null)
    NavigationRail(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
    ) {
        Spacer(Modifier.height(16.dp))
        NavItems.filter { it.destination in destinations }.forEach { item ->
            val isSelected = currentRoute?.topLevelDestination() == item.destination

            NavigationRailItem(
                selected = isSelected,
                onClick = { onNavItemClick(item.destination) },
                modifier = Modifier.testTag("NavTab_${item.destination.name}"),
                icon = { Icon(item.icon, contentDescription = stringResource(item.contentDescriptionRes)) },
                label = { Text(stringResource(item.fullLabelRes)) }
            )
        }
    }
}

fun Route.topLevelDestination(): MainDestination = when (this) {
    Route.Properties, Route.AddProperty, is Route.EditProperty, is Route.AddPropertyLocation,
    is Route.PropertyDashboard,
    is Route.InfrastructureList, is Route.InfrastructureItemDetails, is Route.InfrastructureItemEditor, is Route.InfrastructureRelationships,
    is Route.InfrastructureRelationshipEditor, is Route.InfrastructureParentEditor, is Route.PropertyReports -> MainDestination.Properties
    is Route.Plans, is Route.CreatePlan, is Route.MapEditor, is Route.FeatureAttachments -> MainDestination.Map
    is Route.Maintenance, is Route.MaintenanceRecordDetails, is Route.MaintenanceRecordEditor, is Route.ReminderEditor -> MainDestination.Maintenance
    is Route.Emergency -> MainDestination.Emergency
    is Route.AttachmentEditor -> {
        when (this.navigationOrigin) {
            AttachmentNavigationOrigin.MAP_FEATURE -> MainDestination.Map
            AttachmentNavigationOrigin.INFRASTRUCTURE -> MainDestination.Properties
            AttachmentNavigationOrigin.MAINTENANCE -> MainDestination.Maintenance
            AttachmentNavigationOrigin.PROPERTY_FILES -> MainDestination.Settings
        }
    }
    is Route.AttachmentDetails -> {
        when (this.navigationOrigin) {
            AttachmentNavigationOrigin.MAP_FEATURE -> MainDestination.Map
            AttachmentNavigationOrigin.INFRASTRUCTURE -> MainDestination.Properties
            AttachmentNavigationOrigin.MAINTENANCE -> MainDestination.Maintenance
            AttachmentNavigationOrigin.PROPERTY_FILES -> MainDestination.Settings
        }
    }
    Route.Settings, Route.Backup, is Route.PropertyFiles, Route.HelpCenter, is Route.HelpTopic, Route.GettingStarted, Route.Orientation, Route.Privacy, Route.About -> MainDestination.Settings
}

fun Route.matchesTopLevelRoot(destination: MainDestination, selectedPropertyId: UUID?): Boolean {
    return when (destination) {
        MainDestination.Properties -> {
            this == Route.Properties || (this is Route.PropertyDashboard && this.propertyId == selectedPropertyId) || (this is Route.AddPropertyLocation && this.propertyId == selectedPropertyId) || (this is Route.InfrastructureList && this.propertyId == selectedPropertyId) || (this is Route.InfrastructureRelationships && this.propertyId == selectedPropertyId) || (this is Route.InfrastructureItemDetails && this.propertyId == selectedPropertyId) || (this is Route.InfrastructureItemEditor && this.propertyId == selectedPropertyId) || (this is Route.InfrastructureParentEditor && this.propertyId == selectedPropertyId) || (this is Route.InfrastructureRelationshipEditor && this.propertyId == selectedPropertyId)
        }
        MainDestination.Map -> (this is Route.Plans && this.propertyId == selectedPropertyId) || (this is Route.MapEditor && this.propertyId == selectedPropertyId) || (this is Route.FeatureAttachments && this.propertyId == selectedPropertyId)
        MainDestination.Maintenance -> (this is Route.Maintenance || this is Route.MaintenanceRecordDetails || this is Route.MaintenanceRecordEditor || this is Route.ReminderEditor) && ((this as? Route.Maintenance)?.propertyId == selectedPropertyId || (this as? Route.MaintenanceRecordDetails)?.propertyId == selectedPropertyId || (this as? Route.MaintenanceRecordEditor)?.propertyId == selectedPropertyId || (this as? Route.ReminderEditor)?.propertyId == selectedPropertyId)
        MainDestination.Emergency -> this is Route.Emergency && this.propertyId == selectedPropertyId
        MainDestination.Settings -> this == Route.Settings || (this is Route.PropertyFiles && this.propertyId == selectedPropertyId) || (this is Route.Backup) || this == Route.HelpCenter || this is Route.HelpTopic || this == Route.GettingStarted || this == Route.Privacy || this == Route.About || this == Route.Orientation
    }
}

enum class BackupRouteResolution {
    ALLOW_BACKUP,
    REDIRECT_TO_SETTINGS
}

fun resolveBackupDestination(isBackupEnabled: Boolean): BackupRouteResolution {
    return if (isBackupEnabled) {
        BackupRouteResolution.ALLOW_BACKUP
    } else {
        BackupRouteResolution.REDIRECT_TO_SETTINGS
    }
}

fun sanitizeDisabledBackupRoute(routes: List<Route>): List<Route> {
    val filtered = routes.filter { it !is Route.Backup }
    val clean = filtered.filter { it !is Route.Settings }
    return clean + Route.Settings
}
