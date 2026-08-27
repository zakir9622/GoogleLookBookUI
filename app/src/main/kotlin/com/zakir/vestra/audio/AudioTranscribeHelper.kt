package com.zakir.vestra.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.io.File
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class TranscribeState {
    object Idle : TranscribeState()
    data class Listening(val volumeRms: Float = 0f, val partialText: String = "") : TranscribeState()
    data class Processing(val progressHint: String) : TranscribeState()
    data class Success(
        val text: String,
        val wordCount: Int,
        val durationMs: Long?,
        val audioFile: File?,
    ) : TranscribeState()
    data class Error(val message: String) : TranscribeState()
}

class AudioTranscribeHelper(private val context: Context) {

    private val _state = MutableStateFlow<TranscribeState>(TranscribeState.Idle)
    val state: StateFlow<TranscribeState> = _state.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening() {
        if (!isAvailable()) {
            _state.value = TranscribeState.Error("Speech recognition is not available on this device.")
            return
        }
        stop()

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = TranscribeState.Listening()
            }

            override fun onBeginningOfSpeech() {
                _state.value = TranscribeState.Listening()
            }

            override fun onRmsChanged(rmsdB: Float) {
                val current = _state.value
                if (current is TranscribeState.Listening) {
                    _state.value = current.copy(volumeRms = rmsdB.coerceIn(0f, 10f))
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                val current = _state.value
                val partial = if (current is TranscribeState.Listening) current.partialText else ""
                _state.value = TranscribeState.Processing(if (partial.isNotBlank()) "Finalizing transcript: \"$partial\"…" else "Generating transcript…")
            }

            override fun onError(error: Int) {
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                    SpeechRecognizer.ERROR_NETWORK -> "Network required for speech-to-text"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout during speech recognition"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No spoken words recognized in audio"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error occurred"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                    else -> "Speech recognition error ($error)"
                }
                _state.value = TranscribeState.Error(errorMsg)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: ""
                if (text.isNotBlank()) {
                    val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                    _state.value = TranscribeState.Success(
                        text = text,
                        wordCount = words,
                        durationMs = null,
                        audioFile = null,
                    )
                } else {
                    _state.value = TranscribeState.Error("Could not recognize any spoken words.")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: return
                if (text.isNotBlank()) {
                    val current = _state.value
                    if (current is TranscribeState.Listening) {
                        _state.value = current.copy(partialText = text)
                    } else {
                        _state.value = TranscribeState.Listening(partialText = text)
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer.startListening(intent)
        _state.value = TranscribeState.Listening()
    }

    fun stop() {
        runCatching {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        }
        speechRecognizer = null
    }

    fun setManualTranscription(text: String, audioFile: File? = null, durationMs: Long? = null) {
        val trimmed = text.trim()
        val words = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        _state.value = TranscribeState.Success(
            text = trimmed,
            wordCount = words,
            durationMs = durationMs,
            audioFile = audioFile,
        )
    }

    fun clear() {
        stop()
        _state.value = TranscribeState.Idle
    }
}
