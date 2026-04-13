package com.secondmemory.ui.screen.dailyview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import com.secondmemory.domain.repository.DailySummaryRepository
import kotlinx.coroutines.launch

/**
 * Screen that displays full markdown/plain content for a selected daily summary file.
 */
@Composable
fun DailySummaryDetailScreen(
    fileName: String,
    dailySummaryRepository: DailySummaryRepository,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var content by remember(fileName) { mutableStateOf("") }

    LaunchedEffect(fileName) {
        scope.launch {
            content = dailySummaryRepository.readSummary(fileName)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = fileName, style = MaterialTheme.typography.titleMedium)

        Text(
            text = if (content.isBlank()) "(Empty summary file)" else content,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            style = MaterialTheme.typography.bodyMedium,
        )

        Button(onClick = onBack) {
            Text("Back")
        }
    }
}
