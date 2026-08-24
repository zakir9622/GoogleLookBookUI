package com.zakir.vestra.shared.engine.lite

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap

/**
 * Meshes the garment cutout onto the target region so it follows the body's
 * row profile instead of landing as a rigid rectangle. drawBitmapMesh gives a
 * smooth piecewise-affine warp with no native dependencies.
 */
object ContourWarp {

    private const val MESH_COLS = 12
    private const val MESH_ROWS = 24

    /** Returns a person-image-sized ARGB bitmap containing only the warped garment. */
    fun fit(garment: Bitmap, region: TargetRegion): Bitmap {
        val out = createBitmap(region.imageWidth, region.imageHeight)
        val canvas = Canvas(out)

        // Mesh vertices in destination space: for each mesh row, interpolate the
        // region's row bounds; columns spread linearly between them with a small
        // margin so sleeves/hems can overhang the silhouette naturally.
        val verts = FloatArray((MESH_COLS + 1) * (MESH_ROWS + 1) * 2)
        var i = 0
        for (row in 0..MESH_ROWS) {
            val yFraction = row.toFloat() / MESH_ROWS
            val y = region.top + yFraction * (region.height - 1)
            val profileRow = (yFraction * (region.height - 1)).toInt().coerceIn(0, region.height - 1)
            val rowLeft = region.rowBounds[profileRow * 2].toFloat()
            val rowRight = region.rowBounds[profileRow * 2 + 1].toFloat()
            val overhang = (rowRight - rowLeft) * 0.06f
            for (col in 0..MESH_COLS) {
                val xFraction = col.toFloat() / MESH_COLS
                verts[i++] = rowLeft - overhang + xFraction * (rowRight - rowLeft + 2 * overhang)
                verts[i++] = y
            }
        }

        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmapMesh(garment, MESH_COLS, MESH_ROWS, verts, 0, null, 0, paint)
        return out
    }
}
