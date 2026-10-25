package com.secondmemory.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * An update emitted while a speech-recognition session is preparing or running.
 */
sealed interface SpeechRecognitionUpdate {
    /** Reports a user-facing preparation or completion status. */
    data class Status(val message: String) : SpeechRecognitionUpdate

    /** Reports a partial or final transcript from the active session. */
    data class Transcript(
        val text: String,
        val isFinal: Boolean,
    ) : SpeechRecognitionUpdate
}

/**
 * Provides speech recognition without exposing the concrete recognition SDK to the UI.
 */
interface SpeechRecognitionRepository {
    /** Starts a session and emits status and transcript updates until it completes or is cancelled. */
    fun recognize(): Flow<SpeechRecognitionUpdate>

    /** Requests the active recognition session to stop. */
    suspend fun stop()

    /** Releases the underlying recognition client. */
    fun close()
}
