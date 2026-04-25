package com.secondmemory.ui.screen.dailyview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.secondmemory.background.DailySummaryWorker
import com.secondmemory.domain.repository.DailySummaryRepository
import com.secondmemory.domain.repository.OperationLogRepository
import com.secondmemory.domain.repository.SettingsRepository
import com.secondmemory.domain.repository.ThoughtRepository
import com.secondmemory.ui.component.showSnackbarImmediate
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
 * A creative, timeline-based view of daily reflections with activity visualization.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyViewScreen(
    thoughtRepository: ThoughtRepository,
    dailySummaryRepository: DailySummaryRepository,
    settingsRepository: SettingsRepository,
    operationLogRepository: OperationLogRepository,
    snackbarHostState: SnackbarHostState,
    onOpenSummary: (String) -> Unit,
) {
    val context = LocalContext.current
    val workManager = remember { WorkManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    var dayItems by remember { mutableStateOf(emptyList<DaySummaryItem>()) }
    var activeSummarizeDays by remember { mutableStateOf(setOf<String>()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showViewDatePicker by remember { mutableStateOf(false) }
    
    var selectedWeekStart by remember {
        mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
    }
    
    val listState = rememberLazyListState()
    val summarizeFabExpanded by remember {
        derivedStateOf {
            (listState.firstVisibleItemIndex == 0) && (listState.firstVisibleItemScrollOffset < 8)
        }
    }

    val currentWeekStart = remember { LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    val isCurrentWeek = selectedWeekStart == currentWeekStart

    fun refresh() {
        scope.launch {
            val summaries = dailySummaryRepository.listDailySummaries()
            val today = LocalDate.now()
            val weekDays = (0..6).map { selectedWeekStart.plusDays(it.toLong()) }
                .filter { !it.isAfter(today) }
                .map { it.format(DateTimeFormatter.BASIC_ISO_DATE) }
            
            dayItems = weekDays.map { dayKey ->
                val summaryFile = summaries.find { it.dayKey == dayKey }
                val lastSummaryUpdatedMillis = dailySummaryRepository.lastUpdatedMillisForDay(dayKey)
                val lastThoughtUpdatedMillis = thoughtRepository.lastUpdatedMillisForDay(dayKey)
                val thoughts = thoughtRepository.listForDay(dayKey)
                
                DaySummaryItem(
                    dayKey = dayKey,
                    hasSummary = summaryFile != null,
                    thoughtCount = thoughts.size,
                    thoughtTimestamps = thoughts.map { it.timestampMillis },
                    summaryLastUpdatedMillis = lastSummaryUpdatedMillis,
                    lastThoughtUpdatedMillis = lastThoughtUpdatedMillis,
                    fileName = summaryFile?.fileName ?: "",
                )
            }.sortedByDescending { it.dayKey }
        }
    }

    val summarizeDay: (String) -> Unit = { dayKey ->
        scope.launch {
            val settings = settingsRepository.currentSettings()
            if (settings.aiApiKey.isBlank()) {
                snackbarHostState.showSnackbarImmediate("Configure AI settings before summarizing.")
                return@launch
            }

            val rawJson = thoughtRepository.readRawJson(dayKey)
            if (rawJson.isBlank()) {
                snackbarHostState.showSnackbarImmediate("No thoughts recorded for $dayKey.")
                return@launch
            }

            activeSummarizeDays += dayKey

            val workRequest = OneTimeWorkRequestBuilder<DailySummaryWorker>()
                .setInputData(workDataOf(DailySummaryWorker.KEY_DAY_KEY to dayKey))
                .addTag("summary_job")
                .addTag("summary_$dayKey")
                .build()

            operationLogRepository.appendLog(
                category = "SUMMARY",
                action = "Generate summary",
                status = "STARTED",
                details = "dayKey=$dayKey",
                source = "DailyViewScreen",
            )

            workManager.enqueueUniqueWork(
                "summary_$dayKey",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

    // Monitor WorkManager
    LaunchedEffect(Unit) {
        workManager.getWorkInfosByTagFlow("summary_job")
            .collect { infos ->
                val busyKeys = mutableSetOf<String>()
                val seenKeys = mutableSetOf<String>()
                var shouldRefresh = false
                
                infos.forEach { info ->
                    val dayKeyTag = info.tags.firstOrNull { (it.startsWith("summary_") && it != "summary_job") }
                    val dayKey = dayKeyTag?.removePrefix("summary_")
                    
                    if (dayKey != null) {
                        seenKeys.add(dayKey)
                        if (!info.state.isFinished) {
                            busyKeys.add(dayKey)
                        } else if (activeSummarizeDays.contains(dayKey)) {
                            if (info.state == WorkInfo.State.SUCCEEDED) shouldRefresh = true
                        }
                    }
                }
                activeSummarizeDays = busyKeys + (activeSummarizeDays - seenKeys)
                if (shouldRefresh) refresh()
            }
    }

    LaunchedEffect(selectedWeekStart) {
        refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = {
                    Text(
                        text = "Daily",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Medium
                    )
                },
                actions = {
                    AnimatedVisibility(
                        visible = !isCurrentWeek,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                    ) {
                        Surface(
                            onClick = { selectedWeekStart = currentWeekStart },
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
                                    "This Week",
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
                    onClick = { showDatePicker = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = "Pick date")
                }
                ExtendedFloatingActionButton(
                    onClick = { summarizeDay(todayDayKey()) },
                    text = { Text("Summarize") },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    expanded = summarizeFabExpanded,
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Week Selector Bar
                Surface(
                    tonalElevation = 2.dp,
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(4.dp),
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

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(dayItems, key = { it.dayKey }) { item ->
                        TimelineItem(
                            item = item,
                            isBusy = activeSummarizeDays.contains(item.dayKey),
                            onOpen = { if (item.hasSummary) onOpenSummary(item.fileName) },
                            onSummarize = { summarizeDay(item.dayKey) }
                        )
                    }
                }
            }

            if (activeSummarizeDays.isNotEmpty()) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                )
            }
        }
    }

    // Dialogs
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val dayKey = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE)
                        summarizeDay(dayKey)
                    }
                    showDatePicker = false
                }) { Text("Summarize") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    if (showViewDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedWeekStart.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showViewDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        selectedWeekStart = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    }
                    showViewDatePicker = false
                }) { Text("View Week") }
            }
        ) { DatePicker(state = datePickerState) }
    }
}

