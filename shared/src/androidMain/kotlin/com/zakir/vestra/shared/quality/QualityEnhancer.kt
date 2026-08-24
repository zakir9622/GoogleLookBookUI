package com.zakir.vestra.shared.quality

import android.graphics.Bitmap
import com.zakir.vestra.shared.engine.lite.ImageOps

/** Applies optional Real-ESRGAN upscale when the pack is installed and ONNX runs. */
internal object QualityEnhancer {
    fun upscaleIfInstalled(quality: QualityPostProcessor, image: Bitmap): Bitmap {
        val (rgba, width, height) = ImageOps.toRgba(image)
        val processed = quality.upscaleIfAvailable(rgba, width, height) ?: return image
        return ImageOps.fromRgba(processed.rgba, processed.width, processed.height)
    }
}
