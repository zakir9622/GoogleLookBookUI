package com.zakir.vestra.shared.cloud

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed class TokenValidationState {
    object Idle : TokenValidationState()
    object Validating : TokenValidationState()
    data class Valid(val providerName: String, val accountInfo: String) : TokenValidationState()
    data class Invalid(val message: String) : TokenValidationState()
    data class Error(val reason: String) : TokenValidationState()
}

/**
 * Validates cloud API tokens with live lightweight HTTP checks against provider APIs.
 */
class TokenValidator(
    private val http: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun validateHfToken(token: String): TokenValidationState {
        val trimmed = token.trim()
        if (trimmed.isBlank()) return TokenValidationState.Invalid("Token is empty")
        return try {
            val response = http.get("https://huggingface.co/api/whoami-v2") {
                header(HttpHeaders.Authorization, "Bearer $trimmed")
            }
            if (response.status == HttpStatusCode.OK) {
                val text = response.bodyAsText()
                val parsed = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                val name = parsed?.get("name")?.jsonPrimitive?.content ?: "HF Account"
                val type = parsed?.get("type")?.jsonPrimitive?.content ?: "user"
                TokenValidationState.Valid("Hugging Face", "Valid ($name · $type)")
            } else if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
                TokenValidationState.Invalid("Invalid or unauthorized token")
            } else {
                TokenValidationState.Error("HTTP ${response.status.value}")
            }
        } catch (e: Exception) {
            TokenValidationState.Error(e.message ?: "Network error")
        }
    }

    suspend fun validateGroqKey(key: String): TokenValidationState {
        val trimmed = key.trim()
        if (trimmed.isBlank()) return TokenValidationState.Invalid("Key is empty")
        return try {
            val response = http.get("https://api.groq.com/openai/v1/models") {
                header(HttpHeaders.Authorization, "Bearer $trimmed")
            }
            if (response.status == HttpStatusCode.OK) {
                TokenValidationState.Valid("Groq", "Valid · Fast inference active")
            } else if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
                TokenValidationState.Invalid("Invalid Groq API key")
            } else {
                TokenValidationState.Error("HTTP ${response.status.value}")
            }
        } catch (e: Exception) {
            TokenValidationState.Error(e.message ?: "Network error")
        }
    }

    suspend fun validateOpenRouterKey(key: String): TokenValidationState {
        val trimmed = key.trim()
        if (trimmed.isBlank()) return TokenValidationState.Invalid("Key is empty")
        return try {
            val response = http.get("https://openrouter.ai/api/v1/auth/key") {
                header(HttpHeaders.Authorization, "Bearer $trimmed")
            }
            if (response.status == HttpStatusCode.OK) {
                TokenValidationState.Valid("OpenRouter", "Valid · Free models active")
            } else if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
                TokenValidationState.Invalid("Invalid OpenRouter key")
            } else {
                TokenValidationState.Error("HTTP ${response.status.value}")
            }
        } catch (e: Exception) {
            TokenValidationState.Error(e.message ?: "Network error")
        }
    }
}
