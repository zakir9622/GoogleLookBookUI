package com.zakir.vestra.shared.cloud

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class GradioOutputTest {

    @Test
    fun extractMediaRefFromNestedGalleryOutput() {
        val nested = JsonArray(
            listOf(
                JsonPrimitive(42),
                JsonObject(
                    mapOf(
                        "image" to JsonObject(
                            mapOf("url" to JsonPrimitive("https://example.com/out.png")),
                        ),
                    ),
                ),
            ),
        )
        assertEquals("https://example.com/out.png", GradioOutput.extractMediaRef(nested))
    }

    @Test
    fun extractMediaRefFromPrimitiveDataUrl() {
        val element = JsonPrimitive("data:image/png;base64,abc")
        assertEquals("data:image/png;base64,abc", GradioOutput.extractMediaRef(element))
    }
}
