package com.jumastappworks.mapstead.ui.prototype

import androidx.compose.runtime.*
import java.util.UUID

sealed interface PrototypeDestination {
    data object Welcome : PrototypeDestination
    data class PropertySetup(val step: SetupStep) : PrototypeDestination
    data object Home : PrototypeDestination
    data class Map(val highlightItemId: UUID? = null, val returnToDetails: Boolean = false) : PrototypeDestination
    data object Items : PrototypeDestination
    data object Tasks : PrototypeDestination
    data object EmergencyGuide : PrototypeDestination
    data class ItemDetails(val itemId: UUID) : PrototypeDestination
    data class AddJourney(val step: AddStep) : PrototypeDestination
}

sealed interface SetupStep {
    data object Basics : SetupStep
    data object Location : SetupStep
    data object Confirm : SetupStep
    data object Success : SetupStep
}

sealed interface AddStep {
    data object Entry : AddStep
    data class BrowsePresets(val category: String? = null) : AddStep
    data class LocationForm(val name: String, val isPreset: Boolean) : AddStep
    data class LocationMethod(val name: String, val form: ItemLocationForm) : AddStep
    data class LocationConfirm(val name: String, val method: String) : AddStep
    data class MapDrawing(val name: String, val form: ItemLocationForm) : AddStep
    data class Grouping(val name: String) : AddStep
    data class Photo(val name: String) : AddStep
    data object Review : AddStep
}

enum class ItemLocationForm {
    MARK_ONE, DRAW_RUNS, OUTLINE_AREA, LATER
}

class PrototypeAppState {
    var currentDestination by mutableStateOf<PrototypeDestination>(PrototypeDestination.Welcome)
    val backStack = mutableStateListOf<PrototypeDestination>(PrototypeDestination.Welcome)

    // Properties State
    val properties = mutableStateListOf<PrototypeProperty>()
    var selectedPropertyId by mutableStateOf<UUID?>(null)
    
    // Data State (Scoped to current property)
    val items = mutableStateListOf<PrototypePropertyItem>()
    val tasks = mutableStateListOf<PrototypeTask>()
    
    // Drafts
    var setupDraft by mutableStateOf<PrototypeProperty?>(null)
    var addDraft by mutableStateOf<PrototypePropertyItem?>(null)
    
    // UI State
    var searchQuery by mutableStateOf("")
    var selectedCategory by mutableStateOf("All")
    var mapOptionsOpen by mutableStateOf(false)

    val currentProperty get() = properties.find { it.id == selectedPropertyId }
    
    init {
        // Initially empty properties to trigger Welcome screen
    }

    fun reset() {
        properties.clear()
        properties.add(SampleProperty)
        selectedPropertyId = SampleProperty.id
        refreshScopedData()
        searchQuery = ""
        selectedCategory = "All"
        navigateTo(PrototypeDestination.Home)
    }

    fun selectProperty(id: UUID) {
        selectedPropertyId = id
        refreshScopedData()
        navigateTo(PrototypeDestination.Home)
    }

    private fun refreshScopedData() {
        val pid = selectedPropertyId ?: return
        items.clear()
        tasks.clear()
        
        if (pid == SampleProperty.id) {
            items.addAll(initialSampleItems)
            tasks.addAll(initialSampleTasks)
        } else {
            // New properties start empty
        }
    }

    fun navigateTo(destination: PrototypeDestination) {
        if (destination == PrototypeDestination.Home) {
            backStack.clear()
            backStack.add(PrototypeDestination.Home)
        } else if (currentDestination != destination) {
            backStack.add(destination)
        }
        currentDestination = destination
    }

    fun replaceTop(destination: PrototypeDestination) {
        if (backStack.isNotEmpty()) {
            backStack.removeAt(backStack.size - 1)
        }
        backStack.add(destination)
        currentDestination = destination
    }

