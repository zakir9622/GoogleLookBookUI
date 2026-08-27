package com.zakir.vestra.shared.cloud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PromptRecipeTest {
    @Test
    fun expandsCreativeAxesAndDistinctStyleClauses() {
        val expanded = PromptExpander.expand(
            PromptRecipe(
                basePrompt = "editorial portrait",
                subject = "a sculptural linen look",
                setting = "sunlit courtyard",
                mood = "quiet confidence",
                lighting = "soft window light",
                composition = "three-quarter crop",
                finish = "natural skin texture",
                styleModifierIds = listOf("editorial", "editorial", "tactile"),
            ),
        )

        assertTrue(expanded.startsWith("editorial portrait"))
        assertTrue(expanded.contains("subject: a sculptural linen look"))
        assertTrue(expanded.contains("premium editorial fashion photography"))
        assertEquals(1, expanded.split("premium editorial fashion photography").size - 1)
    }

    @Test
    fun summaryExplainsActiveRecipeDetails() {
        val summary = PromptExpander.summary(
            PromptRecipe(subject = "portrait", styleModifierIds = listOf("cinematic", "neon")),
        )
        assertEquals("1 creative detail · 2 styles", summary)
    }
}
