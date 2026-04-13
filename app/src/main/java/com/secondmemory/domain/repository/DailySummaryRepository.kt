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

    /**
     * Reads summary markdown for a day key if the file exists.
     */
    suspend fun readSummaryForDay(dayKey: String): String

    /**
     * Writes or overwrites summary markdown for a day key.
     */
    suspend fun saveSummaryForDay(dayKey: String, markdown: String)

    /**
     * Returns the summary markdown file name for a day key.
     */
    fun summaryFileName(dayKey: String): String
}
