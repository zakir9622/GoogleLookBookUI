package com.zakir.vestra.shared.engine.pipeline

import kotlin.test.Test
import kotlin.test.assertEquals

class ConditioningTokensTest {

    @Test
    fun concatPlacesTextThenImageRows() {
        val dim = 4
        val text = FloatArray(2 * dim) { 1f }   // 2 text tokens
        val image = FloatArray(3 * dim) { 2f }   // 3 image tokens
        val combined = ConditioningTokens.concat(text, 2, image, 3, dim)

        assertEquals(5 * dim, combined.size)
        // First 2 rows are text (1f), next 3 rows are image (2f).
        assertEquals(1f, combined[0])
        assertEquals(1f, combined[2 * dim - 1])
        assertEquals(2f, combined[2 * dim])
        assertEquals(2f, combined[5 * dim - 1])
    }

    @Test
    fun sequenceLengthAddsTokenCounts() {
        assertEquals(93, ConditioningTokens.sequenceLength(77, 16))
    }

    @Test
    fun scaleImageTokensAppliesFactor() {
        val scaled = ConditioningTokens.scaleImageTokens(floatArrayOf(1f, 2f, 4f), 0.5f)
        assertEquals(listOf(0.5f, 1f, 2f), scaled.toList())
    }

    @Test
    fun clampStepsHonorsMobileBand() {
        assertEquals(20, ConditioningTokens.clampSteps(4))
        assertEquals(22, ConditioningTokens.clampSteps(22))
        assertEquals(25, ConditioningTokens.clampSteps(50))
    }
}
