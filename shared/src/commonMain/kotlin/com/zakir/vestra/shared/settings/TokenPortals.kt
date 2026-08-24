package com.zakir.vestra.shared.settings

/** Free-tier token portals + clipboard heuristics (no Android deps). */
object TokenPortals {
    const val HF = "https://huggingface.co/settings/tokens"
    const val GROQ = "https://console.groq.com/keys"
    const val OPENROUTER = "https://openrouter.ai/keys"

    enum class Kind { HF, GROQ, OPENROUTER }

    fun detectClipboardToken(raw: String): Pair<Kind, String>? {
        val token = raw.trim().lineSequence().firstOrNull()?.trim().orEmpty()
        if (token.length < 8) return null
        return when {
            token.startsWith("hf_") -> Kind.HF to token
            token.startsWith("gsk_") -> Kind.GROQ to token
            token.startsWith("sk-or-") -> Kind.OPENROUTER to token
            token.startsWith("sk-") && token.length > 20 -> Kind.OPENROUTER to token
            else -> null
        }
    }
}
