package com.zakir.vestra.shared.engine.local

import android.content.Context
import com.zakir.vestra.shared.packs.ModelPackManager
import java.io.File

/**
 * Multimodal Gemma 4 vision assist — describe garment / reference photos offline.
 * Uses [LiteRtLmPacks.GEMMA4_CODE] pack with vision backend from config.json.
 */
class AndroidLocalVisionAssist(
    private val context: Context,
    private val packs: ModelPackManager,
    private val useGpu: () -> Boolean = { false },
) : LocalVisionAssist {

    override fun isReady(): Boolean = resolveModel() != null

    override fun describeImage(imagePath: String, question: String): LocalAssistResult {
        val resolved = resolveModel()
            ?: return LocalAssistResult.Unavailable(
                "Download ${LiteRtLmPacks.GEMMA4_CODE} (~2.6 GB) from Model packs for offline vision assist.",
            )
        @Suppress("UNCHECKED_CAST")
        return LiteRtLmInference.runVision(
            context = context,
            packs = packs,
            packId = resolved.packId,
            modelPath = resolved.modelPath,
            useGpu = useGpu(),
            visionEnabled = resolved.config.vision,
            imagePath = imagePath,
            question = question,
            mapOk = { LocalAssistResult.Ok(it.text) },
            mapUnavailable = { LocalAssistResult.Unavailable(it) },
        ) as LocalAssistResult
    }

    private fun resolveModel(): LiteRtLmPackResolver.ResolvedPack? {
        val resolved = LiteRtLmPackResolver.resolveWithConfig(
            packs,
            LiteRtLmPacks.GEMMA4_CODE,
            LiteRtLmPacks.GEMMA4_FILE,
        ) ?: return null
        val file = File(resolved.modelPath)
        if (file.length() < LiteRtLmPackLimits.MIN_GEMMA4_BYTES) return null
        if (!resolved.config.vision) return null
        return resolved
    }
}
