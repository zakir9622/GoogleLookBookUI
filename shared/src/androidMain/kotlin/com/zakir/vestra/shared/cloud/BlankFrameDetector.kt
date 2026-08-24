package com.zakir.vestra.shared.cloud

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.abs

/**
 * Rejects near-solid / blank cloud images so the fallback chain can advance.
 * Runs after [CloudOutputValidator] header checks (Android Bitmap path).
 */
object BlankFrameDetector {
    /** Max mean absolute deviation from mean luminance for a "blank" frame. */
    private const val MAX_MAD = 2.5f
    private const val SAMPLE_STRIDE = 8

    /** @return rejection reason, or null when the image looks textured enough. */
    fun rejectIfBlank(bytes: ByteArray): String? {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return try {
            rejectIfBlank(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    fun rejectIfBlank(bitmap: Bitmap): String? {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 8 || h < 8) return "Image too small to validate"
        var sum = 0.0
        var count = 0
        val samples = ArrayList<Float>((w / SAMPLE_STRIDE) * (h / SAMPLE_STRIDE) + 4)
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val p = bitmap.getPixel(x, y)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
                samples.add(lum)
                sum += lum
                count++
                x += SAMPLE_STRIDE
            }
            y += SAMPLE_STRIDE
        }
        if (count < 4) return null
        val mean = (sum / count).toFloat()
        var madSum = 0.0
        for (v in samples) {
            madSum += abs(v - mean)
        }
        val mad = (madSum / count).toFloat()
        return if (mad < MAX_MAD) {
            "Downloaded image looks blank (low variance)"
        } else {
            null
        }
    }
}
