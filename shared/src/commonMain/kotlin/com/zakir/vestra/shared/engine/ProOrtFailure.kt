package com.zakir.vestra.shared.engine

/**
 * Common heuristics for Pro ONNX load failures (shared with [EngineRouter] AUTO→Lite).
 * Android [ProOrtSessions] produces the detailed copy; this matches either form.
 */
object ProOrtFailure {
    fun isPackIncompatible(message: String?): Boolean {
        val raw = message.orEmpty()
        if (raw.isBlank()) return false
        return raw.contains("float16", ignoreCase = true) ||
            raw.contains("_to_copy", ignoreCase = true) ||
            raw.contains("not a graph input", ignoreCase = true) ||
            raw.contains("Invalid model", ignoreCase = true) ||
            raw.contains("ORT_FAIL", ignoreCase = true) ||
            raw.contains("ORT_INVALID_ARGUMENT", ignoreCase = true) ||
            raw.contains("incompatible with this ONNX", ignoreCase = true) ||
            raw.contains("Pro pack graph", ignoreCase = true) ||
            raw.contains("Pro pack UNet", ignoreCase = true) ||
            raw.contains("Pro pack could not open", ignoreCase = true) ||
            raw.contains("Could not open ONNX session", ignoreCase = true) ||
            raw.contains("Load model from", ignoreCase = true) &&
            raw.contains("unet.onnx", ignoreCase = true)
    }
}
