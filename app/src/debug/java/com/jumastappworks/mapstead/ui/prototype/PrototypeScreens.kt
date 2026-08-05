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

// --- Welcome & Setup ---

@Composable
fun PrototypeWelcomeScreen(appState: PrototypeAppState) {
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
        
        Text("Welcome to Mapstead", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(
            "Mapstead helps you remember what is on your property, where it is, what needs attention, and what someone needs during an emergency.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.outline
        )
        
        Spacer(Modifier.height(48.dp))
        
        Button(
            onClick = { appState.startPropertySetup() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Create My Property", fontWeight = FontWeight.Bold)
        }
        
        Spacer(Modifier.height(16.dp))
        
        TextButton(onClick = { appState.reset() }) {
            Text("Explore a Sample Property")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypePropertySetupJourney(appState: PrototypeAppState, step: SetupStep) {
    val draft = appState.setupDraft ?: return
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Property Setup") },
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
                SetupStep.Basics -> SetupBasicsStep(draft) { appState.navigateTo(PrototypeDestination.PropertySetup(SetupStep.Location)) }
                SetupStep.Location -> SetupLocationStep(draft) { appState.navigateTo(PrototypeDestination.PropertySetup(SetupStep.Confirm)) }
                SetupStep.Confirm -> SetupConfirmStep(draft) { appState.finalizePropertyCreation() }
                SetupStep.Success -> SetupSuccessStep(appState)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupBasicsStep(draft: PrototypeProperty, onNext: () -> Unit) {
    var name by remember { mutableStateOf(draft.name) }
    var type by remember { mutableStateOf(draft.type) }
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Property Basics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Property Name") },
            placeholder = { Text("e.g., Oak Ridge Homestead") },
            modifier = Modifier.fillMaxWidth()
        )

        val types = listOf("Home", "Rental", "Farm or Homestead", "Cabin or Vacation Property", "Land", "Business Property", "Other")
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = type,
                onValueChange = {},
                readOnly = true,
                label = { Text("Property Type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                types.forEach { t ->
                    DropdownMenuItem(text = { Text(t) }, onClick = { type = t; expanded = false })
                }
            }
        }

        Button(onClick = { draft.copy(name = name, type = type).let { /* update draft logic needed in state? simplified for now as draft is mutable state */ }; onNext() }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Next")
        }
    }
}

@Composable
fun SetupLocationStep(draft: PrototypeProperty, onNext: () -> Unit) {
    var selectedMethod by remember { mutableStateOf<String?>(null) }
    
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Where is this property?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        
        LocationOption("Search for an Address", "The easiest way to start", Icons.Default.Search) { selectedMethod = "address" }
        LocationOption("Use My Current Location", "If you are at the property now", Icons.Default.MyLocation) { selectedMethod = "gps" }
        LocationOption("Place It on the Map", "Tap to mark the center", Icons.Default.Map) { selectedMethod = "map" }
        
        HorizontalDivider()
        Text("More Location Options", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
        LocationOption("Enter Coordinates", "If you know the latitude and longitude", Icons.Default.Code) { selectedMethod = "coords" }
    }

    if (selectedMethod != null) {
        Dialog(onDismissRequest = { selectedMethod = null }) {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Simulated Location", style = MaterialTheme.typography.titleMedium)
                    
                    when (selectedMethod) {
                        "address" -> {
                            var query by remember { mutableStateOf("") }
                            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
                            if (query.length > 3) {
                                Text("7901 4th St N, St. Petersburg, FL 33702", modifier = Modifier.clickable { onNext() }.padding(8.dp))
                            }
                        }
                        "gps" -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                            Text("Locating...", modifier = Modifier.align(Alignment.CenterHorizontally))
                            LaunchedEffect(Unit) {
                                kotlinx.coroutines.delay(1000)
                                onNext()
                            }
                        }
                        else -> {
                            Text("Simulation logic for $selectedMethod would go here.")
                            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Confirm") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SetupConfirmStep(draft: PrototypeProperty, onCreate: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Confirm Property", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ReviewRow("Name", draft.name, Icons.Default.Edit) {}
                ReviewRow("Type", draft.type, Icons.Default.Edit) {}
                ReviewRow("Location", "7901 4th St N, St. Petersburg, FL", Icons.Default.Place) {}
                
                Box(modifier = Modifier.fillMaxWidth().height(150.dp).background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Text("Map Preview")
                }
            }
        }

        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Create Property", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SetupSuccessStep(appState: PrototypeAppState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(80.dp), tint = Color(0xFF4CAF50))
        Spacer(Modifier.height(24.dp))
        Text("Your property is ready", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(
            "Mapstead created your first property map. You can now add important places, equipment, boundaries, photos, and tasks.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(48.dp))
        
        Button(onClick = { appState.navigateTo(PrototypeDestination.Home) }, modifier = Modifier.fillMaxWidth()) {
            Text("Go to My Property")
        }
        
        Spacer(Modifier.height(16.dp))
        
        OutlinedButton(onClick = { appState.startAddJourney() }, modifier = Modifier.fillMaxWidth()) {
            Text("Add Something Now")
        }
    }
}

// --- Home ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeHomeScreen(appState: PrototypeAppState) {
    val prop = appState.currentProperty ?: return
    var showSwitcher by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { showSwitcher = true }) {
            Text(prop.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ExpandMore, contentDescription = "Switch Property")
        }

        Text("Good Morning!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

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

        if (appState.tasks.isEmpty()) {
            Text("No tasks need attention", color = MaterialTheme.colorScheme.outline)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Needs Attention", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                appState.tasks.filter { it.status != PrototypeTaskStatus.COMPLETED }.take(2).forEach { task ->
                    TaskCard(task) { task.relatedItemId?.let { appState.navigateTo(PrototypeDestination.ItemDetails(it)) } }
                }
            }
        }

        if (appState.items.isEmpty()) {
            Text("Nothing has been added yet.", color = MaterialTheme.colorScheme.outline)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Recently Viewed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                appState.items.take(3).forEach { item ->
                    ItemRow(item) { appState.navigateTo(PrototypeDestination.ItemDetails(item.id)) }
                }
            }
        }
    }

    if (showSwitcher) {
        ModalBottomSheet(onDismissRequest = { showSwitcher = false }) {
            Column(modifier = Modifier.padding(16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Switch Property", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                appState.properties.forEach { p ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { appState.selectProperty(p.id); showSwitcher = false }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Home, contentDescription = null, tint = if (p.id == appState.selectedPropertyId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.width(16.dp))
                        Text(p.name, modifier = Modifier.weight(1f), fontWeight = if (p.id == appState.selectedPropertyId) FontWeight.Bold else FontWeight.Normal)
                        if (p.isSample) Badge { Text("Sample") }
                    }
                }
                TextButton(onClick = { appState.startPropertySetup(); showSwitcher = false }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Property")
                }
            }
        }
    }
}

