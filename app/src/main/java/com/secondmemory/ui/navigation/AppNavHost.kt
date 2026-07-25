package com.secondmemory.ui.navigation

import android.content.Intent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.secondmemory.RecordThoughtActivity
import com.secondmemory.domain.llm.LlmSummaryClient
import com.secondmemory.domain.repository.DailySummaryRepository
import com.secondmemory.domain.repository.OperationLogRepository
import com.secondmemory.domain.repository.SettingsRepository
import com.secondmemory.domain.repository.ThoughtRepository
import com.secondmemory.domain.repository.WeeklySummaryRepository
import com.secondmemory.ui.screen.SummaryDetailScreen
import com.secondmemory.ui.screen.dailyview.DailyViewScreen
import com.secondmemory.ui.screen.weeklyview.WeeklyViewScreen
import com.secondmemory.ui.screen.rawthoughts.RawThoughtsScreen
import com.secondmemory.ui.screen.settings.OperationLogsScreen
import com.secondmemory.ui.screen.settings.SettingsScreen
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Defines app navigation routes and wires repositories into destination screens.
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    thoughtRepository: ThoughtRepository,
    dailySummaryRepository: DailySummaryRepository,
    weeklySummaryRepository: WeeklySummaryRepository,
    settingsRepository: SettingsRepository,
    operationLogRepository: OperationLogRepository,
    llmSummaryClient: LlmSummaryClient,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = AppDestination.RawThoughts.route,
    ) {
        composable(AppDestination.RawThoughts.route) {
            RawThoughtsScreen(
                thoughtRepository = thoughtRepository,
                onRecordThought = {
                    context.startActivity(Intent(context, RecordThoughtActivity::class.java))
                },
            )
        }
        composable(AppDestination.DailyView.route) {
            DailyViewScreen(
                thoughtRepository = thoughtRepository,
                dailySummaryRepository = dailySummaryRepository,
                settingsRepository = settingsRepository,
                operationLogRepository = operationLogRepository,
                onOpenSummary = { fileName ->
                    navController.navigate(AppDestination.SummaryDetail.routeForFile(fileName))
                },
                snackbarHostState = snackbarHostState
            )
        }
        composable(AppDestination.WeeklyView.route) {
            WeeklyViewScreen(
                weeklySummaryRepository = weeklySummaryRepository,
                dailySummaryRepository = dailySummaryRepository,
                settingsRepository = settingsRepository,
                operationLogRepository = operationLogRepository,
                llmSummaryClient = llmSummaryClient,
                onOpenSummary = { fileName ->
                    navController.navigate(AppDestination.SummaryDetail.routeForFile(fileName))
                },
                snackbarHostState = snackbarHostState
            )
        }
        composable(
            route = AppDestination.SummaryDetail.route,
            arguments = listOf(navArgument("fileName") { type = NavType.StringType }),
        ) { backStackEntry ->
            val encoded = backStackEntry.arguments?.getString("fileName").orEmpty()
            val fileName = URLDecoder.decode(encoded, StandardCharsets.UTF_8.toString())

            SummaryDetailScreen(
                fileName = fileName,
                loadContent = { name ->
                    // Let's just try both repositories
                    val weekly = weeklySummaryRepository.readSummary(name)
                    if (weekly.isNotBlank()) weekly else dailySummaryRepository.readSummary(name)
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppDestination.Settings.route) {
            SettingsScreen(
                settingsRepository = settingsRepository,
                llmSummaryClient = llmSummaryClient,
                onOpenOperationLogs = {
                    navController.navigate(AppDestination.OperationLogs.route)
                }
            )
        }
        composable(AppDestination.OperationLogs.route) {
            OperationLogsScreen(
                operationLogRepository = operationLogRepository,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
