package com.secondmemory.data.repository

import android.content.Context
import com.secondmemory.domain.model.Thought
import com.secondmemory.domain.model.ThoughtSource
import com.secondmemory.domain.repository.ThoughtRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class JsonThoughtRepository(private val context: Context) : ThoughtRepository {
    private val mutex = Mutex()

    override suspend fun listForDay(dayKey: String): List<Thought> = withContext(Dispatchers.IO) {
        mutex.withLock {
            readDayThoughts(dayKey)
        }
    }

    override suspend fun saveThought(dayKey: String, thought: Thought) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = readDayThoughts(dayKey).toMutableList()
            val existingIndex = current.indexOfFirst { it.id == thought.id }
            if (existingIndex >= 0) {
                current[existingIndex] = thought
            } else {
                current.add(thought)
            }
            writeDayThoughts(dayKey, current)
        }
    }

    override suspend fun deleteThought(dayKey: String, thoughtId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = readDayThoughts(dayKey)
                .filterNot { it.id == thoughtId }
            writeDayThoughts(dayKey, current)
        }
    }

    private fun readDayThoughts(dayKey: String): List<Thought> {
        val file = dayFile(dayKey)
        if (!file.exists()) return emptyList()

        val raw = file.readText()
        if (raw.isBlank()) return emptyList()

        val root = JSONObject(raw)
        val array = root.optJSONArray("thoughts") ?: JSONArray()
        val result = mutableListOf<Thought>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val sourceValue = item.optString("source", ThoughtSource.MANUAL.name)
            val source = runCatching { ThoughtSource.valueOf(sourceValue) }
                .getOrDefault(ThoughtSource.MANUAL)

            result.add(
                Thought(
                    id = item.optString("id"),
                    timestampMillis = item.optLong("timestampMillis"),
                    text = item.optString("text"),
                    source = source,
                )
            )
        }

        return result
    }

    private fun writeDayThoughts(dayKey: String, thoughts: List<Thought>) {
        val root = JSONObject()
        val array = JSONArray()

        thoughts.forEach { thought ->
            val item = JSONObject()
                .put("id", thought.id)
                .put("timestampMillis", thought.timestampMillis)
                .put("text", thought.text)
                .put("source", thought.source.name)
            array.put(item)
        }

        root.put("thoughts", array)
        dayFile(dayKey).writeText(root.toString(2))
    }

    private fun dayFile(dayKey: String): File {
        val dailyDir = File(context.filesDir, DAILY_DIR_NAME)
        if (!dailyDir.exists()) {
            dailyDir.mkdirs()
        }
        return File(dailyDir, "$dayKey.json")
    }

    private companion object {
        const val DAILY_DIR_NAME = "daily"
    }
}
