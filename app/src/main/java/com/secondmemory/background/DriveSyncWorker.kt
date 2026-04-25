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

        val settings = settingsRepository.currentSettings()

        return runCatching {
            if (!hasInternetConnection()) {
                throw java.io.IOException("No internet connectivity detected")
            }
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
            val retry = shouldRetryWork(error, runAttemptCount)
            val errorMessage = error.message ?: "Background drive sync failed"
            operationLogRepository.appendLog(
                category = "WORK",
                action = "Drive worker failed",
                status = if (retry) "RETRY" else "ERROR",
                details = "$errorMessage (attempt ${runAttemptCount + 1})",
                source = "DriveSyncWorker",
            )
            if (retry) {
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
