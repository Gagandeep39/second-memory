package com.secondmemory.ui.screen.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.secondmemory.domain.model.Thought
import com.secondmemory.domain.model.ThoughtSource
import com.secondmemory.domain.repository.ThoughtRepository
import com.secondmemory.util.todayDayKey
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import java.util.UUID

@Composable
fun RecordThoughtScreen(
    thoughtRepository: ThoughtRepository,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var draftText by remember { mutableStateOf("") }

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
            text = "Type or paste your thought. Speech transcription will be connected next.",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = draftText,
            onValueChange = { draftText = it },
            modifier = Modifier.fillMaxSize().weight(1f),
            label = { Text("Thought") },
        )

        Button(
            enabled = draftText.isNotBlank(),
            onClick = {
                scope.launch {
                    thoughtRepository.saveThought(
                        dayKey = todayDayKey(),
                        thought = Thought(
                            id = UUID.randomUUID().toString(),
                            timestampMillis = System.currentTimeMillis(),
                            text = draftText.trim(),
                            source = ThoughtSource.MANUAL,
                        ),
                    )
                    onBack()
                }
            }
        ) {
            Text("Save Thought")
        }

        Button(onClick = onBack) {
            Text("Back")
        }
    }
}
