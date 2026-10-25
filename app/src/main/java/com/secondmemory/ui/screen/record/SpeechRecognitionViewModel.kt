package com.secondmemory.ui.screen.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.secondmemory.data.repository.GenAiSpeechRecognitionRepository
import com.secondmemory.domain.repository.SpeechRecognitionRepository
import com.secondmemory.domain.repository.SpeechRecognitionUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Coordinates speech-recognition sessions and exposes stable state to the recording screen.
 */
class SpeechRecognitionViewModel(
    private val repository: SpeechRecognitionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SpeechRecognitionUiState())
    /** Current speech-recognition state for the recording screen. */
    val uiState: StateFlow<SpeechRecognitionUiState> = _uiState.asStateFlow()

    private var recognitionJob: Job? = null
    private var stoppingJob: Job? = null

    /** Starts recognition unless a session is already active or stopping. */
    fun start() {
        if (recognitionJob?.isActive == true || stoppingJob?.isActive == true) return

        _uiState.update {
            it.copy(
                isListening = true,
                status = "Preparing speech recognition...",
                transcript = "",
                isFinalTranscript = false,
            )
        }
        recognitionJob = viewModelScope.launch {
            try {
                repository.recognize().collect { update ->
                    when (update) {
                        is SpeechRecognitionUpdate.Status ->
                            _uiState.update { it.copy(status = update.message) }
                        is SpeechRecognitionUpdate.Transcript ->
                            _uiState.update {
                                it.copy(
                                    transcript = update.text,
                                    isFinalTranscript = update.isFinal,
                                )
                            }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(status = "Speech recognition failed. Try again.")
                }
            } finally {
                _uiState.update { it.copy(isListening = false) }
                recognitionJob = null
            }
        }
    }

    /** Stops recognition and waits for the active collection to finish before returning. */
    fun stop() {
        if (stoppingJob?.isActive == true) return
        val activeJob = recognitionJob
        stoppingJob = viewModelScope.launch {
            activeJob?.cancelAndJoin()
            repository.stop()
            _uiState.update {
                it.copy(isListening = false, status = "Stopped listening.")
            }
            stoppingJob = null
        }
    }

    /** Updates the state when microphone access is declined by the user. */
    fun onPermissionDenied() {
        _uiState.update {
            it.copy(status = "Microphone permission denied. You can still type manually.")
        }
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }
}

/**
 * Creates the production speech-recognition ViewModel for the recording activity.
 */
class SpeechRecognitionViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SpeechRecognitionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SpeechRecognitionViewModel(GenAiSpeechRecognitionRepository()) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

/**
 * UI state emitted by [SpeechRecognitionViewModel].
 */
data class SpeechRecognitionUiState(
    val isListening: Boolean = false,
    val status: String = "Listening will start automatically.",
    val transcript: String = "",
    val isFinalTranscript: Boolean = false,
)
