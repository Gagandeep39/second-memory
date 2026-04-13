package com.secondmemory.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import com.secondmemory.domain.model.AppSettings
import com.secondmemory.domain.repository.SettingsRepository
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp

/**
 * Screen that exposes persisted app-level feature toggles.
 */
@Composable
fun SettingsScreen(settingsRepository: SettingsRepository) {
    val scope = rememberCoroutineScope()
    val settings by settingsRepository.observeSettings().collectAsState(
        initial = AppSettings(
            driveSyncEnabled = false,
            cloudSummaryEnabled = true,
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
        )
        SettingToggleRow(
            title = "Google Drive Sync",
            description = "Enable synchronization of local daily files to Drive.",
            checked = settings.driveSyncEnabled,
            onCheckedChange = { enabled ->
                scope.launch {
                    settingsRepository.setDriveSyncEnabled(enabled)
                }
            },
        )
        SettingToggleRow(
            title = "Cloud Summaries",
            description = "Allow daily summary generation using a cloud model.",
            checked = settings.cloudSummaryEnabled,
            onCheckedChange = { enabled ->
                scope.launch {
                    settingsRepository.setCloudSummaryEnabled(enabled)
                }
            },
        )
    }
}

/**
 * Reusable row that displays a title, description, and toggle control.
 */
@Composable
private fun SettingToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.8f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
