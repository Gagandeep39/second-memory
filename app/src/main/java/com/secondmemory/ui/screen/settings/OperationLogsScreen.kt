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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.Scaffold
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondmemory.domain.repository.OperationLogRepository
import com.secondmemory.util.formatDateTime
import kotlinx.coroutines.launch

/**
 * Highlights all occurrences of [query] in [text] with the given [highlightColor] as background.
 * If [query] is blank, returns [text] as a plain AnnotatedString.
 */
@Composable
private fun highlightQuery(
    text: String,
    query: String,
    highlightColor: Color
): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()
    val builder = AnnotatedString.Builder()
    var idx = 0
    while (idx < lowerText.length) {
        val matchIdx = lowerText.indexOf(lowerQuery, idx)
        if (matchIdx == -1) {
            builder.append(text.substring(idx))
            break
        }
        if (matchIdx > idx) {
            builder.append(text.substring(idx, matchIdx))
        }
        builder.withStyle(SpanStyle(background = highlightColor)) {
            builder.append(text.substring(matchIdx, matchIdx + lowerQuery.length))
        }
        idx = matchIdx + lowerQuery.length
    }
    return builder.toAnnotatedString()
}

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
    var searchQuery by remember { mutableStateOf("") }
    val filteredLogs = if (searchQuery.isBlank()) logs else logs.filter {
        it.category.contains(searchQuery, ignoreCase = true) ||
        it.status.contains(searchQuery, ignoreCase = true) ||
        it.action.contains(searchQuery, ignoreCase = true) ||
        (it.details?.contains(searchQuery, ignoreCase = true) == true) ||
        (it.source?.contains(searchQuery, ignoreCase = true) == true)
    }

    Scaffold (
        topBar = {
            TopAppBar(
                title = { Text("Operation Logs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
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
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    label = { Text("Search logs") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    }
                )
                if (filteredLogs.isEmpty()) {
                    Text(
                        text = if (logs.isEmpty()) "No operations recorded yet." else "No logs match your search.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // Top spacing
                        item {
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
                        }
                        items(filteredLogs.size, key = { idx -> filteredLogs[idx].id }) { idx ->
                            val entry = filteredLogs[idx]
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(1.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = highlightQuery(entry.category, searchQuery, MaterialTheme.colorScheme.primary),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Text(
                                        text = highlightQuery(entry.status, searchQuery, MaterialTheme.colorScheme.primary),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = when (entry.status) {
                                            "SUCCESS" -> MaterialTheme.colorScheme.primary
                                            "ERROR" -> MaterialTheme.colorScheme.error
                                            "STARTED" -> MaterialTheme.colorScheme.secondary
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }

                                Text(
                                    text = highlightQuery(entry.action, searchQuery, MaterialTheme.colorScheme.primary),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )

                                entry.details?.let { details ->
                                    Text(
                                        text = highlightQuery(details, searchQuery, MaterialTheme.colorScheme.primary),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 1.dp)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = formatDateTime(entry.timestampMillis),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                    entry.source?.let { source ->
                                        Text(
                                            text = highlightQuery(source, searchQuery, MaterialTheme.colorScheme.primary),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                }
                            }
                            if (idx < filteredLogs.lastIndex) {
                                androidx.compose.material3.HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp),
                                    thickness = 0.7.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
