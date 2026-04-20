package com.secondmemory.domain.model

/**
 * Stores the last sync outcome and timing for display in Settings.
 */
data class SyncMetadata(
    val state: SyncState,
    val lastSyncAtMillis: Long?,
    val lastSyncMessage: String?,
    val driveFolderId: String? = null,
)
