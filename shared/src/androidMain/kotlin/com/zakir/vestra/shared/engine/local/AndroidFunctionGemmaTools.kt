package com.zakir.vestra.shared.engine.local

import android.content.Context
import com.zakir.vestra.shared.packs.ModelPackManager
import java.io.File

/**
 * FunctionGemma 270M tool-calling pack (Gallery Mobile Actions class).
 * Experimental — requires [LiteRtLmPacks.FUNCTION_GEMMA] installed.
 */
class AndroidFunctionGemmaTools(
    private val context: Context,
    private val packs: ModelPackManager,
    private val useGpu: () -> Boolean = { false },
    private val toolSet: LookbookStudioToolSet = LookbookStudioToolSet(),
) : LocalCodeGenerator {

    override fun providerId(): String = LiteRtLmPacks.FUNCTION_GEMMA

    override fun isReady(): Boolean {
        val resolved = LiteRtLmPackResolver.resolveWithConfig(
            packs,
            LiteRtLmPacks.FUNCTION_GEMMA,
            LiteRtLmPacks.FUNCTION_GEMMA_FILE,
        ) ?: return false
        return File(resolved.modelPath).length() >= LiteRtLmPackLimits.MIN_FUNCTION_BYTES
    }

    override fun generate(prompt: String, system: String): LocalCodeResult {
        val resolved = LiteRtLmPackResolver.resolveWithConfig(
            packs,
            LiteRtLmPacks.FUNCTION_GEMMA,
            LiteRtLmPacks.FUNCTION_GEMMA_FILE,
        ) ?: return LocalCodeResult.Unavailable(
            "Download ${LiteRtLmPacks.FUNCTION_GEMMA} from Model packs for local tools.",
        )
        @Suppress("UNCHECKED_CAST")
        return LiteRtLmInference.runText(
            context = context,
            packs = packs,
            packId = resolved.packId,
            modelPath = resolved.modelPath,
            useGpu = useGpu(),
            tools = listOf(toolSet),
            prompt = prompt,
            system = system,
            mapOk = { LocalCodeResult.Ok(it.text, it.tokensIn, it.tokensOut) },
            mapUnavailable = { LocalCodeResult.Unavailable(it) },
        ) as LocalCodeResult
    }
}
