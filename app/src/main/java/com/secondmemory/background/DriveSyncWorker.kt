package com.secondmemory.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.secondmemory.data.drive.GoogleDriveSyncClient
import com.secondmemory.data.repository.DataStoreOperationLogRepository
import com.secondmemory.data.repository.DataStoreSettingsRepository
import com.secondmemory.data.repository.DataStoreSyncRepository
import com.secondmemory.util.ensureAppDataDirectories

import com.secondmemory.util.hasInternetConnection

/**
 * Periodic worker that runs Google Drive sync when the feature is enabled.
 */
class DriveSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val operationLogRepository = DataStoreOperationLogRepository(appContext)
    private val syncRepository = DataStoreSyncRepository(
        context = appContext,
        driveSyncClient = GoogleDriveSyncClient(appContext),
    )
    private val settingsRepository = DataStoreSettingsRepository(
        context = appContext,
        syncRepository = syncRepository,
    )

    override suspend fun doWork(): Result {
        operationLogRepository.appendLog(
            category = "WORK",
            action = "Drive worker started",
            status = "STARTED",
            details = "runAttempt=${runAttemptCount + 1}",
            source = "DriveSyncWorker",
        )
        ensureAppDataDirectories(applicationContext)
        // Pre-check for actual internet connectivity
        if (!hasInternetConnection()) {
            operationLogRepository.appendLog(
                category = "WORK",
                action = "Drive worker no internet",
                status = "RETRY",
                details = "No internet connectivity detected",
                source = "DriveSyncWorker",
            )
            return Result.retry()
        }
        val settings = settingsRepository.currentSettings()
        if (!settings.driveSyncEnabled) {
            operationLogRepository.appendLog(
                category = "WORK",
                action = "Drive worker skipped",
                status = "SKIPPED",
                details = "Drive sync is disabled in settings",
                source = "DriveSyncWorker",
            )
            return Result.success()
        }

        return runCatching {
            syncRepository.syncNow(
                driveSyncEnabled = true,
                accountEmail = settings.connectedGoogleAccountEmail,
            )
            operationLogRepository.appendLog(
                category = "WORK",
                action = "Drive worker completed",
                status = "SUCCESS",
                details = settings.connectedGoogleAccountEmail ?: "No connected account",
                source = "DriveSyncWorker",
            )
            Result.success()
        }.getOrElse { error ->
            operationLogRepository.appendLog(
                category = "WORK",
                action = "Drive worker failed",
                status = if (shouldRetryWork(error)) "RETRY" else "ERROR",
                details = error.message ?: "Background drive sync failed",
                source = "DriveSyncWorker",
            )
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
