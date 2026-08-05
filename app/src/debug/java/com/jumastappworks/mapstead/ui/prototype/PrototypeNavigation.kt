package com.jumastappworks.mapstead.ui.prototype

import androidx.compose.runtime.*
import java.util.UUID

sealed interface PrototypeDestination {
    data object Home : PrototypeDestination
    data class Map(val highlightItemId: UUID? = null, val returnToDetails: Boolean = false) : PrototypeDestination
    data object Items : PrototypeDestination
    data object Tasks : PrototypeDestination
    data object EmergencyGuide : PrototypeDestination
    data class ItemDetails(val itemId: UUID) : PrototypeDestination
    data class AddJourney(val step: AddStep) : PrototypeDestination
}

sealed interface AddStep {
    data object Category : AddStep
    data class Preset(val category: String) : AddStep
    data class LocationChoice(val name: String, val category: String) : AddStep
    data class LocationConfirm(val name: String, val category: String) : AddStep
    data class LocationManual(val name: String, val category: String) : AddStep
    data class Photo(val name: String, val category: String) : AddStep
    data object Review : AddStep
}

class PrototypeAppState {
    var currentDestination by mutableStateOf<PrototypeDestination>(PrototypeDestination.Home)
    val backStack = mutableStateListOf<PrototypeDestination>(PrototypeDestination.Home)

    // Data State
    val items = mutableStateListOf<PrototypePropertyItem>()
    val tasks = mutableStateListOf<PrototypeTask>()
    
    // Journey State
    var addDraft by mutableStateOf<PrototypePropertyItem?>(null)
    
    // UI State
    var searchQuery by mutableStateOf("")
    var selectedCategory by mutableStateOf("All")
    var mapOptionsOpen by mutableStateOf(false)
    
    init {
        reset()
    }

    fun reset() {
        items.clear()
        items.addAll(initialFakeItems)
        tasks.clear()
        tasks.addAll(initialFakeTasks)
        searchQuery = ""
        selectedCategory = "All"
        currentDestination = PrototypeDestination.Home
        backStack.clear()
        backStack.add(PrototypeDestination.Home)
        addDraft = null
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

    fun goBack() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.size - 1)
            currentDestination = backStack.last()
        }
    }

    fun startAddJourney() {
        addDraft = PrototypePropertyItem(name = "", category = "", locationDescription = "")
        navigateTo(PrototypeDestination.AddJourney(AddStep.Category))
    }

    fun completeAddJourney(savedItem: PrototypePropertyItem) {
        // 1. Identify where the journey started
        val journeyStartIndex = backStack.indexOfFirst { it is PrototypeDestination.AddJourney }
        
        // 2. Remove all AddJourney steps from backstack
        if (journeyStartIndex != -1) {
            while (backStack.size > journeyStartIndex) {
                backStack.removeAt(backStack.size - 1)
            }
        }

        saveItem(savedItem)
        addDraft = null
        
        // 3. Push details (Now the top is the origin, e.g. Home or Items)
        navigateTo(PrototypeDestination.ItemDetails(savedItem.id))
    }

    fun saveItem(item: PrototypePropertyItem) {
        val index = items.indexOfFirst { it.id == item.id }
        if (index != -1) {
            items[index] = item
        } else {
            items.add(item)
            // Update tasks referencing this name (e.g., "pool-pump" vs "Pool Pump")
            val searchToken = item.name.replace(" ", "-")
            tasks.forEachIndexed { i, task ->
                if (task.title.contains(searchToken, ignoreCase = true) && task.relatedItemId == null) {
                    tasks[i] = task.copy(relatedItemId = item.id)
                }
            }
        }
    }
}

private val initialFakeItems = listOf(
    PrototypePropertyItem(name = "Main Water Shutoff", category = "Water & Plumbing", locationDescription = "Front of house, near driveway", isEmergency = true, latitude = 34.1235, longitude = -118.5670),
    // Pool Pump removed for Add Journey demonstration
    PrototypePropertyItem(name = "Well", category = "Water & Plumbing", locationDescription = "North pasture, marked by well house", latitude = 34.1240, longitude = -118.5680),
    PrototypePropertyItem(name = "Septic Tank", category = "Water & Plumbing", locationDescription = "South lawn, access near oak tree", latitude = 34.1220, longitude = -118.5672),
    PrototypePropertyItem(name = "Electrical Panel", category = "Power & Electrical", locationDescription = "Garage, west wall", isEmergency = true, latitude = 34.1233, longitude = -118.5668),
    PrototypePropertyItem(name = "Propane Tank", category = "Power & Electrical", locationDescription = "Side of house, near generator", isEmergency = true, latitude = 34.1231, longitude = -118.5665),
    PrototypePropertyItem(name = "North Fence", category = "Boundaries & Access", locationDescription = "Along County Road 4", latitude = 34.1250, longitude = -118.5670),
    PrototypePropertyItem(name = "Equipment Shed", category = "Buildings & Structures", locationDescription = "Adjacent to garden area", latitude = 34.1228, longitude = -118.5678),
    PrototypePropertyItem(name = "Pond", category = "Outdoor & Land", locationDescription = "Center of property", latitude = 34.1232, longitude = -118.5673)
)

private val initialFakeTasks = listOf(
    PrototypeTask(title = "Replace pool-pump filter", status = PrototypeTaskStatus.OVERDUE, relatedItemId = null),
    PrototypeTask(title = "Inspect well pressure tank", status = PrototypeTaskStatus.DUE_SOON, relatedItemId = initialFakeItems[1].id),
    PrototypeTask(title = "Service generator", status = PrototypeTaskStatus.UPCOMING),
    PrototypeTask(title = "Septic inspection", status = PrototypeTaskStatus.COMPLETED, relatedItemId = initialFakeItems[2].id)
)
