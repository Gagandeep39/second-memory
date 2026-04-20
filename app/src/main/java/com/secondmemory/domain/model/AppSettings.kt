package com.secondmemory.domain.model

/**
 * User-configurable app settings persisted in DataStore.
 */
data class AppSettings(
    val driveSyncEnabled: Boolean,
    val connectedGoogleAccountEmail: String?,
    val cloudSummaryEnabled: Boolean,
    val geminiApiKey: String, // Keep for backward compatibility or migration
    val aiProvider: AIProvider,
    val aiBaseUrl: String,
    val aiApiKey: String,
    val aiModel: String,
    val customPrompt: String,
    val syncState: SyncState,
    val lastSyncAtMillis: Long?,
    val lastSyncMessage: String?,
    val driveFolderId: String?,
)