// --- Map ---

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
            FloatingActionButton(onClick = { mapCenter = Offset(0f, 0f); zoomLevel = 1f }, containerColor = MaterialTheme.colorScheme.surface) {
                Icon(Icons.Default.CenterFocusStrong, contentDescription = "Recenter")
            }
            FloatingActionButton(onClick = { mapCenter = -customerLocation; zoomLevel = 1.5f }, containerColor = MaterialTheme.colorScheme.surface) {
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
                Card(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp, start = 16.dp, end = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Bold)
                            Text(item.locationDescription, style = MaterialTheme.typography.bodySmall)
                        }
                        if (returnToDetails) {
                            Button(onClick = { appState.goBack() }) { Text("Return to Details") }
                        } else {
                            TextButton(onClick = { appState.navigateTo(PrototypeDestination.ItemDetails(item.id)) }) { Text("Details") }
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

// --- Universal Add Something ---

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
                AddStep.Entry -> AddEntryStep(appState, draft)
                is AddStep.BrowsePresets -> BrowsePresetsStep(appState, draft, step.category)
                is AddStep.LocationForm -> CustomLocationFormStep(appState, draft, step.name)
                is AddStep.LocationMethod -> LocationMethodStep(appState, draft, step.name, step.form)
                is AddStep.LocationConfirm -> LocationConfirmStep(step.name) { confirmedDesc ->
                    appState.addDraft = draft.copy(locationDescription = confirmedDesc, latitude = 34.1235, longitude = -118.5671)
                    appState.navigateTo(PrototypeDestination.AddJourney(AddStep.Photo(step.name)))
                }
                is AddStep.MapDrawing -> MapDrawingStep(appState, draft, step.name, step.form)
                is AddStep.Grouping -> GroupingStep(appState, draft, step.name)
                is AddStep.Photo -> PhotoStep { hasPhoto ->
                    appState.addDraft = appState.addDraft?.copy(hasPhoto = hasPhoto)
                    appState.navigateTo(PrototypeDestination.AddJourney(AddStep.Review))
                }
                AddStep.Review -> ReviewStep(appState)
            }
        }
    }
}

