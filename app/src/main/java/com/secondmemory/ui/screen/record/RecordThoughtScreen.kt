package com.secondmemory.ui.screen.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RecordThoughtScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Record Thought",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Speech capture and editable transcript will be implemented in this screen.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onBack) {
            Text("Back")
        }
    }
}
