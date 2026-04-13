package com.secondmemory.ui.screen.dailyview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DailyViewScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(
            text = "Daily View",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Date-based files and markdown previews will be added next.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
