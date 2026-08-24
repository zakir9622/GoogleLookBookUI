package com.zakir.vestra.shared.engine.local

import android.content.Context
import com.zakir.vestra.shared.audio.AndroidSystemTts
import com.zakir.vestra.shared.audio.VoiceKnobs
import com.zakir.vestra.shared.audio.VoicePersona
import com.zakir.vestra.shared.packs.ModelPackManager
import java.io.File

/**
 * On-device Audio Studio generator.
 *
 * **Works offline today:** Android system TTS (Google / OEM voices) → optional DSP knobs.
 * Neural `local-tts-v1` pack remains a future upgrade path ([LocalAudioFlags.TTS_RUNNER_WIRED]).
 */
class AndroidLocalAudioGenerator(
    context: Context,
    private val packs: ModelPackManager,
    private val outputDir: File,
    private val packId: String = NEURAL_PACK_ID,
    private val voiceChanger: LocalVoiceChanger = AndroidLocalVoiceChanger(outputDir),
) : LocalAudioGenerator {

    private val systemTts = AndroidSystemTts(context, outputDir)

    override fun isReady(): Boolean = systemTts.isReady()

    override fun generate(
        text: String,
        persona: VoicePersona,
        knobs: VoiceKnobs,
        seed: Long?,
    ): LocalAudioResult {
        // Prefer system TTS (true offline). Neural pack path reserved for later.
        if (LocalAudioFlags.TTS_RUNNER_WIRED && packs.isReady(packId)) {
            // Future: neural ONNX TTS. Fall through to system until productized.
        }
        val spoken = systemTts.speakToFile(text, persona)
        if (spoken !is LocalAudioResult.Ok) return spoken
        val safeKnobs = knobs.sanitized()
        if (safeKnobs.isIdentity || !voiceChanger.isReady()) return spoken
        return when (val changed = voiceChanger.transform(spoken.audioPath, safeKnobs)) {
            is LocalAudioResult.Ok -> changed
            is LocalAudioResult.Unavailable -> spoken // keep raw TTS if DSP fails
        }
    }

    fun shutdown() {
        systemTts.shutdown()
    }

    companion object {
        const val NEURAL_PACK_ID = "local-tts-v1"
    }
}
