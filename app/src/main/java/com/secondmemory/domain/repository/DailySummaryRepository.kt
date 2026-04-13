package com.secondmemory.domain.repository

import com.secondmemory.domain.model.DailySummaryFile

/**
 * Contract for listing and reading daily summary markdown files.
 */
interface DailySummaryRepository {
    /**
     * Returns all daily summary files sorted newest first.
     */
    suspend fun listDailySummaries(): List<DailySummaryFile>

    /**
     * Loads the full markdown content for a specific summary file.
     */
    suspend fun readSummary(fileName: String): String
}
