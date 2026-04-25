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
import androidx.compose.material.icons.automirrored.filled.Segment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.secondmemory.background.WeeklySummaryWorker
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
import java.time.YearMonth

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
    val context = LocalContext.current
    val workManager = remember { WorkManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    var weeklyItems by remember { mutableStateOf(emptyList<WeeklySummaryItem>()) }
    var activeSummarizeWeeks by remember { mutableStateOf(setOf<String>()) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    
    val listState = rememberLazyListState()
    val summarizeFabExpanded by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 8
        }
    }

    val currentMonth = remember { YearMonth.now() }
    val isCurrentMonth = selectedMonth == currentMonth

    val filteredItems = remember(weeklyItems, selectedMonth) {
        weeklyItems.filter { item ->
            val range = weekRangeFromKey(item.weekKey)
            val startMonth = YearMonth.from(range.first)
            val endMonth = YearMonth.from(range.second)
            startMonth == selectedMonth || endMonth == selectedMonth
        }.sortedByDescending { it.weekKey }
    }

    fun refresh() {
        scope.launch {
            val summaries = weeklySummaryRepository.listWeeklySummaries()
            weeklyItems = summaries.map { summaryFile ->
                val markdown = weeklySummaryRepository.readSummary(summaryFile.fileName)
                val lastUpdatedMillis = weeklySummaryRepository.lastUpdatedMillisForWeek(summaryFile.weekKey)
                
                // Count daily summaries used in this week
                val dayKeys = dayKeysInWeek(summaryFile.weekKey)
                val dailyCount = dayKeys.count { dailySummaryRepository.readSummaryForDay(it).isNotBlank() }
                
                WeeklySummaryItem(
                    weekKey = summaryFile.weekKey,
                    dailySummaryCount = dailyCount,
                    summaryWordCount = markdown.wordCount(),
                    summaryLastUpdatedMillis = lastUpdatedMillis,
                    fileName = summaryFile.fileName,
                )
            }
        }
    }

    val summarizeWeek: (String) -> Unit = { weekKey ->
        scope.launch {
            val settings = settingsRepository.currentSettings()
            if (settings.aiApiKey.isBlank()) {
                snackbarHostState.showSnackbar("Configure AI settings before summarizing.")
                return@launch
            }

            val dayKeys = dayKeysInWeek(weekKey)
            val dailySummaries = dayKeys.mapNotNull { dayKey ->
                val content = dailySummaryRepository.readSummaryForDay(dayKey)
                if (content.isNotBlank()) "### $dayKey\n\n$content" else null
            }.joinToString("\n\n")

            if (dailySummaries.isBlank()) {
                snackbarHostState.showSnackbar("No daily summaries found for week $weekKey.")
                return@launch
            }

            activeSummarizeWeeks = activeSummarizeWeeks + weekKey

            val workRequest = OneTimeWorkRequestBuilder<WeeklySummaryWorker>()
                .setInputData(workDataOf(WeeklySummaryWorker.KEY_WEEK_KEY to weekKey))
                .addTag("summary_job")
                .addTag("summary_$weekKey")
                .build()

            operationLogRepository.appendLog(
                category = "SUMMARY",
                action = "Generate weekly summary",
                status = "STARTED",
                details = "weekKey=$weekKey, provider=${settings.aiProvider}",
                source = "WeeklyViewScreen"
            )

            workManager.enqueueUniqueWork(
                "summary_$weekKey",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )

            snackbarHostState.showSnackbar("Weekly summary requested for $weekKey.")
        }
    }

    // Monitor all summary jobs reactively to update UI loading states
    LaunchedEffect(Unit) {
        // Observe all jobs tagged with "summary_job" to track their progress
        workManager.getWorkInfosByTagFlow("summary_job")
            .collect { infos ->
                val busyKeys = mutableSetOf<String>()
                val seenKeys = mutableSetOf<String>()
                var shouldRefresh = false
                
                infos.forEach { info ->
                    // Extract the specific weekKey from tags (e.g., "summary_2024-W10").
                    val weekKeyTag = info.tags.firstOrNull { it.startsWith("summary_") && it != "summary_job" }
                    val weekKey = weekKeyTag?.removePrefix("summary_")
                    
                    if (weekKey != null) {
                        seenKeys.add(weekKey)
                        val isFinished = info.state.isFinished
                        
                        if (!isFinished) {
                            // If the job is active, add it to the set to show loading indicators
                            busyKeys.add(weekKey)
                        } else if (activeSummarizeWeeks.contains(weekKey)) {
                            // Terminal state reached. If we were tracking this key, show result feedback.
                            when (info.state) {
                                androidx.work.WorkInfo.State.SUCCEEDED -> {
                                    shouldRefresh = true
                                    scope.launch { snackbarHostState.showSnackbar("Weekly summary generated for $weekKey") }
                                }
                                androidx.work.WorkInfo.State.FAILED -> {
                                    val error = info.outputData.getString("error") ?: "Unknown error"
                                    scope.launch { snackbarHostState.showSnackbar("Failed for $weekKey: $error") }
                                }
                                androidx.work.WorkInfo.State.CANCELLED -> {
                                    scope.launch { snackbarHostState.showSnackbar("Summary cancelled for $weekKey") }
                                }
                                else -> {}
                            }
                        }
                    }
                }
                
                // Update activeSummarizeWeeks with optimistic UI logic
                activeSummarizeWeeks = busyKeys + (activeSummarizeWeeks - seenKeys)
                
                if (shouldRefresh) {
                    refresh()
                }
            }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    if (showMonthPicker) {
        AlertDialog(
            onDismissRequest = { showMonthPicker = false },
            title = { Text("Select Month") },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedMonth = selectedMonth.minusYears(1) }) {
                            Icon(Icons.Default.ChevronLeft, null)
                        }
                        Text(selectedMonth.year.toString(), style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = { selectedMonth = selectedMonth.plusYears(1) }) {
                            Icon(Icons.Default.ChevronRight, null)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    val months = java.time.Month.entries
                    Column {
                        for (i in 0 until 4) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                for (j in 0 until 3) {
                                    val month = months[i * 3 + j]
                                    TextButton(
                                        onClick = {
                                            selectedMonth = YearMonth.of(selectedMonth.year, month)
                                            showMonthPicker = false
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = if (selectedMonth.month == month) 
                                            ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                            else ButtonDefaults.textButtonColors()
                                    ) {
                                        Text(month.name.take(3))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMonthPicker = false }) { Text("Close") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = {
                    Text(
                        text = "Weekly",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                actions = {
                    AnimatedVisibility(
                        visible = !isCurrentMonth,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                    ) {
                        Surface(
                            onClick = { selectedMonth = currentMonth },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Today,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "This Month",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FloatingActionButton(
                    onClick = { showMonthPicker = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = "Pick month",
                    )
                }
                ExtendedFloatingActionButton(
                    onClick = {
                        val currentWeek = currentWeekKey()
                        if (!activeSummarizeWeeks.contains(currentWeek)) {
                            summarizeWeek(currentWeek)
                        }
                    },
                    text = { Text("Summarize") },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = "Summarize week",
                        )
                    },
                    expanded = summarizeFabExpanded,
                    containerColor = if (activeSummarizeWeeks.isNotEmpty()) 
                        MaterialTheme.colorScheme.surfaceVariant 
                    else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (activeSummarizeWeeks.isNotEmpty())
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f) 
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Month Selector Bar
                Surface(
                    tonalElevation = 2.dp,
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { selectedMonth = selectedMonth.minusMonths(1) }
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month")
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { showMonthPicker = true }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (isCurrentMonth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isCurrentMonth) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCurrentMonth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { selectedMonth = selectedMonth.plusMonths(1) }
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next Month")
                        }
                    }
                }

                if (filteredItems.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "No weekly summaries for ${selectedMonth.format(DateTimeFormatter.ofPattern("MMMM"))}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Tap Summarize to generate a review of your weeks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredItems, key = { it.weekKey }) { item ->
                            WeeklySummaryCard(
                                item = item,
                                isBusy = activeSummarizeWeeks.contains(item.weekKey),
                                onOpen = { onOpenSummary(item.fileName) },
                                onSummarize = { summarizeWeek(item.weekKey) }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
            
            if (activeSummarizeWeeks.isNotEmpty()) {
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
    val currentWeek = currentWeekKey()
    val isThisWeek = item.weekKey == currentWeek

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isThisWeek) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
            else MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(
            width = if (isThisWeek) 2.dp else 1.dp,
            color = if (isThisWeek) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatWeekKeyDisplay(item.weekKey),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isThisWeek) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetadataBadge(
                        icon = Icons.AutoMirrored.Filled.Segment,
                        text = "${item.dailySummaryCount} days"
                    )
                    MetadataBadge(
                        icon = Icons.Default.Description,
                        text = "${item.summaryWordCount} words"
                    )
                }

                if (item.summaryLastUpdatedMillis != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Updated ${formatDateTime(item.summaryLastUpdatedMillis)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Surface(
                        onClick = onSummarize,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Re-summarize",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataBadge(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatWeekKeyDisplay(weekKey: String): String {
    val range = weekRangeFromKey(weekKey)
    val formatter = DateTimeFormatter.ofPattern("MMM dd")
    return "${range.first.format(formatter)} - ${range.second.format(formatter)}, ${range.first.year}"
}

private data class WeeklySummaryItem(
    val weekKey: String,
    val dailySummaryCount: Int,
    val summaryWordCount: Int,
    val summaryLastUpdatedMillis: Long?,
    val fileName: String,
)

private fun String.wordCount(): Int {
    if (isBlank()) return 0
    return trim().split(Regex("\\s+")).count { it.isNotBlank() }
}
