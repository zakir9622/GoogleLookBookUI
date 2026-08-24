package com.zakir.vestra.shared.safety

/**
 * On-device input safety gate for Play compliance (PLAY_COMPLIANCE.md).
 * Blocks obvious unsafe prompts and reserves [TryOnError.SafetyBlocked] for
 * future NSFW ONNX classifier in the Lite pack.
 */
object InputSafetyGate {

    private val blockedPromptPatterns = listOf(
        Regex("""\b(nude|naked|nsfw|porn|xxx|sexual\s+act)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(child|minor|underage)\b.*\b(nude|naked|sexual)\b""", RegexOption.IGNORE_CASE),
    )

    fun checkPrompt(prompt: String): SafetyVerdict {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return SafetyVerdict.Ok
        blockedPromptPatterns.forEach { pattern ->
            if (pattern.containsMatchIn(trimmed)) {
                return SafetyVerdict.Blocked(
                    "This prompt was blocked by the input safety filter. " +
                        "Rephrase as modest fashion / editorial photography.",
                )
            }
        }
        return SafetyVerdict.Ok
    }
}

sealed interface SafetyVerdict {
    data object Ok : SafetyVerdict
    data class Blocked(val reason: String) : SafetyVerdict
}
