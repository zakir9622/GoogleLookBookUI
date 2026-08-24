package com.zakir.vestra.shared.engine.local

import com.zakir.vestra.shared.audio.VoiceKnobs
import com.zakir.vestra.shared.audio.VoicePersona

/**
 * On-device TTS / voice (Audio Studio).
 * Android implements this with **system TTS** (offline today) + optional DSP knobs.
 * Neural `local-tts-v1` remains optional when [LocalAudioFlags.TTS_RUNNER_WIRED].
 */
interface LocalAudioGenerator {
    fun isReady(): Boolean
    fun generate(
        text: String,
        persona: VoicePersona,
        knobs: VoiceKnobs,
        seed: Long? = null,
    ): LocalAudioResult
}

sealed class LocalAudioResult {
    data class Ok(val audioPath: String) : LocalAudioResult()
    data class Unavailable(val reason: String) : LocalAudioResult()
}

object UnimplementedLocalAudioGenerator : LocalAudioGenerator {
    override fun isReady(): Boolean = false
    override fun generate(
        text: String,
        persona: VoicePersona,
        knobs: VoiceKnobs,
        seed: Long?,
    ): LocalAudioResult = LocalAudioResult.Unavailable(
        "On-device TTS pack not wired — use cloud Audio Studio, or install local-tts-v1 when published.",
    )
}

/**
 * Offline voice changer — applies [VoiceKnobs] to an existing clip.
 * Basic DSP can run without a neural pack; neural VC flips [NEURAL_WIRED].
 */
interface LocalVoiceChanger {
    fun isReady(): Boolean
    fun transform(inputPath: String, knobs: VoiceKnobs): LocalAudioResult
}

object UnimplementedLocalVoiceChanger : LocalVoiceChanger {
    override fun isReady(): Boolean = false
    override fun transform(inputPath: String, knobs: VoiceKnobs): LocalAudioResult =
        LocalAudioResult.Unavailable("Local voice changer not available on this platform.")
}

/** Neural ONNX TTS pack (Kokoro/Piper) — system TTS is used until this is productized. */
object LocalAudioFlags {
    const val TTS_RUNNER_WIRED: Boolean = false
    const val NEURAL_VC_WIRED: Boolean = false
}
