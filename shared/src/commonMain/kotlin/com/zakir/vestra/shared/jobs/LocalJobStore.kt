package com.zakir.vestra.shared.jobs

import com.russhwolf.settings.Settings
import com.zakir.vestra.shared.diagnostics.RunCapability
import com.zakir.vestra.shared.time.EpochClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * lookbookweb persists generation jobs to a backend so a killed/backgrounded app can resume
 * "I asked for X and it didn't finish" on next open (`src/lib/videoJobs.ts`). This app has no
 * backend and shouldn't add one — but a long local run (Bonsai Image 4B is "several minutes on
 * CPU" per its own catalog note) has nothing protecting it if the process is reclaimed mid-run.
 *
 * This does NOT mean resuming mid-inference — ONNX/LiteRT sessions aren't checkpointable. It
 * means the *user-facing memory* that a generation was in flight survives, instead of silently
 * vanishing: a row still [LocalJobStatus.RUNNING] on next app start is surfaced as "interrupted
 * — tap to retry" rather than just disappearing with no trace.
 */
enum class LocalJobStatus { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

@Serializable
data class LocalJob(
    val id: String,
    val capability: String,
    /** Short, truncated prompt text for display — not the full prompt. */
    val promptPreview: String,
    val status: LocalJobStatus,
    val startedAtMs: Long,
    val updatedAtMs: Long,
)

class LocalJobStore(private val settings: Settings) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _jobs = MutableStateFlow(load())
    val jobs: StateFlow<List<LocalJob>> = _jobs

    /** Call when a local generation starts. Returns the job id to pass to [complete]/[cancel]. */
    fun start(capability: RunCapability, prompt: String): String {
        val now = EpochClock.System.nowMs()
        val id = "$now-${capability.name}"
        val job = LocalJob(
            id = id,
            capability = capability.name,
            promptPreview = prompt.take(PROMPT_PREVIEW_CHARS),
            status = LocalJobStatus.RUNNING,
            startedAtMs = now,
            updatedAtMs = now,
        )
        persist((listOf(job) + _jobs.value).take(MAX_JOBS))
        return id
    }

    fun complete(id: String, success: Boolean) = setStatus(id, if (success) LocalJobStatus.DONE else LocalJobStatus.FAILED)

    fun cancel(id: String) = setStatus(id, LocalJobStatus.CANCELLED)

    /** Dismiss an interrupted-job banner without changing anything else about the record. */
    fun dismiss(id: String) = setStatus(id, LocalJobStatus.CANCELLED)

    private fun setStatus(id: String, status: LocalJobStatus) {
        val now = EpochClock.System.nowMs()
        persist(_jobs.value.map { if (it.id == id) it.copy(status = status, updatedAtMs = now) else it })
    }

    /**
     * Rows still [LocalJobStatus.RUNNING] or [LocalJobStatus.QUEUED] from a *previous* app run —
     * call once on app start, before any new job can be started. A row this store itself just
     * created in the current process is never stale (a fresh in-memory flow always agrees with
     * what's on screen), so this is safe to call exactly once at startup.
     */
    fun interruptedJobs(): List<LocalJob> =
        _jobs.value.filter { it.status == LocalJobStatus.RUNNING || it.status == LocalJobStatus.QUEUED }

    private fun load(): List<LocalJob> =
        settings.getStringOrNull(KEY)?.let { raw ->
            runCatching { json.decodeFromString<List<LocalJob>>(raw) }.getOrNull()
        }.orEmpty()

    private fun persist(jobs: List<LocalJob>) {
        _jobs.value = jobs
        settings.putString(KEY, json.encodeToString(jobs))
    }

    companion object {
        const val KEY = "local_job_store_v1"
        private const val MAX_JOBS = 20
        private const val PROMPT_PREVIEW_CHARS = 80
    }
}
