package com.secondmemory.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondmemory.ui.component.MarkdownText

/**
 * Screen that displays full markdown/plain content for a selected summary file.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryDetailScreen(
    fileName: String,
    loadContent: suspend (String) -> String,
    onBack: () -> Unit,
) {
    var content by remember(fileName) { mutableStateOf("") }

    LaunchedEffect(fileName) {
        content = loadContent(fileName)
    }

    Scaffold (
        topBar = {
            TopAppBar(title = {
                Text(fileName)
            },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        ) {
            // Content (scrollable)
            if (content.isBlank()) {
                Text(
                    text = "(Empty summary file)",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                MarkdownText(
                    markdown = content,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                )
            }
        }
    }
}
