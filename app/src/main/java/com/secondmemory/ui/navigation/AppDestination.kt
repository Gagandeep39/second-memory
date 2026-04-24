package com.secondmemory.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    data object RawThoughts : AppDestination(
        route = "raw_thoughts",
        label = "Thoughts",
        icon = Icons.Default.Psychology,
    )

    data object DailyView : AppDestination(
        route = "daily_view",
        label = "Daily",
        icon = Icons.Default.Today,
    )

    data object WeeklyView : AppDestination(
        route = "weekly_view",
        label = "Weekly",
        icon = Icons.Default.DateRange,
    )

    data object Settings : AppDestination(
        route = "settings",
        label = "Settings",
        icon = Icons.Default.Settings,
    )

    /**
     * Route for the detailed operation log screen.
     */
    data object OperationLogs : AppDestination(
        route = "operation_logs",
        label = "Operation Logs",
        icon = Icons.Default.History,
    )

    data object RecordThought : AppDestination(
        route = "record_thought",
        label = "Capture",
        icon = Icons.Default.GraphicEq,
    )

    data object SummaryDetail : AppDestination(
        route = "summary_detail/{fileName}",
        label = "Summary Detail",
        icon = Icons.Default.Description,
    ) {
        /**
         * Creates a concrete route with encoded file name.
         */
        fun routeForFile(fileName: String): String {
            val encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString())
            return "summary_detail/$encoded"
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
            get() = listOf(RawThoughts, DailyView, WeeklyView, Settings)
    }
}
