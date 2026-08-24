package com.zakir.vestra.shared.quality

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.zakir.vestra.shared.engine.lite.ImageOps
import com.zakir.vestra.shared.engine.lite.OrtModel
import com.zakir.vestra.shared.packs.ModelPackManager
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp

actual fun createQualityPostProcessor(packs: ModelPackManager): QualityPostProcessor =
    AndroidQualityPostProcessor(packs)

/**
 * Android quality post-processor. Runs Real-ESRGAN / BiRefNet when their
 * packs are installed; otherwise returns null and callers keep the original image.
 */
class AndroidQualityPostProcessor(
    private val packs: ModelPackManager,
) : QualityPostProcessor {

    override fun upscaleIfAvailable(rgba: ByteArray, width: Int, height: Int): ProcessedImage? {
        val dir = packs.installedDir(REALESRGAN_PACK) ?: return null
        val model = File(dir).listFiles()?.firstOrNull { it.name.endsWith(".onnx") } ?: return null
        packs.markPackInUse(REALESRGAN_PACK)
        return try {
            QualityOnnxUpscaler(model.absolutePath).upscale(rgba, width, height)
        } catch (_: Throwable) {
            null
        } finally {
            packs.markPackIdle(REALESRGAN_PACK)
        }
    }

    override fun refineMatteIfAvailable(rgba: ByteArray, width: Int, height: Int): ByteArray? {
        val dir = packs.installedDir(BIREFNET_PACK) ?: return null
        val model = File(dir).listFiles()?.firstOrNull { it.name.endsWith(".onnx") } ?: return null
        packs.markPackInUse(BIREFNET_PACK)
        return try {
            QualityOnnxMatte(model.absolutePath).refine(rgba, width, height)
        } catch (_: Throwable) {
            // OutOfMemoryError and ORT native failures must not crash the try-on flow.
            null
        } finally {
            packs.markPackIdle(BIREFNET_PACK)
        }
    }

    companion object {
        const val REALESRGAN_PACK = "realesrgan-v1"
        const val BIREFNET_PACK = "birefnet-v1"
    }
}

/**
 * Real-ESRGAN (James040 FP16 ONNX): inputs `input` NCHW float16 in [0,1] +
 * `denoise_strength` float16 scalar. Shared [OrtModel] cannot run this graph.
 */
internal class QualityOnnxUpscaler(private val modelPath: String) {
    fun upscale(rgba: ByteArray, width: Int, height: Int): ProcessedImage? = runCatching {
        val env = OrtEnvironment.getEnvironment()
        OrtSession.SessionOptions().use { opts ->
            if (com.zakir.vestra.shared.engine.lite.OrtEpPolicy.preferNnapi) {
                runCatching { opts.addNnapi() }
            }
            env.createSession(modelPath, opts).use { session ->
                val inH = height.coerceIn(64, 512)
                val inW = width.coerceIn(64, 512)
                val bitmap = ImageOps.fromRgba(rgba, width, height)
                val chw = ImageOps.toUnitChw(bitmap, inH, inW)
                val imageHalf = floatArrayToHalfShortBuffer(chw)
                val denoiseHalf = floatArrayToHalfShortBuffer(floatArrayOf(0f))
                OnnxTensor.createTensor(
                    env,
                    imageHalf,
                    longArrayOf(1, 3, inH.toLong(), inW.toLong()),
                    OnnxJavaType.FLOAT16,
                ).use { imageTensor ->
                    OnnxTensor.createTensor(
                        env,
                        denoiseHalf,
                        longArrayOf(1),
                        OnnxJavaType.FLOAT16,
                    ).use { denoiseTensor ->
                        val feeds = linkedMapOf(
                            resolveInputName(session, "input") to imageTensor,
                            resolveInputName(session, "denoise_strength") to denoiseTensor,
                        )
                        session.run(feeds).use { results ->
                            val output = results[0] as? OnnxTensor ?: return@runCatching null
                            val shape = output.info.shape
                            val outH = shape.getOrNull(2)?.toInt()?.takeIf { it > 0 } ?: (inH * 2)
                            val outW = shape.getOrNull(3)?.toInt()?.takeIf { it > 0 } ?: (inW * 2)
                            val channels = shape.getOrNull(1)?.toInt()?.takeIf { it > 0 } ?: 3
                            val count = OrtModel.elementCount(shape)
                            if (count !in 1..OrtModel.MAX_OUTPUT_ELEMENTS || channels < 3) {
                                return@runCatching null
                            }
                            val shorts = ShortArray(count)
                            output.shortBuffer.get(shorts)
                            val floats = FloatArray(count) { i -> halfBitsToFloat(shorts[i]) }
                            val plane = outH * outW
                            val outRgba = ByteArray(plane * 4)
                            for (i in 0 until plane) {
                                val dst = i * 4
                                outRgba[dst] = (floats[i].coerceIn(0f, 1f) * 255).toInt().toByte()
                                outRgba[dst + 1] = (floats[plane + i].coerceIn(0f, 1f) * 255).toInt().toByte()
                                outRgba[dst + 2] = (floats[2 * plane + i].coerceIn(0f, 1f) * 255).toInt().toByte()
                                outRgba[dst + 3] = 255.toByte()
                            }
                            ProcessedImage(outRgba, outW, outH)
                        }
                    }
                }
            }
        }
    }.getOrNull()

