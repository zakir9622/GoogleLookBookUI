package com.zakir.vestra.shared.engine.lite

import android.graphics.Bitmap
import androidx.core.graphics.scale

/** Bitmap ⇄ tensor helpers shared by the Lite pipeline stages. */
object ImageOps {

    /** ImageNet-normalized NCHW float tensor from a bitmap resized to [height]×[width]. */
    fun toNormalizedChw(bitmap: Bitmap, height: Int, width: Int): FloatArray {
        val scaled = bitmap.scale(width, height)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        val chw = FloatArray(3 * height * width)
        val plane = height * width
        for (i in pixels.indices) {
            val p = pixels[i]
            chw[i] = ((p shr 16 and 0xFF) / 255f - 0.485f) / 0.229f
            chw[plane + i] = ((p shr 8 and 0xFF) / 255f - 0.456f) / 0.224f
            chw[2 * plane + i] = ((p and 0xFF) / 255f - 0.406f) / 0.225f
        }
        return chw
    }

    /** Unit-interval NCHW float tensor in [0,1] (Real-ESRGAN / SR models). */
    fun toUnitChw(bitmap: Bitmap, height: Int, width: Int): FloatArray {
        val scaled = bitmap.scale(width, height)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        val chw = FloatArray(3 * height * width)
        val plane = height * width
        for (i in pixels.indices) {
            val p = pixels[i]
            chw[i] = (p shr 16 and 0xFF) / 255f
            chw[plane + i] = (p shr 8 and 0xFF) / 255f
            chw[2 * plane + i] = (p and 0xFF) / 255f
        }
        return chw
    }

    /**
     * Min–max normalizes a single-channel map to 0..1 and resizes it (bilinear)
     * to [outWidth]×[outHeight], returned row-major.
     */
    fun normalizeAndResizeMask(
        mask: FloatArray,
        maskWidth: Int,
        maskHeight: Int,
        outWidth: Int,
        outHeight: Int,
    ): FloatArray {
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        for (v in mask) {
            if (v < min) min = v
            if (v > max) max = v
        }
        val range = (max - min).takeIf { it > 1e-6f } ?: 1f
        return resizeMask(mask, maskWidth, maskHeight, outWidth, outHeight) { v ->
            (v - min) / range
        }
    }

    /** Bilinear resize of a single-channel map (optional per-value map). */
    fun resizeMask(
        mask: FloatArray,
        maskWidth: Int,
        maskHeight: Int,
        outWidth: Int,
        outHeight: Int,
        map: (Float) -> Float = { it },
    ): FloatArray {
        val out = FloatArray(outWidth * outHeight)
        for (y in 0 until outHeight) {
            val srcY = y.toFloat() * (maskHeight - 1) / (outHeight - 1).coerceAtLeast(1)
            val y0 = srcY.toInt().coerceIn(0, maskHeight - 1)
            val y1 = (y0 + 1).coerceAtMost(maskHeight - 1)
            val fy = srcY - y0
            for (x in 0 until outWidth) {
                val srcX = x.toFloat() * (maskWidth - 1) / (outWidth - 1).coerceAtLeast(1)
                val x0 = srcX.toInt().coerceIn(0, maskWidth - 1)
                val x1 = (x0 + 1).coerceAtMost(maskWidth - 1)
                val fx = srcX - x0
                val top = map(mask[y0 * maskWidth + x0]) * (1 - fx) + map(mask[y0 * maskWidth + x1]) * fx
                val bottom = map(mask[y1 * maskWidth + x0]) * (1 - fx) + map(mask[y1 * maskWidth + x1]) * fx
                out[y * outWidth + x] = top * (1 - fy) + bottom * fy
            }
        }
        return out
    }

    /** Applies a 0..1 soft mask as the bitmap's alpha channel. */
    fun applyAlphaMask(bitmap: Bitmap, mask: FloatArray): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val alpha = (mask[i].coerceIn(0f, 1f) * 255).toInt()
            pixels[i] = (alpha shl 24) or (pixels[i] and 0x00FFFFFF)
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /** Argmax over class planes for a CHW logits tensor → class-id map. */
    fun argmax(logits: FloatArray, classes: Int, plane: Int): IntArray {
        val ids = IntArray(plane)
        for (i in 0 until plane) {
            var best = 0
            var bestValue = logits[i]
            for (c in 1 until classes) {
                val value = logits[c * plane + i]
                if (value > bestValue) {
                    bestValue = value
                    best = c
                }
            }
            ids[i] = best
        }
        return ids
    }

    /** ARGB_8888 bitmap → row-major RGBA bytes. */
    fun toRgba(bitmap: Bitmap): Triple<ByteArray, Int, Int> {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val rgba = ByteArray(width * height * 4)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val dst = i * 4
            rgba[dst] = ((pixel shr 16) and 0xFF).toByte()
            rgba[dst + 1] = ((pixel shr 8) and 0xFF).toByte()
            rgba[dst + 2] = (pixel and 0xFF).toByte()
            rgba[dst + 3] = ((pixel shr 24) and 0xFF).toByte()
        }
        return Triple(rgba, width, height)
    }

    /** Row-major RGBA bytes → ARGB_8888 bitmap. */
    fun fromRgba(rgba: ByteArray, width: Int, height: Int): Bitmap {
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val src = i * 4
            val alpha = rgba[src + 3].toInt() and 0xFF
            val red = rgba[src].toInt() and 0xFF
            val green = rgba[src + 1].toInt() and 0xFF
            val blue = rgba[src + 2].toInt() and 0xFF
            pixels[i] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /** Tight bounding box of true cells in a row-major boolean map; null when empty. */
    fun boundingBox(mask: BooleanArray, width: Int, height: Int): IntArray? {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (mask[y * width + x]) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        return if (right < 0) null else intArrayOf(left, top, right, bottom)
    }
}
