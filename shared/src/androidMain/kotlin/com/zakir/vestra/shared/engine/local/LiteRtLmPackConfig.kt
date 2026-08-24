package com.zakir.vestra.shared.engine.local

import org.json.JSONObject
import java.io.File

/** Reads optional pack-local config.json for LiteRT-LM packs. */
data class LiteRtLmPackConfig(
    val runtime: String = "litert-lm",
    val primaryFile: String,
    val capability: String = "code",
    val vision: Boolean = false,
    val audio: Boolean = false,
    val tools: Boolean = false,
) {
    companion object {
        fun read(dir: File, defaultPrimary: String): LiteRtLmPackConfig {
            val config = File(dir, "config.json")
            if (!config.isFile) {
                return LiteRtLmPackConfig(primaryFile = defaultPrimary)
            }
            return runCatching {
                val json = JSONObject(config.readText())
                LiteRtLmPackConfig(
                    runtime = json.optString("runtime", "litert-lm"),
                    primaryFile = json.optString("primaryFile", defaultPrimary),
                    capability = json.optString("capability", "code"),
                    vision = json.optBoolean("vision", false),
                    audio = json.optBoolean("audio", false),
                    tools = json.optBoolean("tools", false),
                )
            }.getOrElse {
                LiteRtLmPackConfig(primaryFile = defaultPrimary)
            }
        }

        fun modelPath(dir: File, defaultPrimary: String): String? {
            val cfg = read(dir, defaultPrimary)
            val model = File(dir, cfg.primaryFile)
            return model.takeIf { it.isFile }?.absolutePath
        }
    }
}

/** Minimum on-disk bytes for integrity probes (avoid loading full models at verify). */
object LiteRtLmPackLimits {
    const val MIN_GEMMA4_BYTES = 500_000_000L
    const val MIN_AUDIO_BYTES = 50_000_000L
    const val MIN_FUNCTION_BYTES = 100_000_000L
    const val MIN_LEGACY_GEMMA3_BYTES = 50_000_000L

    /** Qwen3 0.6B INT4 ships at 347,251,840 bytes — floor well under it but over a truncated file. */
    const val MIN_QWEN3_BYTES = 300_000_000L
}
