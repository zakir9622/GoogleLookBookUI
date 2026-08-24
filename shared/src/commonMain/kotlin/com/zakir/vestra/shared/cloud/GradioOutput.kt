package com.zakir.vestra.shared.cloud

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses Gradio queue outputs into a downloadable media reference (URL, path, or data-URL).
 * Gradio Spaces nest files at unpredictable indices — never assume output[0] is the image.
 */
object GradioOutput {

    fun extractMediaRef(element: JsonElement): String {
        return when (val found = firstMediaElement(element)) {
            is JsonPrimitive -> found.content
            is JsonObject -> {
                found["url"]?.jsonPrimitive?.content
                    ?: found["path"]?.jsonPrimitive?.content
                    ?: found["video"]?.jsonPrimitive?.content
                    ?: found["image"]?.jsonPrimitive?.content
                    ?: error("Unrecognized cloud output format")
            }
            else -> error("Unrecognized cloud output format")
        }
    }

    private fun firstMediaElement(element: JsonElement, depth: Int = 0): JsonElement? {
        if (depth > 4) return null
        return when (element) {
            is JsonPrimitive -> element.takeIf { it.isString && looksLikeMediaRef(it.content) }
            is JsonObject -> when {
                element.containsFileRef() -> element
                else -> MEDIA_KEYS.firstNotNullOfOrNull { key ->
                    element[key]?.let { firstMediaElement(it, depth + 1) }
                }
            }
            is JsonArray -> element.firstNotNullOfOrNull { firstMediaElement(it, depth + 1) }
        }
    }

    private fun JsonObject.containsFileRef(): Boolean =
        (this["url"] as? JsonPrimitive)?.isString == true ||
            (this["path"] as? JsonPrimitive)?.isString == true

    private fun looksLikeMediaRef(value: String): Boolean =
        value.startsWith("http", ignoreCase = true) ||
            value.startsWith("data:", ignoreCase = true) ||
            value.startsWith("/") ||
            MEDIA_EXTENSIONS.any { value.endsWith(it, ignoreCase = true) }

    private val MEDIA_KEYS = listOf("image", "video", "value", "data", "output", "file")
    private val MEDIA_EXTENSIONS = listOf(".png", ".jpg", ".jpeg", ".webp", ".gif", ".mp4", ".webm")
}
