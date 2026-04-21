package com.secondmemory.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.secondmemory.domain.model.AIProvider
import com.secondmemory.domain.model.AppSettings
import com.secondmemory.domain.model.SyncMetadata
import com.secondmemory.domain.model.SyncState
import com.secondmemory.domain.repository.SettingsRepository
import com.secondmemory.domain.repository.SyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

private val Context.appSettingsStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/**
 * DataStore-backed settings repository for feature toggles and preferences.
 * Sensitive data like API keys are stored in EncryptedSharedPreferences.
 */
class DataStoreSettingsRepository(
    private val context: Context,
    private val syncRepository: SyncRepository,
) : SettingsRepository {
    private val operationLogRepository = DataStoreOperationLogRepository(context)

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs = EncryptedSharedPreferences.create(
        context,
        "secure_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun observeSettings(): Flow<AppSettings> {
        return context.appSettingsStore.data.combine(syncRepository.observeSyncMetadata()) { preferences, syncMetadata ->
            preferences.toAppSettings(syncMetadata)
        }
    }

    override suspend fun currentSettings(): AppSettings {
        val preferences = context.appSettingsStore.data.first()
        return preferences.toAppSettings(syncRepository.currentSyncMetadata())
    }

    override suspend fun setDriveSyncEnabled(enabled: Boolean) {
        context.appSettingsStore.edit { prefs ->
            val hasConnectedAccount = !(prefs[Keys.CONNECTED_GOOGLE_ACCOUNT_EMAIL] ?: "").isBlank()
            prefs[Keys.DRIVE_SYNC_ENABLED] = enabled && hasConnectedAccount
        }
        operationLogRepository.appendLog(
            category = "SETTINGS",
            action = "Drive sync toggled",
            status = "SUCCESS",
            details = "enabled=$enabled",
            source = "DataStoreSettingsRepository",
        )
    }

    override suspend fun setConnectedGoogleAccountEmail(email: String?) {
        context.appSettingsStore.edit { prefs ->
            val trimmed = email?.trim().orEmpty()
            prefs[Keys.CONNECTED_GOOGLE_ACCOUNT_EMAIL] = trimmed
            if (trimmed.isBlank()) {
                prefs[Keys.DRIVE_SYNC_ENABLED] = false
            }
        }
        operationLogRepository.appendLog(
            category = "SETTINGS",
            action = if (email.isNullOrBlank()) "Google account disconnected" else "Google account connected",
            status = "SUCCESS",
            details = email ?: "none",
            source = "DataStoreSettingsRepository",
        )
    }

    override suspend fun setCloudSummaryEnabled(enabled: Boolean) {
        context.appSettingsStore.edit { prefs ->
            prefs[Keys.CLOUD_SUMMARY_ENABLED] = enabled
        }
        operationLogRepository.appendLog(
            category = "SETTINGS",
            action = "Cloud summaries toggled",
            status = "SUCCESS",
            details = "enabled=$enabled",
            source = "DataStoreSettingsRepository",
        )
    }

    override suspend fun setAiConfig(
        provider: AIProvider,
        baseUrl: String,
        apiKey: String,
        model: String,
        customPrompt: String
    ) {
        securePrefs.edit().putString(Keys.SECURE_AI_API_KEY, apiKey.trim()).apply()

        context.appSettingsStore.edit { prefs ->
            prefs[Keys.AI_PROVIDER] = provider.name
            prefs[Keys.AI_BASE_URL] = baseUrl.trim()
            prefs[Keys.AI_MODEL] = model.trim()
            prefs[Keys.CUSTOM_PROMPT] = customPrompt.trim()
        }
        operationLogRepository.appendLog(
            category = "SETTINGS",
            action = "AI config updated (secure)",
            status = "SUCCESS",
            details = "provider=${provider.name}, model=$model",
            source = "DataStoreSettingsRepository",
        )
    }

    override fun getDefaultPrompt(): String = DEFAULT_PROMPT

    override fun observeSyncMetadata(): Flow<SyncMetadata> {
        return syncRepository.observeSyncMetadata()
    }

    override suspend fun currentSyncMetadata(): SyncMetadata {
        return syncRepository.currentSyncMetadata()
    }

    /**
     * Maps datastore preferences to strongly typed app settings.
     */
    private fun Preferences.toAppSettings(syncMetadata: SyncMetadata): AppSettings {
        val connectedAccount = this[Keys.CONNECTED_GOOGLE_ACCOUNT_EMAIL]
        val providerName = this[Keys.AI_PROVIDER] ?: AIProvider.GEMINI.name
        val provider = runCatching { AIProvider.valueOf(providerName) }.getOrDefault(AIProvider.GEMINI)
        
        // Read only from secure storage
        val aiApiKey = securePrefs.getString(Keys.SECURE_AI_API_KEY, "") ?: ""
            
        val cloudEnabled = this[Keys.CLOUD_SUMMARY_ENABLED] ?: false
        val driveEnabled = (this[Keys.DRIVE_SYNC_ENABLED] ?: false) && !connectedAccount.isNullOrBlank()

        return AppSettings(
            driveSyncEnabled = driveEnabled,
            connectedGoogleAccountEmail = connectedAccount,
            cloudSummaryEnabled = cloudEnabled,
            geminiApiKey = aiApiKey,
            aiProvider = provider,
            aiBaseUrl = this[Keys.AI_BASE_URL] ?: provider.defaultBaseUrl,
            aiApiKey = aiApiKey,
            aiModel = this[Keys.AI_MODEL] ?: (""),
            customPrompt = this[Keys.CUSTOM_PROMPT] ?: DEFAULT_PROMPT,
            syncState = syncMetadata.state,
            lastSyncAtMillis = syncMetadata.lastSyncAtMillis,
            lastSyncMessage = syncMetadata.lastSyncMessage,
            driveFolderId = syncMetadata.driveFolderId,
        )
    }

    /**
     * Preference keys used by the settings store.
     */
    private object Keys {
        val DRIVE_SYNC_ENABLED = booleanPreferencesKey("drive_sync_enabled")
        val CONNECTED_GOOGLE_ACCOUNT_EMAIL = stringPreferencesKey("connected_google_account_email")
        val CLOUD_SUMMARY_ENABLED = booleanPreferencesKey("cloud_summary_enabled")

        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val AI_BASE_URL = stringPreferencesKey("ai_base_url")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val CUSTOM_PROMPT = stringPreferencesKey("custom_prompt")
        
        // Key for EncryptedSharedPreferences
        const val SECURE_AI_API_KEY = "ai_api_key"
    }

    private companion object {
        const val DEFAULT_PROMPT = """You are generating a structured daily journal summary from raw thought logs. 

Input: JSON containing timestamped thoughts captured throughout a single day. 

Instructions: 
- Return valid markdown only. 
- Be concise but meaningful. Infer intent where needed, but do not invent details.
- Do not condense long stories into short summaries and do not expand short notes into longer essays.
- Remove noise, repetition, and low-value thoughts.
- Merge similar thoughts into a single idea. 
- Preserve chronological flow where helpful. 

Output format: 

## Summary of the day 
Write a clear, narrative-style summary of the day as a cohesive story. Focus on key activities, themes, and mindset. 

## Achievements 
List concrete things completed or meaningful progress made. 
- Use bullet points 
- Only include items with clear completion or progress 

## Things to do 
List actionable follow-ups or pending tasks inferred from the thoughts. 
- Keep each item short and specific 
"""
    }
}
