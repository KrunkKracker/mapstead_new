package com.jumastappworks.mapstead.ui.help

import androidx.compose.foundation.clickable
import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.help.*
import com.jumastappworks.mapstead.ui.components.KeyboardPolicy
import com.jumastappworks.mapstead.ui.components.TextFieldSemanticType

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpCenterScreen(
    onTopicClick: (HelpTopicId) -> Unit,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    
    val resolvedTopics = remember(context) {
        HelpContent.TOPICS.associate { topic ->
            topic.id to ResolvedHelpTopic(
                id = topic.id,
                title = context.getString(topic.titleRes),
                summary = context.getString(topic.summaryRes),
                sections = topic.sections.map { ResolvedHelpSection(context.getString(it.headingRes), context.getString(it.bodyRes)) },
                keywords = topic.keywords.map { context.getString(it) }
            )
        }
    }

    val filteredTopics = remember(searchQuery) {
        HelpSearchEngine.searchHelpTopics(HelpContent.TOPICS, resolvedTopics, searchQuery)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help_center_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.help_search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardPolicy.getOptions(TextFieldSemanticType.SEARCH),
                keyboardActions = KeyboardPolicy.getActions(focusManager, onSearch = { focusManager.clearFocus() }),
                shape = MaterialTheme.shapes.medium
            )

            if (filteredTopics.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.help_no_results, searchQuery),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (searchQuery.isBlank()) {
                        item {
                            Text(
                                text = stringResource(R.string.help_start_here),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        
                        val startTopic = HelpContent.TOPICS.find { it.id == HelpTopicId.GETTING_STARTED }
                        if (startTopic != null) {
                            item {
                                HelpTopicCard(topic = startTopic, onClick = { onTopicClick(startTopic.id) })
                            }
                        }

                        HelpCategory.entries.forEach { category ->
                            val categoryTopics = HelpContent.TOPICS.filter { it.category == category && it.id != HelpTopicId.GETTING_STARTED }
                            if (categoryTopics.isNotEmpty()) {
                                item {
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        text = getCategoryLabel(category),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                items(categoryTopics) { topic ->
                                    HelpTopicCard(topic = topic, onClick = { onTopicClick(topic.id) })
                                }
                            }
                        }
                    } else {
                        items(filteredTopics) { topic ->
                            HelpTopicCard(topic = topic, onClick = { onTopicClick(topic.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun getCategoryLabel(category: HelpCategory): String = when (category) {
    HelpCategory.BASICS -> stringResource(R.string.help_category_basics)
    HelpCategory.MAPPING -> stringResource(R.string.help_category_mapping)
    HelpCategory.PROPERTY_RECORDS -> stringResource(R.string.help_category_property_records)
    HelpCategory.MAINTENANCE_SAFETY -> stringResource(R.string.help_category_maintenance_safety)
    HelpCategory.DATA_APP_INFO -> stringResource(R.string.help_category_data_app_info)
}

@Composable
private fun HelpTopicCard(
    topic: HelpTopic,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(topic.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(topic.summaryRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}
