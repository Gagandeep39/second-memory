package com.secondmemory.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.BottomAppBarDefaults.windowInsets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondmemory.domain.repository.OperationLogRepository
import com.secondmemory.util.formatDateTime
import kotlinx.coroutines.launch

/**
 * Screen that lists operational events for sync, API calls, and WorkManager jobs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationLogsScreen(
    operationLogRepository: OperationLogRepository,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val logs by operationLogRepository.observeLogs().collectAsState(initial = emptyList())

    Scaffold (
        topBar = {
            TopAppBar(title = {
                Text("Operation Logs")
            },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // The spacer is no longer needed; 'actions' automatically aligns to the right
                    IconButton(
                        onClick = { scope.launch { operationLogRepository.clearLogs() } }
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Logs"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (logs.isEmpty()) {
                    Text(
                        text = "No operations recorded yet.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Top spacing
                        item {
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                        }
                        items(logs, key = { entry -> entry.id }) { entry ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = "${entry.category} • ${entry.status}",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = entry.action,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = formatDateTime(entry.timestampMillis),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    entry.details?.let { details ->
                                        Text(
                                            text = details,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    entry.source?.let { source ->
                                        Text(
                                            text = "source: $source",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        // Bottom spacing
                        item {
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}
