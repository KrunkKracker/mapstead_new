package com.jumastappworks.mapstead.ui.prototype

import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            onClick = { appState.navigateTo(PrototypeDestination.AddJourney(AddStep.Category)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add Something", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(
                onClick = { appState.navigateTo(PrototypeDestination.Items) },
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
            FakeTasks.filter { it.status != PrototypeTaskStatus.COMPLETED }.take(2).forEach { task ->
                TaskCard(task) { task.relatedItemId?.let { appState.navigateTo(PrototypeDestination.ItemDetails(it)) } }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Recently Viewed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            FakeItems.take(3).forEach { item ->
                ItemRow(item) { appState.navigateTo(PrototypeDestination.ItemDetails(item.id)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeMapScreen(appState: PrototypeAppState, highlightItemId: UUID? = null, onBack: (() -> Unit)? = null) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Fake Map Illustration
        Canvas(modifier = Modifier.fillMaxSize().background(Color(0xFFE1F5FE))) {
            // Draw property boundary, house, pool, etc. (Simulated)
        }
        
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.height(8.dp))
            }
            
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
            FloatingActionButton(onClick = { /* Recenter */ }, containerColor = MaterialTheme.colorScheme.surface) {
                Icon(Icons.Default.CenterFocusStrong, contentDescription = "Recenter")
            }
            FloatingActionButton(onClick = { /* My Location */ }, containerColor = MaterialTheme.colorScheme.surface) {
                Icon(Icons.Default.MyLocation, contentDescription = "My Location")
            }
            FloatingActionButton(onClick = { appState.navigateTo(PrototypeDestination.AddJourney(AddStep.Category)) }) {
                Icon(Icons.Default.Add, contentDescription = "Add Something")
            }
        }

        if (highlightItemId != null) {
            val item = FakeItems.find { it.id == highlightItemId }
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
                        TextButton(onClick = { appState.navigateTo(PrototypeDestination.ItemDetails(item.id)) }) {
                            Text("Details")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeItemsScreen(appState: PrototypeAppState) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Property Items", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            
            OutlinedTextField(
                value = "",
                onValueChange = {},
                placeholder = { Text("Search Items") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("All", "Water", "Power", "Buildings", "Safety")) { cat ->
                    FilterChip(selected = cat == "All", onClick = {}, label = { Text(cat) })
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(FakeItems) { item ->
                ItemRow(item) { appState.navigateTo(PrototypeDestination.ItemDetails(item.id, from = PrototypeDestination.Items)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeTasksScreen(appState: PrototypeAppState) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Tasks", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PrototypeTaskStatus.entries.forEach { status ->
                val tasks = FakeTasks.filter { it.status == status }
                if (tasks.isNotEmpty()) {
                    Text(status.name.replace("_", " ").lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.outline)
                    tasks.forEach { task ->
                        TaskCard(task) { task.relatedItemId?.let { appState.navigateTo(PrototypeDestination.ItemDetails(it, from = PrototypeDestination.Tasks)) } }
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
            Text("Emergency Guide", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
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
            FakeItems.filter { it.isEmergency }.forEach { item ->
                ItemRow(item) { appState.navigateTo(PrototypeDestination.ItemDetails(item.id, from = PrototypeDestination.EmergencyGuide)) }
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
fun PrototypeItemDetails(appState: PrototypeAppState, itemId: UUID, from: PrototypeDestination) {
    val item = FakeItems.find { it.id == itemId } ?: return
    
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
            // Main Photo Placeholder
            Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(item.category, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("Status: ${item.status}", style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = { appState.navigateTo(PrototypeDestination.Map(highlightItemId = item.id, returnTo = PrototypeDestination.ItemDetails(itemId, from))) }) {
                    Icon(Icons.Default.Map, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Show on Map")
                }
            }

            DetailSection("At a Glance") {
                Text(item.locationDescription)
            }

            val tasks = FakeTasks.filter { it.relatedItemId == item.id }
            if (tasks.isNotEmpty()) {
                DetailSection("Needs Attention") {
                    tasks.forEach { TaskCard(it) {} }
                }
            }

            DetailSection("Where It Is") {
                Text("Latitude: 34.1234, Longitude: -118.5678")
                Text("Near the equipment wall, marked with a blue flag.")
            }

            DetailSection("Photos & Files") {
                Text("2 photos, 1 manual (PDF)")
            }

            if (item.isEmergency) {
                DetailSection("Emergency Information") {
                    Text("CRITICAL: Turn clockwise to shut off.")
                }
            }

            DetailSection("More Details") {
                Text("Manufacturer: Pentair")
                Text("Model: IntelliFlo VSF")
                Text("Installed: June 2022")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeAddJourney(appState: PrototypeAppState, step: AddStep) {
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
                AddStep.Category -> CategoryStep { appState.navigateTo(PrototypeDestination.AddJourney(AddStep.Preset(it))) }
                is AddStep.Preset -> PresetStep(step.category) { appState.navigateTo(PrototypeDestination.AddJourney(AddStep.LocationChoice(it, step.category))) }
                is AddStep.LocationChoice -> LocationChoiceStep(step.name) { 
                    appState.navigateTo(PrototypeDestination.AddJourney(AddStep.LocationConfirm(step.name, step.category))) 
                }
                is AddStep.LocationConfirm -> LocationConfirmStep(step.name) {
                    appState.navigateTo(PrototypeDestination.AddJourney(AddStep.Photo(step.name, step.category)))
                }
                is AddStep.Photo -> PhotoStep { hasPhoto ->
                    appState.navigateTo(PrototypeDestination.AddJourney(AddStep.Review(step.name, step.category, hasPhoto)))
                }
                is AddStep.Review -> ReviewStep(step) {
                    // Update state and navigate to details
                    appState.navigateTo(PrototypeDestination.ItemDetails(FakeItems.find { it.name == "Pool Pump" }!!.id))
                }
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
fun LocationChoiceStep(name: String, onNext: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Where is the $name?", style = MaterialTheme.typography.titleLarge)
        
        LocationOption("I’m Standing Next to It", "Use your current position", Icons.Default.MyLocation, onNext)
        LocationOption("Place It on the Map", "Pick a spot manually", Icons.Default.Map, onNext)
        LocationOption("Add the Location Later", "Save just the details for now", Icons.Default.Schedule, onNext)
    }
}

@Composable
fun LocationConfirmStep(name: String, onConfirm: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Confirm Location", style = MaterialTheme.typography.titleLarge)
        
        Box(modifier = Modifier.fillMaxWidth().height(250.dp).background(Color(0xFFE8F5E9), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                Text("Suggested Position", fontWeight = FontWeight.Bold)
                Text("Near the pool equipment wall", style = MaterialTheme.typography.bodySmall)
            }
        }

        Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
            Text("Confirm Location")
        }
        
        OutlinedButton(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
            Text("Adjust on Map")
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
fun ReviewStep(step: AddStep.Review, onSave: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Review & Save", style = MaterialTheme.typography.titleLarge)
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ReviewRow("Name", step.name, Icons.Default.Edit)
                ReviewRow("Category", step.category, null)
                ReviewRow("Location", "Near pool equipment", Icons.Default.Place)
                ReviewRow("Photo", if (step.hasPhoto) "1 Photo attached" else "No photo", Icons.Default.CameraAlt)
                
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    label = { Text("Add a note (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Save Item", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ReviewRow(label: String, value: String, icon: ImageVector?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        if (icon != null) {
            IconButton(onClick = {}) { Icon(icon, contentDescription = "Edit $label", modifier = Modifier.size(20.dp)) }
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
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
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
