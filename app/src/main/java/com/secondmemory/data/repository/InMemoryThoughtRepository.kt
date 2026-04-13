package com.secondmemory.data.repository

import com.secondmemory.domain.model.Thought
import com.secondmemory.domain.repository.ThoughtRepository

class InMemoryThoughtRepository : ThoughtRepository {
    private val storage: MutableMap<String, MutableList<Thought>> = mutableMapOf()

    override suspend fun listForDay(dayKey: String): List<Thought> {
        return storage[dayKey].orEmpty()
    }

    override suspend fun saveThought(dayKey: String, thought: Thought) {
        val current = storage.getOrPut(dayKey) { mutableListOf() }
        val existingIndex = current.indexOfFirst { it.id == thought.id }
        if (existingIndex >= 0) {
            current[existingIndex] = thought
        } else {
            current.add(thought)
        }
    }

    override suspend fun deleteThought(dayKey: String, thoughtId: String) {
        storage[dayKey]?.removeAll { it.id == thoughtId }
    }
}
