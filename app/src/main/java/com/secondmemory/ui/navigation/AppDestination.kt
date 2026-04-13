package com.secondmemory.ui.navigation

import com.secondmemory.R

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

    data object RecordThought : AppDestination(
        route = "record_thought",
        label = "Record Thought",
        icon = R.drawable.ic_home,
    )

    companion object {
        val topLevel = listOf(RawThoughts, DailyView, Settings)
    }
}
