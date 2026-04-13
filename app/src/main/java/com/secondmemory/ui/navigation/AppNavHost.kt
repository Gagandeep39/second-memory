package com.secondmemory.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.secondmemory.ui.screen.dailyview.DailyViewScreen
import com.secondmemory.ui.screen.rawthoughts.RawThoughtsScreen
import com.secondmemory.ui.screen.record.RecordThoughtScreen
import com.secondmemory.ui.screen.settings.SettingsScreen

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = AppDestination.RawThoughts.route,
    ) {
        composable(AppDestination.RawThoughts.route) {
            RawThoughtsScreen(onRecordThought = {
                navController.navigate(AppDestination.RecordThought.route)
            })
        }
        composable(AppDestination.DailyView.route) {
            DailyViewScreen()
        }
        composable(AppDestination.Settings.route) {
            SettingsScreen()
        }
        composable(AppDestination.RecordThought.route) {
            RecordThoughtScreen(onBack = { navController.popBackStack() })
        }
    }
}
