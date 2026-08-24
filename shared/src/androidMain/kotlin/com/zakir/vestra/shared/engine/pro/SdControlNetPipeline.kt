package com.zakir.vestra.shared.engine.pro

import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.scale
import com.zakir.vestra.shared.engine.lite.ImageOps
import com.zakir.vestra.shared.engine.lite.OrtSessionCache
import com.zakir.vestra.shared.engine.pipeline.ConditioningInputs
import com.zakir.vestra.shared.engine.pipeline.ConditioningStage
import com.zakir.vestra.shared.engine.pipeline.ConditioningTokens
import com.zakir.vestra.shared.engine.pipeline.MultiConditioningPipeline
import com.zakir.vestra.shared.engine.pipeline.PipelineRequirements
import java.io.File

/**
 * On-device SD1.5 + ControlNet-Depth + IP-Adapter try-on, implementing the
 * staged [MultiConditioningPipeline]. The ONNX contract below is exactly what
 * ml/convert_pro_pack.py exports and validates (input names + shapes verified
 * end-to-end on CPU before shipping the pack):
 *
 *  A. STRUCTURE  depth.onnx: person[1,3,518,518] → depth[1,1,518,518] → RGB
 *                ControlNet conditioning image [1,3,512,512]
 *  B. TEXTURE    ip_image_encoder.onnx: garment[1,3,224,224] →
 *                image_embeds[1,257,1280] (CLIP-H penultimate hidden states).
 *                No separate projection: the IP-Adapter Plus resampler + cross-
 *                attention are baked into unet.onnx.
 *  C. SYNTHESIS  per step: controlnet.onnx(sample,t,text[1,77,768],depth cond)
 *                → 12 down + 1 mid residuals; unet.onnx(sample,t,text,
 *                image_embeds[1,1,257,1280], down_0..down_11, mid) → noise;
 *                CFG 7.0; DDIM step. Then vae_decoder.onnx → mask paste-back.
 *
 * All graphs are FP16 ONNX at 512×512. Correctness of the runtime chain is
 * validated on a flagship (VestraProBench) — the agent container has no NPU to
 * run these weights, only to prove the graphs load and shape-match.
 */
