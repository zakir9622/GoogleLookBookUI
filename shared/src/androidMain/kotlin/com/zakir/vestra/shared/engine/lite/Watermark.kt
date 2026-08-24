package com.zakir.vestra.shared.engine.lite

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Visible AI-content label. Google Play's AI-generated-content policy expects
 * generated images to be identifiable; the EXIF tag is added at save time by
 * LiteEngineIo (visible + metadata marking together).
 */
object Watermark {

    const val EXIF_TAG_VALUE = "AI-generated with Vestra"

    fun apply(image: Bitmap): Bitmap {
        val out = image.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val textSize = (out.width * 0.028f).coerceAtLeast(18f)
        val padding = textSize * 0.8f

        val text = "✦ AI generated"
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 244, 239, 230)
            this.textSize = textSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val textWidth = textPaint.measureText(text)

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(110, 10, 10, 12)
        }
        val right = out.width - padding
        val bottom = out.height - padding
        canvas.drawRoundRect(
            right - textWidth - padding,
            bottom - textSize - padding * 0.6f,
            right + padding * 0.4f,
            bottom + padding * 0.4f,
            textSize * 0.5f,
            textSize * 0.5f,
            backgroundPaint,
        )
        canvas.drawText(text, right - textWidth, bottom, textPaint)
        return out
    }
}
