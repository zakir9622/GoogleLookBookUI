package com.zakir.vestra.shared.engine.local

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalSdturboPackValidatorTest {

    private val config = LocalImagePackConfig(
        graphs = LocalImageGraphs(
            text_encoder = "text_encoder.onnx",
            unet = "unet.onnx",
            vae_decoder = "vae_decoder.onnx",
        ),
    )

    @Test
    fun missingGraphsFlagsTinyOrAbsentFiles() {
        val sizes = mapOf(
            "text_encoder.onnx" to 2_000_000L,
            "unet.onnx" to 500_000L,
            "vae_decoder.onnx" to 2_000_000L,
        )
        assertEquals(listOf("unet.onnx"), LocalSdturboPackValidator.missingGraphs(config) { sizes[it] })
    }

    @Test
    fun completeWhenGraphsAndTokenizerPresent() {
        val sizes = mapOf(
            "text_encoder.onnx" to 2_000_000L,
            "unet.onnx" to 2_000_000L,
            "vae_decoder.onnx" to 2_000_000L,
        )
        val exists = setOf("vocab.json", "merges.txt", "text_encoder.onnx", "unet.onnx", "vae_decoder.onnx")
        assertTrue(
            LocalSdturboPackValidator.isComplete(
                config,
                fileBytes = { sizes[it] },
                fileExists = { it in exists },
            ),
        )
    }

    @Test
    fun incompleteWithoutTokenizer() {
        val sizes = mapOf(
            "text_encoder.onnx" to 2_000_000L,
            "unet.onnx" to 2_000_000L,
            "vae_decoder.onnx" to 2_000_000L,
        )
        assertFalse(
            LocalSdturboPackValidator.isComplete(
                config,
                fileBytes = { sizes[it] },
                fileExists = { it.endsWith(".onnx") },
            ),
        )
    }
}
