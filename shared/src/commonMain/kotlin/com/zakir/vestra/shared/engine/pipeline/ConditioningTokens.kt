package com.zakir.vestra.shared.engine.pipeline

/**
 * Pure-Kotlin assembly of the UNet's `encoder_hidden_states` for the
 * IP-Adapter path: the CLIP text embeddings (77 tokens) are concatenated with
 * the IP-Adapter image tokens along the sequence axis, so cross-attention sees
 * both the prompt and the garment's visual features. Kept platform-free so the
 * shaping is unit-tested on the JVM (the heavy ONNX runs live in androidMain).
 *
 * Layout is row-major [1, seq, dim]: `text` rows first, then `image` rows.
 */
object ConditioningTokens {

    /** Sequence length of the combined tensor. */
    fun sequenceLength(textTokens: Int, imageTokens: Int): Int = textTokens + imageTokens

    /**
     * Concatenates [text] ([textTokens]×[dim]) and [image] ([imageTokens]×[dim])
     * into one row-major [ (textTokens+imageTokens) × dim ] buffer.
     */
    fun concat(
        text: FloatArray,
        textTokens: Int,
        image: FloatArray,
        imageTokens: Int,
        dim: Int,
    ): FloatArray {
        require(text.size == textTokens * dim) { "text size ${text.size} != $textTokens*$dim" }
        require(image.size == imageTokens * dim) { "image size ${image.size} != $imageTokens*$dim" }
        val out = FloatArray((textTokens + imageTokens) * dim)
        text.copyInto(out, 0)
        image.copyInto(out, textTokens * dim)
        return out
    }

    /**
     * Scales IP-Adapter image tokens by a strength factor (0..1) before
     * concatenation — the app-side equivalent of IP-Adapter `scale`, letting the
     * garment features dominate without overpowering the prompt guidance.
     */
    fun scaleImageTokens(image: FloatArray, scale: Float): FloatArray =
        FloatArray(image.size) { image[it] * scale }

    /** Denoise steps clamped to the mobile-safe 20–25 band (spec requirement). */
    fun clampSteps(requested: Int): Int = requested.coerceIn(20, 25)
}
