package com.secondmemory.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
@Composable
fun OperationLogsScreen(
    operationLogRepository: OperationLogRepository,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val logs by operationLogRepository.observeLogs().collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Operation Logs",
            style = MaterialTheme.typography.headlineMedium,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
            ) {
                Text("Back")
            }
            Button(
                onClick = { scope.launch { operationLogRepository.clearLogs() } },
                modifier = Modifier.weight(1f),
            ) {
                Text("Clear Logs")
            }
        }

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
            }
        }
    }
}
