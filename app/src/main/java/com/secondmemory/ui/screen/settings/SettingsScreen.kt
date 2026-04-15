package com.secondmemory.ui.screen.settings

import android.app.Activity
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.secondmemory.R
import com.secondmemory.domain.llm.LlmSummaryClient
import com.secondmemory.domain.model.AppSettings
import com.secondmemory.domain.model.SyncState
import com.secondmemory.domain.repository.SettingsRepository
import com.secondmemory.util.formatDateTime
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Screen that exposes persisted app-level feature toggles and sync controls.
 */
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    llmSummaryClient: LlmSummaryClient,
    onOpenOperationLogs: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val settings by settingsRepository.observeSettings().collectAsState(
        initial = AppSettings(
            driveSyncEnabled = false,
            connectedGoogleAccountEmail = null,
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
    val googleWebClientId = stringResource(R.string.google_web_client_id)
    val context = LocalContext.current
    val credentialManager = CredentialManager.create(context)
    var geminiStatusMessage by remember { mutableStateOf<String?>(null) }
    var driveStatusMessage by remember { mutableStateOf<String?>(null) }
    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        driveStatusMessage = if (result.resultCode == Activity.RESULT_OK) {
            "Drive authorization granted."
        } else {
            "Drive authorization was not completed."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        // --- Google Drive Sync Section ---
        SettingsSection(title = "Sync & Backup", icon = Icons.Default.Sync) {
            if (settings.connectedGoogleAccountEmail.isNullOrBlank()) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            driveStatusMessage = "Connecting Google account..."
                            val request = buildGoogleCredentialRequest(googleWebClientId)
                            runCatching {
                                val result = credentialManager.getCredential(
                                    context = context,
                                    request = request,
                                )
                                extractGoogleAccountEmail(result)
                            }.onSuccess { email ->
                                settingsRepository.setConnectedGoogleAccountEmail(email)
                                driveStatusMessage = if (email.isNullOrBlank()) {
                                    "Google connected, but account email was unavailable."
                                } else {
                                    "Google account connected."
                                }
                            }.onFailure { error ->
                                driveStatusMessage = when (error) {
                                    is GetCredentialCancellationException -> "Google sign-in was cancelled."
                                    is NoCredentialException -> "No Google credential available on this device."
                                    is GetCredentialException -> "Google sign-in failed: ${error.message}"
                                    else -> "Google sign-in failed: ${error.message}"
                                }
                            }
                        }
                    },
                ) {
                    Text("Connect Google Account")
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Connected account",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = settings.connectedGoogleAccountEmail ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        TextButton(
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        credentialManager.clearCredentialState(ClearCredentialStateRequest())
                                    }
                                    settingsRepository.setConnectedGoogleAccountEmail(null)
                                    settingsRepository.setDriveSyncEnabled(false)
                                    driveStatusMessage = "Google account disconnected."
                                }
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Disconnect")
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !settings.connectedGoogleAccountEmail.isNullOrBlank() &&
                                settings.syncState != SyncState.SYNCING,
                        onClick = {
                            scope.launch {
                                runCatching {
                                    settingsRepository.syncNow()
                                }.onSuccess {
                                    driveStatusMessage = "Drive sync finished."
                                }.onFailure { error ->
                                    val recoverableAuth = error as? UserRecoverableAuthException
                                        ?: error.cause as? UserRecoverableAuthException
                                    val recoverableIoAuth = error as? UserRecoverableAuthIOException
                                        ?: error.cause as? UserRecoverableAuthIOException
                                    if (recoverableAuth != null) {
                                        recoverableAuth.intent?.let { consentLauncher.launch(it) }
                                        driveStatusMessage = "Google authorization required."
                                    } else if (recoverableIoAuth != null) {
                                        consentLauncher.launch(recoverableIoAuth.intent)
                                        driveStatusMessage = "Google authorization required."
                                    } else {
                                        driveStatusMessage = "Sync failed: ${error.message}"
                                    }
                                }
                            }
                        },
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Sync Now")
                    }

                    Text(
                        text = "Sync works bidirectionally: files are uploaded to and downloaded from Drive.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    // Sync Status Card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Status",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = when (settings.syncState) {
                                    SyncState.IDLE -> "Idle"
                                    SyncState.SYNCING -> "Syncing now..."
                                    SyncState.SUCCESS -> "Last sync succeeded"
                                    SyncState.ERROR -> "Last sync failed"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            settings.lastSyncAtMillis?.let { timestamp ->
                                Text(
                                    text = "Last sync: ${formatDateTime(timestamp)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            settings.lastSyncMessage?.let { message ->
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (settings.syncState == SyncState.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    SettingToggleRow(
                        title = "Daily Auto-Sync",
                        description = "Automatically sync in the background once per day.",
                        checked = settings.driveSyncEnabled,
                        enabled = !settings.connectedGoogleAccountEmail.isNullOrBlank(),
                        onCheckedChange = { enabled ->
                            scope.launch {
                                settingsRepository.setDriveSyncEnabled(enabled)
                            }
                        },
                    )
                }
            }

            driveStatusMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // --- AI Features Section ---
        SettingsSection(title = "AI Features", icon = Icons.Default.Cloud) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = geminiApiKeyDraft,
                    onValueChange = { geminiApiKeyDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Gemini API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    supportingText = {
                        Text("Required for cloud summaries and AI insights.")
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            scope.launch {
                                settingsRepository.setGeminiApiKey(geminiApiKeyDraft)
                                geminiStatusMessage = "Gemini key saved."
                            }
                        },
                    ) {
                        Text("Save Key")
                    }

                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = geminiApiKeyDraft.isNotBlank(),
                        onClick = {
                            scope.launch {
                                geminiStatusMessage = "Testing Gemini key..."
                                runCatching {
                                    llmSummaryClient.testConnection(geminiApiKeyDraft)
                                }.onSuccess {
                                    geminiStatusMessage = "Gemini key is valid."
                                }.onFailure { error ->
                                    geminiStatusMessage = "Test failed: ${error.message}"
                                }
                            }
                        },
                    ) {
                        Text("Test")
                    }
                }

                geminiStatusMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                HorizontalDivider()

                SettingToggleRow(
                    title = "Cloud Summaries",
                    description = "Use Gemini to generate daily summaries of your thoughts.",
                    checked = settings.cloudSummaryEnabled,
                    enabled = settings.geminiApiKey.isNotBlank(),
                    onCheckedChange = { enabled ->
                        scope.launch {
                            settingsRepository.setCloudSummaryEnabled(enabled)
                        }
                    },
                )

                if (settings.geminiApiKey.isBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Add API key to enable cloud summaries.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // --- Advanced Section ---
        SettingsSection(title = "Advanced", icon = Icons.Default.History) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenOperationLogs
            ) {
                Icon(Icons.Default.History, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("View Operation Logs")
            }
        }
    }
}

