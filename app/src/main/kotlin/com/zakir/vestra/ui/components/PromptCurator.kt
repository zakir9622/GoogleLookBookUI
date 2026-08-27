package com.zakir.vestra.ui.components

import com.zakir.vestra.shared.cloud.AiCapability
import kotlin.random.Random

/**
 * Runtime prompt curation for the studio composer.
 *
 * Suggestions are composed from contextual phrase banks and shuffled per studio session. This keeps
 * the composer feeling alive without requiring a network call or shipping one fixed carousel order.
 */
object PromptCurator {
    fun curate(
        capability: AiCapability,
        referenceAttached: Boolean,
        sessionSeed: Long,
        currentPrompt: String = "",
    ): List<QuickPromptItem> {
        val random = Random(sessionSeed xor (capability.ordinal.toLong() shl 12))
        val prompts = when (capability) {
            AiCapability.IMAGE_GEN -> if (referenceAttached) {
                listOf(
                    QuickPromptItem("Reframe the composition with soft window light and a quieter background", "REFINE"),
                    QuickPromptItem("Shift the palette toward midnight blue, pearl, and warm skin tones", "COLOR"),
                    QuickPromptItem("Add tactile fabric detail while keeping the subject natural", "DETAIL"),
                    QuickPromptItem("Make this feel like a premium campaign still with generous negative space", "CAMPAIGN"),
                    QuickPromptItem("Try a close editorial crop with a subtle depth-of-field falloff", "COMPOSE"),
                )
            } else {
                listOf(
                    QuickPromptItem("An editorial portrait in rain-glossed neon, quiet confidence, 50mm lens", "PORTRAIT"),
                    QuickPromptItem("A tactile material study with sculptural folds and soft museum light", "MATERIAL"),
                    QuickPromptItem("A cinematic campaign frame with one bold silhouette and negative space", "CAMPAIGN"),
                    QuickPromptItem("A sunlit courtyard scene with natural texture and an understated palette", "SCENE"),
                    QuickPromptItem("A surreal product tableau floating in warm atmospheric light", "CONCEPT"),
                )
            }
            AiCapability.VIDEO -> listOf(
                QuickPromptItem("Open with a slow push-in, then let the subject cross a pool of light", "CAMERA"),
                QuickPromptItem("Create a restrained six-second fashion film with fabric-led motion", "MOTION"),
                QuickPromptItem("Use a handheld-feeling close-up with natural pauses and soft grain", "TEXTURE"),
                QuickPromptItem("Build a seamless loop from a still frame into a confident turn", "LOOP"),
                QuickPromptItem("Stage a moonlit city walk with reflections and a gentle tracking shot", "ATMOSPHERE"),
            )
            AiCapability.CODE -> listOf(
                QuickPromptItem("Turn this idea into a small Compose screen with clear loading and empty states", "UI"),
                QuickPromptItem("Refactor this flow into immutable StateFlow state and event-based actions", "ARCHITECTURE"),
                QuickPromptItem("Add resilient offline behavior with a visible retry path", "RESILIENCE"),
                QuickPromptItem("Review this implementation for accessibility, touch targets, and test tags", "QUALITY"),
                QuickPromptItem("Make this component feel premium without increasing layout complexity", "POLISH"),
            )
            AiCapability.AUDIO -> listOf(
                QuickPromptItem("A calm, close-mic voice with warm room tone and deliberate pacing", "VOICE"),
                QuickPromptItem("A minimal ambient bed that leaves space for narration", "AMBIENCE"),
                QuickPromptItem("A confident product-intro read with a soft, human finish", "NARRATION"),
                QuickPromptItem("A subtle rhythmic texture for a late-night creative session", "TEXTURE"),
            )
            else -> emptyList()
        }

        val normalized = currentPrompt.trim().lowercase()
        return prompts
            .filterNot { item ->
                normalized.isNotBlank() && item.prompt.lowercase().split(' ').any { word ->
                    word.length > 5 && normalized.contains(word)
                }
            }
            .shuffled(random)
            .take(4)
    }
}
