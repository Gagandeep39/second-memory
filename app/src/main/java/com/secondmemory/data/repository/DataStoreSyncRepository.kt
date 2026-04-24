package com.secondmemory.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.secondmemory.data.drive.GoogleDriveSyncClient
import com.secondmemory.domain.model.SyncMetadata
import com.secondmemory.domain.model.SyncState
import com.secondmemory.domain.repository.SyncRepository
import com.secondmemory.util.NotificationHelper
import com.secondmemory.util.dailyDirectory
import com.secondmemory.util.monthlyDirectory
import com.secondmemory.util.rawDirectory
import com.secondmemory.util.weeklyDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

private val Context.syncStore: DataStore<Preferences> by preferencesDataStore(name = "sync_state")

/**
 * DataStore-backed sync repository that tracks sync status and performs local sync planning.
 *
 * The actual remote Drive upload/download implementation can be swapped in later without
 * changing the UI or sync state contract.
 */
class DataStoreSyncRepository(
    private val context: Context,
    private val driveSyncClient: GoogleDriveSyncClient,
) : SyncRepository {
    private val operationLogRepository = DataStoreOperationLogRepository(context)
    private val notificationHelper = NotificationHelper(context)

    override fun observeSyncMetadata(): Flow<SyncMetadata> {
        return context.syncStore.data.map { preferences ->
            preferences.toMetadata()
        }
    }

    override suspend fun currentSyncMetadata(): SyncMetadata {
        val preferences = context.syncStore.data.first()
        return preferences.toMetadata()
    }

    override suspend fun syncNow(driveSyncEnabled: Boolean, accountEmail: String?) {
        withContext(Dispatchers.IO) {
            operationLogRepository.appendLog(
                category = "SYNC",
                action = "Sync requested",
                status = "STARTED",
                details = "driveSyncEnabled=$driveSyncEnabled account=${accountEmail ?: "none"}",
                source = "DataStoreSyncRepository",
            )
            context.syncStore.edit { prefs ->
                prefs[Keys.STATE] = SyncState.SYNCING.name
                prefs[Keys.LAST_MESSAGE] = "Syncing local data tree to Google Drive"
            }

            runCatching {
                val report = driveSyncClient.syncLocalDataTree(accountEmail)
                val stats = scanLocalTree()
                val message = buildString {
                    append("Google Drive sync complete:\n- ")
                    append(report.uploadedCount)
                    append(" uploaded\n- ")
                    append(report.downloadedCount)
                    append(" downloaded\n- ")
                    append(report.deletedCount)
                    append(" deleted\n- ")
                    append(report.conflictedCount)
                    append(" conflicted.\nFile stats:\n- ")
                    append(stats.rawCount)
                    append(" raw\n- ")
                    append(stats.dailyCount)
                    append(" daily\n- ")
                    append(stats.weeklyCount)
                    append(" weekly\n- ")
                    append(stats.monthlyCount)
                    append(" monthly")
                }

                context.syncStore.edit { prefs ->
                    prefs[Keys.STATE] = SyncState.SUCCESS.name
                    prefs[Keys.LAST_SYNC_AT] = System.currentTimeMillis()
                    prefs[Keys.LAST_MESSAGE] = message
                    report.rootFolderId?.let { prefs[Keys.DRIVE_FOLDER_ID] = it }
                }

                // Added notification during conflicts
                if (report.conflictedCount > 0) {
                    notificationHelper.showSyncConflictNotification()
                }

                operationLogRepository.appendLog(
                    category = "SYNC",
                    action = "Sync completed",
                    status = "SUCCESS",
                    details = message,
                    source = "DataStoreSyncRepository",
                )
            }.onFailure { error ->
                context.syncStore.edit { prefs ->
                    prefs[Keys.STATE] = SyncState.ERROR.name
                    prefs[Keys.LAST_MESSAGE] = error.message ?: "Sync failed"
                }
                operationLogRepository.appendLog(
                    category = "SYNC",
                    action = "Sync failed",
                    status = "ERROR",
                    details = error.message ?: "Unknown sync failure",
                    source = "DataStoreSyncRepository",
                )
                throw error
            }
        }
    }

    /**
     * Maps preferences into sync metadata with safe defaults.
     */
    private fun Preferences.toMetadata(): SyncMetadata {
        val state = runCatching {
            SyncState.valueOf(this[Keys.STATE] ?: SyncState.IDLE.name)
        }.getOrDefault(SyncState.IDLE)

        return SyncMetadata(
            state = state,
            lastSyncAtMillis = this[Keys.LAST_SYNC_AT],
            lastSyncMessage = this[Keys.LAST_MESSAGE],
            driveFolderId = this[Keys.DRIVE_FOLDER_ID],
        )
    }

    /**
     * Scans local app data folders for a lightweight sync plan preview.
     */
    private fun scanLocalTree(): LocalTreeStats {
        return LocalTreeStats(
            rawCount = countFiles(rawDirectory(context), "json"),
            dailyCount = countFiles(dailyDirectory(context), "md"),
            weeklyCount = countFiles(weeklyDirectory(context), "md"),
            monthlyCount = countFiles(monthlyDirectory(context), "md"),
        )
    }

    /**
     * Counts files of a specific extension in a directory.
     */
    private fun countFiles(directory: File, extension: String): Int {
        return directory.listFiles { file -> file.isFile && file.extension.equals(extension, ignoreCase = true) }
            .orEmpty()
            .size
    }

    private data class LocalTreeStats(
        val rawCount: Int,
        val dailyCount: Int,
        val weeklyCount: Int,
        val monthlyCount: Int,
    )

    private object Keys {
        val STATE = stringPreferencesKey("sync_state")
        val LAST_SYNC_AT = longPreferencesKey("sync_last_at")
        val LAST_MESSAGE = stringPreferencesKey("sync_last_message")
        val DRIVE_FOLDER_ID = stringPreferencesKey("sync_drive_folder_id")
    }
}
