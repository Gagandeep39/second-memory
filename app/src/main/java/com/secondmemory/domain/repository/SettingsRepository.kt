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
     * Enables or disables cloud summary generation.
     */
    suspend fun setCloudSummaryEnabled(enabled: Boolean)

    /**
     * Stores a Gemini API key used for cloud summary generation.
     */
    suspend fun setGeminiApiKey(apiKey: String)

    /**
     * Streams the current sync metadata and subsequent updates.
     */
    fun observeSyncMetadata(): Flow<SyncMetadata>

    /**
     * Returns the current sync metadata snapshot.
     */
    suspend fun currentSyncMetadata(): SyncMetadata

    /**
     * Starts a manual sync operation.
     */
    suspend fun syncNow()
}
