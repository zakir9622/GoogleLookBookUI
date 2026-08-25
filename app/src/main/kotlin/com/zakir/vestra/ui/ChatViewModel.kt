package com.zakir.vestra.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zakir.vestra.shared.chat.ChatRepository
import com.zakir.vestra.shared.cloud.AiCapability
import com.zakir.vestra.shared.cloud.CloudPlatform
import com.zakir.vestra.shared.cloud.GenerativeCloudService
import com.zakir.vestra.shared.diagnostics.RunCapability
import com.zakir.vestra.shared.diagnostics.RunDiagnostics
import com.zakir.vestra.shared.engine.local.LocalCodeStreamEvent
import com.zakir.vestra.shared.local.LocalModelCatalog
import com.zakir.vestra.shared.logging.LogSource
import com.zakir.vestra.shared.logging.LogStateManager
import com.zakir.vestra.shared.news.NewsRepository
import com.zakir.vestra.shared.settings.AppSettings
import com.zakir.vestra.shared.settings.PreflightResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val chat: ChatRepository,
    private val news: NewsRepository?,
    private val generative: GenerativeCloudService,
    private val appSettings: AppSettings,
    private val runDiagnostics: RunDiagnostics?,
    private val deviceRamMb: Long?,
    val logStateManager: LogStateManager = LogStateManager(),
) : ViewModel() {

    val messages = chat.messages
    val activeModule = chat.activeModule
    val logEntries = logStateManager.entries
    val formattedLogs = logStateManager.formattedLines

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var job: Job? = null

    fun setModule(moduleId: String) {
        chat.switchModule(moduleId)
        _error.value = null
    }

    fun clearError() {
        _error.value = null
    }

    fun clearHistory(moduleId: String = chat.activeModule.value) {
        chat.clear(moduleId)
        _error.value = null
        logStateManager.clear()
    }

    fun send(prompt: String, targetModule: String = chat.activeModule.value) {
        val text = prompt.trim().take(4000)
        if (text.isEmpty() || _busy.value) return

        when (val check = appSettings.preflight(AiCapability.CODE)) {
            is PreflightResult.Blocked -> {
                _error.value = check.reason
                logStateManager.warn(LogSource.SYSTEM, "Preflight blocked: ${check.reason}")
                return
            }
            is PreflightResult.Ok -> Unit
        }

        val provider = appSettings.selectedProvider(AiCapability.CODE)
        // A local on-device pick routes through chatWithFallback's local branch, so the
        // cloud-platform guard below must not reject it.
        val localChat = appSettings.prefersLocal(AiCapability.CODE) && generative.localCodeReady()
        if (!localChat &&
            provider.platform !in setOf(CloudPlatform.GROQ, CloudPlatform.OPENROUTER, CloudPlatform.HF_INFERENCE)
        ) {
            val msg = "Pick a chat-capable coding model in Settings (Groq, OpenRouter, or HF Inference)."
            _error.value = msg
            logStateManager.error(LogSource.CLOUD_API, msg)
            return
        }

        chat.append("user", text, provider.id, moduleId = targetModule)
        _error.value = null
        _busy.value = true

        val headlines = news?.headlineContext(5).orEmpty()
        val history = chat.contextForLlm(maxTurns = 10, moduleId = targetModule)
        val system = buildString {
            append("You are a helpful assistant for The Lookbook — modest fashion try-on and on-device AI. ")
            append("Discuss headlines, local Lite/Pro packs, and cloud free-tier models. Keep answers concise.")
            if (headlines.isNotBlank()) {
                append("\n\nRecent headlines:\n")
                append(headlines)
            }
        }
        val composedPrompt = if (history.size <= 1) {
            text
        } else {
            history.dropLast(1).joinToString("\n\n") { (role, content) ->
                "${role.uppercase()}: $content"
            } + "\n\nUSER: $text"
        }

        // A local run must be recorded under its real local model, not the selected cloud
        // provider
        val localProviderId = if (localChat) generative.localChatProviderId() else null
        val modelDisplayName = localProviderId?.let { LocalModelCatalog.byId(it)?.displayName ?: it }
            ?: provider.displayName
        val builder = runDiagnostics?.startRun(
            capability = RunCapability.CHAT,
            tier = null,
            modelId = localProviderId ?: provider.id,
            modelLabel = modelDisplayName,
            deviceRamMb = deviceRamMb,
        )

        val activeSource = if (localChat) LogSource.LITERT else LogSource.CLOUD_API
        logStateManager.info(activeSource, "Dispatching chat request to $modelDisplayName...")

        job?.cancel()
        job = viewModelScope.launch {
            try {
                if (localChat) {
                    logStateManager.info(LogSource.LITERT, "LiteRT engine initialized for on-device inference.")
                    val streamed = streamLocalReply(composedPrompt, system, targetModule)
                    if (streamed != null) {
                        logStateManager.info(
                            LogSource.LITERT,
                            "LiteRT inference complete · ${streamed.tokensIn} in, ${streamed.tokensOut} out",
                        )
                        builder?.complete(
                            success = true,
                            note = "${streamed.providerId} · tokens ${streamed.tokensIn}+${streamed.tokensOut}",
                        )
                        return@launch
                    }
                    logStateManager.warn(LogSource.LITERT, "LiteRT local session unavailable, falling back to cloud endpoint...")
                }
                logStateManager.info(LogSource.CLOUD_API, "Connecting to ${provider.displayName} (${provider.platform.name})...")
                val cloudStartMs = System.currentTimeMillis()
                val (result, used) = generative.chatWithFallback(
                    prompt = composedPrompt,
                    system = system,
                    capability = AiCapability.CODE,
                    temperature = 0.4,
                )
                val cloudEndMs = System.currentTimeMillis()
                val cloudDurationMs = (cloudEndMs - cloudStartMs).coerceAtLeast(1L)
                chat.append(
                    role = "assistant",
                    text = result.text,
                    providerId = used.id,
                    ttftMs = cloudDurationMs,
                    durationMs = cloudDurationMs,
                    tokensIn = result.tokensIn,
                    tokensOut = result.tokensOut,
                    moduleId = targetModule,
                )
                logStateManager.info(
                    LogSource.CLOUD_API,
                    "Received response from ${used.displayName} in ${cloudDurationMs}ms (${result.tokensIn}+${result.tokensOut} tokens)",
                )
                builder?.complete(
                    success = true,
                    note = "${used.id} · tokens ${result.tokensIn}+${result.tokensOut} · ${cloudDurationMs}ms",
                )
            } catch (e: Exception) {
                val rawMsg = e.message?.take(280) ?: "Chat failed"
                logStateManager.error(activeSource, "Chat execution error: $rawMsg")
                _error.value = if (localChat && builder != null) "$rawMsg (ref ${builder.id})" else rawMsg
                builder?.complete(success = false, error = rawMsg)
            } finally {
                _busy.value = false
            }
        }
    }

    private class StreamedReply(
        val providerId: String,
        val tokensIn: Int,
        val tokensOut: Int,
        val ttftMs: Long,
        val durationMs: Long,
    )

    /**
     * Streams a local reply into a live-updating assistant bubble. Returns null (after removing
     * the empty placeholder) when the local model turned out unavailable, so the caller can fall
     * back to [GenerativeCloudService.chatWithFallback] without leaving a ghost message behind.
     */
    private suspend fun streamLocalReply(prompt: String, system: String, moduleId: String = chat.activeModule.value): StreamedReply? {
        val providerId = generative.localChatProviderId()
        val messageId = chat.appendPlaceholder("assistant", providerId, moduleId = moduleId)
        var failure: String? = null
        var tokensIn = 0
        var tokensOut = 0
        var streamCount = 0
        val localStartMs = System.currentTimeMillis()
        var firstTokenTimeMs: Long? = null
        var totalDurationMs = 0L
        var ttftMs = 0L

        logStateManager.info(LogSource.LITERT, "Streaming tokens from LiteRT model $providerId...")
        generative.localChatStream(prompt, system).collect { event ->
            when (event) {
                is LocalCodeStreamEvent.Partial -> {
                    if (firstTokenTimeMs == null) {
                        firstTokenTimeMs = System.currentTimeMillis()
                        ttftMs = (firstTokenTimeMs!! - localStartMs).coerceAtLeast(1L)
                    }
                    streamCount++
                    chat.updateMessage(messageId, event.textSoFar, ttftMs = ttftMs, moduleId = moduleId)
                    if (streamCount % 10 == 0) {
                        logStateManager.debug(LogSource.LITERT, "Streaming output: ${event.textSoFar.length} chars (TTFT: ${ttftMs}ms)")
                    }
                }
                is LocalCodeStreamEvent.Done -> {
                    val localEndMs = System.currentTimeMillis()
                    totalDurationMs = (localEndMs - localStartMs).coerceAtLeast(1L)
                    if (ttftMs == 0L) {
                        ttftMs = ((firstTokenTimeMs ?: localEndMs) - localStartMs).coerceAtLeast(1L)
                    }
                    tokensIn = event.tokensIn
                    tokensOut = event.tokensOut
                    chat.updateMessage(
                        id = messageId,
                        text = event.text,
                        persist = true,
                        ttftMs = ttftMs,
                        durationMs = totalDurationMs,
                        tokensIn = tokensIn,
                        tokensOut = tokensOut,
                        moduleId = moduleId,
                    )
                    logStateManager.info(
                        LogSource.LITERT,
                        "Generation stream completed successfully in ${totalDurationMs}ms (TTFT: ${ttftMs}ms, $tokensOut tokens generated)",
                    )
                }
                is LocalCodeStreamEvent.Unavailable -> {
                    failure = event.reason
                    logStateManager.warn(LogSource.LITERT, "LiteRT stream unavailable: ${event.reason}")
                }
            }
        }
        if (failure != null) {
            chat.removeMessage(messageId, moduleId = moduleId)
            return null
        }
        return StreamedReply(providerId, tokensIn, tokensOut, ttftMs, totalDurationMs)
    }

    fun cancel() {
        job?.cancel()
        job = null
        _busy.value = false
        logStateManager.warn(LogSource.SYSTEM, "User cancelled active generation.")
    }
}
