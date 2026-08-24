package com.zakir.vestra.shared.quality

import com.zakir.vestra.shared.packs.ModelPackManager

/**
 * Optional post-processing quality packs (BiRefNet, Real-ESRGAN) applied after
 * Lite/Pro try-on or Create when installed.
 */
interface QualityPostProcessor {
    /** Upscale [width]×[height] RGBA bytes when realesrgan-v1 is installed. */
    fun upscaleIfAvailable(rgba: ByteArray, width: Int, height: Int): ProcessedImage?

    /** Refine a matte mask when birefnet-v1 is installed. */
    fun refineMatteIfAvailable(rgba: ByteArray, width: Int, height: Int): ByteArray?
}

data class ProcessedImage(
    val rgba: ByteArray,
    val width: Int,
    val height: Int,
)

object NoOpQualityPostProcessor : QualityPostProcessor {
    override fun upscaleIfAvailable(rgba: ByteArray, width: Int, height: Int): ProcessedImage? = null
    override fun refineMatteIfAvailable(rgba: ByteArray, width: Int, height: Int): ByteArray? = null
}

/** Returns a processor when quality packs are installed; otherwise no-op. */
expect fun createQualityPostProcessor(packs: ModelPackManager): QualityPostProcessor
