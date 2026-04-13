package com.secondmemory.domain.model

/**
 * User-configurable app settings persisted in DataStore.
 */
data class AppSettings(
    val driveSyncEnabled: Boolean,
    val cloudSummaryEnabled: Boolean,
    val geminiApiKey: String,
    val syncState: SyncState,
    val lastSyncAtMillis: Long?,
    val lastSyncMessage: String?,
)
