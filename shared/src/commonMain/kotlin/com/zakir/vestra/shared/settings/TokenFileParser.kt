package com.zakir.vestra.shared.settings

/**
 * Parse free-tier API tokens from JSON or plain text drop-files.
 * Supports Documents/TheLookbook/tokens.json (and .txt) as well as picker imports.
 */
object TokenFileParser {

    data class Tokens(
        val hfToken: String? = null,
        val groqApiKey: String? = null,
        val openRouterApiKey: String? = null,
    ) {
        fun anyPresent(): Boolean =
            !hfToken.isNullOrBlank() || !groqApiKey.isNullOrBlank() || !openRouterApiKey.isNullOrBlank()

        fun countPresent(): Int =
            listOf(hfToken, groqApiKey, openRouterApiKey).count { !it.isNullOrBlank() }
    }

    fun parse(raw: String): Tokens {
        val text = raw.trim().removePrefix("\uFEFF")
        if (text.isEmpty()) return Tokens()
        return if (text.startsWith("{")) parseJsonish(text) else parsePlain(text)
    }

    private fun parseJsonish(text: String): Tokens {
        // Lightweight key scrape — avoids depending on kotlinx.serialization in callers.
        fun pick(vararg keys: String): String? {
            for (key in keys) {
                val patterns = listOf(
                    Regex("\"$key\"\\s*:\\s*\"([^\"]+)\""),
                    Regex("\"$key\"\\s*:\\s*'([^']+)'"),
                )
                for (p in patterns) {
                    val m = p.find(text) ?: continue
                    val v = m.groupValues[1].trim()
                    if (v.isNotBlank() && v != "null") return v
                }
            }
            return null
        }
        val hf = pick(
            "hfToken", "hf_token", "HF_TOKEN", "huggingface", "huggingFace",
            "HUGGINGFACE_TOKEN", "HUGGING_FACE_HUB_TOKEN",
        )
        val groq = pick("groqApiKey", "groq_api_key", "GROQ_API_KEY", "groq")
        val openRouter = pick(
            "openRouterApiKey", "open_router_api_key", "OPENROUTER_API_KEY",
            "openrouter", "OPEN_ROUTER_API_KEY",
        )
        return Tokens(
            hfToken = hf ?: detectBare(text, TokenPortals.Kind.HF),
            groqApiKey = groq ?: detectBare(text, TokenPortals.Kind.GROQ),
            openRouterApiKey = openRouter ?: detectBare(text, TokenPortals.Kind.OPENROUTER),
        )
    }

    private fun parsePlain(text: String): Tokens {
        var hf: String? = null
        var groq: String? = null
        var openRouter: String? = null
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) continue
            val kv = Regex("^(?:export\\s+)?([A-Za-z0-9_]+)\\s*[=:]\\s*(.+)$").find(trimmed)
            if (kv != null) {
                val key = kv.groupValues[1].uppercase()
                val value = kv.groupValues[2].trim().trim('"', '\'')
                when {
                    key.contains("HF") || key.contains("HUGGING") -> hf = value
                    key.contains("GROQ") -> groq = value
                    key.contains("OPENROUTER") || key.contains("OPEN_ROUTER") -> openRouter = value
                }
                continue
            }
            when (TokenPortals.detectClipboardToken(trimmed)?.first) {
                TokenPortals.Kind.HF -> if (hf == null) hf = trimmed
                TokenPortals.Kind.GROQ -> if (groq == null) groq = trimmed
                TokenPortals.Kind.OPENROUTER -> if (openRouter == null) openRouter = trimmed
                null -> Unit
            }
        }
        return Tokens(hfToken = hf, groqApiKey = groq, openRouterApiKey = openRouter)
    }

    private fun detectBare(text: String, kind: TokenPortals.Kind): String? {
        for (line in text.lineSequence()) {
            val hit = TokenPortals.detectClipboardToken(line.trim()) ?: continue
            if (hit.first == kind) return hit.second
        }
        return null
    }
}
