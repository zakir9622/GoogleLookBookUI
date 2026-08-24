package com.zakir.vestra.shared.engine.lite

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import com.zakir.vestra.shared.domain.Backdrop

/**
 * Replaces the scene behind the model with a generated studio backdrop. Reuses
 * the Lite pack's salient-object segmenter (`garment_seg.onnx` — a general
 * foreground model) to cut the subject out, then paints the chosen backdrop and
 * feathers the subject back over it. Shared by every on-device engine.
 */
class BackdropCompositor(private val segModelPath: String) {

    /** Returns a new bitmap with [backdrop] behind the subject; source is untouched for ORIGINAL. */
    fun apply(source: Bitmap, backdrop: Backdrop): Bitmap {
        if (backdrop == Backdrop.ORIGINAL) return source

        val model = OrtSessionCache.open(segModelPath)
        val mask = run {
            val (h, w) = model.inputSize(defaultSize = 320)
            val (output, shape) = model.run(ImageOps.toNormalizedChw(source, h, w), h, w)
            val maskH = shape.getOrNull(2)?.toInt() ?: h
            val maskW = shape.getOrNull(3)?.toInt() ?: w
            val plane = output.copyOfRange(0, maskH * maskW)
            ImageOps.normalizeAndResizeMask(plane, maskW, maskH, source.width, source.height)
        }

        val out = createBitmap(source.width, source.height)
        val canvas = Canvas(out)
        drawBackdrop(canvas, backdrop, source.width, source.height)

        // Subject over backdrop, using the segmentation mask as alpha with a
        // soft edge so hair/fabric edges don't cut hard.
        val subject = source.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(source.width * source.height)
        subject.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        for (i in pixels.indices) {
            val m = mask[i].coerceIn(0f, 1f)
            val soft = if (m < 0.5f) m * m * 2f else m
            val alpha = (soft * 255).toInt().coerceIn(0, 255)
            pixels[i] = (alpha shl 24) or (pixels[i] and 0x00FFFFFF)
        }
        val subjectLayer = Bitmap.createBitmap(pixels, source.width, source.height, Bitmap.Config.ARGB_8888)
        canvas.drawBitmap(subjectLayer, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    private fun drawBackdrop(canvas: Canvas, backdrop: Backdrop, width: Int, height: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val w = width.toFloat()
        val h = height.toFloat()
        when (backdrop) {
            Backdrop.ORIGINAL -> Unit
            Backdrop.STUDIO_WHITE -> {
                // Seamless sweep: bright at top, subtle floor shadow at the base.
                paint.shader = LinearGradient(
                    0f, 0f, 0f, h,
                    intArrayOf(0xFFF7F4EF.toInt(), 0xFFFFFFFF.toInt(), 0xFFE7E2D9.toInt()),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP,
                )
                canvas.drawRect(0f, 0f, w, h, paint)
            }
            Backdrop.EDITORIAL -> {
                paint.shader = RadialGradient(
                    w * 0.5f, h * 0.4f, maxOf(w, h) * 0.8f,
                    intArrayOf(0xFF2A2A32.toInt(), 0xFF141418.toInt(), 0xFF0A0A0C.toInt()),
                    floatArrayOf(0f, 0.6f, 1f),
                    Shader.TileMode.CLAMP,
                )
                canvas.drawRect(0f, 0f, w, h, paint)
            }
            Backdrop.GOLDEN_HOUR -> {
                paint.shader = LinearGradient(
                    0f, 0f, w, h,
                    intArrayOf(0xFFF6D9A8.toInt(), 0xFFE0B074.toInt(), 0xFF9C6B3C.toInt()),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP,
                )
                canvas.drawRect(0f, 0f, w, h, paint)
            }
            Backdrop.RUNWAY -> {
                canvas.drawColor(0xFF101014.toInt())
                paint.shader = RadialGradient(
                    w * 0.5f, h * 0.28f, h * 0.7f,
                    intArrayOf(0x66FFFFFF, 0x11FFFFFF, 0x00000000),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP,
                )
                canvas.drawRect(0f, 0f, w, h, paint)
            }
            Backdrop.MONOCHROME -> {
                paint.shader = LinearGradient(
                    0f, 0f, 0f, h,
                    intArrayOf(0xFF9A9A9E.toInt(), 0xFF7C7C80.toInt()),
                    null,
                    Shader.TileMode.CLAMP,
                )
                canvas.drawRect(0f, 0f, w, h, paint)
            }
        }
    }
}
