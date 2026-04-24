package com.secondmemory.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.secondmemory.MainActivity
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
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            appWidgetManager.updateAppWidget(appWidgetId, createRemoteViews(context, minWidth))
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        val minWidth = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        appWidgetManager.updateAppWidget(appWidgetId, createRemoteViews(context, minWidth))
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
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
                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                appWidgetManager.updateAppWidget(appWidgetId, createRemoteViews(context, minWidth))
            }
        }

        /**
         * Builds the widget view hierarchy and click behavior.
         */
        private fun createRemoteViews(context: Context, minWidth: Int = 0): RemoteViews {
            return RemoteViews(context.packageName, R.layout.widget_record_thought).apply {
                val isCompact = minWidth > 0 && minWidth < 250
                
                // Toggle text and app launch button styles based on width
                if (isCompact) {
                    setViewVisibility(R.id.widget_text_container, android.view.View.GONE)
                    setViewVisibility(R.id.widget_launch_app_expanded, android.view.View.GONE)
                    setViewVisibility(R.id.widget_launch_app_compact, android.view.View.VISIBLE)
                } else {
                    setViewVisibility(R.id.widget_text_container, android.view.View.VISIBLE)
                    setViewVisibility(R.id.widget_launch_app_expanded, android.view.View.VISIBLE)
                    setViewVisibility(R.id.widget_launch_app_compact, android.view.View.GONE)
                }

                val mainAppIntent = openMainAppPendingIntent(context)
                setOnClickPendingIntent(R.id.widget_launch_app_expanded, mainAppIntent)
                setOnClickPendingIntent(R.id.widget_launch_app_compact, mainAppIntent)

                setOnClickPendingIntent(
                    R.id.widget_record_thought_action,
                    openRecordThoughtPendingIntent(context),
                )
            }
        }

        /**
         * Creates the immutable pending intent used to launch the main app.
         */
        private fun openMainAppPendingIntent(context: Context): PendingIntent {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getActivity(context, 1002, launchIntent, flags)
        }

        /**
         * Creates the immutable pending intent used by widget taps to record a thought.
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
