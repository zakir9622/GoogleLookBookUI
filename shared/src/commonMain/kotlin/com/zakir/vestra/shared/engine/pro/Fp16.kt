package com.zakir.vestra.shared.engine.pro

/**
 * IEEE-754 half-precision conversion for FP16 ONNX packs.
 *
 * The shipped tiny-SD and Pro packs are quantized to FP16, so their graphs declare
 * `tensor(float16)` inputs and outputs. Feeding FP32 fails with ORT_INVALID_ARGUMENT, and an
 * FP16 output tensor has no float view to read. The pipeline stays FP32 internally; conversion
 * happens only at the ONNX boundary.
 *
 * Subnormals, infinities and NaN are handled explicitly — clamping them to zero would quietly
 * corrupt latents rather than fail loudly.
 */
internal object Fp16 {

    fun fromFloats(data: FloatArray): ShortArray =
        ShortArray(data.size) { fromFloat(data[it]) }

    /**
     * FP32 -> FP16, rounding to nearest. Overflow saturates to +/-Inf.
     *
     * The exponent is rebiased first and each range handled separately. An earlier version
     * applied the rounding bias before branching, which is only valid for the normal path and
     * corrupted subnormals — caught by Fp16Test rather than by garbled output on a device.
     */
    fun fromFloat(value: Float): Short {
        val bits = value.toRawBits()
        val sign = (bits ushr 16) and 0x8000
        val exponent = (bits ushr 23) and 0xFF
        val mantissa = bits and 0x007FFFFF

        // NaN / Infinity: preserved, not saturated, so a bad latent stays diagnosable.
        if (exponent == 0xFF) {
            return if (mantissa != 0) {
                (sign or 0x7E00).toShort() // quiet NaN — must stay non-zero after the shift
            } else {
                (sign or 0x7C00).toShort()
            }
        }

        val halfExponent = exponent - 127 + 15
        if (halfExponent >= 0x1F) return (sign or 0x7C00).toShort() // overflow -> Inf

        if (halfExponent <= 0) {
            // Below 2^-24 there is no representable subnormal left.
            if (halfExponent < -10) return sign.toShort()
            val withImplicitBit = mantissa or 0x0080_0000
            val shift = 14 - halfExponent
            var half = withImplicitBit ushr shift
            if ((withImplicitBit ushr (shift - 1)) and 1 == 1) half++ // round to nearest
            return (sign or half).toShort()
        }

        var half = (halfExponent shl 10) or (mantissa ushr 13)
        if (mantissa and 0x1000 != 0) half++ // round to nearest
        return (sign or half).toShort()
    }

    /** FP16 -> FP32. */
    fun toFloat(half: Short): Float {
        val h = half.toInt() and 0xFFFF
        val sign = (h and 0x8000) shl 16
        val exponent = (h ushr 10) and 0x1F
        val mantissa = h and 0x03FF

        return when {
            exponent == 0 -> {
                if (mantissa == 0) {
                    Float.fromBits(sign) // +/-0
                } else {
                    // Subnormal: normalize by shifting until the implicit bit appears.
                    var e = -1
                    var m = mantissa
                    do {
                        e++
                        m = m shl 1
                    } while (m and 0x0400 == 0)
                    val exp = 127 - 15 - e
                    val frac = (m and 0x03FF) shl 13
                    Float.fromBits(sign or (exp shl 23) or frac)
                }
            }
            exponent == 0x1F -> Float.fromBits(sign or 0x7F800000 or (mantissa shl 13)) // Inf / NaN
            else -> Float.fromBits(sign or ((exponent - 15 + 127) shl 23) or (mantissa shl 13))
        }
    }

    /** BFloat16 -> FP32: the top 16 bits of an FP32, so widening is a shift. */
    fun bfloatToFloat(value: Short): Float =
        Float.fromBits((value.toInt() and 0xFFFF) shl 16)
}
