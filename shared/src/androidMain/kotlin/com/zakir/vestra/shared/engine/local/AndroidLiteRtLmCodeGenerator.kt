package com.zakir.vestra.shared.engine.local

import android.content.Context
import com.google.ai.edge.litertlm.ToolSet
import com.zakir.vestra.shared.engine.litert.LiteRtLmStreamEvent
import com.zakir.vestra.shared.packs.ModelPackManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Code Studio / chat over a LiteRT-LM `.litertlm` pack (Gallery-class). Defaults to the
 * Gemma 4 E2B pack; [packId] / [primaryFile] / [minBytes] point it at any other LiteRT-LM
 * pack — e.g. the much smaller Qwen3 0.6B INT4 route.
 */
class AndroidLiteRtLmCodeGenerator(
    private val context: Context,
    private val packs: ModelPackManager,
    private val packId: String = LiteRtLmPacks.GEMMA4_CODE,
    private val useGpu: () -> Boolean = { false },
    private val tools: List<ToolSet> = emptyList(),
    private val primaryFile: String = LiteRtLmPacks.GEMMA4_FILE,
    private val minBytes: Long = LiteRtLmPackLimits.MIN_GEMMA4_BYTES,
    private val downloadHint: String = "~2.6 GB",
) : LocalCodeGenerator {

    override fun providerId(): String = packId

    override fun isReady(): Boolean =
        LiteRtLmInference.litertLmReady(packs, packId, primaryFile, minBytes)

    /**
     * Loads the engine into the shared cache without opening a conversation.
     *
     * An earlier version warmed up by generating one token, which left a session open — and
     * LiteRT-LM allows only one, so the user's first real prompt failed with
     * FAILED_PRECONDITION. Initialization alone is what warm-up should cost.
     */
    override fun warmUp(): String? {
        if (!isReady()) {
            return "Download $packId ($downloadHint) from Model packs."
        }
        val dir = packs.installedDir(packId) ?: return "$packId pack directory missing."
        val modelPath = LiteRtLmPackConfig.modelPath(java.io.File(dir), primaryFile)
            ?: return "$primaryFile missing — re-download $packId."
        return LiteRtLmInference.warmUpEngine(
            context = context,
            packs = packs,
            packId = packId,
            modelPath = modelPath,
            useGpu = useGpu(),
            tools = tools,
        )
    }

    override fun generate(prompt: String, system: String): LocalCodeResult {
        if (!isReady()) {
            return LocalCodeResult.Unavailable(
                "Download $packId ($downloadHint) from Model packs for offline on-device generation.",
            )
        }
        val dir = packs.installedDir(packId)
            ?: return LocalCodeResult.Unavailable("$packId pack directory missing.")
        val modelPath = LiteRtLmPackConfig.modelPath(java.io.File(dir), primaryFile)
            ?: return LocalCodeResult.Unavailable("$primaryFile missing — re-download $packId.")
        @Suppress("UNCHECKED_CAST")
        return LiteRtLmInference.runText(
            context = context,
            packs = packs,
            packId = packId,
            modelPath = modelPath,
            useGpu = useGpu(),
            tools = tools,
            prompt = prompt,
            system = system,
            mapOk = { LocalCodeResult.Ok(it.text, it.tokensIn, it.tokensOut) },
            mapUnavailable = { LocalCodeResult.Unavailable(it) },
        ) as LocalCodeResult
    }

    override fun generateStream(prompt: String, system: String): Flow<LocalCodeStreamEvent> {
        if (!isReady()) {
            return flow {
                emit(
                    LocalCodeStreamEvent.Unavailable(
                        "Download $packId ($downloadHint) from Model packs for offline on-device generation.",
                    ),
                )
            }
        }
        val dir = packs.installedDir(packId)
            ?: return flow { emit(LocalCodeStreamEvent.Unavailable("$packId pack directory missing.")) }
        val modelPath = LiteRtLmPackConfig.modelPath(java.io.File(dir), primaryFile)
            ?: return flow { emit(LocalCodeStreamEvent.Unavailable("$primaryFile missing — re-download $packId.")) }
        return LiteRtLmInference.runTextStream(
            context = context,
            packs = packs,
            packId = packId,
            modelPath = modelPath,
            useGpu = useGpu(),
            tools = tools,
            prompt = prompt,
            system = system,
        ).map { event ->
            when (event) {
                is LiteRtLmStreamEvent.Partial -> LocalCodeStreamEvent.Partial(event.textSoFar)
                is LiteRtLmStreamEvent.Done -> LocalCodeStreamEvent.Done(event.text, event.tokensIn, event.tokensOut)
                is LiteRtLmStreamEvent.Unavailable -> LocalCodeStreamEvent.Unavailable(event.reason)
            }
        }
    }
}
