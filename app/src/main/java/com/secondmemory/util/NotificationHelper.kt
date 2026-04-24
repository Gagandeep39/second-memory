package com.secondmemory.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.secondmemory.MainActivity
import com.secondmemory.R

/**
 * Helper class to manage notification channels and sending notifications.
 */
class NotificationHelper(private val context: Context) {

    // TODO Switch to a non hacky approach later
    private val settingsRepository: com.secondmemory.domain.repository.SettingsRepository by lazy {
        // This is a bit of a hack to get the repository without dependency injection in this utility
        com.secondmemory.data.repository.DataStoreSettingsRepository(
            context,
            com.secondmemory.data.repository.DataStoreSyncRepository(
                context,
                com.secondmemory.data.drive.GoogleDriveSyncClient(context)
            )
        )
    }

    companion object {
        const val CHANNEL_SYNC_ALERTS = "sync_alerts"
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_SUMMARY = "summary"

        private const val NOTIFICATION_ID_SYNC_CONFLICT = 1001
    }

    /**
     * Creates the notification channels required by the app.
     * Should be called on app startup or before showing any notification.
     */
    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_SYNC_ALERTS,
                    "Sync Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts about Google Drive sync issues or conflicts"
                },
                NotificationChannel(
                    CHANNEL_REMINDERS,
                    "Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Daily reminders to record your thoughts"
                },
                NotificationChannel(
                    CHANNEL_SUMMARY,
                    "Daily Summaries",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Notifications when your daily summary is ready"
                }
            )

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannels(channels)
        }
    }

    /**
     * Shows a notification when a Google Drive sync conflict is detected.
     */
    suspend fun showSyncConflictNotification() {
        if (!settingsRepository.currentSettings().notificationsEnabled) {
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_SYNC_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Using foreground as a placeholder
            .setContentTitle("Sync Conflict Detected")
            .setContentText("Conflict in Google Drive. Please check the 'conflicts' folder in your Drive.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_SYNC_CONFLICT, builder.build())
        } catch (e: SecurityException) {
            // Handle missing notification permission on Android 13+
        }
    }
}
