package com.secondmemory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.secondmemory.background.BackgroundWorkScheduler
import com.secondmemory.data.drive.GoogleDriveSyncClient
import com.secondmemory.data.llm.GeminiLlmSummaryClient
import com.secondmemory.data.repository.DataStoreOperationLogRepository
import com.secondmemory.data.repository.DataStoreSettingsRepository
import com.secondmemory.data.repository.FileDailySummaryRepository
import com.secondmemory.data.repository.JsonThoughtRepository
import com.secondmemory.data.repository.DataStoreSyncRepository
import com.secondmemory.ui.component.AppSnackbar
import com.secondmemory.ui.navigation.AppDestination
import com.secondmemory.ui.navigation.AppNavHost
import com.secondmemory.ui.theme.SecondMemoryTheme
import com.secondmemory.util.ensureAppDataDirectories

/**
 * Main Android activity that hosts the Compose app shell.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BackgroundWorkScheduler.scheduleRecurringWork(this)
        enableEdgeToEdge()
        setContent {
            SecondMemoryTheme {
                SecondMemoryApp()
            }
        }
    }
}

/**
 * Root composable that configures adaptive top-level navigation and dependencies.
 */
@PreviewScreenSizes
@Composable
fun SecondMemoryApp() {
    val context = LocalContext.current
    remember(context) { ensureAppDataDirectories(context) }
    val thoughtRepository = remember(context) {
        JsonThoughtRepository(context)
    }
    val dailySummaryRepository = remember(context) { FileDailySummaryRepository(context) }
    val driveSyncClient = remember(context) { GoogleDriveSyncClient(context) }
    val syncRepository = remember(context) {
        DataStoreSyncRepository(
            context = context,
            driveSyncClient = driveSyncClient,
        )
    }
    val settingsRepository = remember(context) {
        DataStoreSettingsRepository(
            context = context,
            syncRepository = syncRepository,
        )
    }
    val llmSummaryClient = remember { GeminiLlmSummaryClient() }
    val operationLogRepository = remember(context) { DataStoreOperationLogRepository(context) }
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = backStackEntry?.destination?.route
    val topLevelDestinations = AppDestination.topLevel

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            topLevelDestinations.forEach { destination ->
                val route = destination.route
                val label = destination.label
                val icon = destination.icon
                item(
                    icon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    label = { Text(label) },
                    selected = currentRoute == route,
                    onClick = {
                        if (currentRoute != route) {
                            navController.navigate(route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    ) {
        Scaffold(
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    AppSnackbar(snackbarData = data)
                }
            }
        ) { innerPadding ->
            Box ()
            {
                AppNavHost(
                    navController = navController,
                    thoughtRepository = thoughtRepository,
                    dailySummaryRepository = dailySummaryRepository,
                    settingsRepository = settingsRepository,
                    operationLogRepository = operationLogRepository,
                    llmSummaryClient = llmSummaryClient,
                    snackbarHostState = snackbarHostState,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SecondMemoryAppPreview() {
    SecondMemoryTheme {
        SecondMemoryApp()
    }
}