package com.zakir.vestra.shared.engine.pipeline

/**
 * Photorealism prompt engineering, shared across every engine tier so on-device
 * (Pro) and Cloud generations use identical guidance.
 *
 * Note on try-on models: image-conditioned try-on (IP-Adapter / CatVTON class)
 * is largely text-free — the garment image drives appearance. These tokens are
 * therefore applied as *auxiliary* guidance: they steer lighting/skin/fabric
 * realism and suppress the CGI/cartoon failure modes, but the garment identity
 * comes from the IP-Adapter image features, not the text. Cloud models that
 * accept a text prompt receive the wrapped string; on-device passes it to the
 * text-encoder stage only when the active pack ships one.
 */
object PromptStyle {

    /** Classifier-free guidance scale tuned for photoreal try-on. */
    const val CFG_SCALE: Float = 7.0f

    /** Denoise steps — a mobile-safe point in the requested 20–25 band. */
    const val STEPS: Int = 22

    const val POSITIVE: String =
        "masterpiece, ultra-high-quality studio portrait, photorealistic, " +
            "cinematic edge-lighting, highly detailed skin texture, pores visible, " +
            "DSLR, 35mm lens, RAW camera photo, high-end editorial fashion photography, " +
            "natural fabric physics and draping"

    const val NEGATIVE: String =
        "CGI, 3D render, anime, painting, digital illustration, deformed limbs, " +
            "plastic skin, smooth cartoon texture, airbrushed, AI artifacts, " +
            "generic generation, low quality, mutated geometry, blurry, watermark"

    /**
     * Wraps an optional user prompt with the hardcoded photographic tokens.
     * The user's words (e.g. a scene or mood) lead; the realism tokens follow.
     */
    fun wrapPositive(userPrompt: String? = null): String =
        if (userPrompt.isNullOrBlank()) POSITIVE else "${userPrompt.trim()}, $POSITIVE"

    fun negative(): String = NEGATIVE
}
