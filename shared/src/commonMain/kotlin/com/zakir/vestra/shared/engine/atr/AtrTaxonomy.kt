package com.zakir.vestra.shared.engine.atr

import com.zakir.vestra.shared.domain.GarmentCategory

/**
 * Full ATR / LIP 18-class taxonomy → [GarmentCategory].
 *
 * Used when Auto classify runs on a **person** parse map at generate time.
 * Pure commonMain so unit tests and the Python fixture harness can share the
 * same decision table without Android / ORT.
 *
 * Label indices match `human_parse.onnx` (ATR):
 * 0 bg, 1 hat, 2 hair, 3 sunglasses, 4 upper-clothes, 5 skirt, 6 pants,
 * 7 dress, 8 belt, 9/10 shoes, 11 face, 12/13 legs, 14/15 arms, 16 bag, 17 scarf.
 */
object AtrTaxonomy {

    const val CLASS_COUNT: Int = 18

    const val BACKGROUND: Int = 0
    const val HAT: Int = 1
    const val HAIR: Int = 2
    const val SUNGLASSES: Int = 3
    const val UPPER: Int = 4
    const val SKIRT: Int = 5
    const val PANTS: Int = 6
    const val DRESS: Int = 7
    const val BELT: Int = 8
    const val LEFT_SHOE: Int = 9
    const val RIGHT_SHOE: Int = 10
    const val FACE: Int = 11
    const val LEFT_LEG: Int = 12
    const val RIGHT_LEG: Int = 13
    const val LEFT_ARM: Int = 14
    const val RIGHT_ARM: Int = 15
    const val BAG: Int = 16
    const val SCARF: Int = 17

    /** Raw per-class pixel counts (length [CLASS_COUNT]). */
    fun counts(classMap: IntArray, classCount: Int = CLASS_COUNT): IntArray {
        val out = IntArray(classCount)
        for (label in classMap) {
            if (label in 0 until classCount) out[label]++
        }
        return out
    }

    /**
     * Fractions of **person** pixels (non-background). Background stays 0.
     * Returns zeros when no person pixels are present.
     */
    fun histogram(classMap: IntArray, classCount: Int = CLASS_COUNT): FloatArray {
        val raw = counts(classMap, classCount)
        val person = raw.sum() - raw.getOrElse(BACKGROUND) { 0 }
        val out = FloatArray(classCount)
        if (person <= 0) return out
        for (i in out.indices) {
            if (i != BACKGROUND) out[i] = raw[i].toFloat() / person
        }
        return out
    }

    fun classify(classMap: IntArray): GarmentCategory =
        classifyHistogram(histogram(classMap))

    /**
     * Decision table for Auto. Prefer specific modest / South-Asian labels when
     * the parse evidence is strong; fall back to ABAYA (Lookbook default outer).
     */
    fun classifyHistogram(h: FloatArray): GarmentCategory {
        fun f(i: Int): Float = h.getOrElse(i) { 0f }

        val scarf = f(SCARF)
        val hat = f(HAT)
        val face = f(FACE)
        val hair = f(HAIR)
        val upper = f(UPPER)
        val dress = f(DRESS)
        val pants = f(PANTS)
        val skirt = f(SKIRT)
        val legs = f(LEFT_LEG) + f(RIGHT_LEG)
        val arms = f(LEFT_ARM) + f(RIGHT_ARM)
        val headCover = scarf + hat
        val lower = pants + skirt
        val torso = upper + dress

        // Face nearly gone + strong scarf → niqab / face veil.
        if (scarf > 0.08f && face < 0.035f && headCover > 0.10f) {
            return GarmentCategory.NIQAB
        }
        // Head-focused scarf, little torso/lower → hijab / headscarf.
        if (headCover > 0.10f && torso < 0.14f && lower < 0.08f) {
            return if (scarf >= hat) GarmentCategory.HIJAB else GarmentCategory.HEADSCARF
        }
        // Scarf over a dressed torso, little pants → dupatta drape.
        if (scarf > 0.07f && torso > 0.12f && lower < 0.12f) {
            return GarmentCategory.DUPATTA
        }

        // Full-body modest / traditional silhouettes.
        if (torso + lower >= 0.32f) {
            when {
                dress > 0.18f && skirt > 0.08f && pants < 0.06f ->
                    return GarmentCategory.LEHENGA
                dress > 0.20f && lower < 0.12f ->
                    return GarmentCategory.DRESS
                // Full-coverage column before upper+pants (abaya vs shalwar).
                torso > 0.26f && (arms + legs) > 0.10f && face < 0.09f && lower > 0.08f ->
                    return GarmentCategory.ABAYA
                upper > 0.12f && pants > 0.10f ->
                    return GarmentCategory.SHALWAR_KAMEEZ
                torso > 0.22f && arms > 0.12f && lower > 0.06f ->
                    return GarmentCategory.JILBAB
                // Flowing outer with some lower signal — not a short kurta.
                torso > 0.18f && arms > 0.08f && lower > 0.05f ->
                    return GarmentCategory.KAFTAN
            }
        }

        if (dress > 0.18f) {
            return if (skirt > pants && skirt > 0.07f) {
                GarmentCategory.LEHENGA
            } else {
                GarmentCategory.DRESS
            }
        }
        if (lower > torso && lower > 0.16f) return GarmentCategory.LOWER_BODY
        if (upper > 0.16f && pants > 0.09f) return GarmentCategory.SHALWAR_KAMEEZ
        if (upper > 0.14f && lower < 0.10f) {
            return if (arms > 0.07f) GarmentCategory.KURTA else GarmentCategory.UPPER_BODY
        }
        if (skirt > 0.14f) return GarmentCategory.LEHENGA
        if (headCover > 0.06f || (hair > 0.12f && scarf > 0.04f)) {
            return GarmentCategory.HIJAB
        }

        return GarmentCategory.ABAYA
    }

    /** Human-readable ATR class name for diagnostics / fixture dumps. */
    fun labelName(id: Int): String = when (id) {
        BACKGROUND -> "background"
        HAT -> "hat"
        HAIR -> "hair"
        SUNGLASSES -> "sunglasses"
        UPPER -> "upper-clothes"
        SKIRT -> "skirt"
        PANTS -> "pants"
        DRESS -> "dress"
        BELT -> "belt"
        LEFT_SHOE -> "left-shoe"
        RIGHT_SHOE -> "right-shoe"
        FACE -> "face"
        LEFT_LEG -> "left-leg"
        RIGHT_LEG -> "right-leg"
        LEFT_ARM -> "left-arm"
        RIGHT_ARM -> "right-arm"
        BAG -> "bag"
        SCARF -> "scarf"
        else -> "class-$id"
    }
}
