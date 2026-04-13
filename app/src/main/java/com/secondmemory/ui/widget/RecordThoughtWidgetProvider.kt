package com.secondmemory.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.secondmemory.RecordThoughtActivity
import com.secondmemory.R

/**
 * Home screen widget that opens the Record Thought screen with one tap.
 */
class RecordThoughtWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, createRemoteViews(context))
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        updateAllWidgets(context)
    }

    companion object {
        /**
         * Refreshes all instances of this widget.
         */
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, RecordThoughtWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            appWidgetIds.forEach { appWidgetId ->
                appWidgetManager.updateAppWidget(appWidgetId, createRemoteViews(context))
            }
        }

        /**
         * Builds the widget view hierarchy and click behavior.
         */
        private fun createRemoteViews(context: Context): RemoteViews {
            return RemoteViews(context.packageName, R.layout.widget_record_thought).apply {
                setOnClickPendingIntent(
                    R.id.widget_record_thought_root,
                    openRecordThoughtPendingIntent(context),
                )
            }
        }

        /**
         * Creates the immutable pending intent used by widget taps.
         */
        private fun openRecordThoughtPendingIntent(context: Context): PendingIntent {
            val launchIntent = Intent(context, RecordThoughtActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getActivity(context, 1001, launchIntent, flags)
        }
    }
}
