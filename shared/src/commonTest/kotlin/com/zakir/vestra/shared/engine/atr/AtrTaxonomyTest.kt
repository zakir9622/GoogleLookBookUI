package com.zakir.vestra.shared.engine.atr

import com.zakir.vestra.shared.domain.GarmentCategory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Synthetic ATR class maps shaped like real worn-photo histograms.
 * Keep in sync with scripts/fixtures/atr JSON files and scripts/test_atr_classify.py.
 */
class AtrTaxonomyTest {

    @Test
    fun abaya_full_coverage_column() {
        assertEquals(
            GarmentCategory.ABAYA,
            AtrTaxonomy.classifyHistogram(fixture("abaya_worn")),
        )
    }

    @Test
    fun hijab_head_focused() {
        assertEquals(
            GarmentCategory.HIJAB,
            AtrTaxonomy.classifyHistogram(fixture("hijab_worn")),
        )
    }

    @Test
    fun niqab_face_covered() {
        assertEquals(
            GarmentCategory.NIQAB,
            AtrTaxonomy.classifyHistogram(fixture("niqab_worn")),
        )
    }

    @Test
    fun shalwar_kameez_upper_and_pants() {
        assertEquals(
            GarmentCategory.SHALWAR_KAMEEZ,
            AtrTaxonomy.classifyHistogram(fixture("shalwar_worn")),
        )
    }

    @Test
    fun kurta_torso_arms() {
        assertEquals(
            GarmentCategory.KURTA,
            AtrTaxonomy.classifyHistogram(fixture("kurta_worn")),
        )
    }

    @Test
    fun dupatta_scarf_over_torso() {
        assertEquals(
            GarmentCategory.DUPATTA,
            AtrTaxonomy.classifyHistogram(fixture("dupatta_worn")),
        )
    }

    @Test
    fun dress_one_piece() {
        assertEquals(
            GarmentCategory.DRESS,
            AtrTaxonomy.classifyHistogram(fixture("dress_worn")),
        )
    }

    @Test
    fun trousers_lower_dominant() {
        assertEquals(
            GarmentCategory.LOWER_BODY,
            AtrTaxonomy.classifyHistogram(fixture("trousers_worn")),
        )
    }

    @Test
    fun lehenga_dress_plus_skirt() {
        assertEquals(
            GarmentCategory.LEHENGA,
            AtrTaxonomy.classifyHistogram(fixture("lehenga_worn")),
        )
    }

    @Test
    fun jilbab_torso_arms_coverage() {
        assertEquals(
            GarmentCategory.JILBAB,
            AtrTaxonomy.classifyHistogram(fixture("jilbab_worn")),
        )
    }

    @Test
    fun kaftan_flowing_torso() {
        assertEquals(
            GarmentCategory.KAFTAN,
            AtrTaxonomy.classifyHistogram(fixture("kaftan_worn")),
        )
    }

    @Test
    fun upper_body_short() {
        assertEquals(
            GarmentCategory.UPPER_BODY,
            AtrTaxonomy.classifyHistogram(fixture("upper_worn")),
        )
    }

    @Test
    fun headscarf_hat_dominant() {
        assertEquals(
            GarmentCategory.HEADSCARF,
            AtrTaxonomy.classifyHistogram(fixture("headscarf_worn")),
        )
    }

    @Test
    fun classify_from_class_map_matches_histogram() {
        val map = IntArray(1000) { 0 }
        // Paint abaya-like region.
        for (i in 0 until 260) map[i] = AtrTaxonomy.UPPER
        for (i in 260 until 400) map[i] = AtrTaxonomy.DRESS
        for (i in 400 until 520) map[i] = AtrTaxonomy.PANTS
        for (i in 520 until 600) map[i] = AtrTaxonomy.LEFT_ARM
        for (i in 600 until 680) map[i] = AtrTaxonomy.RIGHT_ARM
        for (i in 680 until 740) map[i] = AtrTaxonomy.LEFT_LEG
        for (i in 740 until 800) map[i] = AtrTaxonomy.RIGHT_LEG
        for (i in 800 until 850) map[i] = AtrTaxonomy.FACE
        assertEquals(GarmentCategory.ABAYA, AtrTaxonomy.classify(map))
    }

