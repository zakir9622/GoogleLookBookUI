package com.zakir.vestra.shared.engine.local

import android.graphics.Bitmap
import android.graphics.Color
import com.zakir.vestra.shared.packs.ModelPackManager
import com.zakir.vestra.shared.quality.NoOpQualityPostProcessor
import com.zakir.vestra.shared.quality.QualityEnhancer
import com.zakir.vestra.shared.quality.QualityPostProcessor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Bonsai Image 4B (`local-bonsai-image-v1`) — a ternary-weight FLUX.2-klein-architecture
 * diffusion transformer converted to LiteRT, generating 512x512 images fully on-device via
 * three fixed-shape `.tflite` graphs (text encoder, DiT, VAE decoder) over the plain LiteRT
 * `Interpreter` API on CPU/XNNPACK. Text-to-image only — no reference-image conditioning,
 * so [isEditReady] is always false and [generate] rejects a [referenceImageUri].
 *
 * Graphs load and close sequentially so peak memory stays near the DiT's size (~2.9 GiB)
 * rather than the ~4 GiB sum of all three — the difference between completing and an LMK
 * kill on 8 GB devices. A run takes several minutes on a Pixel-class CPU; [generateStream]
 * reports progress once per DiT step rather than leaving the caller with one static message
 * for the whole run.
 *
 * Ported from the Apache-2.0 `BonsaiPipeline.kt` at
 * https://github.com/john-rocky/hf-to-litertlm/tree/main/bonsai_image_work/device/BonsaiAppAndroid
 * (Daisuke Majima), the reference Android app for
 * [litert-community/Bonsai-Image-ternary-4B](https://huggingface.co/litert-community/Bonsai-Image-ternary-4B).
 */
class BonsaiImageEngine(
    private val packs: ModelPackManager,
    private val outputDir: File,
    private val packId: String = PACK_ID,
    private val steps: Int = DEFAULT_STEPS,
    private val quality: QualityPostProcessor = NoOpQualityPostProcessor,
) : LocalImageGenerator {

    override fun isEditReady(): Boolean = false

    override fun isReady(): Boolean {
        if (!packs.isReady(packId)) return false
        val dir = packDir() ?: return false
        val meta = loadMeta(dir) ?: return false
        return missingFiles(dir, meta).isEmpty()
    }

    /**
     * Opens and closes each graph (mmap + allocateTensors, no inference) to prove the pack
     * loads before the first prompt costs several minutes finding out it doesn't.
     */
    override fun warmUp(): String? {
        if (!packs.isReady(packId)) {
            return "Local image pack not installed — download $packId from Model packs."
        }
        val dir = packDir() ?: return "Local image pack directory missing."
        val meta = loadMeta(dir) ?: return "Pack pipeline_meta.json missing or invalid — re-download $packId."
        val missing = missingFiles(dir, meta)
        if (missing.isNotEmpty()) {
            return "Bonsai Image weights incomplete (${missing.joinToString()}). Re-download $packId."
        }
        return runCatching {
            packs.markPackInUse(packId)
            for (name in listOf(meta.textencFile, meta.ditFile, meta.vaeFile)) {
                Graph(File(dir, name), THREADS).close()
            }
            null
        }.getOrElse { it.message ?: "Bonsai Image engine failed to load" }
            .also { packs.markPackIdle(packId) }
    }

    override fun generate(prompt: String, seed: Long?, referenceImageUri: String?): LocalImageResult {
        val ready = when (val r = checkReadiness(referenceImageUri)) {
            is Readiness.NotReady -> return LocalImageResult.Unavailable(r.reason)
            is Readiness.Ready -> r
        }
        return try {
            packs.markPackInUse(packId)
            runGeneration(ready.dir, ready.meta, prompt, seed ?: System.currentTimeMillis())
        } finally {
            packs.markPackIdle(packId)
        }
    }

    /**
     * Streaming variant: emits [LocalImageStreamEvent.Progress] once per DiT step instead of
     * leaving the caller with one static message for the several-minute run. The blocking
     * pipeline still runs on this method's own coroutine (already off the main thread via the
     * caller's `flowOn(Dispatchers.Default)`); [callbackFlow] just gives [runGeneration]'s
     * synchronous `onStage` callback somewhere to push into.
     */
    override fun generateStream(
        prompt: String,
        seed: Long?,
        referenceImageUri: String?,
    ): Flow<LocalImageStreamEvent> {
        val ready = when (val r = checkReadiness(referenceImageUri)) {
            is Readiness.NotReady -> return flowOf(LocalImageStreamEvent.Unavailable(r.reason))
            is Readiness.Ready -> r
        }
        return callbackFlow {
            packs.markPackInUse(packId)
            try {
                val result = runGeneration(
                    ready.dir,
                    ready.meta,
                    prompt,
                    seed ?: System.currentTimeMillis(),
                ) { stage, fraction ->
                    trySendBlocking(LocalImageStreamEvent.Progress(stage, fraction))
                }
                when (result) {
                    is LocalImageResult.Ok -> trySendBlocking(LocalImageStreamEvent.Done(result.imagePath))
                    is LocalImageResult.Unavailable -> trySendBlocking(LocalImageStreamEvent.Unavailable(result.reason))
                }
            } finally {
                packs.markPackIdle(packId)
            }
            close()
            awaitClose { }
        }
    }

    private sealed class Readiness {
        data class Ready(val dir: File, val meta: PipelineMeta) : Readiness()
        data class NotReady(val reason: String) : Readiness()
    }

    private fun checkReadiness(referenceImageUri: String?): Readiness {
        if (!referenceImageUri.isNullOrBlank()) {
            return Readiness.NotReady(
                "Bonsai Image 4B is text-to-image only — pick a reference-free prompt, " +
                    "or switch to Local image edit (img2img).",
            )
        }
        if (!packs.isReady(packId)) {
            return Readiness.NotReady(
                "Local image pack not installed — download $packId from Model packs " +
                    "(~4 GB). Then Create works offline.",
            )
        }
        val dir = packDir() ?: return Readiness.NotReady("Local image pack directory missing.")
        val meta = loadMeta(dir)
            ?: return Readiness.NotReady("Pack pipeline_meta.json missing or invalid — re-download $packId.")
        val missing = missingFiles(dir, meta)
        if (missing.isNotEmpty()) {
            return Readiness.NotReady(
                "Bonsai Image weights incomplete (${missing.joinToString()}). " +
                    "Re-download $packId from Model packs.",
            )
        }
        return Readiness.Ready(dir, meta)
    }

    private fun runGeneration(
        dir: File,
        meta: PipelineMeta,
        prompt: String,
        seed: Long,
        onStage: (stage: String, fraction: Float) -> Unit = { _, _ -> },
    ): LocalImageResult =
        runCatching {
            onStage("Encoding prompt…", 0.02f)
            val tokenizer = BonsaiTokenizer(dir)
            val enc = tokenizer.encodePrompt(prompt)

            val embeds = Graph(File(dir, meta.textencFile), THREADS).use { te ->
                te.run(listOf(ibuf(enc.ids), ibuf(enc.mask)), BonsaiMath.SEQ * 7680)
            }

            onStage("Loading DiT (2.1 GiB)…", 0.10f)
            val sigmas = BonsaiMath.sigmas(steps)
            val imgIds = fbuf(BonsaiMath.imgIds())
            val txtIds = fbuf(BonsaiMath.txtIds())
            val embedsBuf = fbuf(embeds)
            var lat = BonsaiMath.noise(seed)
            Graph(File(dir, meta.ditFile), THREADS).use { dit ->
                for (k in 0 until steps) {
                    onStage("Step ${k + 1} of $steps…", 0.16f + 0.72f * k / steps)
                    val v = dit.run(
                        listOf(fbuf(lat), embedsBuf, fbuf(floatArrayOf(sigmas[k])), imgIds, txtIds),
                        BonsaiMath.TOKENS * BonsaiMath.PACKED_CHANNELS,
                    )
                    val ds = sigmas[k + 1] - sigmas[k]
                    for (i in lat.indices) lat[i] += ds * v[i]
                }
            }

            onStage("Decoding image…", 0.90f)
            val z = BonsaiMath.unpatchify(lat, meta.bnScale, meta.bnShift)
            val y = Graph(File(dir, meta.vaeFile), THREADS).use { vae ->
                vae.run(listOf(fbuf(z)), 3 * 512 * 512)
            }
            val rawBitmap = toBitmap(y)
            onStage("Enhancing…", 0.95f)
            val bitmap = QualityEnhancer.upscaleIfInstalled(quality, rawBitmap)
            outputDir.mkdirs()
            val out = File(outputDir, "local_img_${System.currentTimeMillis()}.png")
            FileOutputStream(out).use { fos ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)) {
                    error("Failed to encode PNG")
                }
            }
            if (bitmap !== rawBitmap) rawBitmap.recycle()
            bitmap.recycle()
            onStage("Done", 1f)
            LocalImageResult.Ok(out.absolutePath)
        }.getOrElse { err ->
            LocalImageResult.Unavailable(err.message?.take(200) ?: "On-device image generation failed")
        }

    private fun toBitmap(chw: FloatArray): Bitmap {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(512 * 512)
        for (p in 0 until 512 * 512) {
            fun channel(c: Int) = ((chw[c * 262144 + p] / 2f + 0.5f) * 255f).coerceIn(0f, 255f).let {
                Math.round(it)
            }
            pixels[p] = Color.rgb(channel(0), channel(1), channel(2))
        }
        bitmap.setPixels(pixels, 0, 512, 0, 0, 512, 512)
        return bitmap
    }

    private fun packDir(): File? = packs.installedDir(packId)?.let(::File)

    private class PipelineMeta(
        val ditFile: String,
        val textencFile: String,
        val vaeFile: String,
        val bnScale: FloatArray,
        val bnShift: FloatArray,
    )

    private fun loadMeta(dir: File): PipelineMeta? = runCatching {
        val file = File(dir, "pipeline_meta.json")
        val jo = JSONObject(file.readText())
        val files = jo.getJSONObject("files")
        fun floats(key: String): FloatArray {
            val arr = jo.getJSONArray(key)
            return FloatArray(arr.length()) { arr.getDouble(it).toFloat() }
        }
        PipelineMeta(
            ditFile = files.getString("dit"),
            textencFile = files.getString("textenc"),
            vaeFile = files.getString("vae"),
            bnScale = floats("latent_bn_scale"),
            bnShift = floats("latent_bn_shift"),
        )
    }.getOrNull()

    private fun missingFiles(dir: File, meta: PipelineMeta): List<String> =
        listOf(meta.textencFile, meta.ditFile, meta.vaeFile, "tokenizer/vocab.json", "tokenizer/merges.txt")
            .filter { !File(dir, it).isFile }

    /** Single fixed-shape graph, CPU/XNNPACK, mmap'd. */
    private class Graph(file: File, threads: Int) : AutoCloseable {
        private val interp: Interpreter = Interpreter(
            file,
            Interpreter.Options().apply {
                numThreads = threads
                setUseXNNPACK(true) // required for blockwise-int4 correctness and speed
            },
        ).apply { allocateTensors() }

        // Input order by serving_default_args_<n>, NEVER by shape/index — the text encoder's
        // input_ids and attention_mask are both (1, 256), so a shape-keyed map would silently
        // drop one of them.
        private val argOrder: IntArray = run {
            val n = interp.inputTensorCount
            fun argpos(i: Int): Int {
                val name = interp.getInputTensor(i).name()
                val at = name.lastIndexOf("args_")
                if (at < 0) return i
                return name.substring(at + 5).takeWhile { it.isDigit() }.toIntOrNull() ?: i
            }
            (0 until n).sortedBy { argpos(it) }.toIntArray()
        }

        /** Inputs in argument order; returns output tensor 0 as floats. */
        fun run(inputs: List<ByteBuffer>, outCount: Int): FloatArray {
            require(inputs.size == argOrder.size) { "inputCount ${inputs.size} != ${argOrder.size}" }
            val byGraphIndex = arrayOfNulls<Any>(inputs.size)
            for ((argIdx, graphIdx) in argOrder.withIndex()) {
                val t = interp.getInputTensor(graphIdx)
                val buf = inputs[argIdx]
                buf.rewind()
                require(t.numBytes() == buf.capacity()) {
                    "byteSize input $graphIdx: graph ${t.numBytes()} != host ${buf.capacity()}"
                }
                byGraphIndex[graphIdx] = buf
            }
            val out = ByteBuffer.allocateDirect(outCount * 4).order(ByteOrder.nativeOrder())
            interp.runForMultipleInputsOutputs(byGraphIndex, mapOf(0 to out))
            out.rewind()
            val floats = FloatArray(outCount)
            out.asFloatBuffer().get(floats)
            return floats
        }

        override fun close() = interp.close()
    }

    companion object {
        const val PACK_ID = "local-bonsai-image-v1"
        const val DEFAULT_STEPS = 4 // model is step-distilled at this count
        private val THREADS = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)

        private fun fbuf(a: FloatArray): ByteBuffer =
            ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder())
                .apply { asFloatBuffer().put(a); rewind() }

        private fun ibuf(a: IntArray): ByteBuffer =
            ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder())
                .apply { asIntBuffer().put(a); rewind() }
    }
}
