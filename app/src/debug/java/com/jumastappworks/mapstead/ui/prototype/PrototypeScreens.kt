package com.jumastappworks.mapstead.ui.prototype

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.util.UUID
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeHomeScreen(appState: PrototypeAppState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column {
            Text("Oak Ridge Homestead", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("Good Morning!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = { appState.startAddJourney() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add Something", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(
                onClick = { 
                    appState.searchQuery = ""
                    appState.navigateTo(PrototypeDestination.Items) 
                },
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Find Something")
            }
            
            FilledTonalButton(
                onClick = { appState.navigateTo(PrototypeDestination.EmergencyGuide) },
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.Default.Warning, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Emergency")
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Needs Attention", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            appState.tasks.filter { it.status != PrototypeTaskStatus.COMPLETED }.take(2).forEach { task ->
                TaskCard(task) { task.relatedItemId?.let { appState.navigateTo(PrototypeDestination.ItemDetails(it)) } }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Recently Viewed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            appState.items.take(3).forEach { item ->
                ItemRow(item) { appState.navigateTo(PrototypeDestination.ItemDetails(item.id)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeMapScreen(appState: PrototypeAppState, highlightItemId: UUID? = null, returnToDetails: Boolean = false) {
    var customerLocation by remember { mutableStateOf(Offset(200f, 300f)) }
    var mapCenter by remember { mutableStateOf(Offset(0f, 0f)) }
    var zoomLevel by remember { mutableStateOf(1f) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Fake Map Illustration
        Canvas(modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE8F5E9))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    mapCenter += pan
                    zoomLevel *= zoom
                }
            }
        ) {
            val center = center + mapCenter
            
            // Boundary
            drawRect(Color(0xFF81C784), topLeft = center - Offset(500f, 800f) * zoomLevel, size = androidx.compose.ui.geometry.Size(1000f, 1600f) * zoomLevel, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
            
            // House
            drawRect(Color(0xFFBCAAA4), topLeft = center - Offset(50f, 50f) * zoomLevel, size = androidx.compose.ui.geometry.Size(100f, 150f) * zoomLevel)
            
            // Pool
            drawCircle(Color(0xFF81D4FA), center = center + Offset(100f, 200f) * zoomLevel, radius = 60f * zoomLevel)
            
            // Pond
            drawCircle(Color(0xFF4FC3F7), center = center + Offset(-250f, -400f) * zoomLevel, radius = 120f * zoomLevel)
            
            // Items
            appState.items.forEach { item ->
                if (!item.needsLocation) {
                    val offset = Offset(
                        (((item.longitude ?: 0.0) + 118.567) * 10000f).toFloat(),
                        (((item.latitude ?: 0.0) - 34.123) * 10000f).toFloat()
                    ) * zoomLevel
                    
                    val isHighlighted = item.id == highlightItemId
                    drawCircle(
                        if (isHighlighted) Color.Red else Color.Blue,
                        center = center + offset,
                        radius = if (isHighlighted) 12f * zoomLevel else 8f * zoomLevel
                    )
                }
            }
            
            // Customer
            drawCircle(Color.Magenta, center = center + customerLocation * zoomLevel, radius = 10f * zoomLevel)
        }
        
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            IconButton(onClick = { appState.goBack() }, modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.height(8.dp))
            
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(12.dp))
                    Text("Search Map", color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        // Map Controls
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FloatingActionButton(onClick = { 
                mapCenter = Offset(0f, 0f)
                zoomLevel = 1f
            }, containerColor = MaterialTheme.colorScheme.surface) {
                Icon(Icons.Default.CenterFocusStrong, contentDescription = "Recenter on Property")
            }
            FloatingActionButton(onClick = { 
                mapCenter = -customerLocation
                zoomLevel = 1.5f
            }, containerColor = MaterialTheme.colorScheme.surface) {
                Icon(Icons.Default.MyLocation, contentDescription = "My Location")
            }
            FloatingActionButton(onClick = { appState.mapOptionsOpen = true }, containerColor = MaterialTheme.colorScheme.surface) {
                Icon(Icons.Default.Layers, contentDescription = "Map Options")
            }
            FloatingActionButton(onClick = { appState.startAddJourney() }) {
                Icon(Icons.Default.Add, contentDescription = "Add Something")
            }
        }

        if (highlightItemId != null) {
            val item = appState.items.find { it.id == highlightItemId }
            if (item != null) {
                Card(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp, start = 16.dp, end = 16.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Bold)
                            Text(item.locationDescription, style = MaterialTheme.typography.bodySmall)
                        }
                        if (returnToDetails) {
                            Button(onClick = { appState.goBack() }) {
                                Text("Return to Details")
                            }
                        } else {
                            TextButton(onClick = { appState.navigateTo(PrototypeDestination.ItemDetails(item.id)) }) {
                                Text("Details")
                            }
                        }
                    }
                }
            }
        }

        if (appState.mapOptionsOpen) {
            ModalBottomSheet(onDismissRequest = { appState.mapOptionsOpen = false }) {
                Column(modifier = Modifier.padding(16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Map Options", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    
                    MapOptionToggle("Layers", Icons.Default.Layers)
                    MapOptionToggle("Basemap", Icons.Default.Map)
                    MapOptionToggle("Measurements", Icons.Default.Straighten)
                    MapOptionToggle("Technical Coordinates", Icons.Default.Code)
                }
            }
        }
    }
}

@Composable
fun MapOptionToggle(label: String, icon: ImageVector) {
    var enabled by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { enabled = !enabled }.padding(vertical = 8.dp)) {
        Icon(icon, contentDescription = null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        Spacer(Modifier.width(16.dp))
        Text(label, modifier = Modifier.weight(1f), fontWeight = if (enabled) FontWeight.Bold else FontWeight.Normal)
        Switch(checked = enabled, onCheckedChange = { enabled = it })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeItemsScreen(appState: PrototypeAppState) {
    val filteredItems = remember(appState.searchQuery, appState.selectedCategory, appState.items.size) {
        appState.items.filter { 
            (appState.selectedCategory == "All" || it.category.contains(appState.selectedCategory, ignoreCase = true) || (appState.selectedCategory == "Safety" && it.isEmergency)) &&
            (it.name.contains(appState.searchQuery, ignoreCase = true) || it.locationDescription.contains(appState.searchQuery, ignoreCase = true))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Property Items", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            
            OutlinedTextField(
                value = appState.searchQuery,
                onValueChange = { appState.searchQuery = it },
                placeholder = { Text("Search Items") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("All", "Water", "Power", "Buildings", "Safety")) { cat ->
                    FilterChip(
                        selected = appState.selectedCategory == cat, 
                        onClick = { appState.selectedCategory = cat }, 
                        label = { Text(cat) }
                    )
                }
            }
        }

        if (filteredItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No items match your search or filter.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(appState.items.toList(), key = { it.id }) { item ->
                    if (item in filteredItems) {
                        ItemRow(item) { appState.navigateTo(PrototypeDestination.ItemDetails(item.id)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeTasksScreen(appState: PrototypeAppState) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Tasks", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PrototypeTaskStatus.entries.forEach { status ->
                val tasks = appState.tasks.filter { it.status == status }
                if (tasks.isNotEmpty()) {
                    Text(status.name.replace("_", " ").lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.outline)
                    tasks.forEach { task ->
                        TaskCard(task) { task.relatedItemId?.let { appState.navigateTo(PrototypeDestination.ItemDetails(it)) } }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeEmergencyGuide(appState: PrototypeAppState) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { appState.goBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Emergency Guide", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Text(
                text = "The Emergency Guide keeps important property information in one place. It does not contact emergency services. For immediate danger, call 911.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Critical Controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            appState.items.filter { it.isEmergency }.forEach { item ->
                ItemRow(item) { appState.navigateTo(PrototypeDestination.ItemDetails(item.id)) }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Property Info", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            DetailRow(Icons.Default.Home, "Address", "1234 Oak Ridge Lane")
            DetailRow(Icons.Default.Phone, "Emergency Contact", "Local Fire Dept: (555) 012-3456")
            DetailRow(Icons.Default.Warning, "Known Hazard", "Steep ravine at north boundary")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeItemDetails(appState: PrototypeAppState, itemId: UUID) {
    val item = appState.items.find { it.id == itemId } ?: return
    var moreDetailsExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item.name) },
                navigationIcon = {
                    IconButton(onClick = { appState.goBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Priority Content
            Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                if (item.hasPhoto) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(item.category, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("Status: ${item.status}", style = MaterialTheme.typography.bodyMedium)
                }
                if (!item.needsLocation) {
                    Button(onClick = { appState.navigateTo(PrototypeDestination.Map(highlightItemId = item.id, returnToDetails = true)) }) {
                        Icon(Icons.Default.Map, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Show on Map")
                    }
                }
            }

            DetailSection("At a Glance") {
                Text("Location: ${item.locationDescription}")
                item.note?.let { 
                    Spacer(Modifier.height(8.dp))
                    Text("Note: $it", style = MaterialTheme.typography.bodyMedium)
                }
            }

            val tasks = appState.tasks.filter { it.relatedItemId == item.id }
            if (tasks.isNotEmpty()) {
                DetailSection("Needs Attention") {
                    tasks.forEach { TaskCard(it) {} }
                }
            }

            DetailSection("Where It Is") {
                if (item.needsLocation) {
                    Text("No location set for this item.", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { /* Add Location later */ }) {
                        Text("Add Location")
                    }
                } else {
                    Text("Plain-language: ${item.locationDescription}")
                }
            }

            DetailSection("Photos & Files") {
                Text(if (item.hasPhoto) "1 photo attached" else "No photos or files")
            }

            if (item.isEmergency) {
                DetailSection("Emergency Information") {
                    Text("CRITICAL: Turn clockwise to shut off.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { moreDetailsExpanded = !moreDetailsExpanded }) {
                    Text("More Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    Icon(if (moreDetailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                }
                if (moreDetailsExpanded) {
                    Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailRow(Icons.Default.Code, "Latitude", item.latitude?.toString() ?: "N/A")
                        DetailRow(Icons.Default.Code, "Longitude", item.longitude?.toString() ?: "N/A")
                        DetailRow(Icons.Default.Business, "Manufacturer", "Pentair")
                        DetailRow(Icons.Default.Settings, "Model", "IntelliFlo VSF")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeAddJourney(appState: PrototypeAppState, step: AddStep) {
    val draft = appState.addDraft ?: return
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Something") },
                navigationIcon = {
                    IconButton(onClick = { appState.goBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when (step) {
                AddStep.Category -> CategoryStep { 
                    appState.addDraft = draft.copy(category = it)
                    appState.navigateTo(PrototypeDestination.AddJourney(AddStep.Preset(it))) 
                }
                is AddStep.Preset -> PresetStep(step.category) { 
                    appState.addDraft = draft.copy(name = it)
                    appState.navigateTo(PrototypeDestination.AddJourney(AddStep.LocationChoice(it, step.category))) 
                }
                is AddStep.LocationChoice -> LocationChoiceStep(step.name) { choice ->
                    when (choice) {
                        "Standing" -> appState.navigateTo(PrototypeDestination.AddJourney(AddStep.LocationConfirm(step.name, step.category)))
                        "Map" -> appState.navigateTo(PrototypeDestination.AddJourney(AddStep.LocationManual(step.name, step.category)))
                        "Later" -> {
                            appState.addDraft = draft.copy(locationDescription = "Needs Location", needsLocation = true, latitude = null, longitude = null)
                            appState.navigateTo(PrototypeDestination.AddJourney(AddStep.Photo(step.name, step.category)))
                        }
                    }
                }
                is AddStep.LocationConfirm -> LocationConfirmStep(step.name) { confirmedDesc ->
                    appState.addDraft = draft.copy(locationDescription = confirmedDesc, needsLocation = false, latitude = 34.1235, longitude = -118.5671)
                    appState.navigateTo(PrototypeDestination.AddJourney(AddStep.Photo(step.name, step.category)))
                }
                is AddStep.LocationManual -> LocationManualStep(step.name) { confirmedDesc ->
                    appState.addDraft = draft.copy(locationDescription = confirmedDesc, needsLocation = false, latitude = 34.1238, longitude = -118.5675)
                    appState.navigateTo(PrototypeDestination.AddJourney(AddStep.Photo(step.name, step.category)))
                }
                is AddStep.Photo -> PhotoStep { hasPhoto ->
                    appState.addDraft = draft.copy(hasPhoto = hasPhoto)
                    appState.navigateTo(PrototypeDestination.AddJourney(AddStep.Review))
                }
                AddStep.Review -> ReviewStep(appState)
            }
        }
    }
}

@Composable
fun CategoryStep(onSelect: (String) -> Unit) {
    val cats = listOf("Water & Plumbing", "Power & Electrical", "Buildings & Structures", "Boundaries & Access", "Outdoor & Land", "Pool Equipment", "Safety & Emergency")
    LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(cats) { cat ->
            Card(onClick = { onSelect(cat) }, modifier = Modifier.height(100.dp)) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(cat, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}

@Composable
fun PresetStep(cat: String, onSelect: (String) -> Unit) {
    val presets = when (cat) {
        "Pool Equipment" -> listOf("Pool Pump", "Pool Filter", "Pool Heater", "Pool Control Panel", "Other Pool Equipment")
        else -> listOf("Custom Item")
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("What are you adding?", style = MaterialTheme.typography.titleLarge)
        presets.forEach { preset ->
            OutlinedButton(onClick = { onSelect(preset) }, modifier = Modifier.fillMaxWidth()) {
                Text(preset)
            }
        }
    }
}

@Composable
fun LocationChoiceStep(name: String, onChoice: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Where is the $name?", style = MaterialTheme.typography.titleLarge)
        
        LocationOption("I’m Standing Next to It", "Use your current position", Icons.Default.MyLocation) { onChoice("Standing") }
        LocationOption("Place It on the Map", "Pick a spot manually", Icons.Default.Map) { onChoice("Map") }
        LocationOption("Add the Location Later", "Save just the details for now", Icons.Default.Schedule) { onChoice("Later") }
    }
}

@Composable
fun LocationConfirmStep(name: String, onConfirm: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Confirm Location", style = MaterialTheme.typography.titleLarge)
        
        Box(modifier = Modifier.fillMaxWidth().height(250.dp).background(Color(0xFFE8F5E9), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                Text("Suggested Position", fontWeight = FontWeight.Bold)
                Text("Near the pool equipment wall", style = MaterialTheme.typography.bodySmall)
            }
        }

        Button(onClick = { onConfirm("Near pool equipment wall") }, modifier = Modifier.fillMaxWidth()) {
            Text("Confirm Location")
        }
        
        OutlinedButton(onClick = { onConfirm("Adjusted pool location") }, modifier = Modifier.fillMaxWidth()) {
            Text("Adjust on Map")
        }
    }
}

@Composable
fun LocationManualStep(name: String, onConfirm: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Place on Map", style = MaterialTheme.typography.titleLarge)
        
        Box(modifier = Modifier.fillMaxWidth().height(250.dp).background(Color(0xFFFFF9C4), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Text("Tap to place $name", fontWeight = FontWeight.Bold)
        }

        Button(onClick = { onConfirm("Manually placed location") }, modifier = Modifier.fillMaxWidth()) {
            Text("Set Location")
        }
    }
}

@Composable
fun PhotoStep(onNext: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Add a Photo", style = MaterialTheme.typography.titleLarge)
        
        Box(modifier = Modifier.size(200.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(100.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { onNext(true) }, modifier = Modifier.fillMaxWidth()) {
                Text("Take Photo")
            }
            OutlinedButton(onClick = { onNext(true) }, modifier = Modifier.fillMaxWidth()) {
                Text("Choose Photo")
            }
            TextButton(onClick = { onNext(false) }) {
                Text("Continue Without Photo")
            }
        }
    }
}

@Composable
fun ReviewStep(appState: PrototypeAppState) {
    val draft = appState.addDraft ?: return
    var showNameDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Review & Save", style = MaterialTheme.typography.titleLarge)
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ReviewRow("Name", draft.name, Icons.Default.Edit) { showNameDialog = true }
                ReviewRow("Category", draft.category, null) {}
                ReviewRow("Location", draft.locationDescription, Icons.Default.Place) {
                    appState.goBack() // Simplified return to location choice
                }
                ReviewRow("Photo", if (draft.hasPhoto) "1 Photo attached" else "No photo", Icons.Default.CameraAlt) {
                    appState.goBack() // Simplified return to photo step
                }
                
                OutlinedTextField(
                    value = draft.note ?: "",
                    onValueChange = { appState.addDraft = draft.copy(note = it) },
                    label = { Text("Add a note (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Button(onClick = { 
            appState.completeAddJourney(draft)
        }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Save Item", fontWeight = FontWeight.Bold)
        }
    }

    if (showNameDialog) {
        var newName by remember { mutableStateOf(draft.name) }
        Dialog(onDismissRequest = { showNameDialog = false }) {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Edit Name", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, modifier = Modifier.fillMaxWidth())
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showNameDialog = false }) { Text("Cancel") }
                        TextButton(onClick = { 
                            appState.addDraft = draft.copy(name = newName)
                            showNameDialog = false 
                        }) { Text("OK") }
                    }
                }
            }
        }
    }
}

@Composable
fun ReviewRow(label: String, value: String, icon: ImageVector?, onEdit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        if (icon != null) {
            IconButton(onClick = onEdit) { Icon(icon, contentDescription = "Edit $label", modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
fun LocationOption(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
fun TaskCard(task: PrototypeTask, onClick: () -> Unit) {
    val color = when (task.status) {
        PrototypeTaskStatus.OVERDUE -> MaterialTheme.colorScheme.error
        PrototypeTaskStatus.DUE_SOON -> Color(0xFFF57C00)
        else -> MaterialTheme.colorScheme.primary
    }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(task.title, fontWeight = FontWeight.Bold)
                Text(task.status.name.replace("_", " ").lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }, style = MaterialTheme.typography.bodySmall, color = color)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
fun ItemRow(item: PrototypePropertyItem, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (item.isEmergency) Icons.Default.Warning else Icons.Default.Foundation, contentDescription = null, tint = if (item.isEmergency) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(item.name, fontWeight = FontWeight.Bold)
                Text("${item.category} \u2022 ${item.locationDescription}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Text(value)
        }
    }
}
