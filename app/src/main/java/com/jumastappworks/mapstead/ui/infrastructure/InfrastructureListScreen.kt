package com.jumastappworks.mapstead.ui.infrastructure

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Foundation
import androidx.compose.material.icons.filled.Warning
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
import com.jumastappworks.mapstead.data.db.entities.InfrastructureItemEntity
import com.jumastappworks.mapstead.data.help.HelpTopicId
import com.jumastappworks.mapstead.ui.components.EmptyState
import com.jumastappworks.mapstead.ui.mapping.MapViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfrastructureListScreen(
    viewModel: MapViewModel,
    onBack: () -> Unit,
    onAddItemClick: () -> Unit,
    onEditItemClick: (UUID) -> Unit,
    onHelpClick: (HelpTopicId) -> Unit
) {
    val items by viewModel.infrastructureItems.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.infrastructure), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            if (items.isNotEmpty()) {
                FloatingActionButton(onClick = onAddItemClick) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_item_title))
                }
            }
        }
    ) { padding ->
        if (items.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.empty_infra_title),
                description = stringResource(R.string.empty_infra_desc),
                icon = Icons.Default.Foundation,
                primaryActionLabel = stringResource(R.string.add_item_title),
                onPrimaryAction = onAddItemClick,
                helpTopicId = HelpTopicId.INFRASTRUCTURE,
                onHelpClick = onHelpClick,
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items) { item ->
                    InfrastructureListItem(
                        item = item,
                        onClick = { onEditItemClick(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun InfrastructureListItem(
    item: InfrastructureItemEntity,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(item.category, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                item.subtype?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall)
                }
            }
            if (item.isEmergencyItem) {
                Surface(
                    color = Color(0xFFF97316), // Safety Orange
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = Color.Black
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Emergency",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}
