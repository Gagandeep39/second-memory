package com.secondmemory.domain.repository

import com.secondmemory.domain.model.Thought

/**
 * Contract for reading and mutating thought entries tied to a day key.
 */
interface ThoughtRepository {
    /**
     * Returns all thoughts stored for the provided ISO day key (yyyy-MM-dd).
     */
    suspend fun listForDay(dayKey: String): List<Thought>

    /**
     * Creates or updates a thought in the provided day collection.
     */
    suspend fun saveThought(dayKey: String, thought: Thought)

    /**
     * Removes a thought by id from the provided day collection.
     */
    suspend fun deleteThought(dayKey: String, thoughtId: String)
}
