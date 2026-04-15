package com.secondmemory.ui.screen.dailyview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.secondmemory.domain.llm.LlmSummaryClient
import com.secondmemory.domain.repository.DailySummaryRepository
import com.secondmemory.domain.repository.OperationLogRepository
import com.secondmemory.domain.repository.SettingsRepository
import com.secondmemory.domain.repository.ThoughtRepository
import com.secondmemory.ui.component.AppSnackbar
import com.secondmemory.util.dayKeyDisplayText
import com.secondmemory.util.formatDateTime
import com.secondmemory.util.todayDayKey
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Screen that lists daily summary markdown files and can generate/open summaries.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyViewScreen(
    thoughtRepository: ThoughtRepository,
    dailySummaryRepository: DailySummaryRepository,
    settingsRepository: SettingsRepository,
    operationLogRepository: OperationLogRepository,
    llmSummaryClient: LlmSummaryClient,
    snackbarHostState: SnackbarHostState,
    onOpenSummary: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var dayItems by remember { mutableStateOf(emptyList<DaySummaryItem>()) }
    var activeSummarizeDay by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showViewDatePicker by remember { mutableStateOf(false) }
    var datePickerSeedMillis by remember { mutableStateOf(todayUtcStartOfDayMillis()) }
    var selectedWeekStart by remember {
        mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
    }

    val listState = rememberLazyListState()
    val summarizeFabExpanded by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 8
        }
    }

    val currentWeekStart = remember { LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    val isCurrentWeek = selectedWeekStart == currentWeekStart

    val filteredItems = remember(dayItems, selectedWeekStart) {
        val weekEnd = selectedWeekStart.plusDays(6)
        dayItems.filter { item ->
            val date = try {
                LocalDate.parse(item.dayKey, DateTimeFormatter.BASIC_ISO_DATE)
            } catch (e: Exception) {
                null
            }
            date != null && !date.isBefore(selectedWeekStart) && !date.isAfter(weekEnd)
        }.sortedByDescending { it.dayKey }
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
            snackbarHostState.showSnackbar("Add Gemini API key in Settings before summarizing.")
            return@summarizeDay
        }
        if (!settings.cloudSummaryEnabled) {
            snackbarHostState.showSnackbar("Enable Cloud Summaries in Settings to summarize.")
            return@summarizeDay
        }

        val rawJson = thoughtRepository.readRawJson(dayKey)
        if (rawJson.isBlank()) {
            snackbarHostState.showSnackbar("Raw JSON for $dayKey is empty or missing.")
            return@summarizeDay
        }

        activeSummarizeDay = dayKey
        
        operationLogRepository.appendLog(
            category = "SUMMARY",
            action = "Generate summary",
            status = "STARTED",
            details = "dayKey=$dayKey",
            source = "DailyViewScreen"
        )

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
                operationLogRepository.appendLog(
                    category = "SUMMARY",
                    action = "Generate summary",
                    status = "SUCCESS",
                    details = "dayKey=$dayKey",
                    source = "DailyViewScreen"
                )
                snackbarHostState.showSnackbar("Summary generated for $dayKey.")
                refresh()
            }.onFailure { error ->
                val errorMsg = error.message ?: "unknown error"
                operationLogRepository.appendLog(
                    category = "SUMMARY",
                    action = "Generate summary",
                    status = "ERROR",
                    details = "Failed to save: $errorMsg",
                    source = "DailyViewScreen"
                )
                snackbarHostState.showSnackbar("Failed to save summary: $errorMsg")
            }
        }.onFailure { error ->
            val errorMsg = error.message ?: "unknown error"
            operationLogRepository.appendLog(
                category = "SUMMARY",
                action = "Generate summary",
                status = "ERROR",
                details = errorMsg,
                source = "DailyViewScreen"
            )
            snackbarHostState.showSnackbar("Summary failed: $errorMsg")
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

    if (showViewDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedWeekStart.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showViewDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        selectedWeekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    }
                    showViewDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showViewDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .height(48.dp)
            ) {
                Text(
                    text = "Daily",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.CenterStart)
                )

                androidx.compose.animation.AnimatedVisibility(
                    visible = !isCurrentWeek,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Surface(
                        onClick = { selectedWeekStart = currentWeekStart },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
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
                                "This Week",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Week Selector Bar
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
                        onClick = { selectedWeekStart = selectedWeekStart.minusWeeks(1) }
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Week")
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { showViewDatePicker = true }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (isCurrentWeek) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatWeekRange(selectedWeekStart),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isCurrentWeek) FontWeight.Bold else FontWeight.Medium,
                            color = if (isCurrentWeek) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { selectedWeekStart = selectedWeekStart.plusWeeks(1) }
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next Week")
                    }
                }
            }

            if (filteredItems.isNotEmpty()) {
                Text(
                    text = "${filteredItems.size} summaries",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No summaries for this week",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Tap the + icon or Summarize to generate one",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filteredItems, key = { item -> item.dayKey }) { item ->
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
                    item { Spacer(modifier = Modifier.height(80.dp)) }
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
    val isToday = item.dayKey == todayDayKey()

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
            else MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(
            width = if (isToday) 2.dp else 1.dp,
            color = if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dayKeyDisplayText(item.dayKey),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetadataBadge(
                        icon = Icons.AutoMirrored.Filled.Message,
                        text = "${item.thoughtCount} thoughts"
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

            // Action area
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
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

/**
 * Small badge for metadata display within cards.
 */
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
 * Formats a week range for user-facing display labels.
 */
private fun formatWeekRange(start: LocalDate): String {
    val end = start.plusDays(6)
    val monthDayFormatter = DateTimeFormatter.ofPattern("MMM dd")

    return if (start.year == end.year) {
        "${start.format(monthDayFormatter)} - ${end.format(monthDayFormatter)}, ${start.year}"
    } else {
        val yearFormatter = DateTimeFormatter.ofPattern("yyyy")
        "${start.format(monthDayFormatter)}, ${start.format(yearFormatter)} - ${end.format(monthDayFormatter)}, ${end.format(yearFormatter)}"
    }
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