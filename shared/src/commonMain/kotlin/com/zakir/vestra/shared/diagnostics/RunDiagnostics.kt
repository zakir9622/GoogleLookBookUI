package com.zakir.vestra.shared.diagnostics

import com.russhwolf.settings.Settings
import com.zakir.vestra.shared.domain.EngineTier
import com.zakir.vestra.shared.usage.UsageSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.zakir.vestra.shared.time.EpochClock

@Serializable
data class RunStage(
    val name: String,
    val durationMs: Long,
    val detail: String = "",
)

/** What kind of generation ran. */
enum class RunCapability {
    TRY_ON,
    IMAGE_GEN,
    IMAGE_EDIT,
    CODE,
    VIDEO,
    AUDIO,
    CHAT,
}

@Serializable
data class RunRecord(
    val id: String,
    val timestampMs: Long,
    val capability: String,
    val tier: String? = null,
    val modelId: String? = null,
    val modelLabel: String? = null,
    val success: Boolean,
    val totalDurationMs: Long,
    val stages: List<RunStage> = emptyList(),
    val error: String? = null,
    val deviceRamMb: Long? = null,
    val note: String = "",
) {
    fun humanSummary(): String = buildString {
        appendLine("${capability.replace('_', ' ')} · ${if (success) "OK" else "FAILED"} · ${totalDurationMs}ms")
        modelLabel?.let { appendLine("Model: $it") }
        tier?.let { appendLine("Tier: $it") }
        stages.forEach { s ->
            appendLine("  • ${s.name}: ${s.durationMs}ms${if (s.detail.isNotBlank()) " — ${s.detail}" else ""}")
        }
        error?.let { appendLine("Error: $it") }
        if (note.isNotBlank()) appendLine("Note: $note")
    }.trim()
}

@Serializable
data class DiagnosticsExportBundle(
    val runs: List<RunRecord>,
    val usageLedger: UsageSummary? = null,
    val exportedAtMs: Long,
    /** Last ~200 logcat lines (warnings+) when exported from the device. */
    val logcatSnippet: String? = null,
    val appVersion: String? = null,
)

/**
 * Persisted run history for local + cloud generations.
 * Export from Settings → Diagnostics when reporting issues.
 */
class RunDiagnostics(
    private val settings: Settings,
    private val onPersist: ((String) -> Unit)? = null,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    private val _records = MutableStateFlow(load())
    val records: StateFlow<List<RunRecord>> = _records

    fun startRun(
        capability: RunCapability,
        tier: EngineTier? = null,
        modelId: String? = null,
        modelLabel: String? = null,
        deviceRamMb: Long? = null,
    ): RunBuilder = RunBuilder(
        capability = capability,
        tier = tier,
        modelId = modelId,
        modelLabel = modelLabel,
        deviceRamMb = deviceRamMb,
        onComplete = ::append,
    )

    fun append(record: RunRecord) {
        val updated = (listOf(record) + _records.value).take(MAX_RECORDS)
        _records.value = updated
        persist(updated)
    }

    fun exportJson(): String = json.encodeToString(_records.value)

    fun exportBundle(
        usage: UsageSummary? = null,
        logcatSnippet: String? = null,
        appVersion: String? = null,
    ): String =
        json.encodeToString(
            DiagnosticsExportBundle(
                runs = _records.value,
                usageLedger = usage,
                exportedAtMs = EpochClock.System.nowMs(),
                logcatSnippet = logcatSnippet,
                appVersion = appVersion,
            ),
        )

    fun clear() {
        _records.value = emptyList()
        settings.remove(KEY)
    }

    private fun load(): List<RunRecord> =
        settings.getStringOrNull(KEY)?.let { raw ->
            runCatching { json.decodeFromString<List<RunRecord>>(raw) }.getOrNull()
        }.orEmpty()

    private fun persist(records: List<RunRecord>) {
        val encoded = json.encodeToString(records)
        settings.putString(KEY, encoded)
        onPersist?.invoke(encoded)
    }

    class RunBuilder internal constructor(
        private val capability: RunCapability,
        private val tier: EngineTier?,
        private val modelId: String?,
        private val modelLabel: String?,
        private val deviceRamMb: Long?,
        private val onComplete: (RunRecord) -> Unit,
    ) {
        private val startedAt = EpochClock.System.nowMs()
        private val stages = mutableListOf<RunStage>()

        /**
         * Stable id for this run, known before [complete] — lets a caller thread a lookup-able
         * reference into a user-facing failure message (e.g. "… (ref $id)"), correlating what
         * the user sees on screen to the full record in Settings → Diagnostics.
         */
        val id: String = "$startedAt-${capability.name}"

        fun stage(name: String, durationMs: Long, detail: String = "") {
            stages += RunStage(name, durationMs, detail)
        }

        fun complete(success: Boolean, error: String? = null, note: String = "") {
            onComplete(
                RunRecord(
                    id = id,
                    timestampMs = startedAt,
                    capability = capability.name,
                    tier = tier?.name,
                    modelId = modelId,
                    modelLabel = modelLabel,
                    success = success,
                    totalDurationMs = EpochClock.System.nowMs() - startedAt,
                    stages = stages.toList(),
                    error = error,
                    deviceRamMb = deviceRamMb,
                    note = note,
                ),
            )
        }
    }

    companion object {
        const val KEY = "run_diagnostics_v1"
        private const val MAX_RECORDS = 100
    }
}
