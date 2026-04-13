package com.secondmemory.domain.repository

import com.secondmemory.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

/**
 * Contract for reading and updating persistent user settings.
 */
interface SettingsRepository {
    /**
     * Streams the current app settings and subsequent updates.
     */
    fun observeSettings(): Flow<AppSettings>

    /**
     * Returns the current settings snapshot.
     */
    suspend fun currentSettings(): AppSettings

    /**
     * Enables or disables Google Drive sync integration.
     */
    suspend fun setDriveSyncEnabled(enabled: Boolean)

    /**
     * Enables or disables cloud summary generation.
     */
    suspend fun setCloudSummaryEnabled(enabled: Boolean)

    /**
     * Stores a Gemini API key used for cloud summary generation.
     */
    suspend fun setGeminiApiKey(apiKey: String)
}
