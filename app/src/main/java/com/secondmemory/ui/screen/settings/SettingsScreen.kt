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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.unit.sp
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.secondmemory.R
import com.secondmemory.background.BackgroundWorkScheduler
import com.secondmemory.background.DriveSyncWorker
import com.secondmemory.data.repository.DataStoreOperationLogRepository
import com.secondmemory.domain.llm.LlmSummaryClient
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.TextField
import com.secondmemory.domain.model.AIProvider
import com.secondmemory.domain.model.AppSettings
import com.secondmemory.domain.model.SyncState
import com.secondmemory.domain.repository.SettingsRepository
import com.secondmemory.util.formatDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Screen that exposes persisted app-level feature toggles and sync controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
            aiProvider = AIProvider.GEMINI,
            aiBaseUrl = AIProvider.GEMINI.defaultBaseUrl,
            aiApiKey = "",
            aiModel = "gemini-1.5-flash-latest",
            customPrompt = "",
            syncState = SyncState.IDLE,
            lastSyncAtMillis = null,
            lastSyncMessage = null,
        )
    )
    var aiProvider by remember(settings.aiProvider) { mutableStateOf(settings.aiProvider) }
    var aiBaseUrl by remember(settings.aiBaseUrl) { mutableStateOf(settings.aiBaseUrl) }
    var aiApiKey by remember(settings.aiApiKey) { mutableStateOf(settings.aiApiKey) }
    var aiModel by remember(settings.aiModel) { mutableStateOf(settings.aiModel) }
    var customPrompt by remember(settings.customPrompt) { mutableStateOf(settings.customPrompt) }
    var aiApiKeyVisible by remember { mutableStateOf(false) }

    var aiStatusMessage by remember { mutableStateOf<String?>(null) }
    var availableModels by remember { mutableStateOf(emptyList<String>()) }
    var isFetchingModels by remember { mutableStateOf(false) }

    val googleWebClientId = stringResource(R.string.google_web_client_id)
    val context = LocalContext.current
    val credentialManager = CredentialManager.create(context)
    var driveStatusMessage by remember { mutableStateOf<String?>(null) }
    var expandedSections by remember { mutableStateOf<Set<String>>(emptySet()) }
    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        driveStatusMessage = if (result.resultCode == Activity.RESULT_OK) {
            "Drive authorization granted."
        } else {
            "Drive authorization was not completed."
        }
    }

    Scaffold (
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(innerPadding),
        ) {

            // --- Google Drive Sync Section ---
            SettingsSection(
                title = "Sync & Backup",
                description = "Manage Google Drive synchronization and daily auto backup",
                icon = Icons.Outlined.Sync,
                expanded = expandedSections.contains("sync"),
                onHeaderClick = {
                    expandedSections = if (expandedSections.contains("sync")) {
                        expandedSections - "sync"
                    } else {
                        expandedSections + "sync"
                    }
                }
            ) {
                if (settings.connectedGoogleAccountEmail.isNullOrBlank()) {
                    ListItem(
                        headlineContent = { Text("Cloud Backup") },
                        supportingContent = { Text("Connect your Google account to sync your thoughts across devices.") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
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
                        trailingContent = {
                            if (settings.syncState == SyncState.SYNCING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(
                                    onClick = {
                                        // Enqueue a one-time sync job in the background using DriveSyncWorker
                                        val workManager = WorkManager.getInstance(context)
                                        // Log manual sync trigger
                                        CoroutineScope(Dispatchers.IO).launch {
                                            DataStoreOperationLogRepository(context)
                                                .appendLog(
                                                    category = "WORK",
                                                    action = "Manual sync triggered from settings",
                                                    status = "STARTED",
                                                    details = "User pressed Sync Now button",
                                                    source = "SettingsScreen"
                                            )
                                        }
                                        val request = OneTimeWorkRequestBuilder<DriveSyncWorker>()
                                            .setConstraints(
                                                Constraints.Builder()
                                                    .setRequiredNetworkType(NetworkType.CONNECTED)
                                                    .build()
                                            )
                                            .build()
                                        workManager.enqueue(request)
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
                                if (enabled) {
                                    BackgroundWorkScheduler.scheduleRecurringWork(context);
                                }
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
            SettingsSection(
                title = "AI Features",
                description = "Configure AI provider and model for summaries",
                icon = Icons.Outlined.Cloud,
                expanded = expandedSections.contains("ai"),
                onHeaderClick = {
                    expandedSections = if (expandedSections.contains("ai")) {
                        expandedSections - "ai"
                    } else {
                        expandedSections + "ai"
                    }
                },
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Provider Settings",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )

                    // Provider Dropdown
                    var providerExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = providerExpanded,
                        onExpandedChange = { providerExpanded = !providerExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = aiProvider.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("AI Provider") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                            shape = MaterialTheme.shapes.large
                        )
                        ExposedDropdownMenu(
                            expanded = providerExpanded,
                            onDismissRequest = { providerExpanded = false }
                        ) {
                            AIProvider.entries.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider.displayName) },
                                    onClick = {
                                        aiProvider = provider
                                        aiBaseUrl = provider.defaultBaseUrl
                                        providerExpanded = false
                                        // Reset model when provider changes
                                        aiModel = if (provider == AIProvider.GEMINI) "gemini-1.5-flash-latest" else ""
                                        availableModels = emptyList()
                                    }
                                )
                            }
                        }
                    }

                    // Base URL
                    OutlinedTextField(
                        value = aiBaseUrl,
                        onValueChange = { 
                            aiBaseUrl = it
                            availableModels = emptyList()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Base URL") },
                        placeholder = { Text("https://api.openai.com/v1") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large
                    )

                    // API Key
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = aiApiKey,
                            onValueChange = {
                                aiApiKey = it
                                availableModels = emptyList()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("API Key") },
                            leadingIcon = { Icon(Icons.Outlined.VpnKey, null) },
                            trailingIcon = {
                                IconButton(onClick = { aiApiKeyVisible = !aiApiKeyVisible }) {
                                    Icon(
                                        imageVector = if (aiApiKeyVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        contentDescription = if (aiApiKeyVisible) "Hide API Key" else "Show API Key"
                                    )
                                }
                            },
                            visualTransformation = if (aiApiKeyVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            shape = MaterialTheme.shapes.large
                        )
                        if (aiProvider != AIProvider.CUSTOM) {
                            Text(
                                text = "Needed to list all available models. Incorrect key will give no models.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }

                    // Model Autocomplete
                    var modelExpanded by remember { mutableStateOf(false) }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ExposedDropdownMenuBox(
                            expanded = modelExpanded,
                            onExpandedChange = {
                                if (aiApiKey.isNotBlank() || aiProvider == AIProvider.CUSTOM) {
                                    modelExpanded = !modelExpanded
                                    if (modelExpanded && availableModels.isEmpty()) {
                                        scope.launch {
                                            isFetchingModels = true
                                            availableModels = llmSummaryClient.fetchModels(aiProvider, aiBaseUrl, aiApiKey)
                                            isFetchingModels = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = aiModel,
                                onValueChange = { aiModel = it },
                                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth(),
                                label = { Text("Model") },
                                trailingIcon = {
                                    if (isFetchingModels) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded)
                                    }
                                },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                shape = MaterialTheme.shapes.large
                            )
                            if (availableModels.isNotEmpty()) {
                                ExposedDropdownMenu(
                                    expanded = modelExpanded,
                                    onDismissRequest = { modelExpanded = false }
                                ) {
                                    availableModels.filter { it.contains(aiModel, ignoreCase = true) }.forEach { modelName ->
                                        DropdownMenuItem(
                                            text = { Text(modelName) },
                                            onClick = {
                                                aiModel = modelName
                                                modelExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = if (aiApiKey.isBlank() && aiProvider != AIProvider.CUSTOM) 
                                "⚠️ API key is needed to list all available models" 
                            else "Select or type the model ID (e.g. gpt-4o, gemini-1.5-pro)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (aiApiKey.isBlank() && aiProvider != AIProvider.CUSTOM) 
                                MaterialTheme.colorScheme.error 
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                scope.launch {
                                    settingsRepository.setAiConfig(aiProvider, aiBaseUrl, aiApiKey, aiModel, customPrompt)
                                    DataStoreOperationLogRepository(context).appendLog(
                                        category = "CONFIG",
                                        action = "AI Provider Configuration updated",
                                        status = "SUCCESS",
                                        details = "Provider: ${aiProvider.displayName}, Model: $aiModel",
                                        source = "SettingsScreen"
                                    )
                                    aiStatusMessage = "AI provider saved."
                                }
                            },
                        ) {
                            Text("Save")
                        }

                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = (aiApiKey.isNotBlank() || aiProvider == AIProvider.CUSTOM) && aiModel.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    aiStatusMessage = "Testing connection..."
                                    runCatching {
                                        llmSummaryClient.testConnection(aiProvider, aiBaseUrl, aiApiKey, aiModel, customPrompt)
                                    }.onSuccess {
                                        aiStatusMessage = "Connection successful!"
                                    }.onFailure { error ->
                                        aiStatusMessage = "Test failed: ${error.message}"
                                    }
                                }
                            },
                        ) {
                            Text("Test")
                        }
                    }

                    AnimatedVisibility(
                        visible = aiStatusMessage != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Text(
                            text = aiStatusMessage ?: "",
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
                    enabled = settings.aiApiKey.isNotBlank() || settings.aiProvider == AIProvider.CUSTOM,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            settingsRepository.setCloudSummaryEnabled(enabled)
                            if (enabled) {
                                BackgroundWorkScheduler.scheduleRecurringWork(context);
                            }
                        }
                    },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // Custom Prompt
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Daily Summarization Prompt",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )

                    TextField(
                        value = customPrompt,
                        onValueChange = { customPrompt = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(240.dp),
                        placeholder = { Text("Instructions for the AI...") },
                        shape = MaterialTheme.shapes.large,
                        minLines = 5,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            errorIndicatorColor = Color.Transparent
                        )
                    )
                    Text(
                        text = "This prompt guides the AI in generating your daily summary. Use markdown format instructions.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    var promptStatusMessage by remember { mutableStateOf<String?>(null) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                scope.launch {
                                    settingsRepository.setAiConfig(aiProvider, aiBaseUrl, aiApiKey, aiModel, customPrompt)
                                    DataStoreOperationLogRepository(context).appendLog(
                                        category = "CONFIG",
                                        action = "Custom Prompt updated",
                                        status = "SUCCESS",
                                        details = "Prompt length: ${customPrompt.length}",
                                        source = "SettingsScreen"
                                    )
                                    promptStatusMessage = "Prompt saved."
                                    delay(3000)
                                    promptStatusMessage = null
                                }
                            },
                        ) {
                            Text("Save")
                        }

                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                customPrompt = settingsRepository.getDefaultPrompt()
                            }
                        ) {
                            Text("Reset")
                        }
                    }

                    AnimatedVisibility(
                        visible = promptStatusMessage != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Text(
                            text = promptStatusMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            SettingsSection(
                title = "Advanced",
                description = "View logs and diagnostic information",
                icon = Icons.Outlined.History,
                expanded = expandedSections.contains("advanced"),
                onHeaderClick = {
                    expandedSections = if (expandedSections.contains("advanced")) {
                        expandedSections - "advanced"
                    } else {
                        expandedSections + "advanced"
                    }
                }
            ) {
                SettingClickableItem(
                    title = "Operation Logs",
                    description = "View detailed synchronization and AI logs",
                    // Removed redundant icon
                    onClick = onOpenOperationLogs
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            AppInfoSection()
        }
    }
}

@Composable
private fun AppInfoSection() {
    val context = LocalContext.current
    val packageInfo = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
    }
    val versionName = packageInfo?.versionName ?: "0.0"
    val appName = stringResource(R.string.app_name)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = appName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Version $versionName",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = "$appName is your private digital garden.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

/**
 * An expandable container for a group of related settings, styled as an accordion.
 */
@Composable
private fun SettingsSection(
    title: String,
    description: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    expanded: Boolean,
    onHeaderClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            modifier = Modifier
                .clickable(onClick = onHeaderClick)
                .height(IntrinsicSize.Min),
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            supportingContent = description?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            leadingContent = {
                Box(
                    modifier = Modifier.fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            trailingContent = {
                Box(
                    modifier = Modifier.fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                content()
            }
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

/**
 * Reusable row that displays a title, description, and toggle control using Material 3 ListItem.
 */
@Composable
private fun SettingToggleItem(
    title: String,
    description: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = description?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
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
