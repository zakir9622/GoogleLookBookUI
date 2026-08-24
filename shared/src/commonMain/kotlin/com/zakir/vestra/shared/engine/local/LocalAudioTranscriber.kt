package com.zakir.vestra.shared.engine.local

/**
 * Offline speech-to-text via LiteRT-LM audio models (Gallery Audio Scribe class).
 */
interface LocalAudioTranscriber {
    fun isReady(): Boolean
    fun transcribe(audioPath: String, prompt: String = DEFAULT_PROMPT): LocalTranscribeResult

    companion object {
        const val DEFAULT_PROMPT = "Transcribe this audio accurately."
    }
}

sealed class LocalTranscribeResult {
    data class Ok(val text: String) : LocalTranscribeResult()
    data class Unavailable(val reason: String) : LocalTranscribeResult()
}

object UnimplementedLocalAudioTranscriber : LocalAudioTranscriber {
    override fun isReady(): Boolean = false
    override fun transcribe(audioPath: String, prompt: String): LocalTranscribeResult =
        LocalTranscribeResult.Unavailable("Local transcription not wired on this platform.")
}
