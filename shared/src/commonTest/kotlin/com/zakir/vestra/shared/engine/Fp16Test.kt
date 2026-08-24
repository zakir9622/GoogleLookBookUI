package com.zakir.vestra.shared.engine

import com.zakir.vestra.shared.engine.pro.Fp16
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Bit-level checks on the FP16 conversion that feeds the quantized ONNX packs.
 *
 * Silent corruption here would surface as garbled images rather than an error, so the round
 * trip is verified against known values and edge cases rather than assumed correct.
 */
class Fp16Test {

    /** Round-trip within half-precision resolution (~3 decimal digits). */
    private fun assertRoundTrips(value: Float, tolerance: Float = 1e-3f) {
        val back = Fp16.toFloat(Fp16.fromFloat(value))
        assertTrue(
            abs(back - value) <= tolerance * maxOf(1f, abs(value)),
            "round trip of $value gave $back",
        )
    }

    @Test
    fun exactlyRepresentableValuesSurviveUnchanged() {
        listOf(0f, 1f, -1f, 2f, 0.5f, -0.5f, 4f, 1024f, -2048f).forEach {
            assertEquals(it, Fp16.toFloat(Fp16.fromFloat(it)), 0f)
        }
    }

    @Test
    fun typicalLatentValuesRoundTrip() {
        // Diffusion latents and embeddings sit roughly in this band.
        listOf(0.1f, -0.25f, 3.75f, -7.5f, 0.0123f, 12.5f, -0.333f).forEach { assertRoundTrips(it) }
    }

    @Test
    fun signedZeroesKeepTheirSign() {
        assertEquals(0f, Fp16.toFloat(Fp16.fromFloat(0f)), 0f)
        val negZero = Fp16.toFloat(Fp16.fromFloat(-0f))
        assertEquals(-0f, negZero, 0f)
        assertTrue(1f / negZero < 0, "negative zero lost its sign")
    }

    @Test
    fun infinitiesArePreservedNotSaturatedToFinite() {
        assertEquals(Float.POSITIVE_INFINITY, Fp16.toFloat(Fp16.fromFloat(Float.POSITIVE_INFINITY)), 0f)
        assertEquals(Float.NEGATIVE_INFINITY, Fp16.toFloat(Fp16.fromFloat(Float.NEGATIVE_INFINITY)), 0f)
    }

    @Test
    fun nanStaysNan() {
        // Must not decay into Infinity — a NaN latent should stay diagnosable.
        assertTrue(Fp16.toFloat(Fp16.fromFloat(Float.NaN)).isNaN())
    }

    @Test
    fun overflowSaturatesToInfinityRatherThanWrapping() {
        // 65504 is the largest finite half; anything beyond must go to Inf, not wrap negative.
        assertEquals(Float.POSITIVE_INFINITY, Fp16.toFloat(Fp16.fromFloat(1e30f)), 0f)
        assertEquals(Float.NEGATIVE_INFINITY, Fp16.toFloat(Fp16.fromFloat(-1e30f)), 0f)
        assertTrue(Fp16.toFloat(Fp16.fromFloat(65504f)).isFinite())
    }

    @Test
    fun underflowGoesToZeroWithoutBlowingUp() {
        val tiny = Fp16.toFloat(Fp16.fromFloat(1e-12f))
        assertEquals(0f, tiny, 0f)
    }

    @Test
    fun subnormalsAreRepresentedRatherThanFlushed() {
        // 6.1e-5 is near the smallest normal half; below it we rely on subnormal encoding.
        val subnormal = 3.0e-5f
        val back = Fp16.toFloat(Fp16.fromFloat(subnormal))
        assertTrue(back > 0f, "subnormal $subnormal flushed to zero")
        assertTrue(abs(back - subnormal) < 1e-5f, "subnormal round trip too far off: $back")
    }

    @Test
    fun arrayConversionMatchesScalarConversion() {
        val values = floatArrayOf(0f, 1f, -3.5f, 0.125f, 1000f)
        val halves = Fp16.fromFloats(values)
        assertEquals(values.size, halves.size)
        values.indices.forEach { i ->
            assertEquals(Fp16.fromFloat(values[i]), halves[i])
        }
    }

    @Test
    fun bfloat16WidensByShiftingIntoTheHighBits() {
        // BFloat16 is the top 16 bits of an FP32, so 1.0f -> 0x3F80.
        assertEquals(1.0f, Fp16.bfloatToFloat(0x3F80.toShort()), 0f)
        assertEquals(0f, Fp16.bfloatToFloat(0), 0f)
        assertEquals(-2.0f, Fp16.bfloatToFloat(0xC000.toShort()), 0f)
    }
}
