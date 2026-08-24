package com.zakir.vestra.shared.engine.local

import android.content.Context
import com.google.ai.edge.litertlm.ToolSet
import com.zakir.vestra.shared.engine.litert.LiteRtLmEngine
import com.zakir.vestra.shared.engine.litert.LiteRtLmEngineCache
import com.zakir.vestra.shared.engine.litert.LiteRtLmGenerateResult
import com.zakir.vestra.shared.engine.litert.LiteRtLmStreamEvent
import com.zakir.vestra.shared.packs.ModelPackManager
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/** Shared warm-engine inference for all LiteRT-LM generators. */
internal object LiteRtLmInference {
    /**
     * Initializes the engine without starting a conversation.
     *
     * withEngine() calls engine.initialize() before running the block, so an empty block pays
     * the whole cold-load cost and leaves no session behind. Warming up via a real one-token
     * generation instead left a conversation open, and LiteRT-LM permits exactly one — the next
     * real prompt then died with FAILED_PRECONDITION: "A session already exists."
     *
     * Returns the failure reason, or null when the model loaded.
     */
    fun warmUpEngine(
        context: Context,
        packs: ModelPackManager,
        packId: String,
        modelPath: String,
        useGpu: Boolean,
        visionEnabled: Boolean = false,
        audioEnabled: Boolean = false,
        tools: List<ToolSet> = emptyList(),
    ): String? {
        packs.markPackInUse(packId)
        return try {
            val spec = LiteRtLmEngineCache.EngineSpec(
                modelPath = modelPath,
                useGpu = useGpu,
                visionEnabled = visionEnabled,
                audioEnabled = audioEnabled,
                toolsKey = LiteRtLmEngine.toolsKey(tools),
            )
            LiteRtLmEngineCache.withEngine(context, spec, tools) { /* load only */ }
            null
        } catch (err: Throwable) {
            err.message?.take(200) ?: "LiteRT-LM failed to load."
        } finally {
            packs.markPackIdle(packId)
        }
    }

    fun runText(
        context: Context,
        packs: ModelPackManager,
        packId: String,
        modelPath: String,
        useGpu: Boolean,
        visionEnabled: Boolean = false,
        audioEnabled: Boolean = false,
        tools: List<ToolSet> = emptyList(),
        prompt: String,
        system: String,
        mapOk: (LiteRtLmGenerateResult.Ok) -> Any,
        mapUnavailable: (String) -> Any,
    ): Any {
        packs.markPackInUse(packId)
        return try {
            val spec = LiteRtLmEngineCache.EngineSpec(
                modelPath = modelPath,
                useGpu = useGpu,
                visionEnabled = visionEnabled,
                audioEnabled = audioEnabled,
                toolsKey = LiteRtLmEngine.toolsKey(tools),
            )
            LiteRtLmEngineCache.withEngine(context, spec, tools) { engine ->
                when (val result = engine.generateText(prompt, system)) {
                    is LiteRtLmGenerateResult.Ok -> mapOk(result)
                    is LiteRtLmGenerateResult.Unavailable -> mapUnavailable(result.reason)
                }
            }
        } catch (err: Throwable) {
            mapUnavailable(err.message?.take(200) ?: "LiteRT-LM failed.")
        } finally {
            packs.markPackIdle(packId)
        }
    }

    /** Streaming counterpart of [runText] — emits partial text as it's generated. */
    fun runTextStream(
        context: Context,
        packs: ModelPackManager,
        packId: String,
        modelPath: String,
        useGpu: Boolean,
        tools: List<ToolSet> = emptyList(),
        prompt: String,
        system: String,
    ): Flow<LiteRtLmStreamEvent> {
        val spec = LiteRtLmEngineCache.EngineSpec(
            modelPath = modelPath,
            useGpu = useGpu,
            visionEnabled = false,
            audioEnabled = false,
            toolsKey = LiteRtLmEngine.toolsKey(tools),
        )
        return LiteRtLmEngineCache.withEngineFlow(context, spec, tools) { engine ->
            engine.generateTextStream(prompt, system)
        }
            .onStart { packs.markPackInUse(packId) }
            .onCompletion { packs.markPackIdle(packId) }
    }

    fun runVision(
        context: Context,
        packs: ModelPackManager,
        packId: String,
        modelPath: String,
        useGpu: Boolean,
        visionEnabled: Boolean,
        imagePath: String,
        question: String,
        mapOk: (LiteRtLmGenerateResult.Ok) -> Any,
        mapUnavailable: (String) -> Any,
    ): Any {
        packs.markPackInUse(packId)
        return try {
            val spec = LiteRtLmEngineCache.EngineSpec(
                modelPath = modelPath,
                useGpu = useGpu,
                visionEnabled = visionEnabled,
                audioEnabled = false,
            )
            LiteRtLmEngineCache.withEngine(context, spec) { engine ->
                when (val result = engine.describeImage(imagePath, question)) {
                    is LiteRtLmGenerateResult.Ok -> mapOk(result)
                    is LiteRtLmGenerateResult.Unavailable -> mapUnavailable(result.reason)
                }
            }
        } catch (err: Throwable) {
            mapUnavailable(err.message?.take(200) ?: "Vision assist failed.")
        } finally {
            packs.markPackIdle(packId)
        }
    }

    fun runTranscribe(
        context: Context,
        packs: ModelPackManager,
        packId: String,
        modelPath: String,
        useGpu: Boolean,
        audioEnabled: Boolean,
        audioPath: String,
        prompt: String,
        mapOk: (LiteRtLmGenerateResult.Ok) -> Any,
        mapUnavailable: (String) -> Any,
    ): Any {
        packs.markPackInUse(packId)
        return try {
            val spec = LiteRtLmEngineCache.EngineSpec(
                modelPath = modelPath,
                useGpu = useGpu,
                visionEnabled = false,
                audioEnabled = audioEnabled,
            )
            LiteRtLmEngineCache.withEngine(context, spec) { engine ->
                when (val result = engine.transcribeAudio(audioPath, prompt)) {
                    is LiteRtLmGenerateResult.Ok -> mapOk(result)
                    is LiteRtLmGenerateResult.Unavailable -> mapUnavailable(result.reason)
                }
            }
        } catch (err: Throwable) {
            mapUnavailable(err.message?.take(200) ?: "Transcription failed.")
        } finally {
            packs.markPackIdle(packId)
        }
    }

    fun gemma4Ready(packs: ModelPackManager, packId: String): Boolean =
        litertLmReady(packs, packId, LiteRtLmPacks.GEMMA4_FILE, LiteRtLmPackLimits.MIN_GEMMA4_BYTES)

    /**
     * Installed-and-complete check for any LiteRT-LM pack. [defaultPrimaryFile] is only the
     * fallback — a pack shipping its own config.json overrides it, so packs whose weights
     * live under a different filename resolve without an engine change.
     */
    fun litertLmReady(
        packs: ModelPackManager,
        packId: String,
        defaultPrimaryFile: String,
        minBytes: Long,
    ): Boolean {
        if (!packs.isReady(packId)) return false
        val dir = packs.installedDir(packId) ?: return false
        val path = LiteRtLmPackConfig.modelPath(File(dir), defaultPrimaryFile) ?: return false
        return File(path).length() >= minBytes
    }
}
