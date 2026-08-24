package com.zakir.vestra.shared.domain

/** One-tap casting presets for common modest-wear scenarios. */
object CastingPresets {
    val pakistaniBazaar = CastingProfile(
        ethnicity = Ethnicity.SOUTH_ASIAN,
        skinTone = SkinTone.MEDIUM,
        bodyType = BodyType.AVERAGE,
        hairCoverage = HairCoverage.HIJAB,
        garmentColor = GarmentColor.EMERALD,
        scenario = Scenario.BAZAAR,
    )

    val studioAbaya = CastingProfile(
        ethnicity = Ethnicity.ARAB,
        skinTone = SkinTone.OLIVE,
        bodyType = BodyType.SLIM,
        hairCoverage = HairCoverage.HIJAB,
        garmentColor = GarmentColor.BLACK,
        scenario = Scenario.STUDIO_WHITE,
    )

    val gulfElegant = CastingProfile(
        ethnicity = Ethnicity.ARAB,
        skinTone = SkinTone.FAIR,
        bodyType = BodyType.CURVY,
        hairCoverage = HairCoverage.NIQAB,
        garmentColor = GarmentColor.NAVY,
        scenario = Scenario.HOME_INTERIOR,
    )

    val goldenHourDupatta = CastingProfile(
        ethnicity = Ethnicity.SOUTH_ASIAN,
        skinTone = SkinTone.BROWN,
        bodyType = BodyType.CURVY,
        hairCoverage = HairCoverage.HIJAB,
        garmentColor = GarmentColor.GOLD,
        scenario = Scenario.OUTDOOR_GOLDEN_HOUR,
    )

    val all = listOf(
        "Pakistani bazaar" to pakistaniBazaar,
        "Studio abaya" to studioAbaya,
        "Gulf elegant" to gulfElegant,
        "Golden hour" to goldenHourDupatta,
    )
}
