package com.zakir.vestra.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * Manual 90° rotation for shop photos without usable EXIF orientation.
 * Decodes with [inSampleSize] so camera JPEGs cannot OOM the UI thread path.
 */
object ImageRotator {

    private const val MAX_DIMENSION = 2048

    fun rotate90(context: Context, uri: String): String? = runCatching {
        val parsed = Uri.parse(uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_DIMENSION)
        val decode = BitmapFactory.Options().apply { inSampleSize = sample }
        val source = context.contentResolver.openInputStream(parsed)?.use {
            BitmapFactory.decodeStream(it, null, decode)
        } ?: return null
        val matrix = Matrix().apply { postRotate(90f) }
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (rotated !== source) source.recycle()

        val dir = File(context.filesDir, "captures").apply { mkdirs() }
        val file = File(dir, "rotated_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            rotated.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        rotated.recycle()
        "file://${file.absolutePath}"
    }.getOrNull()

    private fun sampleSizeFor(width: Int, height: Int, maxDim: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        var w = width
        var h = height
        while (max(w, h) / sample > maxDim) sample *= 2
        return sample.coerceAtLeast(1)
    }
}
