package com.zakir.vestra.shared.engine.local

import kotlin.test.Test
import kotlin.test.assertTrue

class Txt2ImgPipelineTest {

    @Test
    fun sampler_wired_for_true_local_track() {
        assertTrue(Txt2ImgPipeline.SAMPLER_WIRED)
    }

    @Test
    fun common_stub_defers_to_android_engine() {
        val pipeline = Txt2ImgPipeline(
            packDir = "/tmp/local-sdturbo-v1",
            config = LocalImagePackConfig(
                graphs = LocalImageGraphs(
                    text_encoder = "text_encoder.onnx",
                    unet = "unet.onnx",
                    vae_decoder = "vae_decoder.onnx",
                ),
            ),
        )
        assertTrue(pipeline.isRunnable())
        val result = pipeline.generate("abaya studio shot", seed = 1L)
        assertTrue(result is LocalImageResult.Unavailable)
        assertTrue(
            (result as LocalImageResult.Unavailable).reason.contains("AndroidTxt2ImgEngine", ignoreCase = true),
        )
    }
}
