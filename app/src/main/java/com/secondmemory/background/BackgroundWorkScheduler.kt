package com.secondmemory.background

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.secondmemory.data.repository.DataStoreOperationLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Registers recurring background jobs used for sync and nightly summary generation.
 */
object BackgroundWorkScheduler {
    /**
     * Enqueues or cancels recurring background jobs based on current settings.
     */
    fun scheduleRecurringWork(context: Context) {
        val operationLogRepository = DataStoreOperationLogRepository(context)
        val workManager = WorkManager.getInstance(context)
        CoroutineScope(Dispatchers.IO).launch {
            // Read current settings
            val settingsRepository = com.secondmemory.data.repository.DataStoreSettingsRepository(
                context = context,
                syncRepository = com.secondmemory.data.repository.DataStoreSyncRepository(
                    context = context,
                    driveSyncClient = com.secondmemory.data.drive.GoogleDriveSyncClient(context),
                ),
            )
            val settings = settingsRepository.currentSettings()

            // Drive sync job
            if (settings.driveSyncEnabled) {
                workManager.enqueueUniquePeriodicWork(
                    DriveSyncWork.UNIQUE_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    createDriveSyncRequest(),
                )
            } else {
                workManager.cancelUniqueWork(DriveSyncWork.UNIQUE_NAME)
            }

            // Daily summary job
            if (settings.cloudSummaryEnabled) {
                workManager.enqueueUniquePeriodicWork(
                    DailySummaryWork.UNIQUE_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    createDailySummaryRequest(),
                )
            } else {
                workManager.cancelUniqueWork(DailySummaryWork.UNIQUE_NAME)
            }

            operationLogRepository.appendLog(
                category = "WORK",
                action = "Recurring work scheduled",
                status = "SUCCESS",
                details = "drive=${settings.driveSyncEnabled}, dailySummary=${settings.cloudSummaryEnabled}",
                source = "BackgroundWorkScheduler",
            )
        }
    }

    /**
     * Creates the periodic request used for Drive mirror sync.
     */
    private fun createDriveSyncRequest() = PeriodicWorkRequestBuilder<DriveSyncWorker>(
        DriveSyncWork.REPEAT_HOURS,
        TimeUnit.HOURS,
    )
        .setConstraints(networkConstraint())
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            COMMON_BACKOFF_SECONDS,
            TimeUnit.SECONDS,
        )
        .build()

    /**
     * Creates the nightly request that generates previous-day summaries.
     */
    private fun createDailySummaryRequest() = PeriodicWorkRequestBuilder<DailySummaryWorker>(
        DailySummaryWork.REPEAT_HOURS,
        TimeUnit.HOURS,
    )
        .setInitialDelay(nextDailyDelayMillis(), TimeUnit.MILLISECONDS)
        .setConstraints(networkConstraint())
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            COMMON_BACKOFF_SECONDS,
            TimeUnit.SECONDS,
        )
        .build()

    /**
     * Shared network requirements for all recurring background jobs.
     */
    private fun networkConstraint(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }

    /**
     * Computes delay until the next local nightly trigger time.
     */
    private fun nextDailyDelayMillis(now: LocalDateTime = LocalDateTime.now()): Long {
        val nextTrigger = now.withHour(1).withMinute(15).withSecond(0).withNano(0)
        val target = if (nextTrigger.isAfter(now)) nextTrigger else nextTrigger.plusDays(1)
        return Duration.between(now, target).toMillis().coerceAtLeast(0L)
    }

    /**
     * Constants that belong specifically to periodic Drive sync scheduling.
     */
    private object DriveSyncWork {
        const val UNIQUE_NAME = "periodic_drive_sync"
        const val REPEAT_HOURS = 6L
    }

    /**
     * Constants that belong specifically to nightly summary scheduling.
     */
    private object DailySummaryWork {
        const val UNIQUE_NAME = "nightly_daily_summary"
        const val REPEAT_HOURS = 24L
    }

    private const val COMMON_BACKOFF_SECONDS = 30L
}
