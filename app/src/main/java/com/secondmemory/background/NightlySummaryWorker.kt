package com.secondmemory.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.secondmemory.data.llm.GeminiLlmSummaryClient
import com.secondmemory.data.repository.DataStoreSettingsRepository
import com.secondmemory.data.repository.DataStoreSyncRepository
import com.secondmemory.data.repository.FileDailySummaryRepository
import com.secondmemory.data.repository.JsonThoughtRepository
import com.secondmemory.data.drive.GoogleDriveMirrorClient
import com.secondmemory.util.ensureAppDataDirectories
import com.secondmemory.util.shiftDayKey
import com.secondmemory.util.todayDayKey

/**
 * Nightly worker that regenerates the previous day's summary when cloud summaries are enabled.
 *
 * Example: if this runs at 01:15 on April 14, it targets April 13.
 */
class NightlySummaryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val settingsRepository = DataStoreSettingsRepository(
        context = appContext,
        syncRepository = DataStoreSyncRepository(
            context = appContext,
            driveMirrorClient = GoogleDriveMirrorClient(appContext),
        ),
    )
    private val thoughtRepository = JsonThoughtRepository(appContext)
    private val dailySummaryRepository = FileDailySummaryRepository(appContext)
    private val llmSummaryClient = GeminiLlmSummaryClient()

    override suspend fun doWork(): Result {
        ensureAppDataDirectories(applicationContext)
        val settings = settingsRepository.currentSettings()
        if (!settings.cloudSummaryEnabled || settings.geminiApiKey.isBlank()) {
            return Result.success()
        }

        val targetDayKey = previousDayKey()
        val rawJson = thoughtRepository.readRawJson(targetDayKey)
        if (rawJson.isBlank()) {
            return Result.success()
        }

        return runCatching {
            val markdown = llmSummaryClient.summarizeDay(
                dayKey = targetDayKey,
                rawJson = rawJson,
                apiKey = settings.geminiApiKey,
            )
            dailySummaryRepository.saveSummaryForDay(targetDayKey, markdown)
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
            .putString("error", error.message ?: "Nightly summary generation failed")
            .build()
    }
}
