package com.zakir.vestra.shared.engine.lite

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/**
 * Composites the warped garment onto the person and matches its luminance to
 * the scene so the insert doesn't look pasted: gain/offset derived from the
 * person's statistics inside the target region, plus a feathered edge.
 */
object Harmonizer {

    fun compose(person: Bitmap, fittedGarment: Bitmap, region: TargetRegion): Bitmap {
        val width = person.width
        val height = person.height
        val personPixels = IntArray(width * height)
        person.getPixels(personPixels, 0, width, 0, 0, width, height)
        val garmentPixels = IntArray(width * height)
        fittedGarment.getPixels(garmentPixels, 0, width, 0, 0, width, height)

        // Person luminance stats inside the region (the light the garment must live in).
        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        for (idx in personPixels.indices) {
            if (region.mask[idx] > 0.5f) {
                val luma = luma(personPixels[idx])
                sum += luma
                sumSq += luma * luma
                count++
            }
        }
        // Garment luminance stats over its opaque pixels.
        var gSum = 0.0
        var gSumSq = 0.0
        var gCount = 0
        for (pixel in garmentPixels) {
            if ((pixel ushr 24) > 40) {
                val luma = luma(pixel)
                gSum += luma
                gSumSq += luma * luma
                gCount++
            }
        }
        if (count == 0 || gCount == 0) return overlayOnly(person, fittedGarment)

        val personMean = sum / count
        val personStd = kotlin.math.sqrt((sumSq / count - personMean * personMean).coerceAtLeast(1.0))
        val garmentMean = gSum / gCount
        val garmentStd = kotlin.math.sqrt((gSumSq / gCount - garmentMean * garmentMean).coerceAtLeast(1.0))
        // Damped transfer: full stat matching over-corrects on busy scenes.
        val gain = (1.0 + (personStd / garmentStd - 1.0) * 0.5).coerceIn(0.6, 1.6)
        val offset = (personMean - garmentMean * gain) * 0.6

        // Low-frequency shading transfer: the person's blurred luminance inside
        // the region carries the scene's shadow/highlight structure (arm shadows,
        // window light). Modulating the garment by it makes the fabric sit "in"
        // the photo instead of on top of it — the poor man's Poisson blend.
        val shading = blurredLuma(personPixels, width, height)
        val flatShade = shading.averageInMask(region.mask)

        val out = IntArray(width * height)
        for (idx in personPixels.indices) {
            val garment = garmentPixels[idx]
            val alphaInt = garment ushr 24
            if (alphaInt <= 8) {
                out[idx] = personPixels[idx]
                continue
            }
            val shade = if (flatShade > 1.0) (shading[idx] / flatShade).coerceIn(0.55, 1.35) else 1.0
            val red = adjust((garment shr 16 and 0xFF), gain * shade, offset)
            val green = adjust((garment shr 8 and 0xFF), gain * shade, offset)
            val blue = adjust((garment and 0xFF), gain * shade, offset)
            // Feather: soften low-alpha edges further for a cleaner seam.
            val alpha = (alphaInt / 255f).let { if (it < 0.35f) it * it / 0.35f else it }
            val base = personPixels[idx]
            out[idx] = 0xFF shl 24 or
                (blend(base shr 16 and 0xFF, red, alpha) shl 16) or
                (blend(base shr 8 and 0xFF, green, alpha) shl 8) or
                blend(base and 0xFF, blue, alpha)
        }
        return Bitmap.createBitmap(out, width, height, Bitmap.Config.ARGB_8888)
    }

    /** Heavily downsampled box-blurred luminance, upsampled back — cheap low-pass. */
    private fun blurredLuma(pixels: IntArray, width: Int, height: Int): DoubleArray {
        val smallW = 48
        val smallH = (smallW.toLong() * height / width).toInt().coerceAtLeast(1)
        val small = DoubleArray(smallW * smallH)
        val counts = IntArray(smallW * smallH)
        for (y in 0 until height) {
            val sy = y * smallH / height
            for (x in 0 until width) {
                val si = sy * smallW + x * smallW / width
                small[si] += luma(pixels[y * width + x])
                counts[si]++
            }
        }
        for (i in small.indices) if (counts[i] > 0) small[i] /= counts[i]
        // One 3x3 box pass over the small map is blur enough at this scale.
        val blurred = DoubleArray(small.size)
        for (y in 0 until smallH) {
            for (x in 0 until smallW) {
                var sum = 0.0
                var n = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val yy = y + dy
                        val xx = x + dx
                        if (yy in 0 until smallH && xx in 0 until smallW) {
                            sum += small[yy * smallW + xx]
                            n++
                        }
                    }
                }
                blurred[y * smallW + x] = sum / n
            }
        }
        val out = DoubleArray(width * height)
        for (y in 0 until height) {
            val sy = y * smallH / height
            for (x in 0 until width) {
                out[y * width + x] = blurred[sy * smallW + x * smallW / width]
            }
        }
        return out
    }

    private fun DoubleArray.averageInMask(mask: FloatArray): Double {
        var sum = 0.0
        var count = 0
        for (i in indices) {
            if (mask[i] > 0.5f) {
                sum += this[i]
                count++
            }
        }
        return if (count == 0) 0.0 else sum / count
    }

    private fun overlayOnly(person: Bitmap, garment: Bitmap): Bitmap {
        val out = person.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(out).drawBitmap(garment, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun luma(pixel: Int): Double =
        0.299 * (pixel shr 16 and 0xFF) + 0.587 * (pixel shr 8 and 0xFF) + 0.114 * (pixel and 0xFF)

    private fun adjust(channel: Int, gain: Double, offset: Double): Int =
        (channel * gain + offset).toInt().coerceIn(0, 255)

    private fun blend(base: Int, over: Int, alpha: Float): Int =
        (base * (1 - alpha) + over * alpha).toInt().coerceIn(0, 255)
}
