package com.secondmemory.data.repository

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.audio.AudioSource
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse
import com.secondmemory.domain.repository.SpeechRecognitionRepository
import com.secondmemory.domain.repository.SpeechRecognitionUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Locale

/**
 * Speech-recognition repository backed by Google ML Kit GenAI Speech Recognition.
 */
class GenAiSpeechRecognitionRepository : SpeechRecognitionRepository {
    private val speechRecognizer = SpeechRecognition.getClient(
        SpeechRecognizerOptions.Builder().apply {
            locale = Locale.US
            preferredMode = SpeechRecognizerOptions.Mode.MODE_BASIC
        }.build(),
    )

    override fun recognize(): Flow<SpeechRecognitionUpdate> = flow {
        when (speechRecognizer.checkStatus()) {
            FeatureStatus.DOWNLOADABLE -> {
                speechRecognizer.download().collect { status ->
                    when (status) {
                        is DownloadStatus.DownloadProgress ->
                            emit(SpeechRecognitionUpdate.Status("Downloading speech model..."))
                        is DownloadStatus.DownloadCompleted ->
                            emit(SpeechRecognitionUpdate.Status("Listening..."))
                        is DownloadStatus.DownloadFailed ->
                            throw IllegalStateException("Speech model download failed")
                        else -> Unit
                    }
                }
            }
            FeatureStatus.AVAILABLE -> Unit
            else -> throw IllegalStateException("Speech recognition is unavailable on this device")
        }

        emit(SpeechRecognitionUpdate.Status("Listening..."))
        val request = com.google.mlkit.genai.speechrecognition.SpeechRecognizerRequest.Builder().apply {
            audioSource = AudioSource.fromMic()
        }.build()

        speechRecognizer.startRecognition(request).collect { response ->
            when (response) {
                is SpeechRecognizerResponse.PartialTextResponse ->
                    emit(SpeechRecognitionUpdate.Transcript(response.text, isFinal = false))
                is SpeechRecognizerResponse.FinalTextResponse ->
                    emit(SpeechRecognitionUpdate.Transcript(response.text, isFinal = true))
                is SpeechRecognizerResponse.ErrorResponse -> throw response.e
                is SpeechRecognizerResponse.CompletedResponse ->
                    emit(SpeechRecognitionUpdate.Status("Done."))
            }
        }
    }

    override suspend fun stop() {
        speechRecognizer.stopRecognition()
    }

    override fun close() {
        speechRecognizer.close()
    }
}
