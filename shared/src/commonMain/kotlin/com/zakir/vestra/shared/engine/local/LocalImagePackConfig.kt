package com.zakir.vestra.shared.engine.local

import kotlinx.serialization.Serializable

@Serializable
data class LocalImagePackConfig(
    val version: Int = 1,
    val graphs: LocalImageGraphs? = null,
    val scheduler: LocalImageScheduler? = null,
    val resolution: Int = 512,
    val lcmDistilled: Boolean = true,
)

@Serializable
data class LocalImageGraphs(
    val text_encoder: String? = null,
    val unet: String? = null,
    val vae_decoder: String? = null,
    val vae_encoder: String? = null,
) {
    val textEncoder: String? get() = text_encoder
    val vaeDecoder: String? get() = vae_decoder
    val vaeEncoder: String? get() = vae_encoder
}

@Serializable
data class LocalImageScheduler(
    val type: String = "lcm",
    val steps: Int = 4,
    val guidance: Float = 1.0f,
)
