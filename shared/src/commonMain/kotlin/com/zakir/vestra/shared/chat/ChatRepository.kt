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
)

/**
 * Local conversation memory for News & Chat tab.
 * Last [MAX_MESSAGES] turns persisted on device.
 */
class ChatRepository(private val settings: Settings) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _messages = MutableStateFlow(load())
    val messages: StateFlow<List<ChatMessage>> = _messages

    fun append(role: String, text: String, providerId: String? = null) {
        val msg = ChatMessage(
            id = "${EpochClock.System.nowMs()}-$role",
            role = role,
            text = text.trim(),
            timestampMs = EpochClock.System.nowMs(),
            providerId = providerId,
        )
        val updated = (_messages.value + msg).takeLast(MAX_MESSAGES)
        _messages.value = updated
        settings.putString(KEY, json.encodeToString(updated))
    }

    /**
     * Appends an empty message and returns its id, for a caller that will fill it in live as a
     * response streams — [updateMessage] moves the text forward on every chunk without writing
     * to disk each time; only the final [updateMessage] with `persist = true` does.
     */
    fun appendPlaceholder(role: String, providerId: String? = null): String {
        val msg = ChatMessage(
            id = "${EpochClock.System.nowMs()}-$role-${_messages.value.size}",
            role = role,
            text = "",
            timestampMs = EpochClock.System.nowMs(),
            providerId = providerId,
        )
        _messages.value = (_messages.value + msg).takeLast(MAX_MESSAGES)
        return msg.id
    }

    /** Updates an existing message's text in place (e.g. a streaming assistant reply). */
    fun updateMessage(id: String, text: String, persist: Boolean = false) {
        val updated = _messages.value.map { if (it.id == id) it.copy(text = text.trim()) else it }
        _messages.value = updated
        if (persist) settings.putString(KEY, json.encodeToString(updated))
    }

    /** Removes a message by id — e.g. an empty streaming placeholder that never got a result. */
    fun removeMessage(id: String) {
        _messages.value = _messages.value.filterNot { it.id == id }
    }

    fun contextForLlm(maxTurns: Int = 12): List<Pair<String, String>> =
        _messages.value.takeLast(maxTurns).map { it.role to it.text }

    fun clear() {
        _messages.value = emptyList()
        settings.remove(KEY)
    }

    private fun load(): List<ChatMessage> =
        settings.getStringOrNull(KEY)?.let { raw ->
            runCatching { json.decodeFromString<List<ChatMessage>>(raw) }.getOrNull()
        }.orEmpty()

    companion object {
        const val KEY = "chat_history_v1"
        private const val MAX_MESSAGES = 80
    }
}
