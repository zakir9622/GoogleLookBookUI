package com.zakir.vestra.shared.cloud

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class LlmResult(
    val text: String,
    val tokensIn: Int = 0,
    val tokensOut: Int = 0,
)

/** OpenAI-compatible chat completions for Groq, OpenRouter, and HF Inference. */
class LlmClient(
    private val http: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun chat(
        platform: CloudPlatform,
        model: String,
        prompt: String,
        apiKey: String,
        system: String = "You are a helpful coding assistant. Return clear, working code with brief explanations.",
        temperature: Double = 0.2,
    ): LlmResult {
        require(apiKey.isNotBlank()) { "API key required for $platform" }
        require(model.isNotBlank()) { "Model id required" }
        require(prompt.isNotBlank()) { "Prompt is empty" }

        val (url, authHeader) = when (platform) {
            CloudPlatform.GROQ ->
                "https://api.groq.com/openai/v1/chat/completions" to "Bearer $apiKey"
            CloudPlatform.OPENROUTER ->
                "https://openrouter.ai/api/v1/chat/completions" to "Bearer $apiKey"
            CloudPlatform.HF_INFERENCE ->
                "https://router.huggingface.co/v1/chat/completions" to "Bearer $apiKey"
            else -> error("LLM not supported on $platform — pick Groq, OpenRouter, or HF Inference in Settings")
        }

        val body = buildJsonObject {
            put("model", model)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "system")
                            put("content", system)
                        },
                    )
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", prompt)
                        },
                    )
                },
            )
            put("temperature", temperature.coerceIn(0.0, 1.5))
            put("max_tokens", 2048)
        }

        val httpResponse = http.post(url) {
            header("Authorization", authHeader)
            if (platform == CloudPlatform.OPENROUTER) {
                header("HTTP-Referer", "https://github.com/zakir9622/Agentic-AI")
                header("X-Title", "The Lookbook")
            }
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val raw = httpResponse.bodyAsText()
        val element = runCatching { json.parseToJsonElement(raw) }.getOrNull()
        val response = element as? JsonObject

        if (!httpResponse.status.isSuccess()) {
            val detail = extractErrorMessage(response) ?: raw.take(200).ifBlank { null }
            error(
                when (httpResponse.status.value) {
                    401, 403 -> "HF/API token rejected (${httpResponse.status.value}). Use a classic Read/Write token (not fine-grained without Inference), then Save in Settings."
                    404 -> "Model not available on free $platform: $model. Switch model in Settings."
                    402 -> "HF Inference monthly credits used up. Wait for reset or add Groq/OpenRouter in Settings."
                    429 -> "Free-tier rate limit on $platform. Wait a minute or switch model."
                    else -> {
                        val msg = detail.orEmpty()
                        when {
                            msg.contains("not supported by any provider", ignoreCase = true) ->
                                "Model '$model' isn't on HF Inference Providers. In Settings → Coding pick Qwen2.5-Coder 32B, Groq Llama, or OpenRouter :free."
                            detail != null -> "$platform HTTP ${httpResponse.status.value}: $detail"
                            else -> "$platform HTTP ${httpResponse.status.value}"
                        }
                    }
                },
            )
        }

        if (response == null) {
            error("Invalid JSON from $platform. Check your token and model in Settings.")
        }

        val content = extractMessageContent(response)
            ?.takeIf { it.isNotBlank() }
            ?: extractReasoningContent(response)
            ?.takeIf { it.isNotBlank() }
            ?: run {
                val err = extractErrorMessage(response)
                error(
                    err?.let { "Empty LLM response from $platform — $it" }
                        ?: "Empty LLM response from $platform. Confirm HF token in Settings, or switch to Groq / OpenRouter :free.",
                )
            }

        val usage = response["usage"]?.jsonObject
        val tokensIn = usage?.get("prompt_tokens")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val tokensOut = usage?.get("completion_tokens")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        return LlmResult(text = content, tokensIn = tokensIn, tokensOut = tokensOut)
    }

    private fun extractMessageContent(response: JsonObject): String? {
        val message = response["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?: return null
        val contentEl = message["content"] ?: return null
        return when (contentEl) {
            is JsonPrimitive -> contentEl.contentOrNull
            is JsonArray -> contentEl.mapNotNull { part ->
                when (part) {
                    is JsonPrimitive -> part.contentOrNull
                    is JsonObject -> part["text"]?.jsonPrimitive?.contentOrNull
                        ?: part["content"]?.jsonPrimitive?.contentOrNull
                    else -> null
                }
            }.joinToString("").ifBlank { null }
            else -> null
        }
    }

    private fun extractReasoningContent(response: JsonObject): String? {
        val message = response["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?: return null
        message["reasoning"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        val details = message["reasoning_details"]?.jsonArray ?: return null
        return details.mapNotNull { part ->
            when (part) {
                is JsonObject -> part["text"]?.jsonPrimitive?.contentOrNull
                    ?: part["content"]?.jsonPrimitive?.contentOrNull
                is JsonPrimitive -> part.contentOrNull
                else -> null
            }
        }.joinToString("").trim().ifBlank { null }
    }

    private fun extractErrorMessage(response: JsonObject?): String? {
        if (response == null) return null
        response["error"]?.let { err ->
            when (err) {
                is JsonPrimitive -> return err.contentOrNull
                is JsonObject -> {
                    err["message"]?.jsonPrimitive?.contentOrNull?.let { return it }
                    err["code"]?.jsonPrimitive?.contentOrNull?.let { return it }
                }
                else -> Unit
            }
        }
        return response["message"]?.jsonPrimitive?.contentOrNull
    }
}
