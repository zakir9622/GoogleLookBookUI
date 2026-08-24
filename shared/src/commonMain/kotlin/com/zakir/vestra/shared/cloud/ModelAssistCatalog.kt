package com.zakir.vestra.shared.cloud

/**
 * Per-capability assist toggles that free models can honor via prompt / system
 * rewriting. Sampler knobs (steps/CFG/seed) are intentionally not exposed in the
 * composer — Gradio Spaces and HF Inference payloads do not accept them.
 */
data class GenerativeAssists(
    /** Code: complete lawful tasks instead of soft-refusing. */
    val pragmatic: Boolean = true,
    /** Code: slightly higher temperature / exploratory answers. */
    val creative: Boolean = false,
    /** Image/video: lookbook + modest-fashion framing. Audio: spoken script framing. */
    val fashionContext: Boolean = true,
    /** Image/video: sharpness / lighting clauses. */
    val detailBoost: Boolean = true,
    /**
     * Image/video: soften safety false-positives on clothing prompts by
     * reframing as fashion catalog / editorial photography (not NSFW).
     */
    val bypassFilter: Boolean = true,
    /** Image/video: append common quality negatives (blur, artifacts). */
    val qualityGuard: Boolean = true,
    /** Image/edit: offline vision assist on reference photo before generation (L2). */
    val analyzeReference: Boolean = false,
    /** Reserved for local engines that honor sampler overrides (not cloud UI). */
    val inferenceSteps: Int? = null,
    val guidanceScale: Float? = null,
    val seed: Long? = null,
)

object ModelAssistCatalog {
    fun forCapability(capability: AiCapability): List<AssistToggle> = when (capability) {
        AiCapability.CODE -> listOf(
            AssistToggle("pragmatic", "Pragmatic (fewer refusals)", "Ask the model to complete lawful coding tasks instead of declining."),
            AssistToggle("creative", "Creative", "Higher temperature for more exploratory code answers."),
        )
        AiCapability.IMAGE_GEN, AiCapability.IMAGE_EDIT, AiCapability.VIDEO -> listOf(
            AssistToggle("bypass", "Bypass filter assist", "Reframe as fashion/editorial so clothing prompts trip fewer false blocks."),
            AssistToggle("fashion", "Fashion context", "Modest wear lookbook framing."),
            AssistToggle("detail", "Detail boost", "Sharpness and lighting clauses."),
            AssistToggle("quality", "Quality guard", "Avoid blur / artifacts language."),
            AssistToggle("analyze", "Analyze reference", "Offline vision assist describes attached photo before generation."),
        )
        AiCapability.AUDIO -> listOf(
            AssistToggle("fashion", "Fashion context", "Modest lookbook narration framing in the spoken script."),
        )
        AiCapability.TRY_ON -> emptyList() // garment+person inputs, not free-text assist
    }
}

data class AssistToggle(
    val id: String,
    val label: String,
    val description: String,
)
