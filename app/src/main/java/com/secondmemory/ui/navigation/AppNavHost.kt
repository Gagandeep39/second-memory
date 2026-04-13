package com.secondmemory.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.secondmemory.domain.repository.SettingsRepository
import com.secondmemory.domain.repository.ThoughtRepository
import com.secondmemory.ui.screen.dailyview.DailyViewScreen
import com.secondmemory.ui.screen.rawthoughts.RawThoughtsScreen
import com.secondmemory.ui.screen.record.RecordThoughtScreen
import com.secondmemory.ui.screen.settings.SettingsScreen

/**
 * Defines app navigation routes and wires repositories into destination screens.
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    thoughtRepository: ThoughtRepository,
    settingsRepository: SettingsRepository,
) {
    NavHost(
        navController = navController,
        startDestination = AppDestination.RawThoughts.route,
    ) {
        composable(AppDestination.RawThoughts.route) {
            RawThoughtsScreen(
                thoughtRepository = thoughtRepository,
                onRecordThought = {
                    navController.navigate(AppDestination.RecordThought.route)
                },
            )
        }
        composable(AppDestination.DailyView.route) {
            DailyViewScreen()
        }
        composable(AppDestination.Settings.route) {
            SettingsScreen(settingsRepository = settingsRepository)
        }
        composable(AppDestination.RecordThought.route) {
            RecordThoughtScreen(
                thoughtRepository = thoughtRepository,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
