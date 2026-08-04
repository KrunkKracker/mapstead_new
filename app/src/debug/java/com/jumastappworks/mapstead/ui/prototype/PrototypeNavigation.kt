package com.jumastappworks.mapstead.ui.prototype

import androidx.compose.runtime.*
import java.util.UUID

sealed interface PrototypeDestination {
    data object Home : PrototypeDestination
    data class Map(val highlightItemId: UUID? = null, val returnTo: PrototypeDestination? = null) : PrototypeDestination
    data object Items : PrototypeDestination
    data object Tasks : PrototypeDestination
    data object EmergencyGuide : PrototypeDestination
    data class ItemDetails(val itemId: UUID, val from: PrototypeDestination = Home) : PrototypeDestination
    data class AddJourney(val step: AddStep) : PrototypeDestination
}

sealed interface AddStep {
    data object Category : AddStep
    data class Preset(val category: String) : AddStep
    data class LocationChoice(val name: String, val category: String) : AddStep
    data class LocationConfirm(val name: String, val category: String) : AddStep
    data class Photo(val name: String, val category: String) : AddStep
    data class Review(val name: String, val category: String, val hasPhoto: Boolean = false) : AddStep
}

class PrototypeAppState {
    var currentDestination by mutableStateOf<PrototypeDestination>(PrototypeDestination.Home)
    private val backStack = mutableStateListOf<PrototypeDestination>(PrototypeDestination.Home)

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
}
