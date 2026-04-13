package com.secondmemory.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.secondmemory.data.drive.GoogleDriveMirrorClient
import com.secondmemory.data.repository.DataStoreSettingsRepository
import com.secondmemory.data.repository.DataStoreSyncRepository
import com.secondmemory.util.ensureAppDataDirectories

/**
 * Periodic worker that runs Google Drive sync when the feature is enabled.
 */
class DriveSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val syncRepository = DataStoreSyncRepository(
        context = appContext,
        driveMirrorClient = GoogleDriveMirrorClient(appContext),
    )
    private val settingsRepository = DataStoreSettingsRepository(
        context = appContext,
        syncRepository = syncRepository,
    )

    override suspend fun doWork(): Result {
        ensureAppDataDirectories(applicationContext)
        val settings = settingsRepository.currentSettings()
        if (!settings.driveSyncEnabled) {
            return Result.success()
        }

        return runCatching {
            syncRepository.syncNow(
                driveSyncEnabled = true,
                accountEmail = settings.connectedGoogleAccountEmail,
            )
            Result.success()
        }.getOrElse { error ->
            if (shouldRetryWork(error)) {
                Result.retry()
            } else {
                Result.failure(errorData(error))
            }
        }
    }

    /**
     * Packs a short error reason for diagnostics.
     */
    private fun errorData(error: Throwable): Data {
        return Data.Builder()
            .putString("error", error.message ?: "Background drive sync failed")
            .build()
    }
}
