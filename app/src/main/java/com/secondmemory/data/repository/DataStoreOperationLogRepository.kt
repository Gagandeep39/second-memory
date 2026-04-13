package com.secondmemory.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.secondmemory.domain.model.OperationLogEntry
import com.secondmemory.domain.repository.OperationLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.operationLogStore: DataStore<Preferences> by preferencesDataStore(name = "operation_logs")

/**
 * DataStore-backed repository that stores operation events as JSON entries.
 */
class DataStoreOperationLogRepository(
    private val context: Context,
) : OperationLogRepository {
    override fun observeLogs(): Flow<List<OperationLogEntry>> {
        return context.operationLogStore.data.map { preferences ->
            parseEntries(preferences[Keys.LOGS_JSON])
        }
    }

    override suspend fun appendLog(
        category: String,
        action: String,
        status: String,
        details: String?,
        source: String?,
    ) {
        context.operationLogStore.edit { prefs ->
            val current = parseEntries(prefs[Keys.LOGS_JSON])
            val updated = buildList {
                add(
                    OperationLogEntry(
                        id = System.currentTimeMillis(),
                        timestampMillis = System.currentTimeMillis(),
                        category = category,
                        action = action,
                        status = status,
                        details = details,
                        source = source,
                    )
                )
                addAll(current.take(MAX_ENTRIES - 1))
            }
            prefs[Keys.LOGS_JSON] = serializeEntries(updated)
        }
    }

    override suspend fun clearLogs() {
        context.operationLogStore.edit { prefs ->
            prefs[Keys.LOGS_JSON] = "[]"
        }
    }

    /**
     * Parses raw JSON into operation entries while tolerating malformed rows.
     */
    private fun parseEntries(raw: String?): List<OperationLogEntry> {
        if (raw.isNullOrBlank()) return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        OperationLogEntry(
                            id = item.optLong("id", 0L),
                            timestampMillis = item.optLong("timestampMillis", 0L),
                            category = item.optString("category", "General"),
                            action = item.optString("action", "Unknown"),
                            status = item.optString("status", "INFO"),
                            details = item.optString("details").takeIf { it.isNotBlank() },
                            source = item.optString("source").takeIf { it.isNotBlank() },
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Serializes entries to a compact JSON array for DataStore persistence.
     */
    private fun serializeEntries(entries: List<OperationLogEntry>): String {
        val json = JSONArray()
        entries.forEach { entry ->
            json.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("timestampMillis", entry.timestampMillis)
                    put("category", entry.category)
                    put("action", entry.action)
                    put("status", entry.status)
                    put("details", entry.details ?: "")
                    put("source", entry.source ?: "")
                }
            )
        }
        return json.toString()
    }

    private object Keys {
        val LOGS_JSON = stringPreferencesKey("operation_logs_json")
    }

    private companion object {
        const val MAX_ENTRIES = 300
    }
}
