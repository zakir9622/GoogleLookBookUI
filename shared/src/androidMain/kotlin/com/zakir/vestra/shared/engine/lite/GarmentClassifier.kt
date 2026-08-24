package com.zakir.vestra.shared.engine.lite

import android.graphics.Bitmap
import com.zakir.vestra.shared.domain.GarmentCategory
import com.zakir.vestra.shared.engine.atr.AtrTaxonomy

/**
 * Garment categorization for Auto mode.
 *
 * Prefer [classifyFromAtr] when a person parse map is available (generate-time).
 * [classify] uses cutout geometry only — safe at garment-pick time and when
 * `human_parse.onnx` is missing.
 */
object GarmentClassifier {

    fun classifyFromAtr(classMap: IntArray): GarmentCategory =
        AtrTaxonomy.classify(classMap)

    fun classify(garmentCutout: Bitmap): GarmentCategory {
        val width = garmentCutout.width
        val height = garmentCutout.height
        val pixels = IntArray(width * height)
        garmentCutout.getPixels(pixels, 0, width, 0, 0, width, height)

        var opaque = 0
        // Row occupancy tells apart "legs" shapes (two columns) from solid bodies.
        var splitRows = 0
        var measuredRows = 0
        for (y in 0 until height step (height / 64).coerceAtLeast(1)) {
            var runs = 0
            var inRun = false
            var rowOpaque = 0
            for (x in 0 until width) {
                val isOpaque = (pixels[y * width + x] ushr 24) > 40
                if (isOpaque) {
                    rowOpaque++
                    if (!inRun) {
                        runs++
                        inRun = true
                    }
                } else {
                    inRun = false
                }
            }
            if (rowOpaque > width / 20) {
                measuredRows++
                if (runs >= 2) splitRows++
            }
        }
        for (pixel in pixels) if ((pixel ushr 24) > 40) opaque++

        val aspect = height.toFloat() / width.coerceAtLeast(1)
        val fill = opaque.toFloat() / (width * height)
        val splitFraction = if (measuredRows == 0) 0f else splitRows.toFloat() / measuredRows

        return when {
            // Thin / hollow — scarves and drapes.
            fill < 0.22f && aspect < 1.25f -> GarmentCategory.DUPATTA
            fill < 0.30f && aspect < 1.45f -> GarmentCategory.HIJAB
            // Two-column lower body.
            splitFraction > 0.45f && aspect > 1.05f -> GarmentCategory.LOWER_BODY
            // Tall solid columns — modest outerwear family.
            aspect > 2.05f && fill > 0.48f -> GarmentCategory.ABAYA
            aspect > 1.85f && fill > 0.42f -> GarmentCategory.JILBAB
            aspect > 1.65f && fill > 0.38f -> GarmentCategory.KAFTAN
            // Split torso + legs → shalwar set.
            aspect > 1.25f && splitFraction > 0.22f && fill > 0.32f ->
                GarmentCategory.SHALWAR_KAMEEZ
            // Long one-piece.
            aspect > 1.35f && fill > 0.35f && splitFraction < 0.18f -> GarmentCategory.DRESS
            // Short / wide torso piece.
            aspect < 1.15f && fill > 0.34f -> GarmentCategory.UPPER_BODY
            aspect > 1.20f -> GarmentCategory.KURTA
            else -> GarmentCategory.KURTA
        }
    }
}