@Composable
fun AddEntryStep(appState: PrototypeAppState, draft: PrototypePropertyItem) {
    var name by remember { mutableStateOf("") }
    val commonPresets = listOf("Well", "Main Water Shutoff", "Septic Tank", "Electrical Panel", "Propane Tank", "Pool Pump", "Pool Filter", "Gate", "Fence", "Equipment Shed")
    val suggestions = commonPresets.filter { it.contains(name, ignoreCase = true) }.take(3)

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("What are you adding?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Item Name") },
            placeholder = { Text("e.g., Chicken Coop") },
            modifier = Modifier.fillMaxWidth()
        )

        if (name.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { suggestion ->
                    ListItem(
                        headlineContent = { Text(suggestion) },
                        leadingContent = { Icon(Icons.Default.History, contentDescription = null) },
                        modifier = Modifier.clickable { 
                            appState.addDraft = draft.copy(name = suggestion, category = "Suggested")
                            appState.navigateTo(PrototypeDestination.AddJourney(AddStep.LocationMethod(suggestion, ItemLocationForm.MARK_ONE)))
                        }
                    )
                }
                ListItem(
                    headlineContent = { Text("Create \"$name\"") },
                    leadingContent = { Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { 
                        appState.addDraft = draft.copy(name = name)
                        appState.navigateTo(PrototypeDestination.AddJourney(AddStep.LocationForm(name, false)))
                    }
                )
            }
        }

        OutlinedButton(onClick = { appState.navigateTo(PrototypeDestination.AddJourney(AddStep.BrowsePresets())) }, modifier = Modifier.fillMaxWidth()) {
            Text("Browse Common Items")
        }
    }
}

@Composable
fun BrowsePresetsStep(appState: PrototypeAppState, draft: PrototypePropertyItem, cat: String?) {
    if (cat == null) {
        val cats = listOf("Water & Plumbing", "Power & Electrical", "Buildings & Structures", "Boundaries & Access", "Outdoor & Land", "Pool Equipment", "Safety & Emergency")
        LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(cats) { c ->
                Card(onClick = { appState.replaceTop(PrototypeDestination.AddJourney(AddStep.BrowsePresets(c))) }, modifier = Modifier.height(100.dp)) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(c, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp)) }
                }
            }
        }
    } else {
        val presets = when (cat) {
            "Pool Equipment" -> listOf("Pool Pump", "Pool Filter", "Pool Heater", "Pool Control Panel")
            "Water & Plumbing" -> listOf("Well", "Main Water Shutoff", "Septic Tank")
            else -> listOf("Custom Item")
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(cat, style = MaterialTheme.typography.titleLarge)
            presets.forEach { p ->
                OutlinedButton(onClick = { 
                    appState.addDraft = draft.copy(name = p, category = cat)
                    appState.navigateTo(PrototypeDestination.AddJourney(AddStep.LocationMethod(p, ItemLocationForm.MARK_ONE)))
                }, modifier = Modifier.fillMaxWidth()) { Text(p) }
            }
        }
    }
}

