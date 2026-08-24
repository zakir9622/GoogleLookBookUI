package com.zakir.vestra.shared.diagnostics

import com.zakir.vestra.shared.domain.EngineTier
import com.zakir.vestra.shared.time.EpochClock

/**
 * Optional bridge so engines can emit structured stage timings without constructor churn.
 * Set once from [com.zakir.vestra.VestraApp] on startup.
 *
 * Each [startTryOn] returns a [RunHandle] keyed by run id so concurrent try-ons do not
 * clobber each other (generation-stability finding P).
 */
object DiagnosticsHook {
    var store: RunDiagnostics? = null
    var deviceRamMb: Long? = null

    data class RunHandle(
        val id: Long,
        val builder: RunDiagnostics.RunBuilder,
    )

    private val lock = Any()
    private var nextId = 1L
    private val active = mutableMapOf<Long, RunDiagnostics.RunBuilder>()

    fun startTryOn(
        tier: EngineTier,
        modelLabel: String? = null,
        deviceRamMb: Long? = null,
    ): RunHandle? {
        val builder = store?.startRun(
            capability = RunCapability.TRY_ON,
            tier = tier,
            modelLabel = modelLabel ?: tier.name,
            deviceRamMb = deviceRamMb ?: this.deviceRamMb,
        ) ?: return null
        val id = synchronized(lock) {
            val assigned = nextId++
            active[assigned] = builder
            assigned
        }
        return RunHandle(id, builder)
    }

    fun stage(handle: RunHandle?, name: String, sinceMs: Long, detail: String = "") {
        handle?.builder?.stage(name, EpochClock.System.nowMs() - sinceMs, detail)
    }

    fun completeTryOn(handle: RunHandle?, success: Boolean, error: String? = null, note: String = "") {
        val builder = handle?.let { h ->
            synchronized(lock) { active.remove(h.id) }
            h.builder
        }
        builder?.complete(success, error, note)
    }

    /** Test helper — active concurrent run count. */
    internal fun activeCount(): Int = synchronized(lock) { active.size }
}
