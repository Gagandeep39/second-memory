package com.secondmemory.ui.screen.weeklyview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.secondmemory.domain.llm.LlmSummaryClient
import com.secondmemory.domain.repository.DailySummaryRepository
import com.secondmemory.domain.repository.OperationLogRepository
import com.secondmemory.domain.repository.SettingsRepository
import com.secondmemory.domain.repository.WeeklySummaryRepository
import com.secondmemory.util.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

/**
 * Screen that lists weekly summary markdown files and can generate/open summaries.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyViewScreen(
    weeklySummaryRepository: WeeklySummaryRepository,
    dailySummaryRepository: DailySummaryRepository,
    settingsRepository: SettingsRepository,
    operationLogRepository: OperationLogRepository,
    llmSummaryClient: LlmSummaryClient,
    snackbarHostState: SnackbarHostState,
    onOpenSummary: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var weeklyItems by remember { mutableStateOf(emptyList<WeeklySummaryItem>()) }
    var activeSummarizeWeek by remember { mutableStateOf<String?>(null) }
    
    val listState = rememberLazyListState()

    fun refresh() {
        scope.launch {
            val summaries = weeklySummaryRepository.listWeeklySummaries()
            weeklyItems = summaries.map { summaryFile ->
                val markdown = weeklySummaryRepository.readSummary(summaryFile.fileName)
                val lastUpdatedMillis = weeklySummaryRepository.lastUpdatedMillisForWeek(summaryFile.weekKey)
                WeeklySummaryItem(
                    weekKey = summaryFile.weekKey,
                    summaryWordCount = markdown.wordCount(),
                    summaryLastUpdatedMillis = lastUpdatedMillis,
                    fileName = summaryFile.fileName,
                )
            }
        }
    }

    val summarizeWeek: suspend (String) -> Unit = summarizeWeek@{ weekKey ->
        val settings = settingsRepository.currentSettings()
        if (settings.aiApiKey.isBlank()) {
            snackbarHostState.showSnackbar("Configure AI settings before summarizing.")
            return@summarizeWeek
        }

        val dayKeys = dayKeysInWeek(weekKey)
        val dailySummaries = dayKeys.mapNotNull { dayKey ->
            val content = dailySummaryRepository.readSummaryForDay(dayKey)
            if (content.isNotBlank()) "### $dayKey\n\n$content" else null
        }.joinToString("\n\n")

        if (dailySummaries.isBlank()) {
            snackbarHostState.showSnackbar("No daily summaries found for week $weekKey.")
            return@summarizeWeek
        }

        activeSummarizeWeek = weekKey
        
        operationLogRepository.appendLog(
            category = "SUMMARY",
            action = "Generate weekly summary",
            status = "STARTED",
            details = "weekKey=$weekKey",
            source = "WeeklyViewScreen"
        )

        runCatching {
            llmSummaryClient.summarizeWeek(
                weekKey = weekKey,
                dailySummaries = dailySummaries,
                provider = settings.aiProvider,
                baseUrl = settings.aiBaseUrl,
                apiKey = settings.aiApiKey,
                model = settings.aiModel,
                prompt = settings.customPrompt
            )
        }.onSuccess { markdown ->
            weeklySummaryRepository.saveSummaryForWeek(weekKey, markdown)
            activeSummarizeWeek = null
            refresh()
            snackbarHostState.showSnackbar("Weekly summary generated for $weekKey.")
        }.onFailure { error ->
            activeSummarizeWeek = null
            snackbarHostState.showSnackbar("Summary failed: ${error.message}")
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weekly Summaries") }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val currentWeek = currentWeekKey()
                    scope.launch { summarizeWeek(currentWeek) }
                },
                text = { Text("Summarize This Week") },
                icon = { Icon(Icons.Default.AutoAwesome, null) },
                containerColor = if (activeSummarizeWeek != null) 
                    MaterialTheme.colorScheme.surfaceVariant 
                else MaterialTheme.colorScheme.primaryContainer,
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(weeklyItems) { item ->
                    WeeklySummaryCard(
                        item = item,
                        isBusy = activeSummarizeWeek == item.weekKey,
                        onOpen = { onOpenSummary(item.fileName) },
                        onSummarize = { scope.launch { summarizeWeek(item.weekKey) } }
                    )
                }
            }
            
            if (activeSummarizeWeek != null) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun WeeklySummaryCard(
    item: WeeklySummaryItem,
    isBusy: Boolean,
    onOpen: () -> Unit,
    onSummarize: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Week ${item.weekKey}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${item.summaryWordCount} words",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (isBusy) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                IconButton(onClick = onSummarize) {
                    Icon(Icons.Default.Refresh, null)
                }
            }
        }
    }
}

private data class WeeklySummaryItem(
    val weekKey: String,
    val summaryWordCount: Int,
    val summaryLastUpdatedMillis: Long?,
    val fileName: String,
)

private fun String.wordCount(): Int = if (isBlank()) 0 else trim().split(Regex("\\s+")).size