    private fun resolveInputName(session: OrtSession, preferred: String): String =
        session.inputNames.firstOrNull { it == preferred }
            ?: session.inputNames.firstOrNull { it.contains(preferred, ignoreCase = true) }
            ?: error("ONNX input '$preferred' not found in ${session.inputNames}")
}

/** BiRefNet matte — ImageNet NCHW float32 → sigmoid logits → alpha. */
internal class QualityOnnxMatte(private val modelPath: String) {
    fun refine(rgba: ByteArray, width: Int, height: Int): ByteArray? = runCatching {
        OrtModel(modelPath).use { model ->
            val (inH, inW) = model.inputSize(defaultSize = 1024)
            val bitmap = ImageOps.fromRgba(rgba, width, height)
            val chw = ImageOps.toNormalizedChw(bitmap, inH, inW)
            val (output, shape) = model.run(chw, inH, inW)
            val outH = shape.getOrNull(2)?.toInt() ?: inH
            val outW = shape.getOrNull(3)?.toInt() ?: inW
            val plane = outH * outW
            val logits = output.copyOfRange(0, plane)
            for (i in logits.indices) {
                logits[i] = 1f / (1f + exp(-logits[i].coerceIn(-20f, 20f)))
            }
            val mask = ImageOps.resizeMask(logits, outW, outH, width, height)
            val out = rgba.copyOf()
            for (i in mask.indices) {
                out[i * 4 + 3] = (mask[i].coerceIn(0f, 1f) * 255).toInt().toByte()
            }
            out
        }
    }.getOrNull()
}

internal fun floatArrayToHalfShortBuffer(values: FloatArray): java.nio.ShortBuffer {
    val buf = ByteBuffer.allocateDirect(values.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
    for (v in values) {
        buf.put(floatToHalfBits(v))
    }
    buf.rewind()
    return buf
}

internal fun floatToHalfBits(value: Float): Short {
    val bits = java.lang.Float.floatToIntBits(value)
    val sign = (bits ushr 16) and 0x8000
    val exponent = ((bits ushr 23) and 0xff) - 127
    val mantissa = bits and 0x7fffff
    return when {
        exponent == 128 -> (sign or 0x7c00 or if (mantissa != 0) 0x200 else 0).toShort()
        exponent > 15 -> (sign or 0x7c00).toShort()
        exponent >= -14 -> {
            val halfExp = exponent + 15
            val halfMant = mantissa shr 13
            (sign or (halfExp shl 10) or halfMant).toShort()
        }
        exponent >= -24 -> {
            val shift = -14 - exponent
            val mant = (mantissa or 0x800000) shr (shift + 13)
            (sign or mant).toShort()
        }
        else -> sign.toShort()
    }
}

internal fun halfBitsToFloat(half: Short): Float {
    val h = half.toInt() and 0xffff
    val sign = (h ushr 15) and 0x1
    var exp = (h ushr 10) and 0x1f
    var mant = h and 0x3ff
    return when (exp) {
        0 -> if (mant == 0) {
            java.lang.Float.intBitsToFloat(sign shl 31)
        } else {
            while (mant and 0x400 == 0) {
                mant = mant shl 1
                exp--
            }
            mant = mant and 0x3ff
            exp++
            java.lang.Float.intBitsToFloat((sign shl 31) or ((exp + 127 - 15) shl 23) or (mant shl 13))
        }
        0x1f -> java.lang.Float.intBitsToFloat((sign shl 31) or 0x7f800000 or (mant shl 13))
        else -> java.lang.Float.intBitsToFloat((sign shl 31) or ((exp + 127 - 15) shl 23) or (mant shl 13))
    }
}
