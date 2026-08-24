package com.zakir.vestra.shared.quality

import com.zakir.vestra.shared.cloud.CloudModelCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QualityRatingTest {

    @Test
    fun fiveStarThresholdAtNinety() {
        assertEquals(5, QualityRating.starsFromScore(90))
        assertEquals(5, QualityRating.starsFromScore(95))
        assertEquals(4, QualityRating.starsFromScore(89))
    }

    @Test
    fun readyFluxInferenceIsFiveStar() {
        val flux = CloudModelCatalog.byId("flux-schnell-inference")!!
        assertTrue(QualityRating.isFiveStar(flux))
        assertTrue(QualityRating.label(flux).startsWith("5★"))
    }

    @Test
    fun unsupportedHighScoreIsNotFiveStar() {
        val sdxl = CloudModelCatalog.byId("sdxl-lightning-hf")!!
        assertFalse(QualityRating.isFiveStar(sdxl))
        assertTrue(QualityRating.label(sdxl).contains("unsupported"))
    }

    @Test
    fun degradedHighScoreIsNotFiveStar() {
        val mms = CloudModelCatalog.byId("mms-tts-eng-hf")!!
        assertFalse(QualityRating.isFiveStar(mms))
        assertTrue(QualityRating.label(mms).contains("degraded"))
    }
}
