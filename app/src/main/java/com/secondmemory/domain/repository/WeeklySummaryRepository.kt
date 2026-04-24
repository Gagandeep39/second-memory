package com.secondmemory.domain.repository

import com.secondmemory.domain.model.WeeklySummaryFile

/**
 * Contract for listing and reading weekly summary markdown files.
 */
interface WeeklySummaryRepository {
    /**
     * Returns all weekly summary files sorted newest first.
     */
    suspend fun listWeeklySummaries(): List<WeeklySummaryFile>

    /**
     * Loads the full markdown content for a specific summary file.
     */
    suspend fun readSummary(fileName: String): String

    /**
     * Reads summary markdown for a week key if the file exists.
     */
    suspend fun readSummaryForWeek(weekKey: String): String

    /**
     * Writes or overwrites summary markdown for a week key.
     */
    suspend fun saveSummaryForWeek(weekKey: String, markdown: String)

    /**
     * Returns the summary markdown file name for a week key.
     */
    fun summaryFileName(weekKey: String): String

    /**
     * Returns last modified epoch millis for a week summary file, or null if missing.
     */
    suspend fun lastUpdatedMillisForWeek(weekKey: String): Long?
}
