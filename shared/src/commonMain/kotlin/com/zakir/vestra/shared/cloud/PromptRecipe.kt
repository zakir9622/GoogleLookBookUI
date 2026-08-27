package com.zakir.vestra.shared.cloud

import kotlinx.serialization.Serializable

/** A structured creative brief that can be expanded into a provider-ready prompt. */
@Serializable
data class PromptRecipe(
    val basePrompt: String = "",
    val subject: String = "",
    val setting: String = "",
    val mood: String = "",
    val lighting: String = "",
    val composition: String = "",
    val finish: String = "",
    val styleModifierIds: List<String> = emptyList(),
)

/** A deterministic local style control; the model receives the prompt language, not UI labels. */
data class StyleModifier(
    val id: String,
    val label: String,
    val category: String,
    val promptClause: String,
)

object StyleModifierCatalog {
    val all: List<StyleModifier> = listOf(
        StyleModifier("editorial", "Editorial", "Direction", "premium editorial fashion photography"),
        StyleModifier("cinematic", "Cinematic", "Direction", "cinematic visual storytelling with controlled depth of field"),
        StyleModifier("minimal", "Minimal", "Direction", "minimal composition with generous negative space"),
        StyleModifier("dreamlike", "Dreamlike", "Atmosphere", "dreamlike atmosphere with soft surreal transitions"),
        StyleModifier("neon", "Neon", "Atmosphere", "electric neon accents with a midnight color atmosphere"),
        StyleModifier("warm-light", "Warm light", "Lighting", "warm late-afternoon light with soft directional shadows"),
        StyleModifier("studio-light", "Studio light", "Lighting", "precise studio lighting with a clean controlled backdrop"),
        StyleModifier("film-grain", "Film grain", "Finish", "subtle 35mm film grain and natural tonal rolloff"),
        StyleModifier("tactile", "Tactile", "Finish", "high-fidelity tactile material detail and believable texture"),
        StyleModifier("soft-focus", "Soft focus", "Finish", "gentle soft focus around the edges with a crisp subject"),
        StyleModifier("high-fashion", "High fashion", "Subject", "high-fashion silhouette and confident art direction"),
        StyleModifier("product-hero", "Product hero", "Subject", "hero-product framing with clean premium visual hierarchy"),
    )

    fun find(id: String): StyleModifier? = all.firstOrNull { it.id == id }
}

object PromptExpander {
    fun expand(recipe: PromptRecipe): String {
        val base = recipe.basePrompt.trim()
        val sections = buildList {
            if (base.isNotBlank()) add(base)
            recipe.subject.trim().takeIf { it.isNotBlank() }?.let { add("subject: $it") }
            recipe.setting.trim().takeIf { it.isNotBlank() }?.let { add("setting: $it") }
            recipe.mood.trim().takeIf { it.isNotBlank() }?.let { add("mood: $it") }
            recipe.lighting.trim().takeIf { it.isNotBlank() }?.let { add("lighting: $it") }
            recipe.composition.trim().takeIf { it.isNotBlank() }?.let { add("composition: $it") }
            recipe.finish.trim().takeIf { it.isNotBlank() }?.let { add("finish: $it") }
            recipe.styleModifierIds.mapNotNull(StyleModifierCatalog::find)
                .map { it.promptClause }
                .distinct()
                .forEach(::add)
        }
        return sections.joinToString(", ")
    }

    fun summary(recipe: PromptRecipe): String {
        val count = listOf(recipe.subject, recipe.setting, recipe.mood, recipe.lighting, recipe.composition, recipe.finish)
            .count { it.isNotBlank() }
        val styleCount = recipe.styleModifierIds.distinct().size
        return when {
            count == 0 && styleCount == 0 -> "Prompt Director is ready"
            count == 0 -> "$styleCount style ${if (styleCount == 1) "modifier" else "modifiers"} active"
            else -> "$count creative ${if (count == 1) "detail" else "details"} · $styleCount styles"
        }
    }
}
