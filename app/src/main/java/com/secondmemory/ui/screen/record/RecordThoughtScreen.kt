package com.secondmemory.ui.screen.record

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.content.ContextCompat
import android.widget.Toast
import com.secondmemory.domain.model.Thought
import com.secondmemory.domain.model.ThoughtSource
import com.secondmemory.domain.repository.ThoughtRepository
import com.secondmemory.ui.component.AudioLevelVisualizer
import com.secondmemory.util.todayDayKey
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.statusBarsPadding
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
                    isListening = false
                    rmsLevel = 0f
                    speechStatus = "Speech capture failed (code $error). Try again."
                }

                override fun onResults(results: Bundle?) {
                    val topMatch = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!topMatch.isNullOrBlank()) {
                        draftTextFieldValue = appendTranscript(listeningBaseValue, topMatch)
                        hasSpeechInput = true
                        speechStatus = "Transcription complete. You can edit before saving."
                    } else {
                        speechStatus = "No speech detected."
                    }
                    isListening = false
                    rmsLevel = 0f
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Record Thought",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = speechStatus,
            style = MaterialTheme.typography.bodyMedium,
        )

        AudioLevelVisualizer(
            normalizedLevel = rmsLevel,
            isListening = isListening,
        )

        Button(
            onClick = {
                if (isListening) {
                    speechRecognizer?.stopListening()
                    isListening = false
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
        ) {
            Text(if (isListening) "Stop Listening" else "Start Listening")
        }

        OutlinedTextField(
            value = draftTextFieldValue,
            onValueChange = { draftTextFieldValue = it },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            label = { Text("Thought") },
        )

        Button(
            enabled = draftTextFieldValue.text.isNotBlank(),
            onClick = {
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
        ) {
            Text("Save Thought")
        }

        Button(onClick = onBack) {
            Text("Back")
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
