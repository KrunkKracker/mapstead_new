package com.jumastappworks.mapstead.ui.prototype

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeAppShell() {
    val appState = remember { PrototypeAppState() }
    val currentDest = appState.currentDestination

    Scaffold(
        bottomBar = {
            if (appState.properties.isNotEmpty() &&
                currentDest !is PrototypeDestination.AddJourney && 
                currentDest !is PrototypeDestination.EmergencyGuide &&
                currentDest !is PrototypeDestination.PropertySetup &&
                currentDest !is PrototypeDestination.Welcome) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentDest is PrototypeDestination.Home,
                        onClick = { appState.navigateTo(PrototypeDestination.Home) },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = currentDest is PrototypeDestination.Map,
                        onClick = { appState.navigateTo(PrototypeDestination.Map()) },
                        icon = { Icon(Icons.Default.Map, contentDescription = null) },
                        label = { Text("Map") }
                    )
                    NavigationBarItem(
                        selected = currentDest is PrototypeDestination.Items,
                        onClick = { appState.navigateTo(PrototypeDestination.Items) },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        label = { Text("Items") }
                    )
                    NavigationBarItem(
                        selected = currentDest is PrototypeDestination.Tasks,
                        onClick = { appState.navigateTo(PrototypeDestination.Tasks) },
                        icon = { Icon(Icons.Default.Build, contentDescription = null) },
                        label = { Text("Tasks") }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (val dest = currentDest) {
                PrototypeDestination.Welcome -> PrototypeWelcomeScreen(appState)
                is PrototypeDestination.PropertySetup -> PrototypePropertySetupJourney(appState, dest.step)
                PrototypeDestination.Home -> PrototypeHomeScreen(appState)
                is PrototypeDestination.Map -> PrototypeMapScreen(appState, dest.highlightItemId, dest.returnToDetails)
                PrototypeDestination.Items -> PrototypeItemsScreen(appState)
                PrototypeDestination.Tasks -> PrototypeTasksScreen(appState)
                PrototypeDestination.EmergencyGuide -> PrototypeEmergencyGuide(appState)
                is PrototypeDestination.ItemDetails -> PrototypeItemDetails(appState, dest.itemId)
                is PrototypeDestination.AddJourney -> PrototypeAddJourney(appState, dest.step)
            }
        }
    }
}
