package com.zakir.vestra.shared.cloud

import com.zakir.vestra.shared.settings.AppSettings

/**
 * Orders free models for automatic fallback when the selected Space is busy,
 * out of ZeroGPU quota, or offline.
 */
object CloudModelRouting {

    fun fallbackChain(
        selected: CloudModelProvider,
        capability: AiCapability,
        settings: AppSettings? = null,
        health: ModelHealthTracker? = null,
    ): List<CloudModelProvider> {
        val allowDegradedAlternates =
            CloudModelContracts.forProvider(selected).support == ModelSupportLevel.DEGRADED
        val skipInferenceForCredits = health != null && inferenceCreditsExhausted(capability, health)
        fun filterCandidate(candidate: CloudModelProvider): Boolean {
            if (candidate.id == selected.id) return true
            if (health?.isInCooldown(candidate.id) == true) return false
            if (skipInferenceForCredits && candidate.platform == CloudPlatform.HF_INFERENCE) return false
            return true
        }
        val spaceAlternates = CloudModelCatalog.forCapability(capability)
            .filter { candidate ->
                candidate.id != selected.id &&
                    candidate.platform == CloudPlatform.HF_SPACE &&
                    CloudModelContracts.forProvider(candidate).support != ModelSupportLevel.UNSUPPORTED &&
                    (allowDegradedAlternates ||
                        CloudModelContracts.forProvider(candidate).support != ModelSupportLevel.DEGRADED) &&
                    (settings == null || isUsable(candidate, settings)) &&
                    filterCandidate(candidate)
            }
            .sortedWith(healthAwarePriority(health))
        val inferenceAlternates = CloudModelCatalog.forCapability(capability)
            .filter { candidate ->
                candidate.id != selected.id &&
                    candidate.platform == CloudPlatform.HF_INFERENCE &&
                    CloudModelContracts.forProvider(candidate).support != ModelSupportLevel.UNSUPPORTED &&
                    (settings == null || isUsable(candidate, settings)) &&
                    filterCandidate(candidate)
            }
            .sortedWith(healthAwarePriority(health))
        val head = listOf(selected).filter { filterCandidate(it) || it.id == selected.id }
        return when (selected.platform) {
            CloudPlatform.HF_INFERENCE -> head + inferenceAlternates + spaceAlternates
            else -> head + spaceAlternates + inferenceAlternates
        }.distinctBy { it.id }
    }

    /** HF Inference credits are account-wide — one CREDITS cooldown blocks all Inference routes. */
    fun inferenceCreditsExhausted(capability: AiCapability, health: ModelHealthTracker): Boolean =
        CloudModelCatalog.forCapability(capability).any { provider ->
            provider.platform == CloudPlatform.HF_INFERENCE &&
                health.isInCooldown(provider.id) &&
                health.failureKind(provider.id) == ModelHealthTracker.FailureKind.CREDITS
        }

    fun codeFallbackChain(
        selected: CloudModelProvider,
        settings: AppSettings,
    ): List<CloudModelProvider> {
        val alternates = CloudModelCatalog.forCapability(AiCapability.CODE)
            .filter { candidate ->
                candidate.id != selected.id &&
                    CloudModelContracts.forProvider(candidate).support != ModelSupportLevel.UNSUPPORTED
            }
            .sortedWith(codePlatformPriority().then(modelPriority()).then(compareByDescending { it.speedScore }))
        return (listOf(selected) + alternates)
            .filter { isUsable(it, settings) }
            .distinctBy { it.id }
    }

    private fun codePlatformPriority(): Comparator<CloudModelProvider> =
        compareBy<CloudModelProvider> { provider ->
            when (provider.platform) {
                CloudPlatform.OPENROUTER -> 0
                CloudPlatform.GROQ -> 1
                CloudPlatform.HF_INFERENCE ->
                    if (provider.endpoint.contains("7B", ignoreCase = true)) 2 else 3
                else -> 4
            }
        }

    private fun isUsable(candidate: CloudModelProvider, settings: AppSettings): Boolean =
        !candidate.requiresApiKey || !settings.apiKeyFor(candidate).isNullOrBlank()

    private fun healthAwarePriority(health: ModelHealthTracker?): Comparator<CloudModelProvider> =
        compareByDescending<CloudModelProvider> { provider ->
            health?.effectiveSupport(provider)?.let { support ->
                when (support) {
                    ModelSupportLevel.READY -> 3
                    ModelSupportLevel.DEGRADED -> 1
                    ModelSupportLevel.UNSUPPORTED -> 0
                }
            } ?: when (CloudModelContracts.forProvider(provider).support) {
                ModelSupportLevel.READY -> 3
                ModelSupportLevel.DEGRADED -> 2
                ModelSupportLevel.UNSUPPORTED -> 0
            }
        }.thenByDescending { it.qualityScore }

    private fun modelPriority(): Comparator<CloudModelProvider> = healthAwarePriority(null)
}
