package com.secondmemory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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
import com.secondmemory.data.repository.DataStoreSettingsRepository
import com.secondmemory.data.repository.JsonThoughtRepository
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
    val settingsRepository = remember(context) { DataStoreSettingsRepository(context) }
    val navController = rememberNavController()
    val backStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = backStackEntry?.destination?.route
    val topLevelDestinations = AppDestination.topLevel

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            topLevelDestinations.forEach { destination ->
                item(
                    icon = {
                        Icon(
                            painterResource(destination.icon),
                            contentDescription = destination.label,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    label = { Text(destination.label) },
                    selected = currentRoute == destination.route,
                    onClick = {
                        if (currentRoute != destination.route) {
                            navController.navigate(destination.route) {
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
        AppNavHost(
            navController = navController,
            thoughtRepository = thoughtRepository,
            settingsRepository = settingsRepository,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SecondMemoryAppPreview() {
    SecondMemoryTheme {
        SecondMemoryApp()
    }
}