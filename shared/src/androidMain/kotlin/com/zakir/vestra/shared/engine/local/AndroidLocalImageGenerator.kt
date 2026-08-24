package com.zakir.vestra.shared.engine.local

import android.graphics.Bitmap
import com.zakir.vestra.shared.packs.ModelPackManager
import com.zakir.vestra.shared.engine.lite.OrtSessionCache
import com.zakir.vestra.shared.quality.NoOpQualityPostProcessor
import com.zakir.vestra.shared.quality.QualityPostProcessor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Android local Create / Edit Studio generator.
 *
 * Ready when `local-sdturbo-v1` graphs are real **and** [Txt2ImgPipeline.SAMPLER_WIRED].
 * Edit ready when `vae_encoder.onnx` is also present (pack v3+).
 * Runs [AndroidTxt2ImgEngine] (4-ch SD-Turbo / LCM) — never Pro try-on packs.
 */
class AndroidLocalImageGenerator(
    private val packs: ModelPackManager,
    private val outputDir: File,
    private val loadReferenceBitmap: (uri: String) -> Bitmap? = { null },
    private val packId: String = PACK_ID,
    private val quality: QualityPostProcessor = NoOpQualityPostProcessor,
) : LocalImageGenerator {

    override fun isReady(): Boolean {
        if (!Txt2ImgPipeline.SAMPLER_WIRED) return false
        return packGraphsReady()
    }

    override fun isEditReady(): Boolean {
        if (!isReady()) return false
        val dirPath = packs.installedDir(packId) ?: return false
        val dir = File(dirPath)
        val config = loadConfig(dir) ?: return false
        val name = config.graphs?.vaeEncoder ?: "vae_encoder.onnx"
        val enc = File(dir, name)
        return enc.isFile && enc.length() >= MIN_GRAPH_BYTES
    }

    /**
     * Constructs the ONNX sessions for the pack, which is what actually proves it can run.
     * The device reported ORT_INVALID_ARGUMENT on the first real generation while the picker
     * still showed "Ready offline"; opening the graphs here surfaces that at selection time.
     */
    override fun warmUp(): String? {
        if (!Txt2ImgPipeline.SAMPLER_WIRED) return "On-device sampler not wired in this build."
        if (!packs.isReady(packId)) {
            return "Local image pack not installed — download $packId from Model packs."
        }
        val dirPath = packs.installedDir(packId) ?: return "Local image pack directory missing."
        val dir = File(dirPath)
        val config = loadConfig(dir) ?: return "Pack config.json missing or invalid — re-download $packId."
        val missing = missingOrTinyGraphs(dir, config)
        if (missing.isNotEmpty()) {
            return "Local SD-Turbo weights incomplete (${missing.joinToString()}). Re-download $packId."
        }
        return runCatching {
            packs.markPackInUse(packId)
            OrtSessionCache.enterInference()
            AndroidTxt2ImgEngine(dir, config).use { it.warmUp() }
            null
        }.getOrElse { it.message ?: "Local image engine failed to load" }
            .also {
                OrtSessionCache.leaveInference()
                packs.markPackIdle(packId)
            }
    }

    override fun generate(prompt: String, seed: Long?, referenceImageUri: String?): LocalImageResult {
        val ready = when (val r = checkReadiness(referenceImageUri)) {
            is Readiness.NotReady -> return LocalImageResult.Unavailable(r.reason)
            is Readiness.Ready -> r
        }
        val referenceBitmap = if (!referenceImageUri.isNullOrBlank()) {
            loadReferenceBitmap(referenceImageUri)
                ?: return LocalImageResult.Unavailable("Couldn't read the reference image for local edit.")
        } else {
            null
        }
        return try {
            packs.markPackInUse(packId)
            OrtSessionCache.enterInference()
            AndroidTxt2ImgEngine(ready.dir, ready.config, quality).use { engine ->
                engine.generate(prompt, seed, outputDir, referenceBitmap = referenceBitmap)
            }
        } finally {
            OrtSessionCache.leaveInference()
            packs.markPackIdle(packId)
            if (referenceBitmap != null && !referenceBitmap.isRecycled) {
                referenceBitmap.recycle()
            }
        }
    }

    /** Streaming variant: emits [LocalImageStreamEvent.Progress] once per denoising step. */
    override fun generateStream(
        prompt: String,
        seed: Long?,
        referenceImageUri: String?,
    ): Flow<LocalImageStreamEvent> {
        val ready = when (val r = checkReadiness(referenceImageUri)) {
            is Readiness.NotReady -> return flowOf(LocalImageStreamEvent.Unavailable(r.reason))
            is Readiness.Ready -> r
        }
        val referenceBitmap = if (!referenceImageUri.isNullOrBlank()) {
            loadReferenceBitmap(referenceImageUri)
                ?: return flowOf(
                    LocalImageStreamEvent.Unavailable("Couldn't read the reference image for local edit."),
                )
        } else {
            null
        }
        return callbackFlow {
            try {
                packs.markPackInUse(packId)
                OrtSessionCache.enterInference()
                val result = AndroidTxt2ImgEngine(ready.dir, ready.config, quality).use { engine ->
                    engine.generate(
                        prompt,
                        seed,
                        outputDir,
                        referenceBitmap = referenceBitmap,
                        onStep = { step, totalSteps ->
                            trySendBlocking(
                                LocalImageStreamEvent.Progress(
                                    "Step $step of $totalSteps…",
                                    step.toFloat() / totalSteps,
                                ),
                            )
                        },
                    )
                }
                when (result) {
                    is LocalImageResult.Ok -> trySendBlocking(LocalImageStreamEvent.Done(result.imagePath))
                    is LocalImageResult.Unavailable ->
                        trySendBlocking(LocalImageStreamEvent.Unavailable(result.reason))
                }
            } finally {
                OrtSessionCache.leaveInference()
                packs.markPackIdle(packId)
                if (referenceBitmap != null && !referenceBitmap.isRecycled) {
                    referenceBitmap.recycle()
                }
            }
            close()
            awaitClose { }
        }
    }

    private sealed class Readiness {
        data class Ready(val dir: File, val config: LocalImagePackConfig) : Readiness()
        data class NotReady(val reason: String) : Readiness()
    }

    private fun checkReadiness(referenceImageUri: String?): Readiness {
        if (!Txt2ImgPipeline.SAMPLER_WIRED) {
            return Readiness.NotReady("On-device Create Studio sampler not wired in this build.")
        }
        if (!packs.isReady(packId)) {
            return Readiness.NotReady(
                "Local image pack not installed — download $packId from Model packs " +
                    "(~1 GB). Then Create and Edit work offline.",
            )
        }
        val dirPath = packs.installedDir(packId)
            ?: return Readiness.NotReady("Local image pack directory missing.")
        val dir = File(dirPath)
        val config = loadConfig(dir)
            ?: return Readiness.NotReady("Pack config.json missing or invalid — re-download $packId.")
        val missing = missingOrTinyGraphs(dir, config)
        if (missing.isNotEmpty()) {
            return Readiness.NotReady(
                "Local SD-Turbo weights incomplete (${missing.joinToString()}). " +
                    "Re-download $packId from Model packs.",
            )
        }
        if (!referenceImageUri.isNullOrBlank()) {
            val encName = config.graphs?.vaeEncoder ?: "vae_encoder.onnx"
            val enc = File(dir, encName)
            if (!enc.isFile || enc.length() < MIN_GRAPH_BYTES) {
                return Readiness.NotReady("Local image edit needs vae_encoder.onnx — re-download $packId (v3+).")
            }
        }
        return Readiness.Ready(dir, config)
    }

    fun packGraphsReady(): Boolean {
        if (!packs.isReady(packId)) return false
        val dirPath = packs.installedDir(packId) ?: return false
        val dir = File(dirPath)
        val config = loadConfig(dir) ?: return false
        return packComplete(dir, config)
    }

    companion object {
        const val PACK_ID = LocalSdturboPackValidator.PACK_ID
        const val MIN_GRAPH_BYTES = LocalSdturboPackValidator.MIN_GRAPH_BYTES

        private val json = Json { ignoreUnknownKeys = true }

        internal fun loadConfig(dir: File): LocalImagePackConfig? {
            val file = File(dir, "config.json")
            if (!file.isFile) return null
            return runCatching { json.decodeFromString<LocalImagePackConfig>(file.readText()) }.getOrNull()
        }

        internal fun missingOrTinyGraphs(dir: File, config: LocalImagePackConfig): List<String> =
            LocalSdturboPackValidator.missingGraphs(config) { name ->
                val f = File(dir, name)
                if (!f.isFile) null else f.length()
            }

        internal fun packComplete(dir: File, config: LocalImagePackConfig): Boolean =
            LocalSdturboPackValidator.isComplete(
                config,
                fileBytes = { name ->
                    val f = File(dir, name)
                    if (!f.isFile) null else f.length()
                },
                fileExists = { name -> File(dir, name).isFile },
            )
    }
}
