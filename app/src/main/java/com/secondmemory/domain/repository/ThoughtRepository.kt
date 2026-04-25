package com.secondmemory.domain.repository

import com.secondmemory.domain.model.Thought

/**
 * Contract for reading and mutating thought entries tied to a day key.
 */
interface ThoughtRepository {
    /**
     * Returns day keys for all available raw thought files sorted newest first.
     */
    suspend fun listAvailableDayKeys(): List<String>

    /**
     * Returns all thoughts stored for the provided ISO day key (yyyy-MM-dd).
     */
    suspend fun listForDay(dayKey: String): List<Thought>

    /**
     * Loads the raw JSON file content for a day key.
     */
    suspend fun readRawJson(dayKey: String): String

    /**
     * Creates or updates a thought in the provided day collection.
     */
    suspend fun saveThought(dayKey: String, thought: Thought)

    /**
     * Removes a thought by id from the provided day collection.
     */
    suspend fun deleteThought(dayKey: String, thoughtId: String)

    /**
     * Returns last modified epoch millis for a day thoughts file, or null if missing.
     */
    suspend fun lastUpdatedMillisForDay(dayKey: String): Long?
}
