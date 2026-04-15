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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )

            // --- Google Drive Sync Section ---
            SettingsSection(title = "Sync & Backup", icon = Icons.Outlined.Sync) {
                if (settings.connectedGoogleAccountEmail.isNullOrBlank()) {
                    ListItem(
                        headlineContent = { Text("Cloud Backup") },
                        supportingContent = { Text("Connect your Google account to sync your thoughts across devices.") },
                        leadingContent = { Icon(Icons.Outlined.Cloud, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                    ListItem(
                        headlineContent = {
                            Text(
                                text = settings.connectedGoogleAccountEmail ?: "",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        overlineContent = { Text("Connected account") },
                        supportingContent = {
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
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Disconnect")
                            }
                        },
                        leadingContent = { Icon(Icons.Outlined.AccountCircle, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    ListItem(
                        headlineContent = {
                            Text(
                                text = when (settings.syncState) {
                                    SyncState.IDLE -> "Sync Status"
                                    SyncState.SYNCING -> "Syncing now..."
                                    SyncState.SUCCESS -> "Sync Succeeded"
                                    SyncState.ERROR -> "Sync Failed"
                                }
                            )
                        },
                        supportingContent = {
                            Column {
                                settings.lastSyncAtMillis?.let { timestamp ->
                                    Text("Last sync: ${formatDateTime(timestamp)}")
                                }
                                settings.lastSyncMessage?.let { message ->
                                    Text(
                                        text = message,
                                        color = if (settings.syncState == SyncState.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        leadingContent = {
                            Icon(
                                imageVector = when (settings.syncState) {
                                    SyncState.ERROR -> Icons.Outlined.Info
                                    else -> Icons.Outlined.Sync
                                },
                                contentDescription = null,
                                tint = if (settings.syncState == SyncState.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            if (settings.syncState == SyncState.SYNCING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(
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
                                    }
                                ) {
                                    Icon(Icons.Outlined.Sync, contentDescription = "Sync Now")
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    SettingToggleItem(
                        title = "Daily Auto-Sync",
                        description = "Automatically backup in the background",
                        checked = settings.driveSyncEnabled,
                        enabled = !settings.connectedGoogleAccountEmail.isNullOrBlank(),
                        onCheckedChange = { enabled ->
                            scope.launch {
                                settingsRepository.setDriveSyncEnabled(enabled)
                            }
                        },
                    )
                }

                AnimatedVisibility(
                    visible = driveStatusMessage != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Text(
                            text = driveStatusMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // --- AI Features Section ---
            SettingsSection(title = "AI Features", icon = Icons.Outlined.Cloud) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = geminiApiKeyDraft,
                        onValueChange = { geminiApiKeyDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Gemini API Key") },
                        leadingIcon = { Icon(Icons.Outlined.VpnKey, null) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.large
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

                    AnimatedVisibility(
                        visible = geminiStatusMessage != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Text(
                            text = geminiStatusMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                SettingToggleItem(
                    title = "Cloud Summaries",
                    description = "Generate daily summaries using AI",
                    checked = settings.cloudSummaryEnabled,
                    enabled = settings.geminiApiKey.isNotBlank(),
                    onCheckedChange = { enabled ->
                        scope.launch {
                            settingsRepository.setCloudSummaryEnabled(enabled)
                        }
                    },
                )

                if (settings.geminiApiKey.isBlank()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    ListItem(
                        headlineContent = {
                            Text(
                                text = "Add API key to enable cloud summaries.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            // --- Advanced Section ---
            SettingsSection(title = "Advanced", icon = Icons.Outlined.History) {
                SettingClickableItem(
                    title = "Operation Logs",
                    description = "View detailed synchronization and AI logs",
                    icon = Icons.Outlined.History,
                    onClick = onOpenOperationLogs
                )
            }
        }
    }
}

/**
 * A container for a group of related settings, styled with a title, icon, and a card background.
 */
@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier.padding(vertical = 4.dp),
                content = content
            )
        }
    }
}

/**
 * Reusable row that displays a title, description, and toggle control using Material 3 ListItem.
 */
@Composable
private fun SettingToggleItem(
    title: String,
    description: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = description?.let { { Text(it) } },
        trailingContent = {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

/**
 * Reusable row that is clickable, typically used for navigation or triggering actions.
 */
@Composable
private fun SettingClickableItem(
    title: String,
    description: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = description?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
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