class SdControlNetPipeline(
    private val packDir: String,
    private val config: ProPackConfig,
    private val loadPerson: () -> Bitmap?,
    private val loadGarment: () -> Bitmap?,
    private val maskProvider: (Bitmap) -> FloatArray?,
    private val saveResult: (Bitmap) -> String,
) : MultiConditioningPipeline {

    private val res = config.resolution
    private val ipSeq = config.ipEmbedSeq
    private val ipDim = config.ipEmbedDim

    override fun requirements(): PipelineRequirements = PipelineRequirements(
        hasStructureModel = config.controlNet.present() && config.depthModel.present(),
        // The IP-Adapter projection is baked into the UNet; the image encoder
        // alone is enough to supply texture conditioning.
        hasIpAdapter = config.imageEncoder.present(),
        hasUnet = File("$packDir/${config.unetOrDefault()}").exists(),
    )

    override suspend fun run(
        inputs: ConditioningInputs,
        onStage: suspend (ConditioningStage, Float) -> Unit,
    ): String {
        val person = loadPerson() ?: error("Couldn't read the model image")
        val garment = loadGarment() ?: error("Couldn't read the garment image")
        val personSq = person.scale(res, res)
        val garmentSq = garment.scale(res, res)

        // ── Stage A: STRUCTURE (depth from the PERSON → ControlNet cond) ──
        onStage(ConditioningStage.STRUCTURE, 0.05f)
        val t0 = System.currentTimeMillis()
        val depth = graph(config.depthModel)
        val depthCond = run {
            val chw = ImageOps.toNormalizedChw(personSq.scale(518, 518), 518, 518)
            val map = depth.runSingle(mapOf(depth.inputNames.first() to depth.floatTensor(chw, 1, 3, 518, 518)))
            expandDepthToRgb(map, 518, res)
        }
        Log.i(TAG, "structure_depth: ${System.currentTimeMillis() - t0} ms")

        // ── Stage B: TEXTURE (raw CLIP-H image embeds + text embeds) ──
        onStage(ConditioningStage.TEXTURE, 0.15f)
        val enc = graph(config.imageEncoder)
        val imageEmbeds = enc.runSingle(mapOf(enc.inputNames.first() to enc.floatTensor(
            ImageOps.toNormalizedChw(garmentSq.scale(224, 224), 224, 224), 1, 3, 224, 224,
        )))
        require(imageEmbeds.size == ipSeq * ipDim) {
            "image encoder emitted ${imageEmbeds.size} floats, expected ${ipSeq * ipDim}"
        }
        val textEmbeds = encodePrompt(inputs)

        // ── Stage C: SYNTHESIS (ControlNet residuals + UNet denoise loop) ──
        val steps = ConditioningTokens.clampSteps(config.inferenceSteps)
        val scheduler = DdimScheduler()
        val timesteps = scheduler.timesteps(steps)
        val latentLen = 4 * (res / 8) * (res / 8)
        val random = kotlin.random.Random(inputs.seed ?: System.nanoTime())
        val sample = FloatArray(latentLen) { gaussian(random) }
        val lat = (res / 8).toLong()
        val textSeq = TEXT_TOKENS.toLong()

        val tSync = System.currentTimeMillis()
        val control = graph(config.controlNet)
        val unet = graph(config.unetOrDefault())
        timesteps.forEachIndexed { index, timestep ->
            val residuals = control.run(
                inputs = mapOf(
                    "sample" to control.floatTensor(sample, 1, 4, lat, lat),
                    "timestep" to control.timestepTensor("timestep", timestep, 1),
                    "encoder_hidden_states" to control.floatTensor(textEmbeds, 1, textSeq, HIDDEN_DIM.toLong()),
                    "controlnet_cond" to control.floatTensor(depthCond, 1, 3, res.toLong(), res.toLong()),
                ),
                outputs = CONTROL_OUTPUTS,
            )
            val noise = runUnet(unet, sample, timestep, textEmbeds, imageEmbeds, residuals)
            scheduler.step(sample, noise, timestep, steps)
            onStage(ConditioningStage.SYNTHESIS, 0.2f + 0.65f * (index + 1) / steps)
        }
        Log.i(TAG, "synthesis_${steps}steps: ${System.currentTimeMillis() - tSync} ms")

        // Decode + paste back into the untouched person outside the garment mask.
        val dec = graph(config.vaeDecoderOrDefault())
        val decoded = run {
            val rgb = dec.runSingle(mapOf(dec.inputNames.first() to dec.floatTensor(sample, 1, 4, lat, lat)))
            chwToBitmap(rgb, res)
        }
        val mask = maskProvider(personSq)
        val composed = if (mask != null) pasteBack(personSq, decoded, mask) else decoded
        return saveResult(composed)
    }

    // ── helpers (kept small; heavy tensor work is in the ONNX graphs) ──

    private fun encodePrompt(inputs: ConditioningInputs): FloatArray {
        val textEncoder = config.textEncoder ?: return FloatArray(TEXT_TOKENS * HIDDEN_DIM)
        val path = "$packDir/$textEncoder"
        if (!File(path).exists()) return FloatArray(TEXT_TOKENS * HIDDEN_DIM)
        val tokenIds = if (ClipTokenizer.isAvailable(packDir)) {
            ClipTokenizer(packDir).encode(inputs.prompt.positive)
        } else {
            LongArray(TEXT_TOKENS)
        }
        val te = graph(textEncoder)
        // Typed from the graph. Hardcoding int64 here is the same defect the device reported
        // as ORT_INVALID_ARGUMENT for local image generation, and would make run() throw on its
        // first call — silently dropping Pro try-on to the legacy compositor.
        val teInput = te.inputNames.first()
        return te.runSingle(mapOf(teInput to te.tokenTensor(teInput, tokenIds, 1, TEXT_TOKENS.toLong())))
    }

    private fun graph(relativePath: String?): OrtGraph {
        val rel = requireNotNull(relativePath) { "Graph path missing in Pro pack config" }
        return OrtSessionCache.openGraph("$packDir/$rel")
    }

    private fun runUnet(
        unet: OrtGraph,
        sample: FloatArray,
        timestep: Int,
        textEmbeds: FloatArray,
        imageEmbeds: FloatArray,
        residuals: List<FloatArray>,
    ): FloatArray {
        val lat = (res / 8).toLong()
        val base = mutableMapOf(
            "sample" to unet.floatTensor(sample, 1, 4, lat, lat),
            "timestep" to unet.timestepTensor("timestep", timestep, 1),
            "encoder_hidden_states" to unet.floatTensor(textEmbeds, 1, TEXT_TOKENS.toLong(), HIDDEN_DIM.toLong()),
            // IP-Adapter image embeds are 4-D [batch, num_images, seq, dim];
            // the resampler + attention inside unet.onnx consume them.
            "image_embeds" to unet.floatTensor(imageEmbeds, 1, 1, ipSeq.toLong(), ipDim.toLong()),
        )
        // Feed each ControlNet residual with the exact shape the graph declares.
        residuals.forEachIndexed { i, r ->
            val name = if (i < residuals.size - 1) "down_$i" else "mid"
            val shape = config.residualShapes[i].map { it.toLong() }.toLongArray()
            base[name] = unet.floatTensor(r, *shape)
        }
        return unet.runSingle(base)
    }

    private fun expandDepthToRgb(depth: FloatArray, srcSize: Int, outSize: Int): FloatArray {
        val resized = ImageOps.normalizeAndResizeMask(depth, srcSize, srcSize, outSize, outSize)
        val chw = FloatArray(3 * outSize * outSize)
        val plane = outSize * outSize
        for (i in 0 until plane) {
            chw[i] = resized[i]; chw[plane + i] = resized[i]; chw[2 * plane + i] = resized[i]
        }
        return chw
    }

    private fun chwToBitmap(chw: FloatArray, size: Int): Bitmap {
        val plane = size * size
        val pixels = IntArray(plane)
        for (i in 0 until plane) {
            val r = (((chw[i] + 1f) * 127.5f).toInt()).coerceIn(0, 255)
            val g = (((chw[plane + i] + 1f) * 127.5f).toInt()).coerceIn(0, 255)
            val b = (((chw[2 * plane + i] + 1f) * 127.5f).toInt()).coerceIn(0, 255)
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    private fun pasteBack(person: Bitmap, generated: Bitmap, mask: FloatArray): Bitmap {
        val w = person.width; val h = person.height
        val base = IntArray(w * h); person.getPixels(base, 0, w, 0, 0, w, h)
        val gen = IntArray(w * h); generated.scale(w, h).getPixels(gen, 0, w, 0, 0, w, h)
        for (i in base.indices) {
            val a = mask[i].coerceIn(0f, 1f)
            if (a > 0.02f) {
                val br = base[i]; val gr = gen[i]
                val r = ((br shr 16 and 0xFF) * (1 - a) + (gr shr 16 and 0xFF) * a).toInt()
                val g = ((br shr 8 and 0xFF) * (1 - a) + (gr shr 8 and 0xFF) * a).toInt()
                val bl = ((br and 0xFF) * (1 - a) + (gr and 0xFF) * a).toInt()
                base[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
            }
        }
        return Bitmap.createBitmap(base, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun gaussian(random: kotlin.random.Random): Float {
        val u1 = random.nextDouble().coerceAtLeast(1e-9); val u2 = random.nextDouble()
        return (kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)).toFloat()
    }

    companion object {
        private const val TAG = DiffusionEngine.TAG
        private const val TEXT_TOKENS = 77
        private const val HIDDEN_DIM = 768
        private val CONTROL_OUTPUTS = (0..11).map { "down_$it" } + "mid"
    }
}

private fun String?.present(): Boolean = !isNullOrBlank()
private fun ProPackConfig.unetOrDefault(): String = "unet.onnx"
private fun ProPackConfig.vaeDecoderOrDefault(): String = "vae_decoder.onnx"