    fun goBack() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.size - 1)
            currentDestination = backStack.last()
        }
    }

    // --- Property Setup Journey ---

    fun startPropertySetup() {
        setupDraft = PrototypeProperty(name = "", type = "Home")
        navigateTo(PrototypeDestination.PropertySetup(SetupStep.Basics))
    }

    fun finalizePropertyCreation() {
        val newProp = setupDraft ?: return
        properties.add(newProp)
        selectedPropertyId = newProp.id
        setupDraft = null
        refreshScopedData()
        replaceTop(PrototypeDestination.PropertySetup(SetupStep.Success))
    }

    // --- Add Something Journey ---

    fun startAddJourney() {
        val pid = selectedPropertyId ?: return
        addDraft = PrototypePropertyItem(propertyId = pid, name = "", category = "", locationDescription = "")
        navigateTo(PrototypeDestination.AddJourney(AddStep.Entry))
    }

    fun completeAddJourney(savedItem: PrototypePropertyItem) {
        val journeyStartIndex = backStack.indexOfFirst { it is PrototypeDestination.AddJourney }
        if (journeyStartIndex != -1) {
            while (backStack.size > journeyStartIndex) {
                backStack.removeAt(backStack.size - 1)
            }
        }

        saveItem(savedItem)
        addDraft = null
        navigateTo(PrototypeDestination.ItemDetails(savedItem.id))
    }

    fun saveItem(item: PrototypePropertyItem) {
        val index = items.indexOfFirst { it.id == item.id }
        if (index != -1) {
            items[index] = item
        } else {
            items.add(item)
            val searchToken = item.name.replace(" ", "-")
            tasks.forEachIndexed { i, task ->
                if (task.title.contains(searchToken, ignoreCase = true) && task.relatedItemId == null) {
                    tasks[i] = task.copy(relatedItemId = item.id)
                }
            }
        }
    }
}

private val SampleProperty = PrototypeProperty(
    name = "Oak Ridge Homestead",
    type = "Farm or Homestead",
    address = "1234 Oak Ridge Lane",
    isSample = true
)

private val initialSampleItems = listOf(
    PrototypePropertyItem(propertyId = SampleProperty.id, name = "Main Water Shutoff", category = "Water & Plumbing", locationDescription = "Front of house, near driveway", isEmergency = true, latitude = 34.1235, longitude = -118.5670),
    PrototypePropertyItem(propertyId = SampleProperty.id, name = "Well", category = "Water & Plumbing", locationDescription = "North pasture, marked by well house", latitude = 34.1240, longitude = -118.5680),
    PrototypePropertyItem(propertyId = SampleProperty.id, name = "Septic Tank", category = "Water & Plumbing", locationDescription = "South lawn, access near oak tree", latitude = 34.1220, longitude = -118.5672),
    PrototypePropertyItem(propertyId = SampleProperty.id, name = "Electrical Panel", category = "Power & Electrical", locationDescription = "Garage, west wall", isEmergency = true, latitude = 34.1233, longitude = -118.5668),
    PrototypePropertyItem(propertyId = SampleProperty.id, name = "Propane Tank", category = "Power & Electrical", locationDescription = "Side of house, near generator", isEmergency = true, latitude = 34.1231, longitude = -118.5665),
    PrototypePropertyItem(propertyId = SampleProperty.id, name = "North Fence", category = "Boundaries & Access", locationDescription = "Along County Road 4", latitude = 34.1250, longitude = -118.5670),
    PrototypePropertyItem(propertyId = SampleProperty.id, name = "Equipment Shed", category = "Buildings & Structures", locationDescription = "Adjacent to garden area", latitude = 34.1228, longitude = -118.5678),
    PrototypePropertyItem(propertyId = SampleProperty.id, name = "Pond", category = "Outdoor & Land", locationDescription = "Center of property", latitude = 34.1232, longitude = -118.5673)
)

private val initialSampleTasks = listOf(
    PrototypeTask(propertyId = SampleProperty.id, title = "Replace pool-pump filter", status = PrototypeTaskStatus.OVERDUE, relatedItemId = null),
    PrototypeTask(propertyId = SampleProperty.id, title = "Inspect well pressure tank", status = PrototypeTaskStatus.DUE_SOON, relatedItemId = initialSampleItems[1].id),
    PrototypeTask(propertyId = SampleProperty.id, title = "Service generator", status = PrototypeTaskStatus.UPCOMING),
    PrototypeTask(propertyId = SampleProperty.id, title = "Septic inspection", status = PrototypeTaskStatus.COMPLETED, relatedItemId = initialSampleItems[2].id)
)
