package com.zakir.vestra.shared.engine.pipeline

import com.zakir.vestra.shared.domain.BodyType
import com.zakir.vestra.shared.domain.CastingProfile
import com.zakir.vestra.shared.domain.Ethnicity
import com.zakir.vestra.shared.domain.GarmentCategory
import com.zakir.vestra.shared.domain.GarmentColor
import com.zakir.vestra.shared.domain.HairCoverage
import com.zakir.vestra.shared.domain.Scenario
import com.zakir.vestra.shared.domain.SkinTone
import kotlin.test.Test
import kotlin.test.assertTrue

class CastingPromptBuilderTest {

    @Test
    fun buildsModestWearPromptWithCastingParams() {
        val profile = CastingProfile(
            ethnicity = Ethnicity.SOUTH_ASIAN,
            skinTone = SkinTone.MEDIUM,
            bodyType = BodyType.CURVY,
            hairCoverage = HairCoverage.HIJAB,
            garmentColor = GarmentColor.EMERALD,
            scenario = Scenario.BAZAAR,
        )
        val prompt = CastingPromptBuilder.buildPositive(profile, GarmentCategory.ABAYA)
        assertTrue(prompt.contains("Pakistani woman"))
        assertTrue(prompt.contains("curvy"))
        assertTrue(prompt.contains("emerald green"))
        assertTrue(prompt.contains("abaya"))
        assertTrue(prompt.contains("bazaar"))
    }

    @Test
    fun galleryTagsIncludeEthnicityAndCoverage() {
        val tags = CastingProfile(
            hairCoverage = HairCoverage.NIQAB,
            ethnicity = Ethnicity.ARAB,
        ).galleryTags()
        assertTrue("arab" in tags)
        assertTrue("hijab" in tags)
    }
}
