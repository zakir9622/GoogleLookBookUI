package com.zakir.vestra.shared.engine.pipeline

import com.zakir.vestra.shared.domain.CastingProfile
import com.zakir.vestra.shared.domain.GarmentCategory
import com.zakir.vestra.shared.domain.GarmentColor

/**
 * Maps casting parameters + garment category into prompt strings for the Pro
 * text encoder. IP-Adapter carries garment appearance; these tokens steer body,
 * scene, and modest-wear context.
 */
object CastingPromptBuilder {

    fun buildPositive(profile: CastingProfile, category: GarmentCategory?): String {
        val garment = garmentPhrase(category, profile.garmentColor)
        val parts = listOfNotNull(
            profile.ethnicity.promptTag,
            profile.bodyType.promptTag,
            profile.skinTone.promptTag,
            garment,
            profile.hairCoverage.promptTag,
            profile.scenario.promptTag,
        )
        return PromptStyle.wrapPositive(parts.joinToString(", "))
    }

    fun buildNegative(): String = PromptStyle.negative()

    private fun garmentPhrase(category: GarmentCategory?, color: GarmentColor?): String {
        val colorTag = color?.promptTag?.let { "$it " }.orEmpty()
        return when (category) {
            GarmentCategory.ABAYA -> "wearing ${colorTag}abaya with natural drape"
            GarmentCategory.JILBAB -> "wearing ${colorTag}jilbab with natural drape"
            GarmentCategory.KAFTAN -> "wearing ${colorTag}kaftan with natural drape"
            GarmentCategory.HIJAB -> "wearing ${colorTag}hijab with elegant fabric"
            GarmentCategory.NIQAB -> "wearing ${colorTag}niqab with modest coverage"
            GarmentCategory.DUPATTA -> "wearing ${colorTag}dupatta draped gracefully"
            GarmentCategory.SHALWAR_KAMEEZ -> "wearing ${colorTag}Pakistani shalwar kameez"
            GarmentCategory.KURTA -> "wearing ${colorTag}Pakistani kurta"
            GarmentCategory.LEHENGA -> "wearing ${colorTag}lehenga"
            GarmentCategory.FULL_COVERAGE -> "wearing ${colorTag}modest full coverage garment"
            GarmentCategory.HEADSCARF -> "wearing ${colorTag}headscarf"
            GarmentCategory.DRESS -> "wearing ${colorTag}dress"
            GarmentCategory.UPPER_BODY -> "wearing ${colorTag}top"
            GarmentCategory.LOWER_BODY -> "wearing ${colorTag}trousers"
            null -> "wearing ${colorTag}modest traditional garment"
        }
    }
}