/**
 * A creative vertical timeline entry for one day.
 */
@Composable
private fun TimelineItem(
    item: DaySummaryItem,
    isBusy: Boolean,
    onOpen: () -> Unit,
    onSummarize: () -> Unit,
) {
    val date = LocalDate.parse(item.dayKey, DateTimeFormatter.BASIC_ISO_DATE)
    val isToday = item.dayKey == todayDayKey()
    val hasThoughts = item.thoughtCount > 0
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val nodeSize = if (isToday) 14.dp else 10.dp
    val bloomMultiplier = 1f + (item.thoughtCount.toFloat() / 20f).coerceAtMost(1f)
    val finalNodeSize = nodeSize * bloomMultiplier

    val baseColor = when {
        isBusy -> MaterialTheme.colorScheme.primary
        item.needsRefresh -> MaterialTheme.colorScheme.error
        item.hasSummary -> MaterialTheme.colorScheme.primary
        hasThoughts -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max)
            .clickable(enabled = item.hasSummary) { onOpen() }
            .padding(horizontal = 0.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Timeline Column
        Box(
            modifier = Modifier.width(48.dp).fillMaxHeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            // Continuous dashed line
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLine(
                    color = baseColor.copy(alpha = 0.2f),
                    start = Offset(size.width / 2, 0f),
                    end = Offset(size.width / 2, size.height),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            }
            
            // Node with Bloom Effect
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .size(finalNodeSize)
                    .scale(if (item.needsRefresh) pulseScale else 1f)
                    .clip(CircleShape)
                    .background(baseColor)
                    .then(
                        if (item.hasSummary && !item.needsRefresh) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        } else Modifier
                    )
            )
        }

        // Content Column
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 20.dp, top = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Journal Stamp Look
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(
                            text = date.format(DateTimeFormatter.ofPattern("EEEE")).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            text = date.format(DateTimeFormatter.ofPattern("MMM dd")),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                if (isBusy) {
                    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                } else if (hasThoughts) {
                    IconButton(
                        onClick = onSummarize,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (item.needsRefresh) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else Color.Transparent)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Summarize",
                            tint = if (item.needsRefresh) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(40.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Activity Distribution Sparkline
            if (hasThoughts) {
                ActivitySparkline(timestamps = item.thoughtTimestamps)
                Spacer(modifier = Modifier.height(8.dp))
            }

            val statusText = item.getStatusText()

            if (statusText != null) {
                if (item.needsRefresh || (hasThoughts && !item.hasSummary)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (item.needsRefresh) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f) 
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, 
                            if (item.needsRefresh) MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val iconColor = if (item.needsRefresh) MaterialTheme.colorScheme.error 
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            
                            Icon(
                                Icons.Default.Refresh, 
                                null, 
                                modifier = Modifier.size(16.dp), 
                                tint = iconColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if (item.needsRefresh) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * A minimalist horizontal sparkline showing the distribution of thoughts over 24 hours.
 */
@Composable
private fun ActivitySparkline(timestamps: List<Long>) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(16.dp)) {
            val width = size.width
            val height = size.height
            
            // Background track
            drawLine(
                color = Color.Gray.copy(alpha = 0.1f),
                start = Offset(0f, height / 2),
                end = Offset(width, height / 2),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round
            )
            
            // Thought Pips - using Local Time for positioning
            timestamps.forEach { ts ->
                val localDateTime = Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
                val totalMinutes = localDateTime.hour * 60 + localDateTime.minute
                val dayFraction = totalMinutes / 1440f // 1440 minutes in a day
                val x = dayFraction * width
                
                // Outer glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Cyan.copy(alpha = 0.4f), Color.Transparent),
                        center = Offset(x, height / 2),
                        radius = 10.dp.toPx()
                    ),
                    radius = 10.dp.toPx(),
                    center = Offset(x, height / 2)
                )
                
                // Inner core
                drawCircle(
                    color = Color.Cyan,
                    radius = 3.dp.toPx(),
                    center = Offset(x, height / 2)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().alpha(0.4f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("00:00", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp)
            Text("12:00", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp)
            Text("23:59", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp)
        }
    }
}

private data class DaySummaryItem(
    val dayKey: String,
    val hasSummary: Boolean,
    val thoughtCount: Int,
    val thoughtTimestamps: List<Long>,
    val summaryLastUpdatedMillis: Long?,
    val lastThoughtUpdatedMillis: Long?,
    val fileName: String,
) {
    val needsRefresh: Boolean
        get() = hasSummary && 
                summaryLastUpdatedMillis != null &&
                lastThoughtUpdatedMillis != null &&
                lastThoughtUpdatedMillis > (summaryLastUpdatedMillis + 1000)

    /**
     * Returns a user-friendly status message based on the current state of the day's activity.
     */
    fun getStatusText(): String? {
        if (needsRefresh) {
            val newCount = if (summaryLastUpdatedMillis != null) {
                thoughtTimestamps.count { it > (summaryLastUpdatedMillis + 1000) }
            } else 0
            return if (newCount > 0) {
                "$newCount new captures since last synthesis"
            } else {
                "Synthesis out of date"
            }
        }
        
        if (!hasSummary) {
            return if (thoughtCount > 0) {
                "$thoughtCount captures awaiting synthesis"
            } else {
                "No activity recorded"
            }
        }

        return null // Synchronized - hide label
    }
}

private fun formatWeekRange(start: LocalDate): String {
    val end = start.plusDays(6)
    return "${start.format(DateTimeFormatter.ofPattern("MMM dd"))} - ${end.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))}"
}
