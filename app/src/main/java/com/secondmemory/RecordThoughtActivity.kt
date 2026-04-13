package com.secondmemory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.secondmemory.data.repository.JsonThoughtRepository
import com.secondmemory.ui.screen.record.RecordThoughtScreen
import com.secondmemory.ui.theme.SecondMemoryTheme

/**
 * Dedicated activity for recording a thought without app-level navigation chrome.
 *
 * Behavior:
 * - Launched from inside the app: finishing returns to the previous app screen.
 * - Launched from widget/shortcut: finishing closes this standalone flow.
 */
class RecordThoughtActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SecondMemoryTheme {
                val context = LocalContext.current
                val thoughtRepository = remember(context) { JsonThoughtRepository(context) }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    RecordThoughtScreen(
                        thoughtRepository = thoughtRepository,
                        onBack = { finish() },
                    )
                }
            }
        }
    }
}
