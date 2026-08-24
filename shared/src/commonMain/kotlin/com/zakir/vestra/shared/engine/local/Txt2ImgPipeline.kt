package com.zakir.vestra.shared.engine.local

/**
 * On-device SD-Turbo / LCM txt2img pipeline (Create Studio).
 *
 * Separate from Pro try-on UNet (9-channel inpaint). Reuses the same ORT /
 * LatentCodec / scheduler *ideas* once real graphs ship — never the Pro pack
 * files themselves.
 *
 * Unlock checklist:
 * 1. Publish `local-sdturbo-v1` with text_encoder / unet / vae_decoder ≥ 1 MB each + CLIP vocab
 * 2. [SAMPLER_WIRED] is true — AndroidTxt2ImgEngine runs the denoise loop
 * 3. [AndroidLocalImageGenerator.isReady] requires installed pack with real graphs
 */
class Txt2ImgPipeline(
    private val packDir: String,
    private val config: LocalImagePackConfig,
) {
    fun missingRequirements(): List<String> {
        val missing = mutableListOf<String>()
        if (!SAMPLER_WIRED) {
            missing += "sampler (Txt2ImgPipeline.SAMPLER_WIRED=false)"
        }
        val graphs = listOfNotNull(
            config.graphs?.textEncoder,
            config.graphs?.unet,
            config.graphs?.vaeDecoder,
        )
        if (graphs.isEmpty()) {
            missing += "graphs in config.json"
        }
        return missing
    }

    fun isRunnable(): Boolean = SAMPLER_WIRED && config.graphs != null

    /**
     * Common contract stub — Android calls [AndroidTxt2ImgEngine] directly.
     */
    fun generate(prompt: String, seed: Long?): LocalImageResult {
        val missing = missingRequirements()
        if (missing.isNotEmpty()) {
            return LocalImageResult.Unavailable(
                "On-device Create Studio locked ($packDir): ${missing.joinToString()}. " +
                    "Export weights via ml/export_image_gen_pack.py.",
            )
        }
        return LocalImageResult.Unavailable(
            "Use AndroidTxt2ImgEngine on device — common stub has no ORT.",
        )
    }

    companion object {
        /**
         * Denoise loop is implemented in [AndroidTxt2ImgEngine].
         * Product readiness still needs published ONNX graphs on the device.
         */
        const val SAMPLER_WIRED: Boolean = true
    }
}
