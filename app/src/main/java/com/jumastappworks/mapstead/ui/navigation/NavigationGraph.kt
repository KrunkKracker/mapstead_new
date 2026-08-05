package com.jumastappworks.mapstead.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.jumastappworks.mapstead.ui.dashboard.HomeScreen
import com.jumastappworks.mapstead.ui.dashboard.HomeViewModel
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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

enum class MainDestination {
    Home, Map, Items, Tasks
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
    MapsteadNavigationItem(MainDestination.Home, R.string.nav_home_full, R.string.nav_home_compact, R.string.desc_open_home, Icons.Default.Home),
    MapsteadNavigationItem(MainDestination.Map, R.string.nav_map_full, R.string.nav_map_compact, R.string.desc_open_map, Icons.Default.Map),
    MapsteadNavigationItem(MainDestination.Items, R.string.nav_items_full, R.string.nav_items_compact, R.string.desc_open_items, Icons.AutoMirrored.Filled.List),
    MapsteadNavigationItem(MainDestination.Tasks, R.string.nav_tasks_full, R.string.nav_tasks_compact, R.string.desc_open_tasks, Icons.Default.Build)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MapsteadNavGraph(
    basemapProvider: BasemapProvider,
    userPreferencesRepository: UserPreferencesRepository,
    relationshipRepository: InfrastructureRelationshipRepository,
    propertyRepository: PropertyRepository,
    selectionManager: PropertySelectionManager,
    mapRepository: MapRepository,
    initialPropertyId: UUID? = null,
    initialItemId: UUID? = null,
    initialRecordId: UUID? = null,
    initialReminderId: UUID? = null,
    isBackupEnabled: Boolean
) {
    val mainScope = rememberCoroutineScope()
    
    val selectionState by selectionManager.selectionState.collectAsState()
    
    val backStack = rememberNavBackStack(Route.Home)
    
    val selectedPropId = (selectionState as? PropertySelectionState.Selected)?.selectedProperty?.id

    // Startup and selection synchronization
    LaunchedEffect(selectionState) {
        val state = selectionState
        if (state is PropertySelectionState.NoProperties) {
            backStack.clear()
            backStack.add(Route.Home)
        } else if (state is PropertySelectionState.Selected) {
            // Check if we need to apply initial deep links
            if (backStack.size == 1 && backStack.first() == Route.Home) {
                if (initialPropertyId == state.selectedProperty.id) {
                    if (initialItemId != null) {
                        backStack.add(Route.InfrastructureItemDetails(initialPropertyId, initialItemId))
                    }
                    if (initialRecordId != null) {
                        backStack.add(Route.MaintenanceRecordDetails(initialPropertyId, initialRecordId))
                    }
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
    val currentRoute = backStack.lastOrNull() as? Route

    val onNavItemClick: (MainDestination) -> Unit = { dest ->
        val route: Route = when (dest) {
            MainDestination.Home -> Route.Home
            MainDestination.Map -> Route.MapRoot()
            MainDestination.Items -> Route.ItemsRoot
            MainDestination.Tasks -> Route.TasksRoot
        }
        
        if (currentRoute != route) {
            val existingIndex = backStack.indexOfFirst { (it as? Route)?.matchesTopLevelRoot(dest, selectedPropId) == true }
            if (existingIndex != -1) {
                while (backStack.size > existingIndex + 1) {
                    backStack.removeAt(backStack.size - 1)
                }
            } else {
                val staleIndex = backStack.indexOfFirst { (it as? Route)?.topLevelDestination() == dest }
                if (staleIndex != -1) {
                    while (backStack.size > staleIndex) {
                        backStack.removeAt(backStack.size - 1)
                    }
                }
                backStack.add(route)
            }
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        if (layoutInfo.useNavigationRail && selectionState is PropertySelectionState.Selected) {
            MapsteadNavigationRail(
                currentRoute = currentRoute,
                selectedPropId = selectedPropId,
                onNavItemClick = onNavItemClick
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            Scaffold(
                bottomBar = {
                    if (layoutInfo.useBottomNavigation && selectionState is PropertySelectionState.Selected) {
                        MapsteadBottomBar(
                            currentRoute = currentRoute,
                            selectedPropId = selectedPropId,
                            isIconOnly = isIconOnly,
                            onNavItemClick = onNavItemClick
                        )
                    }
                }
            ) { innerPadding ->
                val entryProvider = entryProvider<androidx.navigation3.runtime.NavKey> {
                    entry<Route.Home> {
                        when (val s = selectionState) {
                            PropertySelectionState.Loading -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                            PropertySelectionState.NoProperties -> {
                                WelcomeScreen(
                                    onCreateProperty = { backStack.add(Route.AddProperty) }
                                )
                            }
                            is PropertySelectionState.NeedsSelection -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(stringResource(R.string.select_property), style = MaterialTheme.typography.titleLarge)
                                        Spacer(Modifier.height(16.dp))
                                        s.activeProperties.forEach { p ->
                                            ListItem(
                                                headlineContent = { Text(p.name) },
                                                modifier = Modifier.clickable { mainScope.launch { selectionManager.selectProperty(p.id) } }
                                            )
                                        }
                                        TextButton(onClick = { backStack.add(Route.AddProperty) }) {
                                            Text(stringResource(R.string.add_property))
                                        }
                                    }
                                }
                            }
                            is PropertySelectionState.Selected -> {
                                val vm: HomeViewModel = viewModel(); LaunchedEffect(s.selectedProperty.id) { vm.setPropertyId(s.selectedProperty.id) }
                                HomeScreen(
                                    viewModel = vm,
                                    properties = s.allActiveProperties,
                                    selectedPropertyId = s.selectedProperty.id,
                                    onSelectProperty = { mainScope.launch { selectionManager.selectProperty(it) } },
                                    onAddProperty = { backStack.add(Route.AddProperty) },
                                    onManageProperties = { backStack.add(Route.Properties) },
                                    onNavigateToSettings = { backStack.add(Route.Settings) },
                                    onNavigateToHelp = { topicId -> backStack.add(Route.HelpTopic(topicId)) },
                                    onAddSomething = { 
                                        backStack.add(Route.MapRoot(launchAddSomething = true))
                                    },
                                    onFindSomething = { onNavItemClick(MainDestination.Items) },
                                    onOpenEmergency = { backStack.add(Route.Emergency(s.selectedProperty.id)) },
                                    onOpenTasks = { onNavItemClick(MainDestination.Tasks) },
                                    onOpenItemDetails = { iid -> backStack.add(Route.InfrastructureItemDetails(s.selectedProperty.id, iid)) },
                                    onEditProperty = { pid -> backStack.add(Route.EditProperty(pid)) }
                                )
                            }
                        }
                    }
                    entry<Route.MapRoot> { key ->
                        val propId = selectedPropId
                        if (propId == null) {
                            LaunchedEffect(Unit) { backStack.add(Route.Home) }
                        } else {
                            val plansFlow = remember(propId) { mapRepository.getPlansForProperty(propId) }.collectAsState(emptyList())
                            val propertyFlow = remember(propId) { flow { emit(propertyRepository.getPropertyById(propId)) } }.collectAsState(null)
                            
                            val plans = plansFlow.value
                            val property = propertyFlow.value
                            
                            if (property != null) {
                                val hasLocation = property.latitude != null && property.longitude != null
                                if (plans.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        if (hasLocation) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("This property has a location, but no map yet.")
                                                Button(onClick = { backStack.add(Route.CreatePlan(propId)) }) {
                                                    Text(stringResource(R.string.setup_checklist_create_map))
                                                }
                                            }
                                        } else {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(stringResource(R.string.setup_add_location_cta))
                                                Button(onClick = { backStack.add(Route.AddPropertyLocation(propId)) }) {
                                                    Text(stringResource(R.string.setup_checklist_add_location))
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    val defaultPlan = plans.minByOrNull { it.displayOrder ?: 0 } ?: plans.firstOrNull()
                                    if (defaultPlan != null) {
                                        LaunchedEffect(defaultPlan.id) {
                                            backStack.removeAt(backStack.size - 1)
                                            backStack.add(Route.MapEditor(propId, defaultPlan.id, launchAddSomething = key.launchAddSomething))
                                        }
                                    }
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                    entry<Route.ItemsRoot> {
                        val propId = selectedPropId
                        if (propId == null) {
                            LaunchedEffect(Unit) { backStack.add(Route.Home) }
                        } else {
                            val vm: MapViewModel = viewModel(); LaunchedEffect(propId) { vm.setProperty(propId) }
                            com.jumastappworks.mapstead.ui.infrastructure.InfrastructureListScreen(
                                viewModel = vm,
                                onBack = { /* Root mode, no back */ },
                                onAddItemClick = { backStack.add(Route.InfrastructureItemEditor(propId, null)) },
                                onEditItemClick = { itemId -> backStack.add(Route.InfrastructureItemDetails(propId, itemId)) },
                                onHelpClick = { topicId -> backStack.add(Route.HelpTopic(topicId)) },
                                isRoot = true
                            )
                        }
                    }
                    entry<Route.TasksRoot> {
                        val propId = selectedPropId
                        if (propId == null) {
                            LaunchedEffect(Unit) { backStack.add(Route.Home) }
                        } else {
                            val maintenanceVm: MaintenanceViewModel = viewModel()
                            LaunchedEffect(propId) { 
                                maintenanceVm.setPropertyId(propId)
                                maintenanceVm.setInfrastructureFilter(null)
                            }
                            MaintenanceScreen(
                                viewModel = maintenanceVm,
                                onBack = { /* Root mode, no back */ },
                                onAddRecord = { activeInfrastructureItemId -> 
                                    backStack.add(Route.MaintenanceRecordEditor(propId, null, activeInfrastructureItemId)) 
                                },
                                onOpenRecord = { propIdParam, recordId -> backStack.add(Route.MaintenanceRecordDetails(propIdParam, recordId)) },
                                onHelpClick = { topicId -> backStack.add(Route.HelpTopic(topicId)) },
                                isRoot = true
                            )
                        }
                    }
                    entry<Route.Properties> {
                        val vm: PropertiesViewModel = viewModel()
                        PropertiesScreen(
                            viewModel = vm,
                            onPropertyClick = { id ->
                                mainScope.launch { 
                                    selectionManager.selectProperty(UUID.fromString(id))
                                    backStack.add(Route.Home)
                                }
                            },
                            onAddPropertyClick = { backStack.add(Route.AddProperty) },
                            onHelpClick = { topicId -> backStack.add(Route.HelpTopic(topicId)) }
                        )
                    }
                    entry<Route.PropertyDashboard> { key ->
                        LaunchedEffect(key.propertyId) {
                            selectionManager.selectProperty(key.propertyId)
                            backStack.clear()
                            backStack.add(Route.Home)
                        }
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
                                    selectionManager.selectProperty(id)
                                    backStack.add(Route.Home)
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
                        val openingToken = rememberSaveable(key.propertyId, key.planId) { UUID.randomUUID().toString() }
                        LaunchedEffect(key.propertyId, key.planId, openingToken, key.launchAddSomething) { 
                            vm.openMapContext(key.propertyId, key.planId, openingToken, key.launchAddSomething) 
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
                            navigateToAttachmentDetails = { pid, aid -> backStack.add(Route.AttachmentDetails(pid, aid, AttachmentNavigationOrigin.MAP_FEATURE)) },
                            navigateToInfrastructureDetails = { pid, iid -> 
                                @Suppress("UNCHECKED_CAST")
                                addInfrastructureDetailsUnlessTop(backStack as MutableList<Route>, pid, iid)
                            },
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
                            onShowOnMap = { pid: UUID, mid: UUID, fid: String -> 
                                @Suppress("UNCHECKED_CAST")
                                openOrReturnToMapFeature(backStack as MutableList<Route>, pid, mid, fid)
                            },
                            onAddMaintenance = { pid, iid -> backStack.add(Route.MaintenanceRecordEditor(pid, null, iid)) },
                            onViewMaintenance = { iid -> backStack.add(Route.Maintenance(key.propertyId, iid)) },
                            onNavigateToEditor = { pid, ot, oid, u, t, origin ->
                                backStack.add(Route.AttachmentEditor(pid, ot, oid, null, u, t, origin))
                            },
                            onViewAllAttachments = { pid -> backStack.add(Route.InfrastructureAttachments(pid, key.itemId)) },
                            onAttachmentClick = { pid, aid -> backStack.add(Route.AttachmentDetails(pid, aid, AttachmentNavigationOrigin.INFRASTRUCTURE)) },
                            onManageRelationships = { pid -> backStack.add(Route.InfrastructureRelationships(pid)) },
                            onOpenRelatedItem = { pid, iid -> 
                                @Suppress("UNCHECKED_CAST")
                                addInfrastructureDetailsUnlessTop(backStack as MutableList<Route>, pid, iid)
                            }
                        )
                    }
                    entry<Route.InfrastructureAttachments> { key ->
                        val vm: com.jumastappworks.mapstead.ui.infrastructure.InfrastructureAttachmentsViewModel = viewModel()
                        com.jumastappworks.mapstead.ui.infrastructure.InfrastructureAttachmentsScreen(
                            propertyId = key.propertyId,
                            itemId = key.itemId,
                            viewModel = vm,
                            onBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) },
                            onAttachmentClick = { aid -> backStack.add(Route.AttachmentDetails(key.propertyId, aid, AttachmentNavigationOrigin.INFRASTRUCTURE)) },
                            onNavigateToEditor = { pid, ot, oid, u, t, origin -> backStack.add(Route.AttachmentEditor(pid, ot, oid, null, u, t, origin)) },
                            onHelpClick = { topicId -> backStack.add(Route.HelpTopic(topicId)) }
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
                                @Suppress("UNCHECKED_CAST")
                                handleInfrastructureSave(
                                    savedItemId = iid,
                                    wasEditing = key.itemId != null,
                                    propertyId = key.propertyId,
                                    backStack = backStack as MutableList<Route>
                                )
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
                        val maintenanceVm: MaintenanceViewModel = viewModel()
                        LaunchedEffect(key.propertyId, key.infrastructureItemId) { 
                            maintenanceVm.setPropertyId(key.propertyId)
                            maintenanceVm.setInfrastructureFilter(key.infrastructureItemId)
                        }
                        MaintenanceScreen(
                            viewModel = maintenanceVm,
                            onBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) },
                            onAddRecord = { activeInfrastructureItemId -> 
                                backStack.add(Route.MaintenanceRecordEditor(key.propertyId, null, activeInfrastructureItemId)) 
                            },
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
                            onOpenInfrastructure = { propId, itemId -> 
                                @Suppress("UNCHECKED_CAST")
                                openOrReturnToInfrastructureOwner(backStack as MutableList<Route>, propId, itemId)
                            },
                            onAddReminder = { propId, recordId, itemId -> backStack.add(Route.ReminderEditor(propId, null, recordId, itemId)) },
                            onEditReminder = { propId, rid, recordId, iid -> backStack.add(Route.ReminderEditor(propId, rid, recordId, iid)) },
                            onOpenOnMap = { propId, planId, featId -> 
                                @Suppress("UNCHECKED_CAST")
                                openOrReturnToMapFeature(backStack as MutableList<Route>, propId, planId, featId)
                            },
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
                                        is AttachmentOwnerDestination.Property -> {
                                            mainScope.launch {
                                                selectionManager.selectProperty(dest.propertyId)
                                                backStack.clear()
                                                backStack.add(Route.Home)
                                            }
                                        }
                                        is AttachmentOwnerDestination.InfrastructureItem -> {
                                            @Suppress("UNCHECKED_CAST")
                                            openOrReturnToInfrastructureOwner(backStack as MutableList<Route>, dest.propertyId, dest.itemId)
                                        }
                                        is AttachmentOwnerDestination.MaintenanceRecord -> backStack.add(Route.MaintenanceRecordDetails(dest.propertyId, dest.recordId))
                                        is AttachmentOwnerDestination.MapFeature -> {
                                            @Suppress("UNCHECKED_CAST")
                                            openOrReturnToMapFeature(backStack as MutableList<Route>, dest.propertyId, dest.planId, dest.featureId.toString())
                                        }
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
                            onOpenItem = { iid -> 
                                @Suppress("UNCHECKED_CAST")
                                addInfrastructureDetailsUnlessTop(backStack as MutableList<Route>, key.propertyId, iid)
                            },
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
                                @Suppress("UNCHECKED_CAST")
                                val sanitized = sanitizeDisabledBackupRoute(backStack as List<Route>)
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

                NavDisplay<androidx.navigation3.runtime.NavKey>(
                    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
                    backStack = backStack, entryProvider = entryProvider, modifier = contentModifier,
                    onBack = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1) }
                )
            }
        }
    }
}

@Composable
fun WelcomeScreen(
    onCreateProperty: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        Text(stringResource(R.string.welcome_to_mapstead), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.welcome_supporting_text),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.outline
        )
        
        Spacer(Modifier.height(48.dp))
        
        Button(
            onClick = onCreateProperty,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(stringResource(R.string.create_my_property_action), fontWeight = FontWeight.Bold)
        }
    }
}

fun availableMainDestinations(hasValidProperty: Boolean): List<MainDestination> {
    return if (hasValidProperty) {
        listOf(
            MainDestination.Home,
            MainDestination.Map,
            MainDestination.Items,
            MainDestination.Tasks
        )
    } else {
        emptyList()
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
    if (destinations.isEmpty()) return
    
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
    if (destinations.isEmpty()) return

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
    Route.Home, Route.Properties, Route.AddProperty, is Route.EditProperty, is Route.AddPropertyLocation, is Route.PropertyDashboard -> MainDestination.Home
    is Route.MapRoot, is Route.Plans, is Route.CreatePlan, is Route.MapEditor, is Route.FeatureAttachments -> MainDestination.Map
    Route.ItemsRoot, is Route.InfrastructureList, is Route.InfrastructureItemDetails, is Route.InfrastructureItemEditor, 
    is Route.InfrastructureRelationships, is Route.InfrastructureAttachments,
    is Route.InfrastructureRelationshipEditor, is Route.InfrastructureParentEditor, is Route.PropertyReports -> MainDestination.Items
    Route.TasksRoot, is Route.Maintenance, is Route.MaintenanceRecordDetails, is Route.MaintenanceRecordEditor, is Route.ReminderEditor -> MainDestination.Tasks
    is Route.Emergency -> MainDestination.Home
    is Route.AttachmentEditor -> {
        when (this.navigationOrigin) {
            AttachmentNavigationOrigin.MAP_FEATURE -> MainDestination.Map
            AttachmentNavigationOrigin.INFRASTRUCTURE -> MainDestination.Items
            AttachmentNavigationOrigin.MAINTENANCE -> MainDestination.Tasks
            AttachmentNavigationOrigin.PROPERTY_FILES -> MainDestination.Home
        }
    }
    is Route.AttachmentDetails -> {
        when (this.navigationOrigin) {
            AttachmentNavigationOrigin.MAP_FEATURE -> MainDestination.Map
            AttachmentNavigationOrigin.INFRASTRUCTURE -> MainDestination.Items
            AttachmentNavigationOrigin.MAINTENANCE -> MainDestination.Tasks
            AttachmentNavigationOrigin.PROPERTY_FILES -> MainDestination.Home
        }
    }
    Route.Settings, Route.Backup, is Route.PropertyFiles, Route.HelpCenter, is Route.HelpTopic, Route.GettingStarted, Route.Orientation, Route.Privacy, Route.About -> MainDestination.Home
}

fun Route.matchesTopLevelRoot(destination: MainDestination, selectedPropertyId: UUID?): Boolean {
    return when (destination) {
        MainDestination.Home -> {
            this == Route.Home || this == Route.Properties || this is Route.PropertyDashboard || (this is Route.AddPropertyLocation && this.propertyId == selectedPropertyId)
        }
        MainDestination.Map -> this is Route.MapRoot || (this is Route.Plans && this.propertyId == selectedPropertyId) || (this is Route.MapEditor && this.propertyId == selectedPropertyId) || (this is Route.FeatureAttachments && this.propertyId == selectedPropertyId)
        MainDestination.Items -> {
            this == Route.ItemsRoot || (this is Route.InfrastructureList && this.propertyId == selectedPropertyId) || (this is Route.InfrastructureRelationships && this.propertyId == selectedPropertyId) || (this is Route.InfrastructureItemDetails && this.propertyId == selectedPropertyId) || (this is Route.InfrastructureItemEditor && this.propertyId == selectedPropertyId) || (this is Route.InfrastructureParentEditor && this.propertyId == selectedPropertyId) || (this is Route.InfrastructureRelationshipEditor && this.propertyId == selectedPropertyId) || (this is Route.InfrastructureAttachments && this.propertyId == selectedPropertyId)
        }
        MainDestination.Tasks -> this == Route.TasksRoot || (this is Route.Maintenance || this is Route.MaintenanceRecordDetails || this is Route.MaintenanceRecordEditor || this is Route.ReminderEditor) && ((this as? Route.Maintenance)?.propertyId == selectedPropertyId || (this as? Route.MaintenanceRecordDetails)?.propertyId == selectedPropertyId || (this as? Route.MaintenanceRecordEditor)?.propertyId == selectedPropertyId || (this as? Route.ReminderEditor)?.propertyId == selectedPropertyId)
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

internal fun handleInfrastructureSave(
    savedItemId: UUID,
    wasEditing: Boolean,
    propertyId: UUID,
    backStack: MutableList<Route>
) {
    if (wasEditing) {
        if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1)
    } else {
        if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1)
        backStack.add(Route.InfrastructureItemDetails(propertyId, savedItemId))
    }
}

internal fun openOrReturnToMapFeature(
    backStack: MutableList<Route>,
    propertyId: UUID,
    planId: UUID,
    featureId: String?
) {
    val existingIndex = backStack.indexOfLast { 
        it is Route.MapEditor && it.propertyId == propertyId && it.planId == planId 
    }
    
    if (existingIndex != -1) {
        while (backStack.size > existingIndex + 1) {
            backStack.removeAt(backStack.size - 1)
        }
        val existing = backStack[existingIndex] as Route.MapEditor
        if (existing.featureId != featureId) {
            backStack[existingIndex] = existing.copy(featureId = featureId)
        }
    } else {
        backStack.add(Route.MapEditor(propertyId, planId, featureId))
    }
}

internal fun addInfrastructureDetailsUnlessTop(
    backStack: MutableList<Route>,
    propertyId: UUID,
    itemId: UUID
) {
    val top = backStack.lastOrNull()
    if (top is Route.InfrastructureItemDetails && top.propertyId == propertyId && top.itemId == itemId) {
        return
    }
    backStack.add(Route.InfrastructureItemDetails(propertyId, itemId))
}

internal fun openOrReturnToInfrastructureOwner(
    backStack: MutableList<Route>,
    propertyId: UUID,
    itemId: UUID
) {
    val existingIndex = backStack.indexOfLast { 
        it is Route.InfrastructureItemDetails && it.propertyId == propertyId && it.itemId == itemId 
    }
    
    if (existingIndex != -1) {
        while (backStack.size > existingIndex + 1) {
            backStack.removeAt(backStack.size - 1)
        }
    } else {
        backStack.add(Route.InfrastructureItemDetails(propertyId, itemId))
    }
}
