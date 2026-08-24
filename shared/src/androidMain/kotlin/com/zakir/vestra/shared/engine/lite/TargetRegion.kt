package com.zakir.vestra.shared.engine.lite

import android.graphics.Bitmap

/**
 * The body area the garment lands on, expressed in person-image pixels:
 * a bounding box plus, per row, the left/right extent of the region — the
 * "row profile" the warp follows so the garment hugs the body contour.
 */
class TargetRegion(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    /** Per output row (top..bottom): left and right x of the region. */
    val rowBounds: IntArray,
    /** Soft 0..1 mask over the whole person image, row-major. */
    val mask: FloatArray,
    val imageWidth: Int,
    val imageHeight: Int,
) {
    val width: Int get() = right - left + 1
    val height: Int get() = bottom - top + 1

    companion object {
        /**
         * Builds a region from a parsing mask in model space, scaled to the
         * person bitmap's dimensions.
         */
        fun fromMask(
            maskCells: BooleanArray,
            maskWidth: Int,
            maskHeight: Int,
            box: IntArray,
            person: Bitmap,
        ): TargetRegion {
            val scaleX = person.width.toFloat() / maskWidth
            val scaleY = person.height.toFloat() / maskHeight
            val left = (box[0] * scaleX).toInt().coerceIn(0, person.width - 1)
            val top = (box[1] * scaleY).toInt().coerceIn(0, person.height - 1)
            val right = (box[2] * scaleX).toInt().coerceIn(left, person.width - 1)
            val bottom = (box[3] * scaleY).toInt().coerceIn(top, person.height - 1)

            val rows = bottom - top + 1
            val rowBounds = IntArray(rows * 2)
            for (row in 0 until rows) {
                val maskY = ((top + row) / scaleY).toInt().coerceIn(0, maskHeight - 1)
                var first = -1
                var last = -1
                for (x in box[0]..box[2]) {
                    if (maskCells[maskY * maskWidth + x]) {
                        if (first < 0) first = x
                        last = x
                    }
                }
                if (first < 0) {
                    // Empty row inside the box — reuse the box extent so the
                    // profile stays continuous.
                    rowBounds[row * 2] = left
                    rowBounds[row * 2 + 1] = right
                } else {
                    rowBounds[row * 2] = (first * scaleX).toInt().coerceIn(0, person.width - 1)
                    rowBounds[row * 2 + 1] = (last * scaleX).toInt().coerceIn(0, person.width - 1)
                }
            }

            // Upscale the boolean cells to a soft image-space mask.
            val soft = FloatArray(person.width * person.height)
            for (y in 0 until person.height) {
                val maskY = (y / scaleY).toInt().coerceIn(0, maskHeight - 1)
                for (x in 0 until person.width) {
                    val maskX = (x / scaleX).toInt().coerceIn(0, maskWidth - 1)
                    soft[y * person.width + x] = if (maskCells[maskY * maskWidth + maskX]) 1f else 0f
                }
            }
            return TargetRegion(
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                rowBounds = rowBounds,
                mask = soft,
                imageWidth = person.width,
                imageHeight = person.height,
            )
        }
    }
}
