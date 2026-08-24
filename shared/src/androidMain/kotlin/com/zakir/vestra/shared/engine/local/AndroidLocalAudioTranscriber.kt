package com.zakir.vestra.shared.engine.local

import android.content.Context
import com.zakir.vestra.shared.packs.ModelPackManager
import java.io.File

/**
 * Offline speech-to-text via Gemma 4 multimodal audio (Gallery Audio Scribe class).
 * Shares [LiteRtLmPacks.GEMMA4_CODE] weights — no separate download when Code pack is installed.
 */
class AndroidLocalAudioTranscriber(
    private val context: Context,
    private val packs: ModelPackManager,
    private val useGpu: () -> Boolean = { false },
) : LocalAudioTranscriber {

    override fun isReady(): Boolean = resolveModel() != null

    override fun transcribe(audioPath: String, prompt: String): LocalTranscribeResult {
        val resolved = resolveModel()
            ?: return LocalTranscribeResult.Unavailable(
                "Download ${LiteRtLmPacks.GEMMA4_CODE} from Model packs for offline transcription.",
            )
        @Suppress("UNCHECKED_CAST")
        return LiteRtLmInference.runTranscribe(
            context = context,
            packs = packs,
            packId = resolved.packId,
            modelPath = resolved.modelPath,
            useGpu = useGpu(),
            audioEnabled = resolved.config.audio,
            audioPath = audioPath,
            prompt = prompt,
            mapOk = { LocalTranscribeResult.Ok(it.text) },
            mapUnavailable = { LocalTranscribeResult.Unavailable(it) },
        ) as LocalTranscribeResult
    }

    private fun resolveModel(): LiteRtLmPackResolver.ResolvedPack? {
        val resolved = LiteRtLmPackResolver.resolveWithConfig(
            packs,
            LiteRtLmPacks.GEMMA4_CODE,
            LiteRtLmPacks.GEMMA4_FILE,
        ) ?: return null
        val file = File(resolved.modelPath)
        if (file.length() < LiteRtLmPackLimits.MIN_GEMMA4_BYTES) return null
        if (!resolved.config.audio) return null
        return resolved
    }
}
