package com.secondmemory.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
 */
class DataStoreSettingsRepository(
    private val context: Context,
    private val syncRepository: SyncRepository,
) : SettingsRepository {
    private val operationLogRepository = DataStoreOperationLogRepository(context)

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
            val hasGeminiKey = !(prefs[Keys.GEMINI_API_KEY] ?: "").isBlank()
            prefs[Keys.CLOUD_SUMMARY_ENABLED] = enabled && hasGeminiKey
        }
        operationLogRepository.appendLog(
            category = "SETTINGS",
            action = "Cloud summaries toggled",
            status = "SUCCESS",
            details = "enabled=$enabled",
            source = "DataStoreSettingsRepository",
        )
    }

    override suspend fun setGeminiApiKey(apiKey: String) {
        context.appSettingsStore.edit { prefs ->
            val trimmed = apiKey.trim()
            prefs[Keys.GEMINI_API_KEY] = trimmed
            if (trimmed.isBlank()) {
                prefs[Keys.CLOUD_SUMMARY_ENABLED] = false
            }
        }
        operationLogRepository.appendLog(
            category = "SETTINGS",
            action = "Gemini API key updated",
            status = "SUCCESS",
            details = if (apiKey.isBlank()) "Cleared" else "Saved",
            source = "DataStoreSettingsRepository",
        )
    }

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
        val geminiKey = this[Keys.GEMINI_API_KEY] ?: ""
        val cloudEnabled = (this[Keys.CLOUD_SUMMARY_ENABLED] ?: true) && geminiKey.isNotBlank()
        val driveEnabled = (this[Keys.DRIVE_SYNC_ENABLED] ?: false) && !connectedAccount.isNullOrBlank()
        return AppSettings(
            driveSyncEnabled = driveEnabled,
            connectedGoogleAccountEmail = connectedAccount,
            cloudSummaryEnabled = cloudEnabled,
            geminiApiKey = geminiKey,
            syncState = syncMetadata.state,
            lastSyncAtMillis = syncMetadata.lastSyncAtMillis,
            lastSyncMessage = syncMetadata.lastSyncMessage,
        )
    }

    /**
     * Preference keys used by the settings store.
     */
    private object Keys {
        val DRIVE_SYNC_ENABLED = booleanPreferencesKey("drive_sync_enabled")
        val CONNECTED_GOOGLE_ACCOUNT_EMAIL = stringPreferencesKey("connected_google_account_email")
        val CLOUD_SUMMARY_ENABLED = booleanPreferencesKey("cloud_summary_enabled")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
    }
}
