package com.zakir.vestra.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import com.zakir.vestra.R
import com.zakir.vestra.shared.domain.GarmentCategory
import java.io.File
import java.io.FileOutputStream

data class SampleGarment(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: GarmentCategory,
    val drawableRes: Int,
    val promptHint: String,
)

object SampleGarmentCatalog {
    val items: List<SampleGarment> = listOf(
        SampleGarment(
            id = "abaya_emerald",
            title = "Emerald Silk Abaya",
            subtitle = "Gold geometric front embroidery",
            category = GarmentCategory.ABAYA,
            drawableRes = R.drawable.ic_garment_abaya,
            promptHint = "Emerald green silk crepe abaya with intricate gold thread embroidery along the front placket and cuffs",
        ),
        SampleGarment(
            id = "kaftan_royal",
            title = "Royal Blue Kaftan",
            subtitle = "Moroccan gold sfifa braid",
            category = GarmentCategory.KAFTAN,
            drawableRes = R.drawable.ic_garment_kaftan,
            promptHint = "Royal sapphire blue flowing Moroccan kaftan with golden sfifa braiding and handcrafted buttons",
        ),
        SampleGarment(
            id = "kurta_indigo",
            title = "Indigo Kurta",
            subtitle = "South Asian handloom embroidery",
            category = GarmentCategory.SHALWAR_KAMEEZ,
            drawableRes = R.drawable.ic_garment_kurta,
            promptHint = "Midnight indigo linen kurta with delicate silver thread work on mandarin collar and chest",
        ),
        SampleGarment(
            id = "hijab_chiffon",
            title = "Rose Chiffon Hijab",
            subtitle = "Lightweight modest drape",
            category = GarmentCategory.HIJAB,
            drawableRes = R.drawable.ic_garment_hijab,
            promptHint = "Soft dusty rose breathable chiffon hijab gracefully pinned with soft natural folds",
        ),
        SampleGarment(
            id = "dress_couture",
            title = "Ruby Evening Dress",
            subtitle = "Cinematic high-waist drape",
            category = GarmentCategory.DRESS,
            drawableRes = R.drawable.ic_garment_dress,
            promptHint = "Deep ruby red evening gown with structured shoulders and floor-length flowing drape",
        ),
    )

    fun resolveUri(context: Context, garment: SampleGarment): String {
        val cacheFile = File(context.cacheDir, "sample_${garment.id}.png")
        if (!cacheFile.exists()) {
            val drawable = ContextCompat.getDrawable(context, garment.drawableRes)
                ?: return "android.resource://${context.packageName}/${garment.drawableRes}"
            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 480
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 640
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            runCatching {
                FileOutputStream(cacheFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
        }
        return if (cacheFile.exists()) cacheFile.absolutePath else "android.resource://${context.packageName}/${garment.drawableRes}"
    }
}
