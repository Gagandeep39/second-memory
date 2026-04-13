package com.secondmemory.domain.repository

import com.secondmemory.domain.model.Thought

interface ThoughtRepository {
    suspend fun listForDay(dayKey: String): List<Thought>
    suspend fun saveThought(dayKey: String, thought: Thought)
    suspend fun deleteThought(dayKey: String, thoughtId: String)
}
