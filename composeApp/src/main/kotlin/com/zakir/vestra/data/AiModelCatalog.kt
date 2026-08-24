package com.zakir.vestra.data

import androidx.annotation.DrawableRes
import com.zakir.vestra.R

/**
 * The base-model gallery used by "AI model" mode. v1 ships stylized silhouettes
 * bundled in the APK; M3 replaces them with a downloadable pack of
 * photorealistic synthetic base models (diverse poses, body types, skin tones)
 * — the ids below stay stable so wardrobe history survives the swap.
 */
data class AiModel(
    val id: String,
    val displayName: String,
    @DrawableRes val image: Int,
)

object AiModelCatalog {
    val models: List<AiModel> = listOf(
        AiModel("base-standing-01", "Standing · slim", R.drawable.model_standing_slim),
        AiModel("base-standing-02", "Standing · curvy", R.drawable.model_standing_curvy),
        AiModel("base-walking-01", "Walking", R.drawable.model_walking),
        AiModel("base-seated-01", "Seated", R.drawable.model_seated),
    )

    fun byId(id: String): AiModel? = models.firstOrNull { it.id == id }
}
