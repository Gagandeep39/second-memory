package com.secondmemory.domain.repository

import com.secondmemory.domain.model.SyncMetadata
import kotlinx.coroutines.flow.Flow

/**
 * Contract for running sync operations and observing sync status.
 */
interface SyncRepository {
    /**
     * Streams the latest sync metadata.
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
