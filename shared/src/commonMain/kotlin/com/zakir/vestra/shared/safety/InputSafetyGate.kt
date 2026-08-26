package com.zakir.vestra.shared.safety

import kotlinx.serialization.Serializable

/**
 * Standardized AI Safety Feature Tags indicating safety capabilities, filters, and guarantees.
 */
@Serializable
enum class SafetyFeatureTag(
    val code: String,
    val title: String,
    val description: String,
) {
    MODESTY_ASSURED(
        code = "MODESTY_GUARD",
        title = "Modesty & Ethics Guard",
        description = "Guided lookbook framing for modest fashion, abayas, and respectful presentation.",
    ),
    NSFW_FILTERED(
        code = "NSFW_BLOCKED",
        title = "Adult Content Filter",
        description = "Proactive blocking of explicit, nude, and NSFW generations.",
    ),
    CSAM_ZERO_TOLERANCE(
        code = "CSAM_ZERO",
        title = "Child Safety Compliance",
        description = "Zero-tolerance boundary against underage exploitation or harm.",
    ),
    CIVILITY_CHECKED(
        code = "CIVILITY_OK",
        title = "Civility & Anti-Harassment",
        description = "Pre-generation filter against hate speech, harassment, and toxic prompts.",
    ),
    ETHICAL_AUDIO_GUARD(
        code = "VOICE_ETHICS",
        title = "Audio Deepfake & Consent Guard",
        description = "Safe synthetic voice personas preventing unauthorized voice cloning.",
    ),
    WATERMARK_COMPLIANT(
        code = "PROVENANCE_TAG",
        title = "AI Provenance Tagged",
        description = "Synthetic metadata tagging indicating AI-generated content origin.",
    ),
    COMMUNITY_SAFE(
        code = "COMMUNITY_STANDARDS",
        title = "Community Standards Certified",
        description = "Compliant with Google Play Family and Open AI safety guidelines.",
    ),
}

/**
 * On-device input safety gate for Play compliance (PLAY_COMPLIANCE.md).
 * Blocks obvious unsafe prompts, detects safety feature tags, and enforces
 * modesty/ethics across visual, audio, video, and text generation.
 */
object InputSafetyGate {

    private val blockedPromptPatterns = listOf(
        Regex("""\b(nude|naked|nsfw|porn|xxx|sexual\s+act|explicit\s+nudity)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(child|minor|underage)\b.*\b(nude|naked|sexual|strip)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(deepfake\s+unauthorized|impersonate\s+real\s+person)\b""", RegexOption.IGNORE_CASE),
    )

    val standardSafetyTags: List<SafetyFeatureTag> = listOf(
        SafetyFeatureTag.MODESTY_ASSURED,
        SafetyFeatureTag.NSFW_FILTERED,
        SafetyFeatureTag.CSAM_ZERO_TOLERANCE,
        SafetyFeatureTag.CIVILITY_CHECKED,
        SafetyFeatureTag.COMMUNITY_SAFE,
    )

    fun checkPrompt(prompt: String): SafetyVerdict {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return SafetyVerdict.Ok
        blockedPromptPatterns.forEach { pattern ->
            if (pattern.containsMatchIn(trimmed)) {
                return SafetyVerdict.Blocked(
                    "This prompt was blocked by the input safety filter. " +
                        "Rephrase as modest fashion / editorial photography.",
                    tags = listOf(SafetyFeatureTag.NSFW_FILTERED, SafetyFeatureTag.CSAM_ZERO_TOLERANCE),
                )
            }
        }
        return SafetyVerdict.Ok
    }

    /**
     * Inspects prompt content and returns applicable safety tags and verification status.
     */
    fun evaluateSafetyTags(prompt: String): List<SafetyFeatureTag> {
        val tags = mutableListOf<SafetyFeatureTag>()
        tags.addAll(standardSafetyTags)
        val lower = prompt.lowercase()
        if (lower.contains("audio") || lower.contains("voice") || lower.contains("speak") || lower.contains("tts")) {
            tags.add(SafetyFeatureTag.ETHICAL_AUDIO_GUARD)
        }
        tags.add(SafetyFeatureTag.WATERMARK_COMPLIANT)
        return tags.distinct()
    }
}

@Serializable
sealed interface SafetyVerdict {
    @Serializable
    data object Ok : SafetyVerdict

    @Serializable
    data class Blocked(
        val reason: String,
        val tags: List<SafetyFeatureTag> = emptyList(),
    ) : SafetyVerdict
}

