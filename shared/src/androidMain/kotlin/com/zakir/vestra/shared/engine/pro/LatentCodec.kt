package com.zakir.vestra.shared.engine.pro

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import java.nio.FloatBuffer
import kotlin.random.Random

/**
 * VAE + UNet plumbing for the CatVTON-style pipeline. All latents are NCHW
 * with 4 channels at (latentHeight × latentWidth); the "try-on concat" doubles
 * the [ProPackConfig.concatAxis] dimension — person half first, garment half
 * second. Only the person half is decoded.
 */
class LatentCodec(
    private val packDir: String,
    private val config: ProPackConfig,
) : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val encoder = session("vae_encoder.onnx")
    private val decoder = session("vae_decoder.onnx")

    private val latentPlane = config.latentWidth * config.latentHeight
    private val latentSize = 4 * latentPlane

    val concatAlongWidth: Boolean get() = config.concatAxis != "height"

    // ── image prep ────────────────────────────────────────────────────────────

    /** Center-crops + scales to the model's working resolution. */
    fun centerCrop(source: Bitmap): Bitmap {
        val targetRatio = config.imageWidth.toFloat() / config.imageHeight
        val sourceRatio = source.width.toFloat() / source.height
        val crop = if (sourceRatio > targetRatio) {
            val width = (source.height * targetRatio).toInt()
            Rect((source.width - width) / 2, 0, (source.width + width) / 2, source.height)
        } else {
            val height = (source.width / targetRatio).toInt()
            Rect(0, (source.height - height) / 2, source.width, (source.height + height) / 2)
        }
        val cropped = Bitmap.createBitmap(source, crop.left, crop.top, crop.width(), crop.height())
        return cropped.scale(config.imageWidth, config.imageHeight)
    }

    /** Resizes a person-space mask (row-major 0..1) to the working resolution. */
    fun resizeMask(mask: FloatArray, maskWidth: Int, maskHeight: Int): FloatArray {
        val out = FloatArray(config.imageWidth * config.imageHeight)
        for (y in 0 until config.imageHeight) {
            val srcY = y * maskHeight / config.imageHeight
            for (x in 0 until config.imageWidth) {
                val srcX = x * maskWidth / config.imageWidth
                out[y * config.imageWidth + x] = mask[srcY * maskWidth + srcX]
            }
        }
        return out
    }

    // ── VAE ───────────────────────────────────────────────────────────────────

    fun encode(image: Bitmap): FloatArray {
        val chw = toChw(image, zeroMask = null)
        return runVae(encoder, chw, config.imageHeight, config.imageWidth).also { latent ->
            for (i in latent.indices) latent[i] *= config.vaeScale
        }
    }

    /** Encodes the person with the inpaint region blanked (SD-inpaint convention). */
    fun encodeMasked(image: Bitmap, mask: FloatArray): FloatArray {
        val chw = toChw(image, zeroMask = mask)
        return runVae(encoder, chw, config.imageHeight, config.imageWidth).also { latent ->
            for (i in latent.indices) latent[i] *= config.vaeScale
        }
    }

    fun decodePersonHalf(concatSample: FloatArray): Bitmap {
        val person = splitPersonHalf(concatSample)
        for (i in person.indices) person[i] /= config.vaeScale
        val decoded = runVae(decoder, person, config.latentHeight, config.latentWidth)
        return fromChw(decoded, config.imageWidth, config.imageHeight)
    }

    /** Blends the generated canvas back into the original photo via the soft mask. */
    fun pasteBack(original: Bitmap, generated: Bitmap, maskAtWorkingRes: FloatArray): Bitmap {
        val out = original.copy(Bitmap.Config.ARGB_8888, true)
        val generatedFull = generated.scale(original.width, original.height)
        val pixels = IntArray(original.width * original.height)
        val genPixels = IntArray(original.width * original.height)
        out.getPixels(pixels, 0, original.width, 0, 0, original.width, original.height)
        generatedFull.getPixels(genPixels, 0, original.width, 0, 0, original.width, original.height)
        for (y in 0 until original.height) {
            val maskY = y * config.imageHeight / original.height
            for (x in 0 until original.width) {
                val maskX = x * config.imageWidth / original.width
                val alpha = maskAtWorkingRes[maskY * config.imageWidth + maskX]
                if (alpha > 0.02f) {
                    val idx = y * original.width + x
                    pixels[idx] = blendPixel(pixels[idx], genPixels[idx], alpha)
                }
            }
        }
        return Bitmap.createBitmap(pixels, original.width, original.height, Bitmap.Config.ARGB_8888)
    }

    // ── try-on concat ────────────────────────────────────────────────────────

    /** Random-normal sample over the doubled (person+garment) latent. */
    fun initialNoise(random: Random): FloatArray =
        FloatArray(latentSize * 2) { gaussian(random) }

    /** masked-person ⧺ garment (the conditioning latent). */
    fun conditionLatent(maskedPerson: FloatArray, garment: FloatArray): FloatArray =
        concat(maskedPerson, garment)

    /** Unconditional variant for CFG: garment half zeroed. */
    fun unconditionalLatent(maskedPerson: FloatArray): FloatArray =
        concat(maskedPerson, FloatArray(latentSize))

    /** inpaint-mask ⧺ zeros (garment half is never repainted). */
    fun maskConcat(maskAtWorkingRes: FloatArray): FloatArray {
        val latentMask = FloatArray(latentPlane)
        for (y in 0 until config.latentHeight) {
            val srcY = y * config.imageHeight / config.latentHeight
            for (x in 0 until config.latentWidth) {
                val srcX = x * config.imageWidth / config.latentWidth
                latentMask[y * config.latentWidth + x] =
                    maskAtWorkingRes[srcY * config.imageWidth + srcX]
            }
        }
        return concatPlane(latentMask, FloatArray(latentPlane))
    }

    fun openUnet(): UnetRunner = UnetRunner(
        session = session("unet.onnx"),
        env = env,
        channels = 4,
        height = if (concatAlongWidth) config.latentHeight else config.latentHeight * 2,
        width = if (concatAlongWidth) config.latentWidth * 2 else config.latentWidth,
    )

    override fun close() {
        encoder.close()
        decoder.close()
    }

    // ── internals ────────────────────────────────────────────────────────────

    private fun session(file: String): OrtSession =
        ProOrtSessions.create("$packDir/$file")

    private fun runVae(session: OrtSession, chw: FloatArray, height: Int, width: Int): FloatArray {
        val channels = chw.size / (height * width)
        val tensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(chw),
            longArrayOf(1, channels.toLong(), height.toLong(), width.toLong()),
        )
        tensor.use {
            session.run(mapOf(session.inputNames.first() to tensor)).use { results ->
                val output = results[0] as OnnxTensor
                val out = FloatArray(output.info.shape.fold(1L) { a, d -> a * d }.toInt())
                output.floatBuffer.get(out)
                return out
            }
        }
    }

    /** Bitmap → CHW in [-1, 1]; optionally zeroes masked pixels first. */
    private fun toChw(image: Bitmap, zeroMask: FloatArray?): FloatArray {
        val width = image.width
        val height = image.height
        val pixels = IntArray(width * height)
        image.getPixels(pixels, 0, width, 0, 0, width, height)
        val plane = width * height
        val chw = FloatArray(3 * plane)
        for (i in pixels.indices) {
            val masked = zeroMask != null && zeroMask[i] > 0.5f
            chw[i] = if (masked) 0f else (pixels[i] shr 16 and 0xFF) / 127.5f - 1f
            chw[plane + i] = if (masked) 0f else (pixels[i] shr 8 and 0xFF) / 127.5f - 1f
            chw[2 * plane + i] = if (masked) 0f else (pixels[i] and 0xFF) / 127.5f - 1f
        }
        return chw
    }

    private fun fromChw(chw: FloatArray, width: Int, height: Int): Bitmap {
        val plane = width * height
        val pixels = IntArray(plane)
        for (i in 0 until plane) {
            val r = (((chw[i] + 1f) * 127.5f).toInt()).coerceIn(0, 255)
            val g = (((chw[plane + i] + 1f) * 127.5f).toInt()).coerceIn(0, 255)
            val b = (((chw[2 * plane + i] + 1f) * 127.5f).toInt()).coerceIn(0, 255)
            pixels[i] = 0xFF shl 24 or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /** Concats two 4-channel latents along the configured spatial axis. */
    private fun concat(a: FloatArray, b: FloatArray): FloatArray {
        if (!concatAlongWidth) return a + b // channel-major: heights stack per channel plane
        val out = FloatArray(a.size + b.size)
        val rowLength = config.latentWidth
        var cursor = 0
        for (c in 0 until 4) {
            for (y in 0 until config.latentHeight) {
                val rowStart = c * latentPlane + y * rowLength
                a.copyInto(out, cursor, rowStart, rowStart + rowLength)
                cursor += rowLength
                b.copyInto(out, cursor, rowStart, rowStart + rowLength)
                cursor += rowLength
            }
        }
        return out
    }

    private fun concatPlane(a: FloatArray, b: FloatArray): FloatArray {
        if (!concatAlongWidth) return a + b
        val out = FloatArray(a.size + b.size)
        val rowLength = config.latentWidth
        var cursor = 0
        for (y in 0 until config.latentHeight) {
            a.copyInto(out, cursor, y * rowLength, (y + 1) * rowLength)
            cursor += rowLength
            b.copyInto(out, cursor, y * rowLength, (y + 1) * rowLength)
            cursor += rowLength
        }
        return out
    }

    private fun splitPersonHalf(concatSample: FloatArray): FloatArray {
        if (!concatAlongWidth) return concatSample.copyOfRange(0, latentSize)
        val out = FloatArray(latentSize)
        val rowLength = config.latentWidth
        val doubledRow = rowLength * 2
        for (c in 0 until 4) {
            for (y in 0 until config.latentHeight) {
                val src = (c * config.latentHeight + y) * doubledRow
                concatSample.copyInto(out, c * latentPlane + y * rowLength, src, src + rowLength)
            }
        }
        return out
    }

    private fun blendPixel(base: Int, over: Int, alpha: Float): Int {
        val a = alpha.coerceIn(0f, 1f)
        val r = ((base shr 16 and 0xFF) * (1 - a) + (over shr 16 and 0xFF) * a).toInt()
        val g = ((base shr 8 and 0xFF) * (1 - a) + (over shr 8 and 0xFF) * a).toInt()
        val b = ((base and 0xFF) * (1 - a) + (over and 0xFF) * a).toInt()
        return 0xFF shl 24 or (r shl 16) or (g shl 8) or b
    }

    private fun gaussian(random: Random): Float {
        val u1 = random.nextDouble().coerceAtLeast(1e-9)
        val u2 = random.nextDouble()
        return (kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) *
            kotlin.math.cos(2.0 * Math.PI * u2)).toFloat()
    }
}
