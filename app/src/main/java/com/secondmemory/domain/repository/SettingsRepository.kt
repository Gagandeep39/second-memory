package com.secondmemory.domain.repository

import com.secondmemory.domain.model.AppSettings
import com.secondmemory.domain.model.SyncMetadata
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
     * Stores the email of the Google account connected for Drive sync.
     */
    suspend fun setConnectedGoogleAccountEmail(email: String?)

    /**
     * Enables or disables cloud summary generation.
     */
    suspend fun setCloudSummaryEnabled(enabled: Boolean)

    /**
     * Stores a Gemini API key used for cloud summary generation.
     */
    @Deprecated("Use setAiConfig instead")
    suspend fun setGeminiApiKey(apiKey: String)

    /**
     * Updates AI configuration.
     */
    suspend fun setAiConfig(
        provider: com.secondmemory.domain.model.AIProvider,
        baseUrl: String,
        apiKey: String,
        model: String,
        customPrompt: String
    )

    /**
     * Returns the default summarization prompt.
     */
    fun getDefaultPrompt(): String

    /**
     * Streams the current sync metadata and subsequent updates.
     */
    fun observeSyncMetadata(): Flow<SyncMetadata>

    /**
     * Returns the current sync metadata snapshot.
     */
    suspend fun currentSyncMetadata(): SyncMetadata
}
