package com.zakir.vestra.shared.usage

import com.russhwolf.settings.Settings
import com.zakir.vestra.shared.cloud.AiCapability
import com.zakir.vestra.shared.cloud.CloudModelContracts
import com.zakir.vestra.shared.cloud.CloudModelProvider
import com.zakir.vestra.shared.cloud.CloudPlatform
import com.zakir.vestra.shared.cloud.ModelHealthTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.zakir.vestra.shared.time.EpochClock

@Serializable
data class UsageEvent(
    val id: String,
    val timestampMs: Long,
    val providerId: String,
    val providerName: String,
    val platform: String,
    val capability: String,
    val tokensIn: Int = 0,
    val tokensOut: Int = 0,
    val estCostUsd: Double = 0.0,
    val success: Boolean = true,
    val note: String = "",
)

@Serializable
data class UsageSummary(
    val totalRequests: Int = 0,
    val successCount: Int = 0,
    val totalTokensIn: Int = 0,
    val totalTokensOut: Int = 0,
    val totalEstCostUsd: Double = 0.0,
    val byProvider: Map<String, ProviderUsage> = emptyMap(),
)

@Serializable
data class ProviderUsage(
    val providerId: String,
    val displayName: String,
    val requests: Int = 0,
    val tokensIn: Int = 0,
    val tokensOut: Int = 0,
    val estCostUsd: Double = 0.0,
)

/**
 * Local ledger of cloud AI usage so the user can see tokens/cost per model & service.
 * Persisted in multiplatform Settings (SharedPreferences on Android).
 */
class UsageLedger(private val settings: Settings) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _events = MutableStateFlow(loadEvents())
    val events: StateFlow<List<UsageEvent>> = _events

    private val _summary = MutableStateFlow(summarize(_events.value))
    val summary: StateFlow<UsageSummary> = _summary

    fun record(
        provider: CloudModelProvider,
        tokensIn: Int = 0,
        tokensOut: Int = 0,
        success: Boolean = true,
        note: String = "",
    ) {
        val inTok = tokensIn.coerceAtLeast(0)
        val outTok = tokensOut.coerceAtLeast(0)
        val event = UsageEvent(
            id = "${EpochClock.System.nowMs()}-${provider.id}",
            timestampMs = EpochClock.System.nowMs(),
            providerId = provider.id,
            providerName = provider.displayName,
            platform = provider.platform.name,
            capability = provider.capability.name,
            tokensIn = inTok,
            tokensOut = outTok,
            estCostUsd = 0.0,
            success = success,
            note = note.ifBlank { provider.usageNote },
        )
        val next = (listOf(event) + _events.value).take(MAX_EVENTS)
        _events.value = next
        _summary.value = summarize(next)
        persist(next)
    }

    fun summaryJson(): String = json.encodeToString(_summary.value)

    fun clear() {
        _events.value = emptyList()
        _summary.value = UsageSummary()
        settings.remove(KEY)
    }

    fun estimateNext(provider: CloudModelProvider): String {
        val tokens = provider.estTokensPerRequest
        val contract = CloudModelContracts.forProvider(provider)
        val health = ModelHealthTracker(settings)
        return buildString {
            append(provider.displayName)
            append(" · ")
            append(CloudModelContracts.liveStatusLabel(provider, health))
            append(" · ")
            append(provider.platform.displayLabel())
            append(" · Free")
            if (tokens > 0) append(" · ~$tokens tokens/request")
            append("\n")
            append(contract.schemaNote)
            if (provider.usageNote.isNotBlank() && provider.usageNote != contract.schemaNote) {
                append("\n")
                append(provider.usageNote)
            }
        }
    }

    private fun loadEvents(): List<UsageEvent> {
        val raw = settings.getStringOrNull(KEY) ?: return emptyList()
        return runCatching { json.decodeFromString<List<UsageEvent>>(raw) }.getOrDefault(emptyList())
    }

    private fun persist(events: List<UsageEvent>) {
        settings.putString(KEY, json.encodeToString(events))
    }

    private fun summarize(events: List<UsageEvent>): UsageSummary {
        val by = events.groupBy { it.providerId }.mapValues { (id, list) ->
            ProviderUsage(
                providerId = id,
                displayName = list.first().providerName,
                requests = list.size,
                tokensIn = list.sumOf { it.tokensIn },
                tokensOut = list.sumOf { it.tokensOut },
                estCostUsd = list.sumOf { it.estCostUsd },
            )
        }
        return UsageSummary(
            totalRequests = events.size,
            successCount = events.count { it.success },
            totalTokensIn = events.sumOf { it.tokensIn },
            totalTokensOut = events.sumOf { it.tokensOut },
            totalEstCostUsd = events.sumOf { it.estCostUsd },
            byProvider = by,
        )
    }

    private companion object {
        const val KEY = "usage_ledger_v1"
        const val MAX_EVENTS = 200
    }
}

fun CloudPlatform.displayLabel(): String = when (this) {
    CloudPlatform.HF_SPACE -> "Hugging Face Space (free)"
    CloudPlatform.HF_INFERENCE -> "Hugging Face Inference (free)"
    CloudPlatform.GROQ -> "Groq (free tier)"
    CloudPlatform.OPENROUTER -> "OpenRouter (free)"
}

fun AiCapability.displayLabel(): String = when (this) {
    AiCapability.TRY_ON -> "Virtual try-on"
    AiCapability.IMAGE_GEN -> "Image generation"
    AiCapability.IMAGE_EDIT -> "Image recreate / edit"
    AiCapability.CODE -> "Coding"
    AiCapability.VIDEO -> "Video"
    AiCapability.AUDIO -> "Audio"
}