@Composable
fun CustomLocationFormStep(appState: PrototypeAppState, draft: PrototypePropertyItem, name: String) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("How should this appear on your property?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        
        LocationOption("Mark one location", "A single point on the map", Icons.Default.Place) { 
            appState.navigateTo(PrototypeDestination.AddJourney(AddStep.LocationMethod(name, ItemLocationForm.MARK_ONE)))
        }
        LocationOption("Draw where it runs", "A line for a fence or pipe", Icons.Default.Timeline) { 
            appState.navigateTo(PrototypeDestination.AddJourney(AddStep.MapDrawing(name, ItemLocationForm.DRAW_RUNS)))
        }
        LocationOption("Outline the area", "A shape for a garden or yard", Icons.Default.ChangeHistory) { 
            appState.navigateTo(PrototypeDestination.AddJourney(AddStep.MapDrawing(name, ItemLocationForm.OUTLINE_AREA)))
        }
        LocationOption("Add the location later", "Save details now, place later", Icons.Default.Schedule) { 
            appState.addDraft = draft.copy(locationDescription = "Needs Location", needsLocation = true)
            appState.navigateTo(PrototypeDestination.AddJourney(AddStep.Grouping(name)))
        }
    }
}

@Composable
fun LocationMethodStep(appState: PrototypeAppState, draft: PrototypePropertyItem, name: String, form: ItemLocationForm) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Where is the $name?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        
        LocationOption("I’m Standing Next to It", "Use your current position", Icons.Default.MyLocation) {
            appState.navigateTo(PrototypeDestination.AddJourney(AddStep.LocationConfirm(name, "Standing")))
        }
        LocationOption("Place It on the Map", "Pick a spot manually", Icons.Default.Map) {
            appState.navigateTo(PrototypeDestination.AddJourney(AddStep.LocationConfirm(name, "Map")))
        }
        LocationOption("Add the Location Later", "Save just the details for now", Icons.Default.Schedule) {
            appState.addDraft = draft.copy(locationDescription = "Needs Location", needsLocation = true)
            appState.navigateTo(PrototypeDestination.AddJourney(AddStep.Grouping(name)))
        }
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
                Text("Simulated coordinate confirmed", style = MaterialTheme.typography.bodySmall)
            }
        }
        Button(onClick = { onConfirm("Confirmed location near house") }, modifier = Modifier.fillMaxWidth()) { Text("Confirm Location") }
    }
}

@Composable
fun MapDrawingStep(appState: PrototypeAppState, draft: PrototypePropertyItem, name: String, form: ItemLocationForm) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(if (form == ItemLocationForm.DRAW_RUNS) "Draw where it runs" else "Outline the area", style = MaterialTheme.typography.titleLarge)
        Box(modifier = Modifier.fillMaxWidth().height(300.dp).background(Color(0xFFFFF9C4), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Text("Tap on this fake map to draw $name", fontWeight = FontWeight.Bold)
        }
        Button(onClick = { 
            appState.addDraft = draft.copy(locationDescription = if (form == ItemLocationForm.DRAW_RUNS) "Drawn route" else "Outlined area")
            appState.navigateTo(PrototypeDestination.AddJourney(AddStep.Grouping(name)))
        }, modifier = Modifier.fillMaxWidth()) { Text("Done Drawing") }
    }
}

@Composable
fun GroupingStep(appState: PrototypeAppState, draft: PrototypePropertyItem, name: String) {
    val groups = listOf("Water & Plumbing", "Power & Electrical", "Buildings & Structures", "Boundaries & Access", "Outdoor & Land", "Equipment", "Safety & Emergency", "Other", "Not Sure")
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("How should this be grouped?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(groups) { g ->
                OutlinedButton(onClick = { 
                    appState.addDraft = draft.copy(category = g)
                    appState.navigateTo(PrototypeDestination.AddJourney(AddStep.Photo(name)))
                }) { Text(g, textAlign = TextAlign.Center) }
            }
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
            Button(onClick = { onNext(true) }, modifier = Modifier.fillMaxWidth()) { Text("Take Photo") }
            OutlinedButton(onClick = { onNext(true) }, modifier = Modifier.fillMaxWidth()) { Text("Choose Photo") }
            TextButton(onClick = { onNext(false) }) { Text("Continue Without Photo") }
        }
    }
}

