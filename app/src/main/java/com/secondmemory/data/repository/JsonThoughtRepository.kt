package com.secondmemory.data.repository

import android.content.Context
import com.secondmemory.domain.model.Thought
import com.secondmemory.domain.model.ThoughtSource
import com.secondmemory.domain.repository.ThoughtRepository
import com.secondmemory.util.rawDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * File-based repository that persists thoughts into one JSON file per day.
 */
class JsonThoughtRepository(private val context: Context) : ThoughtRepository {
    private val mutex = Mutex()

    override suspend fun listAvailableDayKeys(): List<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            rawDirectory(context)
                .listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
                .orEmpty()
                .map { file -> file.nameWithoutExtension }
                .sortedDescending()
        }
    }

    override suspend fun listForDay(dayKey: String): List<Thought> = withContext(Dispatchers.IO) {
        mutex.withLock {
            readDayThoughts(dayKey)
        }
    }

    override suspend fun readRawJson(dayKey: String): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = dayFile(dayKey)
            if (!file.exists() || !file.isFile) return@withLock ""
            file.readText()
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

    override suspend fun lastUpdatedMillisForDay(dayKey: String): Long? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = dayFile(dayKey)
            if (file.exists() && file.isFile) file.lastModified() else null
        }
    }

    /**
     * Reads and parses a day file into domain models.
     */
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

    /**
     * Serializes the provided thoughts to JSON and writes to the day file.
     */
    private fun writeDayThoughts(dayKey: String, thoughts: List<Thought>) {
        val root = JSONObject()
            .put("schemaVersion", FILE_SCHEMA_VERSION)
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

    /**
     * Returns the canonical raw-thought file location for the given day key.
     */
    private fun dayFile(dayKey: String): File {
        return File(rawDirectory(context), "$dayKey.json")
    }

    private companion object {
        const val FILE_SCHEMA_VERSION = 1
    }
}
