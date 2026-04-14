package com.secondmemory.ui.screen.dailyview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondmemory.domain.llm.LlmSummaryClient
import com.secondmemory.domain.repository.DailySummaryRepository
import com.secondmemory.domain.repository.SettingsRepository
import com.secondmemory.domain.repository.ThoughtRepository
import com.secondmemory.util.dayKeyDisplayText
import com.secondmemory.util.formatDateTime
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size

/**
 * Screen that lists daily summary markdown files and can generate/open summaries.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyViewScreen(
    thoughtRepository: ThoughtRepository,
    dailySummaryRepository: DailySummaryRepository,
    settingsRepository: SettingsRepository,
    llmSummaryClient: LlmSummaryClient,
    onOpenSummary: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var dayItems by remember { mutableStateOf(emptyList<DaySummaryItem>()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var activeSummarizeDay by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var datePickerSeedMillis by remember { mutableStateOf(todayUtcStartOfDayMillis()) }
    val listState = rememberLazyListState()
    val summarizeFabExpanded by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 8
        }
    }

    fun refresh() {
        scope.launch {
            val summaries = dailySummaryRepository.listDailySummaries()
            dayItems = summaries.map { summaryFile ->
                val markdown = dailySummaryRepository.readSummary(summaryFile.fileName)
                val thoughtCount = thoughtRepository.listForDay(summaryFile.dayKey).size
                val lastUpdatedMillis = dailySummaryRepository.lastUpdatedMillisForDay(summaryFile.dayKey)
                DaySummaryItem(
                    dayKey = summaryFile.dayKey,
                    hasSummary = true,
                    thoughtCount = thoughtCount,
                    summaryWordCount = markdown.wordCount(),
                    summaryLastUpdatedMillis = lastUpdatedMillis,
                    fileName = summaryFile.fileName,
                )
            }
        }
    }

    val summarizeDay: suspend (String) -> Unit = summarizeDay@{ dayKey ->
        val settings = settingsRepository.currentSettings()
        if (settings.geminiApiKey.isBlank()) {
            statusMessage = "Add Gemini API key in Settings before summarizing."
            return@summarizeDay
        }
        if (!settings.cloudSummaryEnabled) {
            statusMessage = "Enable Cloud Summaries in Settings to summarize."
            return@summarizeDay
        }

        val rawJson = thoughtRepository.readRawJson(dayKey)
        if (rawJson.isBlank()) {
            statusMessage = "Raw JSON for $dayKey is empty or missing."
            return@summarizeDay
        }

        activeSummarizeDay = dayKey
        statusMessage = "Summarizing $dayKey..."

        runCatching {
            llmSummaryClient.summarizeDay(
                dayKey = dayKey,
                rawJson = rawJson,
                apiKey = settings.geminiApiKey,
            )
        }.onSuccess { markdown ->
            runCatching {
                dailySummaryRepository.saveSummaryForDay(dayKey, markdown)
            }.onSuccess {
                statusMessage = "Summary generated for $dayKey."
                refresh()
            }.onFailure { error ->
                statusMessage = "Failed to save summary: ${error.message ?: "unknown error"}"
            }
        }.onFailure { error ->
            statusMessage = "Summary failed: ${error.message ?: "unknown error"}"
        }

        activeSummarizeDay = null
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = datePickerSeedMillis,
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedDayKey = datePickerState.selectedDateMillis
                            ?.let(::dayKeyFromUtcMillis)
                            ?: dayKeyFromUtcMillis(datePickerSeedMillis)
                        showDatePicker = false
                        scope.launch {
                            summarizeDay(selectedDayKey)
                        }
                    },
                ) {
                    Text("Generate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FloatingActionButton(
                    onClick = {
                        datePickerSeedMillis = todayUtcStartOfDayMillis()
                        showDatePicker = true
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = "Pick summary date",
                    )
                }

                ExtendedFloatingActionButton(
                    onClick = {
                        scope.launch {
                            summarizeDay(dayKeyFromUtcMillis(todayUtcStartOfDayMillis()))
                        }
                    },
                    text = { Text("Summarize") },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = "Summarize today",
                        )
                    },
                    expanded = summarizeFabExpanded,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Daily",
                style = MaterialTheme.typography.headlineMedium,
            )

            statusMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (dayItems.isEmpty()) {
                Text(
                    text = "No daily summaries found yet. Use Calendar for any date or Summarize for today.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(dayItems, key = { item -> item.dayKey }) { item ->
                        DailySummaryItem(
                            item = item,
                            isBusy = activeSummarizeDay == item.dayKey,
                            onOpen = { onOpenSummary(item.fileName) },
                            onSummarize = {
                                scope.launch {
                                    summarizeDay(item.dayKey)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Row card representing one summary day and its actions.
 */
@Composable
private fun DailySummaryItem(
    item: DaySummaryItem,
    isBusy: Boolean,
    onOpen: () -> Unit,
    onSummarize: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = dayKeyDisplayText(item.dayKey),
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Column {
                    Text(
                        text = "Thoughts: ${item.thoughtCount} • Words: ${item.summaryWordCount}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (item.summaryLastUpdatedMillis != null) {
                        Text(
                            text = "Last summarized: ${formatDateTime(item.summaryLastUpdatedMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            trailingContent = {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onSummarize) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Re-summarize",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        )
    }
}

/**
 * UI model for each summary file displayed in Daily View.
 */
private data class DaySummaryItem(
    val dayKey: String,
    val hasSummary: Boolean,
    val thoughtCount: Int,
    val summaryWordCount: Int,
    val summaryLastUpdatedMillis: Long?,
    val fileName: String,
)

/**
 * Counts words in markdown text for quick metadata display.
 */
private fun String.wordCount(): Int {
    if (isBlank()) return 0
    return trim().split(Regex("\\s+")).count { token -> token.isNotBlank() }
}

/**
 * Returns today's date as a UTC midnight timestamp for the date picker.
 */
private fun todayUtcStartOfDayMillis(): Long {
    return LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

/**
 * Converts a date picker timestamp into the app's yyyymmdd day key.
 */
private fun dayKeyFromUtcMillis(timestampMillis: Long): String {
    return Instant.ofEpochMilli(timestampMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .format(DateTimeFormatter.BASIC_ISO_DATE)
}