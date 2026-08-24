package com.zakir.vestra.shared.cloud

import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.zakir.vestra.shared.time.EpochClock

/**
 * Runtime model health with exponential cooldown after failures.
 * Account-level ZeroGPU / Inference credit exhaustion uses a longer cooldown
 * and a distinct label so the chip does not say “Cooling down · 1m”.
 */
class ModelHealthTracker(
    private val settings: Settings,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    enum class FailureKind {
        GENERIC,
        QUOTA_ACCOUNT,
        CREDITS,
        /** Connectivity blip — must not look like Space cool-down. */
        OFFLINE,
        /** Free-tier rate / queue — short cooldown so fallback can skip this host. */
        RATE_LIMIT,
    }

    fun recordSuccess(providerId: String) {
        val entry = load(providerId).copy(
            consecutiveFailures = 0,
            lastSuccessMs = nowMs(),
            cooldownUntilMs = 0L,
            lastFailureKind = FailureKind.GENERIC.name,
        )
        save(providerId, entry)
    }

    fun recordFailure(providerId: String, kind: FailureKind = FailureKind.GENERIC) {
        val prev = load(providerId)
        val failures = prev.consecutiveFailures + 1
        val cooldownMs = when (kind) {
            FailureKind.QUOTA_ACCOUNT -> QUOTA_ACCOUNT_COOLDOWN_MS
            FailureKind.CREDITS -> CREDITS_COOLDOWN_MS
            FailureKind.OFFLINE -> OFFLINE_COOLDOWN_MS
            FailureKind.RATE_LIMIT -> RATE_LIMIT_COOLDOWN_MS
            FailureKind.GENERIC -> cooldownFor(failures)
        }
        save(
            providerId,
            prev.copy(
                consecutiveFailures = failures,
                lastFailureMs = nowMs(),
                cooldownUntilMs = nowMs() + cooldownMs,
                lastFailureKind = kind.name,
            ),
        )
    }

    fun isInCooldown(providerId: String): Boolean =
        load(providerId).cooldownUntilMs > nowMs()

    fun cooldownRemainingMs(providerId: String): Long =
        (load(providerId).cooldownUntilMs - nowMs()).coerceAtLeast(0L)

    fun failureKind(providerId: String): FailureKind =
        runCatching { FailureKind.valueOf(load(providerId).lastFailureKind) }
            .getOrDefault(FailureKind.GENERIC)

    fun observedLabel(providerId: String): String? {
        val entry = load(providerId)
        val now = nowMs()
        val kind = runCatching { FailureKind.valueOf(entry.lastFailureKind) }
            .getOrDefault(FailureKind.GENERIC)
        return when {
            entry.cooldownUntilMs > now -> when (kind) {
                FailureKind.QUOTA_ACCOUNT -> "ZeroGPU empty · refills daily"
                FailureKind.CREDITS -> "Inference credits empty · monthly"
                FailureKind.OFFLINE -> "Offline · reconnect"
                FailureKind.RATE_LIMIT -> {
                    val secs = ((entry.cooldownUntilMs - now) / 1_000L).coerceAtLeast(1L)
                    "Rate limited · ${secs}s"
                }
                FailureKind.GENERIC -> {
                    val mins = ((entry.cooldownUntilMs - now) / 60_000L).coerceAtLeast(1L)
                    "Cooling down · ${mins}m"
                }
            }
            entry.consecutiveFailures >= 3 -> "Degraded · ${entry.consecutiveFailures} recent failures"
            entry.lastSuccessMs > 0L -> {
                val mins = ((now - entry.lastSuccessMs) / 60_000L).coerceAtLeast(0L)
                if (mins <= 2) "Ready · verified just now" else "Ready · verified ${mins}m ago"
            }
            else -> null
        }
    }

    fun effectiveSupport(provider: CloudModelProvider): ModelSupportLevel {
        val static = CloudModelContracts.forProvider(provider).support
        if (static == ModelSupportLevel.UNSUPPORTED) return static
        if (isInCooldown(provider.id)) return ModelSupportLevel.DEGRADED
        if (load(provider.id).consecutiveFailures >= 3) return ModelSupportLevel.DEGRADED
        return static
    }

    companion object {
        private const val KEY = "model_health_v1"
        /** Account ZeroGPU refills on a daily cadence — avoid a misleading 30s “cool down”. */
        const val QUOTA_ACCOUNT_COOLDOWN_MS = 6L * 60L * 60L * 1000L
        const val CREDITS_COOLDOWN_MS = 24L * 60L * 60L * 1000L
        /** Brief only — connectivity often returns in seconds. */
        const val OFFLINE_COOLDOWN_MS = 5_000L
        /** Skip rate-limited Spaces briefly so fallback can try the next host. */
        const val RATE_LIMIT_COOLDOWN_MS = 90_000L

        fun cooldownFor(consecutiveFailures: Int): Long = when (consecutiveFailures) {
            1 -> 30_000L
            2 -> 120_000L
            3 -> 600_000L
            4 -> 1_800_000L
            else -> 3_600_000L
        }
    }

    @Serializable
    private data class HealthEntry(
        val consecutiveFailures: Int = 0,
        val lastFailureMs: Long = 0L,
        val lastSuccessMs: Long = 0L,
        val cooldownUntilMs: Long = 0L,
        val lastFailureKind: String = FailureKind.GENERIC.name,
    )

    private fun load(providerId: String): HealthEntry =
        settings.getStringOrNull("$KEY:$providerId")?.let { raw ->
            runCatching { json.decodeFromString<HealthEntry>(raw) }.getOrNull()
        } ?: HealthEntry()

    private fun save(providerId: String, entry: HealthEntry) {
        settings.putString("$KEY:$providerId", json.encodeToString(entry))
    }

    private fun nowMs(): Long = EpochClock.System.nowMs()
}
