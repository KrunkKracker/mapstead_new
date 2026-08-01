package com.jumastappworks.mapstead.ui.emergency

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.help.HelpTopicId
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyScreen(
    viewModel: EmergencyViewModel,
    onBack: () -> Unit,
    onOpenMap: (UUID, UUID) -> Unit, // planId, featureId
    onEditItem: (UUID) -> Unit, // itemId
    onHelpClick: (HelpTopicId) -> Unit
) {
    val items by viewModel.emergencyItems.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.emergency_mode), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E), // Graphite
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF1E1E1E)
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                // Using custom themed empty state because global EmptyState is Light/Dark aware but 
                // Emergency Screen is forced dark.
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No emergency items mapped yet.",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(24.dp))
                    TextButton(onClick = { onHelpClick(HelpTopicId.EMERGENCY_MODE) }) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Gray)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.what_is_this), color = Color.Gray)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    EmergencyWarningBanner()
                }
                items(items) { itemWithLoc ->
                    EmergencyItemCard(
                        itemWithLoc = itemWithLoc,
                        onOpenMap = onOpenMap,
                        onEditItem = onEditItem
                    )
                }
            }
        }
    }
}

@Composable
fun EmergencyWarningBanner() {
    Surface(
        color = Color(0xFFEF4444), // Red
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.critical_systems_warning),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun EmergencyItemCard(
    itemWithLoc: EmergencyItemWithLocation,
    onOpenMap: (UUID, UUID) -> Unit,
    onEditItem: (UUID) -> Unit
) {
    val item = itemWithLoc.item
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D2D)), // Muted Graphite
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFFF97316), RoundedCornerShape(8.dp)), // Safety Orange
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Black)
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(item.category, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }
                IconButton(onClick = { onEditItem(item.id) }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Item", tint = Color.White)
                }
            }
            if (!item.emergencyInstructions.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                Text("Emergency Instructions:", fontWeight = FontWeight.Bold, color = Color(0xFFF97316))
                Text(item.emergencyInstructions, color = Color.White)
            }
            if (itemWithLoc.planId != null && itemWithLoc.featureId != null) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onOpenMap(itemWithLoc.planId, itemWithLoc.featureId) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("Open Mapped Location (${itemWithLoc.planName ?: "Map"})", color = Color.Black)
                }
            }
        }
    }
}
