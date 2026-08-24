package com.zakir.vestra.shared.quality

import com.zakir.vestra.shared.cloud.CloudModelContracts
import com.zakir.vestra.shared.cloud.CloudModelProvider
import com.zakir.vestra.shared.cloud.ModelSupportLevel

/**
 * Maps internal [CloudModelProvider.qualityScore] (0–100) to a 1–5 star label.
 * "5-star" in the quality plan means: READY support + score ≥ 90, or local Lite/Pro
 * with optional quality packs (Real-ESRGAN / BiRefNet) applied post-generation.
 */
object QualityRating {
    /** Minimum catalog score treated as five-star when the model contract is READY. */
    const val FIVE_STAR_MIN_SCORE = 90

    fun starsFromScore(qualityScore: Int): Int =
        when {
            qualityScore >= 90 -> 5
            qualityScore >= 75 -> 4
            qualityScore >= 60 -> 3
            qualityScore >= 45 -> 2
            else -> 1
        }

    fun label(provider: CloudModelProvider): String {
        val contract = CloudModelContracts.forProvider(provider)
        val stars = starsFromScore(provider.qualityScore)
        val support = when (contract.support) {
            ModelSupportLevel.READY -> null
            ModelSupportLevel.DEGRADED -> "degraded"
            ModelSupportLevel.UNSUPPORTED -> "unsupported"
        }
        return buildString {
            append(stars)
            append('★')
            if (support != null) append(" · $support")
        }
    }

    fun isFiveStar(provider: CloudModelProvider): Boolean =
        CloudModelContracts.forProvider(provider).support == ModelSupportLevel.READY &&
            provider.qualityScore >= FIVE_STAR_MIN_SCORE
}
