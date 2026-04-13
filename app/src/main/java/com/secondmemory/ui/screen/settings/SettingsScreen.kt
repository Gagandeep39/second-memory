package com.secondmemory.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.secondmemory.domain.llm.LlmSummaryClient
import com.secondmemory.domain.model.AppSettings
import com.secondmemory.domain.model.SyncState
import com.secondmemory.domain.repository.SettingsRepository
import com.secondmemory.util.formatDateTime
import kotlinx.coroutines.launch

/**
 * Screen that exposes persisted app-level feature toggles and sync controls.
 */
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    llmSummaryClient: LlmSummaryClient,
) {
    val scope = rememberCoroutineScope()
    val settings by settingsRepository.observeSettings().collectAsState(
        initial = AppSettings(
            driveSyncEnabled = false,
            cloudSummaryEnabled = false,
            geminiApiKey = "",
            syncState = SyncState.IDLE,
            lastSyncAtMillis = null,
            lastSyncMessage = null,
        )
    )
    var geminiApiKeyDraft by remember(settings.geminiApiKey) {
        mutableStateOf(settings.geminiApiKey)
    }
    var geminiStatusMessage by remember { mutableStateOf<String?>(null) }

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

        Text(
            text = "Sync Status",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = when (settings.syncState) {
                SyncState.IDLE -> "Idle"
                SyncState.SYNCING -> "Syncing now"
                SyncState.SUCCESS -> "Last sync succeeded"
                SyncState.ERROR -> "Last sync failed"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        settings.lastSyncAtMillis?.let { timestamp ->
            Text(
                text = "Last sync time: ${formatDateTime(timestamp)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        settings.lastSyncMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Button(
            enabled = settings.driveSyncEnabled,
            onClick = {
                scope.launch {
                    settingsRepository.syncNow()
                }
            },
        ) {
            Text("Sync Now")
        }

        OutlinedTextField(
            value = geminiApiKeyDraft,
            onValueChange = { geminiApiKeyDraft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Gemini API Key") },
            visualTransformation = PasswordVisualTransformation(),
        )

        Button(
            onClick = {
                scope.launch {
                    settingsRepository.setGeminiApiKey(geminiApiKeyDraft)
                    geminiStatusMessage = "Gemini key saved."
                }
            },
        ) {
            Text("Save Gemini Key")
        }

        Button(
            enabled = geminiApiKeyDraft.isNotBlank(),
            onClick = {
                scope.launch {
                    geminiStatusMessage = "Testing Gemini key..."
                    runCatching {
                        llmSummaryClient.testConnection(geminiApiKeyDraft)
                    }.onSuccess {
                        geminiStatusMessage = "Gemini key is valid."
                    }.onFailure { error ->
                        geminiStatusMessage = "Gemini key test failed: ${error.message}"
                    }
                }
            },
        ) {
            Text("Test Gemini Key")
        }

        geminiStatusMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (settings.geminiApiKey.isBlank()) {
            Text(
                text = "Add Gemini API key to enable cloud summaries.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            SettingToggleRow(
                title = "Cloud Summaries",
                description = "Allow daily summary generation using Gemini cloud model.",
                checked = settings.cloudSummaryEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        settingsRepository.setCloudSummaryEnabled(enabled)
                    }
                },
            )
        }
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
