package com.secondmemory.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.secondmemory.data.llm.DefaultLlmSummaryClient
import com.secondmemory.data.repository.DataStoreOperationLogRepository
import com.secondmemory.data.repository.DataStoreSettingsRepository
import com.secondmemory.data.repository.DataStoreSyncRepository
import com.secondmemory.data.repository.FileDailySummaryRepository
import com.secondmemory.data.repository.FileWeeklySummaryRepository
import com.secondmemory.data.drive.GoogleDriveSyncClient
import com.secondmemory.util.currentWeekKey
import com.secondmemory.util.dayKeysInWeek
import com.secondmemory.util.ensureAppDataDirectories
import com.secondmemory.util.hasInternetConnection

/**
 * Worker that generates a weekly summary from daily summaries.
 */
class WeeklySummaryWorker(
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
    private val dailySummaryRepository = FileDailySummaryRepository(appContext)
    private val weeklySummaryRepository = FileWeeklySummaryRepository(appContext)
    private val llmSummaryClient = DefaultLlmSummaryClient()

    override suspend fun doWork(): Result {
        val targetWeekKey = inputData.getString(KEY_WEEK_KEY) ?: currentWeekKey()

        operationLogRepository.appendLog(
            category = "WORK",
            action = "Weekly summary worker started",
            status = "STARTED",
            details = "weekKey=$targetWeekKey, runAttempt=${runAttemptCount + 1}",
            source = "WeeklySummaryWorker",
        )
        ensureAppDataDirectories(applicationContext)

        val settings = settingsRepository.currentSettings()
        
        val dayKeys = dayKeysInWeek(targetWeekKey)
        val dailySummaries = dayKeys.mapNotNull { dayKey ->
            val content = dailySummaryRepository.readSummaryForDay(dayKey)
            if (content.isNotBlank()) "### $dayKey\n\n$content" else null
        }.joinToString("\n\n")

        if (dailySummaries.isBlank()) {
            operationLogRepository.appendLog(
                category = "WORK",
                action = "Weekly summary worker skipped",
                status = "SKIPPED",
                details = "No daily summaries for week $targetWeekKey",
                source = "WeeklySummaryWorker",
            )
            return Result.success()
        }

        return runCatching {
            if (!hasInternetConnection()) {
                throw java.io.IOException("No internet connectivity detected")
            }
            val markdown = llmSummaryClient.summarizeWeek(
                weekKey = targetWeekKey,
                dailySummaries = dailySummaries,
                provider = settings.aiProvider,
                baseUrl = settings.aiBaseUrl,
                apiKey = settings.aiApiKey,
                model = settings.aiModel,
                prompt = settings.customPrompt
            )
            weeklySummaryRepository.saveSummaryForWeek(targetWeekKey, markdown)
            operationLogRepository.appendLog(
                category = "WORK",
                action = "Weekly summary generated",
                status = "SUCCESS",
                details = targetWeekKey,
                source = "WeeklySummaryWorker",
            )
            Result.success()
        }.getOrElse { error ->
            val retry = shouldRetryWork(error, runAttemptCount)
            val errorMessage = error.message ?: "Weekly summary generation failed"
            operationLogRepository.appendLog(
                category = "WORK",
                action = "Weekly summary worker failed",
                status = if (retry) "RETRY" else "ERROR",
                details = "$errorMessage (attempt ${runAttemptCount + 1})",
                source = "WeeklySummaryWorker",
            )
            if (retry) {
                Result.retry()
            } else {
                Result.failure(errorData(error))
            }
        }
    }

    private fun errorData(error: Throwable): Data {
        return Data.Builder()
            .putString("error", error.message ?: "Weekly summary generation failed")
            .build()
    }

    companion object {
        const val KEY_WEEK_KEY = "week_key"
    }
}
