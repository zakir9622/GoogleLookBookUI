package com.zakir.vestra.storage

import android.content.Context
import android.net.Uri
import com.zakir.vestra.shared.settings.AppSettings
import com.zakir.vestra.shared.settings.TokenFileParser
import com.zakir.vestra.shared.settings.TokenPortals
import org.json.JSONObject
import java.io.File

/**
 * Plain JSON/text sidecar under Documents/TheLookbook so free-tier API tokens
 * can be restored after a fresh install and imported from drop-files.
 */
object TokenSidecar {

    data class Payload(
        val version: Int = 1,
        val hfToken: String? = null,
        val groqApiKey: String? = null,
        val openRouterApiKey: String? = null,
        val savedAtEpochMs: Long = 0L,
    )

    /** Files we auto-scan under Documents/TheLookbook/. */
    val AUTO_FETCH_NAMES: List<String> = listOf(
        DurableStorage.TOKENS_FILE, // tokens.json
        "tokens.txt",
        "hf_token.txt",
        "api_keys.json",
        "api_keys.txt",
    )

    fun persist(context: Context, settings: AppSettings): Boolean {
        if (!DurableStorage.hasAllFilesAccess()) return false
        return runCatching {
            DurableStorage.lookbookRoot().mkdirs()
            val file = DurableStorage.tokensSidecar()
            val payload = JSONObject()
                .put("version", 1)
                .put("hfToken", settings.hfToken.value)
                .put("groqApiKey", settings.groqApiKey.value)
                .put("openRouterApiKey", settings.openRouterApiKey.value)
                .put("savedAtEpochMs", System.currentTimeMillis())
            file.writeText(payload.toString())
            file.setReadable(false, false)
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(true, true)
            true
        }.getOrDefault(false)
    }

    fun read(context: Context): Payload? {
        val file = DurableStorage.tokensSidecar()
        if (!file.exists()) return null
        return runCatching {
            val parsed = TokenFileParser.parse(file.readText())
            Payload(
                version = 1,
                hfToken = parsed.hfToken,
                groqApiKey = parsed.groqApiKey,
                openRouterApiKey = parsed.openRouterApiKey,
                savedAtEpochMs = file.lastModified(),
            )
        }.getOrNull()
    }

    /** When prefs are empty after reinstall, hydrate from the sidecar. */
    fun restoreIntoPrefsIfEmpty(context: Context, settings: AppSettings): Boolean {
        val hasPrefs = !settings.hfToken.value.isNullOrBlank() ||
            !settings.groqApiKey.value.isNullOrBlank() ||
            !settings.openRouterApiKey.value.isNullOrBlank()
        if (hasPrefs) return false
        return autoFetchFromDocuments(settings, overwriteExisting = false) > 0
    }

    /**
     * Scan Documents/TheLookbook for tokens.json / tokens.txt / hf_token.txt / api_keys.*.
     * @return number of keys applied
     */
    fun autoFetchFromDocuments(settings: AppSettings, overwriteExisting: Boolean = false): Int {
        if (!DurableStorage.hasAllFilesAccess()) return 0
        val root = DurableStorage.lookbookRoot()
        if (!root.exists()) return 0
        var applied = 0
        for (name in AUTO_FETCH_NAMES) {
            val file = File(root, name)
            if (!file.isFile) continue
            val text = runCatching { file.readText() }.getOrNull() ?: continue
            applied += applyParsed(TokenFileParser.parse(text), settings, overwriteExisting)
        }
        return applied
    }

    fun importFromUri(context: Context, uri: Uri, settings: AppSettings): Int {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: return 0
        val count = applyParsed(TokenFileParser.parse(text), settings, overwriteExisting = true)
        if (count > 0) persist(context, settings)
        return count
    }

    fun importFromRawText(context: Context, raw: String, settings: AppSettings): Int {
        val count = applyParsed(TokenFileParser.parse(raw), settings, overwriteExisting = true)
        if (count > 0) persist(context, settings)
        return count
    }

    /** Optional sideload BuildConfig / env default — only fills blank HF slot. */
    fun applyDefaultHfIfBlank(settings: AppSettings, defaultToken: String?): Boolean {
        val token = defaultToken?.trim()?.takeIf { it.isNotBlank() && it != "null" } ?: return false
        if (!settings.hfToken.value.isNullOrBlank()) return false
        settings.setHfToken(token)
        return true
    }

    fun applyDefaultOpenRouterIfBlank(settings: AppSettings, defaultToken: String?): Boolean {
        val token = defaultToken?.trim()?.takeIf { it.isNotBlank() && it != "null" } ?: return false
        if (!settings.openRouterApiKey.value.isNullOrBlank()) return false
        settings.setOpenRouterApiKey(token)
        return true
    }

    fun applyDefaultGroqIfBlank(settings: AppSettings, defaultKey: String?): Boolean {
        val key = defaultKey?.trim()?.takeIf { it.isNotBlank() && it != "null" } ?: return false
        if (!settings.groqApiKey.value.isNullOrBlank()) return false
        settings.setGroqApiKey(key)
        return true
    }

    private fun applyParsed(
        parsed: TokenFileParser.Tokens,
        settings: AppSettings,
        overwriteExisting: Boolean,
    ): Int {
        var n = 0
        parsed.hfToken?.takeIf { it.isNotBlank() }?.let { value ->
            if (overwriteExisting || settings.hfToken.value.isNullOrBlank()) {
                settings.setHfToken(value)
                n++
            }
        }
        parsed.groqApiKey?.takeIf { it.isNotBlank() }?.let { value ->
            if (overwriteExisting || settings.groqApiKey.value.isNullOrBlank()) {
                settings.setGroqApiKey(value)
                n++
            }
        }
        parsed.openRouterApiKey?.takeIf { it.isNotBlank() }?.let { value ->
            if (overwriteExisting || settings.openRouterApiKey.value.isNullOrBlank()) {
                settings.setOpenRouterApiKey(value)
                n++
            }
        }
        return n
    }

    fun clearFile() {
        DurableStorage.tokensSidecar().takeIf { it.exists() }?.delete()
    }

    fun detectClipboardToken(raw: String) = TokenPortals.detectClipboardToken(raw)

    object Portal {
        const val HF = TokenPortals.HF
        const val GROQ = TokenPortals.GROQ
        const val OPENROUTER = TokenPortals.OPENROUTER
    }
}
