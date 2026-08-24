package com.zakir.vestra.shared.domain

import kotlinx.serialization.Serializable

@Serializable
enum class Ethnicity(val displayName: String, val promptTag: String) {
    SOUTH_ASIAN("South Asian", "Pakistani woman"),
    ARAB("Arab", "Arab woman"),
    AFRICAN("African", "African woman"),
    EAST_ASIAN("East Asian", "East Asian woman"),
    EUROPEAN("European", "European woman"),
    MIXED("Mixed", "mixed ethnicity woman"),
}

@Serializable
enum class SkinTone(val displayName: String, val promptTag: String) {
    FAIR("Fair", "fair skin"),
    MEDIUM("Medium", "medium brown skin"),
    OLIVE("Olive", "olive skin"),
    BROWN("Brown", "brown skin"),
    DEEP("Deep", "deep brown skin"),
}

@Serializable
enum class BodyType(val displayName: String, val promptTag: String) {
    SLIM("Slim", "slim body type"),
    AVERAGE("Average", "average body type"),
    CURVY("Curvy", "curvy body type"),
    PLUS("Plus", "plus size body type"),
}

@Serializable
enum class HairCoverage(val displayName: String, val promptTag: String) {
    HIJAB("Hijab", "hijab covering hair"),
    NIQAB("Niqab", "niqab covering face and hair"),
    OPEN_HAIR("Open hair", "hair visible"),
    PARTIAL("Partial cover", "partially covered hair with scarf"),
}

@Serializable
enum class GarmentColor(val displayName: String, val promptTag: String) {
    BLACK("Black", "black"),
    WHITE("White", "white"),
    NAVY("Navy", "navy blue"),
    EMERALD("Emerald", "emerald green"),
    BURGUNDY("Burgundy", "burgundy"),
    GOLD("Gold", "gold"),
    BEIGE("Beige", "beige"),
    PASTEL("Pastel", "soft pastel"),
}

@Serializable
enum class Scenario(val displayName: String, val promptTag: String) {
    STUDIO_WHITE("Studio white", "clean white studio backdrop"),
    MOSQUE_COURTYARD("Mosque courtyard", "traditional mosque courtyard background"),
    BAZAAR("Bazaar", "busy traditional bazaar background"),
    HOME_INTERIOR("Home interior", "elegant home interior background"),
    OUTDOOR_GOLDEN_HOUR("Golden hour", "outdoor golden hour lighting"),
}

/** Casting parameters that steer on-device text conditioning and model gallery filtering. */
@Serializable
data class CastingProfile(
    val ethnicity: Ethnicity = Ethnicity.SOUTH_ASIAN,
    val skinTone: SkinTone = SkinTone.MEDIUM,
    val bodyType: BodyType = BodyType.AVERAGE,
    val hairCoverage: HairCoverage = HairCoverage.HIJAB,
    val garmentColor: GarmentColor? = null,
    val scenario: Scenario = Scenario.STUDIO_WHITE,
) {
    /** Tags used to filter the studio-model gallery. */
    fun galleryTags(): Set<String> = buildSet {
        add(ethnicity.name.lowercase())
        add(bodyType.name.lowercase())
        add(skinTone.name.lowercase())
        when (hairCoverage) {
            HairCoverage.HIJAB, HairCoverage.NIQAB, HairCoverage.PARTIAL -> add("hijab")
            HairCoverage.OPEN_HAIR -> add("open_hair")
        }
    }
}
