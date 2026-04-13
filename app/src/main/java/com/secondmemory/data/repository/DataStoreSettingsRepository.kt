package com.secondmemory.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.secondmemory.domain.model.AppSettings
import com.secondmemory.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appSettingsStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/**
 * DataStore-backed settings repository for feature toggles and preferences.
 */
class DataStoreSettingsRepository(private val context: Context) : SettingsRepository {
    override fun observeSettings(): Flow<AppSettings> {
        return context.appSettingsStore.data.map { preferences ->
            preferences.toAppSettings()
        }
    }

    override suspend fun currentSettings(): AppSettings {
        val preferences = context.appSettingsStore.data.first()
        return preferences.toAppSettings()
    }

    override suspend fun setDriveSyncEnabled(enabled: Boolean) {
        context.appSettingsStore.edit { prefs ->
            prefs[Keys.DRIVE_SYNC_ENABLED] = enabled
        }
    }

    override suspend fun setCloudSummaryEnabled(enabled: Boolean) {
        context.appSettingsStore.edit { prefs ->
            val hasGeminiKey = !(prefs[Keys.GEMINI_API_KEY] ?: "").isBlank()
            prefs[Keys.CLOUD_SUMMARY_ENABLED] = enabled && hasGeminiKey
        }
    }

    override suspend fun setGeminiApiKey(apiKey: String) {
        context.appSettingsStore.edit { prefs ->
            val trimmed = apiKey.trim()
            prefs[Keys.GEMINI_API_KEY] = trimmed
            if (trimmed.isBlank()) {
                prefs[Keys.CLOUD_SUMMARY_ENABLED] = false
            }
        }
    }

    /**
     * Maps datastore preferences to strongly typed app settings.
     */
    private fun Preferences.toAppSettings(): AppSettings {
        val geminiKey = this[Keys.GEMINI_API_KEY] ?: ""
        val cloudEnabled = (this[Keys.CLOUD_SUMMARY_ENABLED] ?: true) && geminiKey.isNotBlank()
        return AppSettings(
            driveSyncEnabled = this[Keys.DRIVE_SYNC_ENABLED] ?: false,
            cloudSummaryEnabled = cloudEnabled,
            geminiApiKey = geminiKey,
        )
    }

    /**
     * Preference keys used by the settings store.
     */
    private object Keys {
        val DRIVE_SYNC_ENABLED = booleanPreferencesKey("drive_sync_enabled")
        val CLOUD_SUMMARY_ENABLED = booleanPreferencesKey("cloud_summary_enabled")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
    }
}
