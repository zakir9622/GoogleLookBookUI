package com.zakir.vestra.ui.components

import com.zakir.vestra.shared.cloud.AiCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptCuratorTest {
    @Test
    fun `curates four image suggestions per session`() {
        val prompts = PromptCurator.curate(
            capability = AiCapability.IMAGE_GEN,
            referenceAttached = false,
            sessionSeed = 17L,
        )

        assertEquals(4, prompts.size)
        assertEquals(4, prompts.map { it.prompt }.distinct().size)
        assertTrue(prompts.all { it.prompt.isNotBlank() && !it.tag.isNullOrBlank() })
    }

    @Test
    fun `reference mode produces refinement suggestions`() {
        val prompts = PromptCurator.curate(
            capability = AiCapability.IMAGE_GEN,
            referenceAttached = true,
            sessionSeed = 29L,
        )

        assertEquals(4, prompts.size)
        assertTrue(prompts.all { it.tag in setOf("REFINE", "COLOR", "DETAIL", "CAMPAIGN", "COMPOSE") })
        assertTrue(prompts.none { it.tag == "PORTRAIT" })
    }

    @Test
    fun `current prompt removes overlapping suggestion wording`() {
        val prompts = PromptCurator.curate(
            capability = AiCapability.CODE,
            referenceAttached = false,
            sessionSeed = 4L,
            currentPrompt = "Please build an accessible Compose screen",
        )

        assertTrue(prompts.none { it.prompt.contains("Compose screen", ignoreCase = true) })
    }
}
