package com.secondmemory.ui.screen.rawthoughts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.secondmemory.domain.model.Thought
import com.secondmemory.domain.repository.ThoughtRepository
import com.secondmemory.util.formatTime
import com.secondmemory.util.todayDayKey
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp

@Composable
fun RawThoughtsScreen(
    thoughtRepository: ThoughtRepository,
    onRecordThought: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val dayKey = remember { todayDayKey() }
    var thoughts by remember { mutableStateOf(emptyList<Thought>()) }
    var editingThought by remember { mutableStateOf<Thought?>(null) }
    var editingText by remember { mutableStateOf("") }

    fun refreshThoughts() {
        scope.launch {
            thoughts = thoughtRepository.listForDay(dayKey)
                .sortedByDescending { it.timestampMillis }
        }
    }

    LaunchedEffect(Unit) {
        refreshThoughts()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .padding(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Raw Thoughts",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "${thoughts.size} thought(s) captured today.",
                style = MaterialTheme.typography.bodyMedium,
            )

            if (thoughts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "No thoughts yet. Use the Record FAB to add your first entry.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items = thoughts, key = { it.id }) { thought ->
                        ThoughtItem(
                            thought = thought,
                            onEdit = {
                                editingThought = thought
                                editingText = thought.text
                            },
                            onDelete = {
                                scope.launch {
                                    thoughtRepository.deleteThought(dayKey, thought.id)
                                    refreshThoughts()
                                }
                            },
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            onClick = onRecordThought,
        ) {
            Text("Record")
        }
    }

    if (editingThought != null) {
        AlertDialog(
            onDismissRequest = {
                editingThought = null
                editingText = ""
            },
            title = { Text("Edit Thought") },
            text = {
                OutlinedTextField(
                    value = editingText,
                    onValueChange = { editingText = it },
                    label = { Text("Thought") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = editingText.isNotBlank(),
                    onClick = {
                        val currentThought = editingThought ?: return@TextButton
                        scope.launch {
                            thoughtRepository.saveThought(
                                dayKey = dayKey,
                                thought = currentThought.copy(text = editingText.trim()),
                            )
                            editingThought = null
                            editingText = ""
                            refreshThoughts()
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        editingThought = null
                        editingText = ""
                    }
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ThoughtItem(
    thought: Thought,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = thought.text,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "${formatTime(thought.timestampMillis)} • ${thought.source.name.lowercase()}",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) {
                    Text("Edit")
                }
                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
            }
        }
    }
}
