package com.secondmemory.ui.screen.dailyview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.secondmemory.domain.llm.LlmSummaryClient
import com.secondmemory.domain.repository.DailySummaryRepository
import com.secondmemory.domain.repository.SettingsRepository
import com.secondmemory.domain.repository.ThoughtRepository
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp

/**
 * Screen that lists raw-date entries and can generate/open daily summaries.
 */
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

    fun refresh() {
        scope.launch {
            val dayKeys = thoughtRepository.listAvailableDayKeys()
            dayItems = dayKeys.map { dayKey ->
                val markdown = dailySummaryRepository.readSummaryForDay(dayKey)
                DaySummaryItem(
                    dayKey = dayKey,
                    hasSummary = markdown.isNotBlank(),
                    preview = markdown.lineSequence().firstOrNull { it.isNotBlank() } ?: "(No summary yet)",
                    fileName = dailySummaryRepository.summaryFileName(dayKey),
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Daily View",
            style = MaterialTheme.typography.headlineMedium,
        )

        statusMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (dayItems.isEmpty()) {
            Text(
                text = "No raw day files found yet. Capture raw thoughts first.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(dayItems, key = { item -> item.dayKey }) { item ->
                    DailySummaryItem(
                        item = item,
                        isBusy = activeSummarizeDay == item.dayKey,
                        onOpen = { onOpenSummary(item.fileName) },
                        onSummarize = {
                            scope.launch {
                                val settings = settingsRepository.currentSettings()
                                if (settings.geminiApiKey.isBlank()) {
                                    statusMessage = "Add Gemini API key in Settings before summarizing."
                                    return@launch
                                }
                                if (!settings.cloudSummaryEnabled) {
                                    statusMessage = "Enable Cloud Summaries in Settings to summarize."
                                    return@launch
                                }

                                val rawJson = thoughtRepository.readRawJson(item.dayKey)
                                if (rawJson.isBlank()) {
                                    statusMessage = "Raw JSON for ${item.dayKey} is empty or missing."
                                    return@launch
                                }

                                activeSummarizeDay = item.dayKey
                                statusMessage = "Summarizing ${item.dayKey}..."

                                runCatching {
                                    llmSummaryClient.summarizeDay(
                                        dayKey = item.dayKey,
                                        rawJson = rawJson,
                                        apiKey = settings.geminiApiKey,
                                    )
                                }.onSuccess { markdown ->
                                    dailySummaryRepository.saveSummaryForDay(item.dayKey, markdown)
                                    statusMessage = "Summary generated for ${item.dayKey}."
                                    refresh()
                                }.onFailure { error ->
                                    statusMessage = "Summary failed: ${error.message}"
                                }

                                activeSummarizeDay = null
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * Row card representing one day and summary actions.
 */
@Composable
private fun DailySummaryItem(
    item: DaySummaryItem,
    isBusy: Boolean,
    onOpen: () -> Unit,
    onSummarize: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = item.dayKey, style = MaterialTheme.typography.titleMedium)
            Text(text = item.preview, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !isBusy,
                    onClick = onSummarize,
                ) {
                    Text(if (isBusy) "Summarizing..." else "Summarize")
                }
                if (item.hasSummary) {
                    TextButton(onClick = onOpen) {
                        Text("Open")
                    }
                }
            }
        }
    }
}

/**
 * UI model for each day displayed in Daily View.
 */
private data class DaySummaryItem(
    val dayKey: String,
    val hasSummary: Boolean,
    val preview: String,
    val fileName: String,
)
