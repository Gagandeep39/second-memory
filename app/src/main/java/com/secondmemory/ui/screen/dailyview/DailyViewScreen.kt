package com.secondmemory.ui.screen.dailyview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.secondmemory.domain.model.DailySummaryFile
import com.secondmemory.domain.repository.DailySummaryRepository
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp

/**
 * Screen that lists daily summary markdown files from local storage.
 */
@Composable
fun DailyViewScreen(
    dailySummaryRepository: DailySummaryRepository,
    onOpenSummary: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var summaries by remember { mutableStateOf(emptyList<DailySummaryFile>()) }

    fun refresh() {
        scope.launch {
            summaries = dailySummaryRepository.listDailySummaries()
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

        if (summaries.isEmpty()) {
            Text(
                text = "No daily summaries yet. Generated markdown files will appear here.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(summaries, key = { item -> item.fileName }) { summary ->
                    DailySummaryItem(
                        summary = summary,
                        onOpen = { onOpenSummary(summary.fileName) },
                    )
                }
            }
        }
    }
}

/**
 * Row card representing one summary file and preview snippet.
 */
@Composable
private fun DailySummaryItem(
    summary: DailySummaryFile,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = summary.dayKey, style = MaterialTheme.typography.titleMedium)
            Text(text = summary.preview, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
