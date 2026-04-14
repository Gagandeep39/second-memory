package com.secondmemory.ui.screen.record

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.secondmemory.domain.model.Thought
import com.secondmemory.domain.model.ThoughtSource
import com.secondmemory.domain.repository.ThoughtRepository
import com.secondmemory.ui.component.AudioLevelVisualizer
import com.secondmemory.util.todayDayKey
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Screen used to capture a thought by typing or speech transcription and persist it for today.
 */
@Composable
fun RecordThoughtScreen(
    thoughtRepository: ThoughtRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draftTextFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var isListening by remember { mutableStateOf(false) }
    var hasSpeechInput by remember { mutableStateOf(false) }
    var rmsLevel by remember { mutableStateOf(0f) }
    var speechStatus by remember { mutableStateOf("Listening will start automatically.") }
    var listeningBaseValue by remember { mutableStateOf(TextFieldValue("")) }
    var showHint by remember { mutableStateOf(true) }
    var isUserRequestedStop by remember { mutableStateOf(false) }
    // Hide hint after 3.5 seconds
    LaunchedEffect(Unit) {
        showHint = true
        kotlinx.coroutines.delay(3500)
        showHint = false
    }

    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    fun beginListening() {
        if (speechRecognizer == null) {
            speechStatus = "Speech recognition is not available on this device."
            return
        }
        listeningBaseValue = draftTextFieldValue
        speechStatus = "Listening..."
        isListening = true
        speechRecognizer.startListening(speechIntent)
    }

    val requestAudioPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            beginListening()
        } else {
            speechStatus = "Microphone permission denied. You can still type manually."
        }
    }

    DisposableEffect(speechRecognizer) {
        if (speechRecognizer != null) {
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    speechStatus = "Listening..."
                }

                override fun onBeginningOfSpeech() {
                    speechStatus = "Capturing speech..."
                }

                override fun onRmsChanged(rmsdB: Float) {
                    rmsLevel = normalizeRmsLevel(rmsdB)
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    isListening = false
                    rmsLevel = 0f
                    speechStatus = "Processing transcription..."
                }

                override fun onError(error: Int) {
                    rmsLevel = 0f

                    // 1. If the user explicitly clicked stop, ignore the error and exit gracefully.
                    if (isUserRequestedStop) {
                        isListening = false
                        speechStatus = "Stopped by user."
                        return
                    }

                    // 2. If it's a silence timeout, restart the listener to keep it alive.
                    if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH) {
                        beginListening()
                    } else {
                        // 3. Handle actual failures (network issues, permissions, etc.)
                        isListening = false
                        speechStatus = "Speech capture failed (code $error). Try again."
                    }
                }

                override fun onResults(results: Bundle?) {
                    // Process final results here if needed

                    // If the user hasn't clicked stop, keep the loop going
                    if (!isUserRequestedStop) {
                        beginListening()
                    } else {
                        isListening = false
                        speechStatus = "Done."
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!partial.isNullOrBlank()) {
                        draftTextFieldValue = appendTranscript(listeningBaseValue, partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }

        onDispose {
            speechRecognizer?.destroy()
        }
    }

    LaunchedEffect(Unit) {
        val isGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (isGranted) {
            beginListening()
        } else {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                // Title at the top
                Text(
                    text = "Record Thought",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 24.dp, start = 24.dp, end = 24.dp, bottom = 8.dp)
                )
                // Top: Text field occupies upper half
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    OutlinedTextField(
                        value = draftTextFieldValue,
                        onValueChange = { draftTextFieldValue = it },
                        modifier = Modifier
                            .fillMaxSize(),
                        label = { Text("What's on your mind?") },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        singleLine = false,
                        maxLines = 16,
                    )
                }

                // Elegant horizontal button row below the textbox
                val canSave = draftTextFieldValue.text.isNotBlank()
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
                ) {
                    androidx.compose.material3.Button(
                        onClick = onBack,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(20.dp)
                        )
                        androidx.compose.material3.Text(
                            text = "Back",
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    androidx.compose.material3.Button(
                        onClick = {
                            if (canSave) {
                                scope.launch {
                                    thoughtRepository.saveThought(
                                        dayKey = todayDayKey(),
                                        thought = Thought(
                                            id = UUID.randomUUID().toString(),
                                            timestampMillis = System.currentTimeMillis(),
                                            text = draftTextFieldValue.text.trim(),
                                            source = if (hasSpeechInput) ThoughtSource.SPEECH else ThoughtSource.MANUAL,
                                        ),
                                    )
                                    Toast.makeText(context, "Thought saved", Toast.LENGTH_SHORT).show()
                                    onBack()
                                }
                            }
                        },
                        enabled = canSave,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = if (canSave) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (canSave) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save Thought",
                            modifier = Modifier.size(20.dp)
                        )
                        androidx.compose.material3.Text(
                            text = "Save",
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                // Bottom: Visualizer occupies lower half
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = speechStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        AudioLevelVisualizer(
                            normalizedLevel = rmsLevel,
                            isListening = isListening,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                ) {
                                    if (isListening) {
                                        speechRecognizer?.stopListening()
                                        isListening = false
                                        isUserRequestedStop = true
                                        rmsLevel = 0f
                                        speechStatus = "Stopped listening."
                                    } else {
                                        val isGranted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO,
                                        ) == PackageManager.PERMISSION_GRANTED

                                        if (isGranted) {
                                            beginListening()
                                        } else {
                                            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                }
                        )
                    }
                }
            }

            // Subtle hint at the bottom
            AnimatedVisibility(
                visible = showHint,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = "Tap the visualizer to toggle listening",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

/**
 * Maps platform RMS dB values to a stable 0..1 range for UI animation.
 */
private fun normalizeRmsLevel(rmsdB: Float): Float {
    return ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
}

/**
 * Inserts transcript at the current cursor or selection and moves cursor to inserted end.
 */
private fun appendTranscript(
    baseValue: TextFieldValue,
    transcript: String,
): TextFieldValue {
    val cleanTranscript = transcript.trim()
    if (cleanTranscript.isBlank()) return baseValue

    val text = baseValue.text
    val start = baseValue.selection.min.coerceIn(0, text.length)
    val end = baseValue.selection.max.coerceIn(start, text.length)

    val prefix = text.substring(0, start)
    val suffix = text.substring(end)

    val needsLeadingSpace = prefix.isNotBlank() && !prefix.last().isWhitespace()
    val needsTrailingSpace = suffix.isNotBlank() && !suffix.first().isWhitespace()

    val inserted = buildString {
        if (needsLeadingSpace) append(' ')
        append(cleanTranscript)
        if (needsTrailingSpace) append(' ')
    }

    val newText = prefix + inserted + suffix
    val cursor = (prefix.length + inserted.length).coerceAtMost(newText.length)
    return TextFieldValue(text = newText, selection = TextRange(cursor))
}
