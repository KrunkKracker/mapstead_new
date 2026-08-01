package com.jumastappworks.mapstead.ui.help

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jumastappworks.mapstead.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.privacy_policy)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            PrivacySection(
                title = stringResource(R.string.privacy_section_storage_title),
                content = stringResource(R.string.privacy_section_storage_body)
            )

            PrivacySection(
                title = stringResource(R.string.privacy_section_files_title),
                content = stringResource(R.string.privacy_section_files_body)
            )

            PrivacySection(
                title = stringResource(R.string.privacy_section_cloud_title),
                content = stringResource(R.string.privacy_section_cloud_body)
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.privacy_section_permissions_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                PermissionItem(
                    label = stringResource(R.string.privacy_section_internet_label),
                    body = stringResource(R.string.privacy_section_internet_body)
                )

                PermissionItem(
                    label = stringResource(R.string.privacy_section_location_label),
                    body = stringResource(R.string.privacy_section_location_body)
                )
                
                PermissionItem(
                    label = stringResource(R.string.privacy_section_notifications_label),
                    body = stringResource(R.string.privacy_section_notifications_body)
                )
                
                PermissionItem(
                    label = stringResource(R.string.privacy_section_media_label),
                    body = stringResource(R.string.privacy_section_media_body)
                )
            }

            PrivacySection(
                title = stringResource(R.string.privacy_section_export_title),
                content = stringResource(R.string.privacy_section_export_body)
            )

            PrivacySection(
                title = stringResource(R.string.privacy_section_removal_title),
                content = stringResource(R.string.privacy_section_removal_body)
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PermissionItem(label: String, body: String) {
    Column(modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(text = body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PrivacySection(
    title: String,
    content: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