    companion object {
        /** Fractions of person pixels — same numbers as JSON fixtures. */
        fun fixture(id: String): FloatArray {
            val h = FloatArray(AtrTaxonomy.CLASS_COUNT)
            when (id) {
                "abaya_worn" -> {
                    h[AtrTaxonomy.UPPER] = 0.22f
                    h[AtrTaxonomy.DRESS] = 0.12f
                    h[AtrTaxonomy.PANTS] = 0.10f
                    h[AtrTaxonomy.LEFT_ARM] = 0.07f
                    h[AtrTaxonomy.RIGHT_ARM] = 0.07f
                    h[AtrTaxonomy.LEFT_LEG] = 0.06f
                    h[AtrTaxonomy.RIGHT_LEG] = 0.06f
                    h[AtrTaxonomy.FACE] = 0.05f
                    h[AtrTaxonomy.HAIR] = 0.04f
                }
                "hijab_worn" -> {
                    h[AtrTaxonomy.SCARF] = 0.28f
                    h[AtrTaxonomy.HAIR] = 0.12f
                    h[AtrTaxonomy.HAT] = 0.02f
                    h[AtrTaxonomy.FACE] = 0.18f
                    h[AtrTaxonomy.UPPER] = 0.08f
                }
                "niqab_worn" -> {
                    h[AtrTaxonomy.SCARF] = 0.32f
                    h[AtrTaxonomy.HAT] = 0.04f
                    h[AtrTaxonomy.FACE] = 0.02f
                    h[AtrTaxonomy.HAIR] = 0.06f
                    h[AtrTaxonomy.UPPER] = 0.10f
                }
                "shalwar_worn" -> {
                    h[AtrTaxonomy.UPPER] = 0.24f
                    h[AtrTaxonomy.PANTS] = 0.22f
                    h[AtrTaxonomy.LEFT_ARM] = 0.06f
                    h[AtrTaxonomy.RIGHT_ARM] = 0.06f
                    h[AtrTaxonomy.FACE] = 0.08f
                    h[AtrTaxonomy.HAIR] = 0.05f
                }
                "kurta_worn" -> {
                    h[AtrTaxonomy.UPPER] = 0.28f
                    h[AtrTaxonomy.LEFT_ARM] = 0.10f
                    h[AtrTaxonomy.RIGHT_ARM] = 0.10f
                    h[AtrTaxonomy.FACE] = 0.10f
                    h[AtrTaxonomy.PANTS] = 0.04f
                }
                "dupatta_worn" -> {
                    h[AtrTaxonomy.SCARF] = 0.14f
                    h[AtrTaxonomy.UPPER] = 0.22f
                    h[AtrTaxonomy.DRESS] = 0.06f
                    h[AtrTaxonomy.FACE] = 0.10f
                    h[AtrTaxonomy.PANTS] = 0.04f
                }
                "dress_worn" -> {
                    h[AtrTaxonomy.DRESS] = 0.36f
                    h[AtrTaxonomy.UPPER] = 0.04f
                    h[AtrTaxonomy.LEFT_ARM] = 0.05f
                    h[AtrTaxonomy.RIGHT_ARM] = 0.05f
                    h[AtrTaxonomy.FACE] = 0.08f
                    h[AtrTaxonomy.PANTS] = 0.02f
                }
                "trousers_worn" -> {
                    h[AtrTaxonomy.PANTS] = 0.30f
                    h[AtrTaxonomy.LEFT_LEG] = 0.12f
                    h[AtrTaxonomy.RIGHT_LEG] = 0.12f
                    h[AtrTaxonomy.UPPER] = 0.08f
                    h[AtrTaxonomy.FACE] = 0.06f
                }
                "lehenga_worn" -> {
                    h[AtrTaxonomy.DRESS] = 0.22f
                    h[AtrTaxonomy.SKIRT] = 0.18f
                    h[AtrTaxonomy.UPPER] = 0.08f
                    h[AtrTaxonomy.FACE] = 0.07f
                    h[AtrTaxonomy.LEFT_ARM] = 0.04f
                    h[AtrTaxonomy.RIGHT_ARM] = 0.04f
                }
                "jilbab_worn" -> {
                    h[AtrTaxonomy.UPPER] = 0.20f
                    h[AtrTaxonomy.DRESS] = 0.08f
                    h[AtrTaxonomy.PANTS] = 0.08f
                    h[AtrTaxonomy.LEFT_ARM] = 0.10f
                    h[AtrTaxonomy.RIGHT_ARM] = 0.10f
                    h[AtrTaxonomy.FACE] = 0.07f
                    h[AtrTaxonomy.LEFT_LEG] = 0.04f
                    h[AtrTaxonomy.RIGHT_LEG] = 0.04f
                }
                "kaftan_worn" -> {
                    h[AtrTaxonomy.UPPER] = 0.18f
                    h[AtrTaxonomy.DRESS] = 0.08f
                    h[AtrTaxonomy.PANTS] = 0.06f
                    h[AtrTaxonomy.LEFT_ARM] = 0.09f
                    h[AtrTaxonomy.RIGHT_ARM] = 0.09f
                    h[AtrTaxonomy.FACE] = 0.08f
                }
                "upper_worn" -> {
                    h[AtrTaxonomy.UPPER] = 0.30f
                    h[AtrTaxonomy.FACE] = 0.12f
                    h[AtrTaxonomy.HAIR] = 0.08f
                    h[AtrTaxonomy.LEFT_ARM] = 0.04f
                    h[AtrTaxonomy.PANTS] = 0.03f
                }
                "headscarf_worn" -> {
                    h[AtrTaxonomy.HAT] = 0.18f
                    h[AtrTaxonomy.HAIR] = 0.10f
                    h[AtrTaxonomy.FACE] = 0.14f
                    h[AtrTaxonomy.UPPER] = 0.06f
                    h[AtrTaxonomy.SCARF] = 0.05f
                }
                else -> error("unknown fixture $id")
            }
            return h
        }
    }
}