/**
 * A container for a group of related settings, styled with a title, icon, and a card background.
 *
 * @param title The display name of the section.
 * @param icon The icon to show next to the title.
 * @param content The composable content to be placed inside the section card.
 */
@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                content()
            }
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
    enabled: Boolean = true,
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
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

/**
 * Extracts the Google account identifier from a Credential Manager response.
 */
private fun extractGoogleAccountEmail(response: GetCredentialResponse): String? {
    val credential = response.credential
    if (credential !is CustomCredential) return null
    if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) return null

    return try {
        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
        extractEmailClaimFromIdToken(googleCredential.idToken)
            ?: googleCredential.id.takeIf { it.contains('@') }
            ?: credential.data.getString("email")?.takeIf { it.contains('@') }
    } catch (_: GoogleIdTokenParsingException) {
        null
    }
}

/**
 * Builds a credential request that supports both One Tap and Sign in with Google flows.
 */
private fun buildGoogleCredentialRequest(serverClientId: String): GetCredentialRequest {
    val googleIdOption = GetGoogleIdOption.Builder()
        .setServerClientId(serverClientId)
        .setFilterByAuthorizedAccounts(false)
        .setAutoSelectEnabled(false)
        .build()

    return GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()
}

/**
 * Reads the email claim from a Google ID token payload.
 */
private fun extractEmailClaimFromIdToken(idToken: String): String? {
    return try {
        val payload = idToken.split('.')
            .getOrNull(1)
            ?.let { Base64.decode(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) }
            ?.toString(Charsets.UTF_8)
            ?: return null
        JSONObject(payload).optString("email").takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}
