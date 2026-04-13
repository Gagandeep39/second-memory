package com.secondmemory.ui.navigation

import com.secondmemory.R
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class AppDestination(
    val route: String,
    val label: String,
    val icon: Int,
) {
    data object RawThoughts : AppDestination(
        route = "raw_thoughts",
        label = "Raw Thoughts",
        icon = R.drawable.ic_home,
    )

    data object DailyView : AppDestination(
        route = "daily_view",
        label = "Daily View",
        icon = R.drawable.ic_favorite,
    )

    data object Settings : AppDestination(
        route = "settings",
        label = "Settings",
        icon = R.drawable.ic_account_box,
    )

    /**
     * Route for the detailed operation log screen.
     */
    data object OperationLogs : AppDestination(
        route = "operation_logs",
        label = "Operation Logs",
        icon = R.drawable.ic_favorite,
    )

    data object RecordThought : AppDestination(
        route = "record_thought",
        label = "Record Thought",
        icon = R.drawable.ic_home,
    )

    data object DailySummaryDetail : AppDestination(
        route = "daily_summary_detail/{fileName}",
        label = "Daily Summary",
        icon = R.drawable.ic_favorite,
    ) {
        /**
         * Creates a concrete route with encoded file name.
         */
        fun routeForFile(fileName: String): String {
            val encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString())
            return "daily_summary_detail/$encoded"
        }
    }

    companion object {
        /**
         * Top-level destinations used by adaptive navigation chrome.
         *
         * This is computed on access to avoid JVM static initialization ordering issues
         * with object declarations in some runtime builds.
         */
        val topLevel: List<AppDestination>
            get() = listOf(RawThoughts, DailyView, Settings)
    }
}
