package com.zakir.vestra.shared.cloud

import com.zakir.vestra.shared.settings.AppSettings
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Resolves which curated free cloud models the user can run with current keys,
 * and optionally discovers warm HF Inference models when an HF token is present.
 */
class FreeCloudDiscovery(
    private val http: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun curatedUsable(settings: AppSettings, capability: AiCapability): List<CloudModelProvider> =
        CloudModelCatalog.forCapability(capability).filter { provider ->
            when {
                !provider.freeTier -> false
                CloudModelContracts.forProvider(provider).support == ModelSupportLevel.UNSUPPORTED -> false
                !provider.requiresApiKey -> true
                else -> !settings.apiKeyFor(provider).isNullOrBlank()
            }
        }

    fun curatedLocked(settings: AppSettings, capability: AiCapability): List<CloudModelProvider> =
        CloudModelCatalog.forCapability(capability).filter { provider ->
            provider.freeTier &&
                CloudModelContracts.forProvider(provider).support != ModelSupportLevel.UNSUPPORTED &&
                provider.requiresApiKey &&
                settings.apiKeyFor(provider).isNullOrBlank()
        }

    /** Models kept in catalog but blocked in-app (wrong schema / missing mask UI). */
    fun curatedUnsupported(capability: AiCapability): List<CloudModelProvider> =
        CloudModelCatalog.forCapability(capability).filter {
            CloudModelContracts.forProvider(it).support == ModelSupportLevel.UNSUPPORTED
        }

    /**
     * All selectable models for [capability]: curated usable + HF router discoveries.
     */
    fun selectable(settings: AppSettings, capability: AiCapability): List<CloudModelProvider> {
        val curated = curatedUsable(settings, capability)
        val discovered = settings.discoveredProviders.value.filter {
            it.capability == capability &&
                it.freeTier &&
                CloudModelContracts.forProvider(it).support != ModelSupportLevel.UNSUPPORTED &&
                (!it.requiresApiKey || !settings.apiKeyFor(it).isNullOrBlank())
        }
        return (curated + discovered).distinctBy { it.id }
    }

    /**
     * Refresh HF Inference Provider discoveries for CODE + IMAGE capabilities and persist
     * them in [AppSettings.rememberDiscovered].
     */
    suspend fun refreshRouterDiscovery(settings: AppSettings) {
        val token = settings.hfToken.value?.takeIf { it.isNotBlank() } ?: return
        val discovered = listOf(
            AiCapability.CODE,
            AiCapability.IMAGE_GEN,
            AiCapability.IMAGE_EDIT,
        ).flatMap { capability ->
            discoverHf(token, capability)
        }.distinctBy { it.id }
        settings.rememberDiscovered(discovered)
    }

    /**
     * Best-effort discovery of free HF Inference endpoints matching [capability].
     * Try-on / video use curated Spaces only — discovery always returns empty for those.
     * Failures return empty — curated catalog remains the source of truth.
     */
    suspend fun discoverHf(token: String?, capability: AiCapability): List<CloudModelProvider> {
        if (token.isNullOrBlank()) return emptyList()
        if (capability == AiCapability.TRY_ON || capability == AiCapability.VIDEO || capability == AiCapability.AUDIO) {
            return emptyList()
        }
        val routerModels = discoverFromRouter(token, capability)
        if (routerModels.isNotEmpty()) return routerModels
        return discoverFromWarmHub(token, capability)
    }

    private suspend fun discoverFromRouter(token: String, capability: AiCapability): List<CloudModelProvider> =
        runCatching {
            val response = http.get("https://router.huggingface.co/v1/models") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (!response.status.isSuccess()) return emptyList()
            val body = response.bodyAsText()
            val models = parseRouterModels(body)
            models.mapNotNull { hit -> hit.toDiscoveredProvider(capability) }
                .take(8)
        }.getOrDefault(emptyList())

    private suspend fun discoverFromWarmHub(token: String, capability: AiCapability): List<CloudModelProvider> {
        val pipeline = when (capability) {
            AiCapability.IMAGE_GEN -> "text-to-image"
            AiCapability.IMAGE_EDIT -> "image-to-image"
            AiCapability.CODE -> "text-generation"
            AiCapability.TRY_ON, AiCapability.VIDEO, AiCapability.AUDIO -> return emptyList()
        }
        val url =
            "https://huggingface.co/api/models?pipeline_tag=$pipeline&inference=warm&sort=downloads&direction=-1&limit=12"
        return runCatching {
            val response = http.get(url) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            check(response.status.isSuccess())
            val models = json.decodeFromString<List<HfModelHit>>(response.bodyAsText())
            models.mapNotNull { hit ->
                val id = hit.id ?: return@mapNotNull null
                if (!matchesCapability(id, capability)) return@mapNotNull null
                discoveredProvider(id, capability, hit.cardData?.license)
            }
        }.getOrDefault(emptyList())
    }

    private fun parseRouterModels(raw: String): List<RouterModelHit> = runCatching {
        val root = json.parseToJsonElement(raw)
        val array = when (root) {
            is JsonObject -> root["data"]?.jsonArray ?: return emptyList()
            is JsonArray -> root
            else -> return emptyList()
        }
        array.mapNotNull { el ->
            val obj = el.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            RouterModelHit(
                id = id,
                ownedBy = obj["owned_by"]?.jsonPrimitive?.content,
            )
        }
    }.getOrDefault(emptyList())

    private fun RouterModelHit.toDiscoveredProvider(capability: AiCapability): CloudModelProvider? {
        if (!matchesCapability(id, capability)) return null
        if (CloudModelCatalog.providers.any { it.endpoint == id }) return null
        // Only surface models we have an explicit Inference route for (finding F).
        if (capability == AiCapability.IMAGE_GEN || capability == AiCapability.IMAGE_EDIT) {
            if (!KNOWN_INFERENCE_IMAGE_MODELS.contains(id)) return null
        }
        return discoveredProvider(id, capability, license = "Check model card")
    }

    private fun discoveredProvider(
        modelId: String,
        capability: AiCapability,
        license: String?,
    ): CloudModelProvider = CloudModelProvider(
        id = "hf-disc-${modelId.replace('/', '-')}",
        displayName = modelId.substringAfter('/'),
        description = "Discovered HF Inference model · $modelId",
        platform = CloudPlatform.HF_INFERENCE,
        capability = capability,
        endpoint = modelId,
        apiName = "inference",
        license = license ?: "Check model card",
        requiresApiKey = true,
        freeTier = true,
        qualityScore = 68,
        speedScore = 72,
        usageNote = "Auto-listed from HF router (Inference Providers).",
    )

    private fun matchesCapability(modelId: String, capability: AiCapability): Boolean {
        val lower = modelId.lowercase()
        return when (capability) {
            AiCapability.CODE ->
                lower.contains("coder") ||
                    lower.contains("code") ||
                    lower.contains("starcoder") ||
                    lower.contains("deepseek") && lower.contains("r1")
            AiCapability.IMAGE_GEN ->
                lower.contains("flux") ||
                    lower.contains("sdxl") ||
                    lower.contains("turbo") ||
                    lower.contains("schnell") ||
                    lower.contains("z-image")
            AiCapability.IMAGE_EDIT ->
                lower.contains("pix2pix") ||
                    lower.contains("instruct") ||
                    lower.contains("image-edit") ||
                    lower.contains("qwen") && lower.contains("edit")
            AiCapability.TRY_ON, AiCapability.VIDEO, AiCapability.AUDIO -> false
        }
    }
}

@Serializable
private data class HfModelHit(
    val id: String? = null,
    @SerialName("cardData") val cardData: HfCardData? = null,
)

@Serializable
private data class HfCardData(
    val license: String? = null,
)

private data class RouterModelHit(
    val id: String,
    val ownedBy: String? = null,
)

/** Models with an explicit HfInferenceClient route — never mint blind nscale discoveries. */
private val KNOWN_INFERENCE_IMAGE_MODELS = setOf(
    "black-forest-labs/FLUX.1-schnell",
    "stabilityai/sdxl-turbo",
    "Tongyi-MAI/Z-Image-Turbo",
    "timbrooks/instruct-pix2pix",
)
