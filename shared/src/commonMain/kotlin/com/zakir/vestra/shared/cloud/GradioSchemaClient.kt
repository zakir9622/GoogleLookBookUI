package com.zakir.vestra.shared.cloud

import com.zakir.vestra.shared.time.EpochClock
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Fetches live Gradio `/info` schemas and builds payloads by role (finding F / M3).
 */
class GradioSchemaClient(
    private val http: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val cache = mutableMapOf<String, CachedSchema>()

    data class CachedSchema(
        val endpoints: Map<String, List<ParamInfo>>,
        val fetchedAtMs: Long,
    )

    data class ParamInfo(
        val name: String,
        val type: String?,
        val default: JsonElement?,
    )

    suspend fun fetchEndpoints(spaceHost: String, ttlMs: Long = 3_600_000L): Map<String, List<ParamInfo>> {
        val now = EpochClock.System.nowMs()
        cache[spaceHost]?.let { cached ->
            if (now - cached.fetchedAtMs < ttlMs) return cached.endpoints
        }
        val endpoints = fetchLive(spaceHost)
        cache[spaceHost] = CachedSchema(endpoints, now)
        return endpoints
    }

    suspend fun buildPayload(
        spaceHost: String,
        apiName: String,
        roles: Map<String, JsonElement>,
    ): List<JsonElement>? {
        val endpoints = runCatching { fetchEndpoints(spaceHost) }.getOrNull() ?: return null
        val params = endpoints[apiName] ?: endpoints["/$apiName"] ?: return null
        if (params.isEmpty()) return null
        return params.map { param ->
            roles[param.name]
                ?: roles[param.name.lowercase()]
                ?: matchRole(param.name, roles)
                ?: param.default
                ?: JsonNull
        }
    }

    private fun matchRole(name: String, roles: Map<String, JsonElement>): JsonElement? {
        val n = name.lowercase()
        return when {
            n.contains("prompt") || n == "text" || n == "instruction" ->
                roles["prompt"] ?: roles["text"]
            n.contains("image") || n.contains("init") || n == "img" ->
                roles["image"]
            n.contains("negative") -> roles["negative"]
            n.contains("seed") -> roles["seed"]
            n.contains("step") -> roles["steps"]
            n.contains("guidance") || n.contains("cfg") || n.contains("scale") ->
                roles["guidance"]
            n.contains("width") -> roles["width"]
            n.contains("height") -> roles["height"]
            else -> null
        }
    }

    private suspend fun fetchLive(spaceHost: String): Map<String, List<ParamInfo>> {
        val urls = listOf(
            "https://$spaceHost/gradio_api/info",
            "https://$spaceHost/info",
        )
        var lastError: Exception? = null
        for (url in urls) {
            try {
                val response = http.get(url)
                if (!response.status.isSuccess()) continue
                return parseInfo(response.bodyAsText())
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("No Gradio info for $spaceHost")
    }

    fun parseInfo(raw: String): Map<String, List<ParamInfo>> {
        val root = json.parseToJsonElement(raw).jsonObject
        val named = root["named_endpoints"]?.jsonObject
            ?: root["endpoints"]?.jsonObject
            ?: return emptyMap()
        return named.mapValues { (_, endpoint) ->
            val params = endpoint.jsonObject["parameters"]?.jsonArray
                ?: endpoint.jsonObject["inputs"]?.jsonArray
                ?: JsonArray(emptyList())
            params.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val name = obj["parameter_name"]?.jsonPrimitive?.contentOrNull
                    ?: obj["name"]?.jsonPrimitive?.contentOrNull
                    ?: obj["label"]?.jsonPrimitive?.contentOrNull
                    ?: return@mapNotNull null
                ParamInfo(
                    name = name,
                    type = obj["type"]?.jsonPrimitive?.contentOrNull
                        ?: obj["component"]?.jsonPrimitive?.contentOrNull,
                    default = obj["parameter_default"] ?: obj["default"] ?: obj["value"],
                )
            }
        }
    }

    companion object {
        fun promptRoles(
            prompt: String,
            image: JsonElement? = null,
            seed: Int = 0,
            steps: Int = 4,
            guidance: Double = 1.0,
            width: Int = 1024,
            height: Int = 1024,
            negative: String = "",
        ): Map<String, JsonElement> = buildMap {
            put("prompt", JsonPrimitive(prompt))
            put("text", JsonPrimitive(prompt))
            put("seed", JsonPrimitive(seed))
            put("steps", JsonPrimitive(steps))
            put("guidance", JsonPrimitive(guidance))
            put("width", JsonPrimitive(width))
            put("height", JsonPrimitive(height))
            if (negative.isNotBlank()) put("negative", JsonPrimitive(negative))
            if (image != null) put("image", image)
        }
    }
}