@Composable
fun ReviewStep(appState: PrototypeAppState) {
    val draft = appState.addDraft ?: return
    var showNameDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("Review & Save", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ReviewRow("Name", draft.name, Icons.Default.Edit) { showNameDialog = true }
                ReviewRow("Group", draft.category, Icons.Default.Edit) { appState.goBack() }
                ReviewRow("Location", draft.locationDescription, Icons.Default.Place) { appState.goBack() }
                ReviewRow("Photo", if (draft.hasPhoto) "1 Photo attached" else "No photo", Icons.Default.CameraAlt) { appState.goBack() }
                OutlinedTextField(value = draft.note ?: "", onValueChange = { appState.addDraft = draft.copy(note = it) }, label = { Text("Add a note (optional)") }, modifier = Modifier.fillMaxWidth())
            }
        }
        Button(onClick = { appState.completeAddJourney(draft) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
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
                        TextButton(onClick = { appState.addDraft = draft.copy(name = newName); showNameDialog = false }) { Text("OK") }
                    }
                }
            }
        }
    }
}

// --- Common Components ---

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
            OutlinedTextField(value = appState.searchQuery, onValueChange = { appState.searchQuery = it }, placeholder = { Text("Search Items") }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("All", "Water", "Power", "Buildings", "Safety")) { cat ->
                    FilterChip(selected = appState.selectedCategory == cat, onClick = { appState.selectedCategory = cat }, label = { Text(cat) })
                }
            }
        }
        if (filteredItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nothing found.", color = MaterialTheme.colorScheme.outline) }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filteredItems, key = { it.id }) { item -> ItemRow(item) { appState.navigateTo(PrototypeDestination.ItemDetails(item.id)) } }
            }
        }
    }
}

@Composable
fun PrototypeTasksScreen(appState: PrototypeAppState) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Tasks", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        if (appState.tasks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No tasks need attention.", color = MaterialTheme.colorScheme.outline) }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrototypeTaskStatus.entries.forEach { status ->
                    val tasks = appState.tasks.filter { it.status == status }
                    if (tasks.isNotEmpty()) {
                        Text(status.name.replace("_", " ").lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.outline)
                        tasks.forEach { task -> TaskCard(task) { task.relatedItemId?.let { appState.navigateTo(PrototypeDestination.ItemDetails(it)) } } }
                    }
                }
            }
        }
    }
}

@Composable
fun PrototypeEmergencyGuide(appState: PrototypeAppState) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { appState.goBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Emergency Guide", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Text(text = "The Emergency Guide keeps important property information in one place. It does not contact emergency services. For immediate danger, call 911.", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Critical Controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            appState.items.filter { it.isEmergency }.forEach { item -> ItemRow(item) { appState.navigateTo(PrototypeDestination.ItemDetails(item.id)) } }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Property Info", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            DetailRow(Icons.Default.Home, "Address", appState.currentProperty?.address ?: "No address set")
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
                    IconButton(onClick = { appState.goBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(if (item.hasPhoto) Icons.Default.CameraAlt else Icons.Default.Image, contentDescription = null, modifier = Modifier.size(48.dp), tint = if (item.hasPhoto) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
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
                item.note?.let { Text("Note: $it", style = MaterialTheme.typography.bodyMedium) }
            }
            DetailSection("Where It Is") {
                if (item.needsLocation) {
                    Text("No location set.", color = MaterialTheme.colorScheme.error)
                } else {
                    Text("Description: ${item.locationDescription}")
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
                    }
                }
            }
        }
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
fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        content()
    }
}
