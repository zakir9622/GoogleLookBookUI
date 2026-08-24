package com.zakir.vestra.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.zakir.vestra.shared.domain.PackKind
import com.zakir.vestra.shared.packs.ModelPackManager
import java.io.File

/**
 * A base model in the casting gallery. [coilModel] is what Coil renders — a
 * [File] for pack-supplied photos, or a drawable resource Int for the bundled
 * silhouette fallback.
 */
data class StudioModel(
    val id: String,
    val displayName: String,
    val coilModel: Any,
    val tags: List<String> = emptyList(),
)

/**
 * Serves the casting gallery. When a MODELS pack is installed (photorealistic
 * base models the owner supplies — see ml/build_models_pack.py) its models are
 * used; otherwise the four bundled vector silhouettes stand in so the app is
 * fully usable before any pack is downloaded.
 */
class StudioModelRepository(
    private val context: Context,
    private val packs: ModelPackManager,
) {
    fun models(): List<StudioModel> {
        installedModelsPack()?.let { (dir, metas) ->
            return metas.map { meta ->
                StudioModel(
                    id = meta.id,
                    displayName = meta.displayName,
                    coilModel = File("$dir/${meta.file}"),
                    tags = meta.tags,
                )
            }
        }
        return AiModelCatalog.models.map {
            StudioModel(id = it.id, displayName = it.displayName, coilModel = it.image)
        }
    }

    /** Rasterized base image for the engines (pack photo, or the silhouette drawable). */
    fun resolveBitmap(modelId: String): Bitmap? {
        installedModelsPack()?.let { (dir, metas) ->
            metas.firstOrNull { it.id == modelId }?.let { meta ->
                return BitmapFactory.decodeFile("$dir/${meta.file}")
            }
        }
        val model = AiModelCatalog.byId(modelId) ?: AiModelCatalog.models.firstOrNull() ?: return null
        val drawable = ContextCompat.getDrawable(context, model.image) ?: return null
        val bitmap = createBitmap(900, 1200)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun installedModelsPack(): Pair<String, List<com.zakir.vestra.shared.domain.StudioModelMeta>>? {
        val state = packs.states.value.values.firstOrNull {
            it.pack.kind == PackKind.MODELS && packs.isInstalled(it.pack.id)
        } ?: return null
        val dir = packs.installedDir(state.pack.id) ?: return null
        return dir to state.pack.studioModels
    }
}
