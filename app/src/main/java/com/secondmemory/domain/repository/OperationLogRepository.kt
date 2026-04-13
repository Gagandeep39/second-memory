package com.secondmemory.domain.repository

import com.secondmemory.domain.model.OperationLogEntry
import kotlinx.coroutines.flow.Flow

/**
 * Contract for reading and writing operational log events.
 */
interface OperationLogRepository {
    /**
     * Streams all log entries ordered with newest first.
     */
    fun observeLogs(): Flow<List<OperationLogEntry>>

    /**
     * Appends a new operation event to persistent log storage.
     */
    suspend fun appendLog(
        category: String,
        action: String,
        status: String,
        details: String? = null,
        source: String? = null,
    )

    /**
     * Removes all persisted log entries.
     */
    suspend fun clearLogs()
}
