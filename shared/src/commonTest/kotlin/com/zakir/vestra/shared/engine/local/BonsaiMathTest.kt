package com.zakir.vestra.shared.engine.local

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cross-checks for the Bonsai Image 4B host math against independent re-derivations of the
 * same published formulas — not against [BonsaiMath] itself, so a transcription bug in the
 * port would show up as a mismatch rather than being invisible to its own test.
 */
class BonsaiMathTest {

    /** Independent re-derivation of `generate.py`'s `flowmatch_sigmas`, not [BonsaiMath.sigmas]. */
    private fun referenceSigmas(steps: Int): FloatArray {
        val tokens = 1024.0
        val m200 = 0.00016927 * tokens + 0.45666666
        val m10 = 8.73809524e-05 * tokens + 1.89833333
        val a = (m200 - m10) / 190.0
        val mu = a * steps + (m200 - 200.0 * a)
        val out = DoubleArray(steps)
        for (i in 0 until steps) {
            // np.linspace(1.0, 1.0/steps, steps)[i]
            val lin = if (steps == 1) 1.0 else 1.0 + i * (1.0 / steps - 1.0) / (steps - 1)
            out[i] = exp(mu) / (exp(mu) + (1.0 / lin - 1.0))
        }
        return (out.map { it.toFloat() } + 0f).toFloatArray()
    }

    @Test
    fun sigmasMatchIndependentFormula() {
        for (steps in listOf(1, 2, 4, 8)) {
            val ported = BonsaiMath.sigmas(steps)
            val reference = referenceSigmas(steps)
            assertEquals(reference.size, ported.size)
            for (i in reference.indices) {
                assertTrue(
                    abs(reference[i] - ported[i]) < 1e-5f,
                    "steps=$steps index=$i: ${reference[i]} vs ${ported[i]}",
                )
            }
        }
    }

    @Test
    fun sigmasAreMonotonicallyDecreasingAndEndAtZero() {
        val sigmas = BonsaiMath.sigmas(4)
        assertEquals(5, sigmas.size)
        assertEquals(0f, sigmas.last())
        for (i in 0 until sigmas.size - 1) {
            assertTrue(sigmas[i] > sigmas[i + 1], "sigma[$i]=${sigmas[i]} should exceed sigma[${i + 1}]=${sigmas[i + 1]}")
        }
    }

    @Test
    fun imgIdsShapeAndKnownIndices() {
        val ids = BonsaiMath.imgIds()
        assertEquals(BonsaiMath.TOKENS * 4, ids.size)
        // token (h=0, w=0) -> [0, 0, 0, 0]
        assertEquals(listOf(0f, 0f, 0f, 0f), ids.toList().subList(0, 4))
        // token (h=5, w=7): index = (5*32 + 7) * 4 = 668
        val base = (5 * BonsaiMath.LAT_GRID + 7) * 4
        assertEquals(listOf(0f, 5f, 7f, 0f), ids.toList().subList(base, base + 4))
    }

    @Test
    fun txtIdsShapeAndKnownIndices() {
        val ids = BonsaiMath.txtIds()
        assertEquals(BonsaiMath.SEQ * 4, ids.size)
        assertEquals(listOf(0f, 0f, 0f, 0f), ids.toList().subList(0, 4))
        val base = 100 * 4
        assertEquals(listOf(0f, 0f, 0f, 100f), ids.toList().subList(base, base + 4))
    }

    @Test
    fun unpatchifyAppliesAffineThenPlacesEachSubPixel() {
        val packed = FloatArray(BonsaiMath.TOKENS * BonsaiMath.PACKED_CHANNELS)
        val scale = FloatArray(BonsaiMath.PACKED_CHANNELS) { 2f }
        val shift = FloatArray(BonsaiMath.PACKED_CHANNELS) { 1f }
        // token (h=3, w=4), packed channel m = c*4 + i*2 + j for c=5, i=1, j=0 -> m = 22
        val h = 3
        val w = 4
        val c = 5
        val i = 1
        val j = 0
        val m = c * 4 + i * 2 + j
        val tokenBase = (h * BonsaiMath.LAT_GRID + w) * BonsaiMath.PACKED_CHANNELS
        packed[tokenBase + m] = 10f

        val z = BonsaiMath.unpatchify(packed, scale, shift)

        // z[c, 2h+i, 2w+j] with the 32x64x64 flat layout c*4096 + row*64 + col
        val row = 2 * h + i
        val col = 2 * w + j
        val flatIndex = c * 4096 + row * 64 + col
        assertEquals(2f * 10f + 1f, z[flatIndex]) // scale * value + shift

        // Every other entry only received the shift (packed value 0 there).
        val untouchedIndex = c * 4096 + row * 64 + (col + 1)
        assertEquals(1f, z[untouchedIndex])
    }

    @Test
    fun noiseIsDeterministicPerSeedAndDiffersAcrossSeeds() {
        val a1 = BonsaiMath.noise(42L)
        val a2 = BonsaiMath.noise(42L)
        val b = BonsaiMath.noise(43L)
        assertEquals(a1.size, BonsaiMath.TOKENS * BonsaiMath.PACKED_CHANNELS)
        assertTrue(a1.contentEquals(a2), "same seed must reproduce the same stream")
        assertTrue(!a1.contentEquals(b), "different seeds must not collide")
    }

    @Test
    fun noiseIsFiniteAndApproximatelyStandardNormal() {
        val samples = BonsaiMath.noise(7L)
        assertTrue(samples.all { it.isFinite() })
        val mean = samples.map { it.toDouble() }.average()
        val variance = samples.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)
        assertTrue(abs(mean) < 0.05, "mean $mean should be near 0")
        assertTrue(abs(stdDev - 1.0) < 0.05, "stddev $stdDev should be near 1")
    }
}
