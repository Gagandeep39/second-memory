package com.secondmemory.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.secondmemory.data.llm.GeminiLlmSummaryClient
import com.secondmemory.data.repository.DataStoreOperationLogRepository
import com.secondmemory.data.repository.DataStoreSettingsRepository
import com.secondmemory.data.repository.DataStoreSyncRepository
import com.secondmemory.data.repository.FileDailySummaryRepository
import com.secondmemory.data.repository.JsonThoughtRepository
import com.secondmemory.data.drive.GoogleDriveSyncClient
import com.secondmemory.util.ensureAppDataDirectories
import com.secondmemory.util.shiftDayKey
import com.secondmemory.util.todayDayKey

import com.secondmemory.util.hasInternetConnection

/**
 * Daily worker that regenerates the previous day's summary.
 *
 * Example: if this runs at 01:15 on April 14, it targets April 13.
 */
class DailySummaryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val operationLogRepository = DataStoreOperationLogRepository(appContext)
    private val settingsRepository = DataStoreSettingsRepository(
        context = appContext,
        syncRepository = DataStoreSyncRepository(
            context = appContext,
            driveSyncClient = GoogleDriveSyncClient(appContext),
        ),
    )
    private val thoughtRepository = JsonThoughtRepository(appContext)
    private val dailySummaryRepository = FileDailySummaryRepository(appContext)
    private val llmSummaryClient = GeminiLlmSummaryClient()

    override suspend fun doWork(): Result {
        operationLogRepository.appendLog(
            category = "WORK",
            action = "Daily summary worker started",
            status = "STARTED",
            details = "runAttempt=${runAttemptCount + 1}",
            source = "DailySummaryWorker",
        )
        ensureAppDataDirectories(applicationContext)
        // Pre-check for actual internet connectivity
        if (!hasInternetConnection()) {
            operationLogRepository.appendLog(
                category = "WORK",
                action = "Daily summary worker no internet",
                status = "RETRY",
                details = "No internet connectivity detected",
                source = "DailySummaryWorker",
            )
            return Result.retry()
        }
        val settings = settingsRepository.currentSettings()
        val targetDayKey = previousDayKey()
        val rawJson = thoughtRepository.readRawJson(targetDayKey)
        if (rawJson.isBlank()) {
            operationLogRepository.appendLog(
                category = "WORK",
                action = "Daily summary worker skipped",
                status = "SKIPPED",
                details = "No raw thoughts for $targetDayKey",
                source = "DailySummaryWorker",
            )
            return Result.success()
        }

        return runCatching {
            val markdown = llmSummaryClient.summarizeDay(
                dayKey = targetDayKey,
                rawJson = rawJson,
                apiKey = settings.geminiApiKey,
            )
            dailySummaryRepository.saveSummaryForDay(targetDayKey, markdown)
            operationLogRepository.appendLog(
                category = "WORK",
                action = "Daily summary generated",
                status = "SUCCESS",
                details = targetDayKey,
                source = "DailySummaryWorker",
            )
            Result.success()
        }.getOrElse { error ->
            operationLogRepository.appendLog(
                category = "WORK",
                action = "Daily summary worker failed",
                status = if (shouldRetryWork(error)) "RETRY" else "ERROR",
                details = error.message ?: "Daily summary generation failed",
                source = "DailySummaryWorker",
            )
            if (shouldRetryWork(error)) {
                Result.retry()
            } else {
                Result.failure(errorData(error))
            }
        }
    }

    /**
     * Returns the day key for the previous local day.
     */
    private fun previousDayKey(): String {
        return shiftDayKey(todayDayKey(), -1)
    }

    /**
     * Packs a short error reason for diagnostics.
     */
    private fun errorData(error: Throwable): Data {
        return Data.Builder()
            .putString("error", error.message ?: "Daily summary generation failed")
            .build()
    }
}
