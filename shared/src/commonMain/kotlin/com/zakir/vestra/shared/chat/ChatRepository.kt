package com.zakir.vestra.shared.chat

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.zakir.vestra.shared.time.EpochClock

@Serializable
data class ChatMessage(
    val id: String,
    val role: String,
    val text: String,
    val timestampMs: Long,
    val providerId: String? = null,
    val ttftMs: Long? = null,
    val durationMs: Long? = null,
    val tokensIn: Int? = null,
    val tokensOut: Int? = null,
    val moduleId: String = ChatRepository.DEFAULT_MODULE,
)

/**
 * Local conversation memory with per-module caching layer.
 * Persists up to [MAX_MESSAGES] turns per module locally (e.g. news, code, image, audio, video).
 * Guarantees that switching between tabs or contexts keeps each module's conversation and generated content intact.
 */
class ChatRepository(private val settings: Settings) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _activeModule = MutableStateFlow(DEFAULT_MODULE)
    val activeModule: StateFlow<String> = _activeModule

    // In-memory cache map per module
    private val moduleCaches = mutableMapOf<String, MutableList<ChatMessage>>()

    private val _messages = MutableStateFlow(loadModule(DEFAULT_MODULE))
    val messages: StateFlow<List<ChatMessage>> = _messages

    /**
     * Switch active module context (e.g. "news", "code", "image", "video", "audio").
     * Updates activeModule and emits the chosen module's cached messages.
     */
    fun switchModule(moduleId: String) {
        val target = moduleId.trim().ifEmpty { DEFAULT_MODULE }
        if (_activeModule.value == target && moduleCaches.containsKey(target)) return
        _activeModule.value = target
        _messages.value = getOrLoadModule(target)
    }

    /** Returns current messages for a specific module without switching active selection. */
    fun messagesForModule(moduleId: String): List<ChatMessage> {
        val target = moduleId.trim().ifEmpty { DEFAULT_MODULE }
        return getOrLoadModule(target)
    }

    fun append(
        role: String,
        text: String,
        providerId: String? = null,
        ttftMs: Long? = null,
        durationMs: Long? = null,
        tokensIn: Int? = null,
        tokensOut: Int? = null,
        moduleId: String = _activeModule.value,
    ) {
        val target = moduleId.trim().ifEmpty { DEFAULT_MODULE }
        val msg = ChatMessage(
            id = "${EpochClock.System.nowMs()}-$role",
            role = role,
            text = text.trim(),
            timestampMs = EpochClock.System.nowMs(),
            providerId = providerId,
            ttftMs = ttftMs,
            durationMs = durationMs,
            tokensIn = tokensIn,
            tokensOut = tokensOut,
            moduleId = target,
        )
        val current = getOrLoadModule(target)
        val updated = (current + msg).takeLast(MAX_MESSAGES)
        moduleCaches[target] = updated.toMutableList()
        if (_activeModule.value == target) {
            _messages.value = updated
        }
        persistModule(target, updated)
    }

    /**
     * Appends an empty message and returns its id, for a caller that will fill it in live as a
     * response streams — [updateMessage] moves the text forward on every chunk without writing
     * to disk each time; only the final [updateMessage] with `persist = true` does.
     */
    fun appendPlaceholder(
        role: String,
        providerId: String? = null,
        moduleId: String = _activeModule.value,
    ): String {
        val target = moduleId.trim().ifEmpty { DEFAULT_MODULE }
        val current = getOrLoadModule(target)
        val msg = ChatMessage(
            id = "${EpochClock.System.nowMs()}-$role-${current.size}",
            role = role,
            text = "",
            timestampMs = EpochClock.System.nowMs(),
            providerId = providerId,
            moduleId = target,
        )
        val updated = (current + msg).takeLast(MAX_MESSAGES)
        moduleCaches[target] = updated.toMutableList()
        if (_activeModule.value == target) {
            _messages.value = updated
        }
        return msg.id
    }

    /** Updates an existing message's text in place (e.g. a streaming assistant reply). */
    fun updateMessage(
        id: String,
        text: String,
        persist: Boolean = false,
        ttftMs: Long? = null,
        durationMs: Long? = null,
        tokensIn: Int? = null,
        tokensOut: Int? = null,
        moduleId: String = _activeModule.value,
    ) {
        val target = moduleId.trim().ifEmpty { DEFAULT_MODULE }
        val current = getOrLoadModule(target)
        val updated = current.map {
            if (it.id == id) {
                it.copy(
                    text = text.trim(),
                    ttftMs = ttftMs ?: it.ttftMs,
                    durationMs = durationMs ?: it.durationMs,
                    tokensIn = tokensIn ?: it.tokensIn,
                    tokensOut = tokensOut ?: it.tokensOut,
                )
            } else it
        }
        moduleCaches[target] = updated.toMutableList()
        if (_activeModule.value == target) {
            _messages.value = updated
        }
        if (persist) persistModule(target, updated)
    }

    /** Removes a message by id — e.g. an empty streaming placeholder that never got a result. */
    fun removeMessage(id: String, moduleId: String = _activeModule.value) {
        val target = moduleId.trim().ifEmpty { DEFAULT_MODULE }
        val current = getOrLoadModule(target)
        val updated = current.filterNot { it.id == id }
        moduleCaches[target] = updated.toMutableList()
        if (_activeModule.value == target) {
            _messages.value = updated
        }
        persistModule(target, updated)
    }

    fun contextForLlm(
        maxTurns: Int = 12,
        moduleId: String = _activeModule.value,
    ): List<Pair<String, String>> {
        val target = moduleId.trim().ifEmpty { DEFAULT_MODULE }
        return getOrLoadModule(target).takeLast(maxTurns).map { it.role to it.text }
    }

    fun clear(moduleId: String = _activeModule.value) {
        val target = moduleId.trim().ifEmpty { DEFAULT_MODULE }
        moduleCaches[target] = mutableListOf()
        if (_activeModule.value == target) {
            _messages.value = emptyList()
        }
        settings.remove(moduleStorageKey(target))
        if (target == DEFAULT_MODULE) {
            settings.remove(LEGACY_KEY)
        }
    }

    fun clearAll() {
        moduleCaches.clear()
        _messages.value = emptyList()
        settings.remove(LEGACY_KEY)
        KNOWN_MODULES.forEach { mod ->
            settings.remove(moduleStorageKey(mod))
        }
    }

    private fun getOrLoadModule(moduleId: String): List<ChatMessage> {
        return moduleCaches.getOrPut(moduleId) {
            loadModule(moduleId).toMutableList()
        }
    }

    private fun persistModule(moduleId: String, list: List<ChatMessage>) {
        val key = moduleStorageKey(moduleId)
        settings.putString(key, json.encodeToString(list))
    }

    private fun loadModule(moduleId: String): List<ChatMessage> {
        val key = moduleStorageKey(moduleId)
        val raw = settings.getStringOrNull(key)
        if (!raw.isNullOrBlank()) {
            val parsed = runCatching { json.decodeFromString<List<ChatMessage>>(raw) }.getOrNull()
            if (!parsed.isNullOrEmpty()) return parsed
        }
        // Fallback for default module to legacy key for backwards compatibility
        if (moduleId == DEFAULT_MODULE) {
            val legacyRaw = settings.getStringOrNull(LEGACY_KEY)
            if (!legacyRaw.isNullOrBlank()) {
                val legacyParsed = runCatching { json.decodeFromString<List<ChatMessage>>(legacyRaw) }.getOrNull()
                if (!legacyParsed.isNullOrEmpty()) {
                    // Migrate forward
                    persistModule(DEFAULT_MODULE, legacyParsed)
                    return legacyParsed
                }
            }
        }
        return emptyList()
    }

    private fun moduleStorageKey(moduleId: String): String = "chat_history_v2_$moduleId"

    companion object {
        const val DEFAULT_MODULE = "news"
        const val MODULE_CODE = "code"
        const val MODULE_IMAGE = "image"
        const val MODULE_AUDIO = "audio"
        const val MODULE_VIDEO = "video"
        const val MODULE_TRYON = "tryon"

        val KNOWN_MODULES = listOf(
            DEFAULT_MODULE,
            MODULE_CODE,
            MODULE_IMAGE,
            MODULE_AUDIO,
            MODULE_VIDEO,
            MODULE_TRYON,
        )

        private const val LEGACY_KEY = "chat_history_v1"
        private const val MAX_MESSAGES = 80
    }
}
