package com.zakir.vestra.shared.cloud

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Hugging Face Inference Providers (same stack OpenCode uses) for text-to-image when
 * ZeroGPU Spaces are exhausted or offline.
 */
@OptIn(ExperimentalEncodingApi::class)
class HfInferenceClient(
    private val http: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun imageToImage(
        modelId: String,
        prompt: String,
        imageBytes: ByteArray,
        hfToken: String,
        width: Int = 512,
        height: Int = 512,
    ): ByteArray {
        require(hfToken.isNotBlank()) { "HF token required for Inference Providers" }
        require(prompt.isNotBlank()) { "Prompt is empty" }
        require(imageBytes.isNotEmpty()) { "Reference image is empty" }
        val imageB64 = Base64.encode(imageBytes)
        val routes = editRoutesFor(modelId)
        if (routes.isEmpty()) {
            throw CloudFailureException(CloudFailure.RouteUnsupported)
        }
        var lastError: Exception? = null
        for (route in routes) {
            try {
                return when (route.kind) {
                    RouteKind.NSCALE_EDIT -> postNscaleEdit(route.providerModelId, prompt, imageB64, hfToken, width, height)
                    RouteKind.FAL_QUEUE_EDIT -> postFalEdit(route.providerModelId, prompt, imageB64, hfToken, width, height)
                    else -> error("Unsupported edit route for $modelId")
                }
            } catch (e: Exception) {
                lastError = e
                if (e.isNonRetryableInferenceError()) throw e
            }
        }
        throw lastError ?: IllegalStateException("HF Inference edit failed for $modelId")
    }

    suspend fun textToImage(
        modelId: String,
        prompt: String,
        hfToken: String,
        width: Int = 1024,
        height: Int = 1024,
    ): ByteArray {
        require(hfToken.isNotBlank()) { "HF token required for Inference Providers" }
        require(prompt.isNotBlank()) { "Prompt is empty" }
        val routes = routesFor(modelId)
        if (routes.isEmpty()) {
            throw CloudFailureException(CloudFailure.RouteUnsupported)
        }
        var lastError: Exception? = null
        for (route in routes) {
            try {
                return when (route.kind) {
                    RouteKind.NSCALE -> postNscale(route.providerModelId, prompt, hfToken, width, height)
                    RouteKind.FAL_QUEUE -> postFalQueue(route.providerModelId, prompt, hfToken, width, height)
                    RouteKind.NSCALE_EDIT, RouteKind.FAL_QUEUE_EDIT ->
                        error("Edit route in textToImage — use imageToImage()")
                }
            } catch (e: Exception) {
                lastError = e
                if (e.isNonRetryableInferenceError()) throw e
            }
        }
        throw lastError ?: IllegalStateException("HF Inference failed for $modelId")
    }

    /**
     * Text-to-speech via HF Inference (router). Returns raw WAV/FLAC/MP3 bytes.
     */
    suspend fun textToSpeech(
        modelId: String,
        text: String,
        hfToken: String,
    ): ByteArray {
        require(hfToken.isNotBlank()) { "HF token required for Inference TTS" }
        require(text.isNotBlank()) { "Text is empty" }
        val url = "https://router.huggingface.co/hf-inference/models/$modelId"
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                val response = http.post(url) {
                    header("Authorization", "Bearer $hfToken")
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject { put("inputs", text) }.toString())
                }
                if (response.status.value == 503) {
                    delay(1_500L * (attempt + 1))
                    lastError = IllegalStateException("TTS model loading")
                    return@repeat
                }
                if (!response.status.isSuccess()) {
                    val raw = response.bodyAsText()
                    throw IllegalStateException(
                        extractErrorMessage(raw) ?: "TTS HTTP ${response.status.value}",
                    )
                }
                val bytes = response.readRawBytes()
                if (bytes.isEmpty()) error("Empty TTS response")
                return bytes
            } catch (e: Exception) {
                lastError = e
                if (e.isNonRetryableInferenceError()) throw e
                delay(800L * (attempt + 1))
            }
        }
        throw lastError ?: IllegalStateException("HF TTS failed for $modelId")
    }

    private suspend fun postNscale(
        providerModelId: String,
        prompt: String,
        hfToken: String,
        width: Int,
        height: Int,
    ): ByteArray {
        val body = buildJsonObject {
            put("response_format", "b64_json")
            put("prompt", prompt)
            put("model", providerModelId)
            put("size", "${width}x$height")
        }
        val response = http.post("https://router.huggingface.co/nscale/v1/images/generations") {
            header("Authorization", "Bearer $hfToken")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val raw = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw inferenceHttpError(response.status.value, raw, providerModelId)
        }
        val b64 = json.parseToJsonElement(raw).jsonObject["data"]
            ?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("b64_json")
            ?.jsonPrimitive?.content
            ?: error("HF Inference ($providerModelId) returned no image data")
        return Base64.decode(b64)
    }

    private suspend fun postFalQueue(
        providerModelId: String,
        prompt: String,
        hfToken: String,
        width: Int,
        height: Int,
    ): ByteArray {
        val submitBody = buildJsonObject {
            put("prompt", prompt)
            put("image_size", buildJsonObject {
                put("width", width)
                put("height", height)
            })
        }
        val submit = http.post(
            "https://router.huggingface.co/fal-ai/$providerModelId?_subdomain=queue",
        ) {
            header("Authorization", "Bearer $hfToken")
            contentType(ContentType.Application.Json)
            setBody(submitBody)
        }
        val submitRaw = submit.bodyAsText()
        if (!submit.status.isSuccess()) {
            throw inferenceHttpError(submit.status.value, submitRaw, providerModelId)
        }
        val submitJson = json.parseToJsonElement(submitRaw).jsonObject
        val statusUrl = submitJson["status_url"]?.jsonPrimitive?.content
            ?: error("Fal queue did not return status_url")
        val resultUrl = submitJson["response_url"]?.jsonPrimitive?.content
            ?: error("Fal queue did not return response_url")

        repeat(120) {
            val statusResponse = http.get(statusUrl) {
                header("Authorization", "Bearer $hfToken")
            }
            val statusRaw = statusResponse.bodyAsText()
            if (!statusResponse.status.isSuccess()) {
                throw inferenceHttpError(statusResponse.status.value, statusRaw, providerModelId)
            }
            val status = json.parseToJsonElement(statusRaw).jsonObject["status"]?.jsonPrimitive?.content
            if (status == "COMPLETED") {
                val resultResponse = http.get(resultUrl) {
                    header("Authorization", "Bearer $hfToken")
                }
                if (!resultResponse.status.isSuccess()) {
                    throw inferenceHttpError(resultResponse.status.value, resultResponse.bodyAsText(), providerModelId)
                }
                return extractFalImageBytes(resultResponse.bodyAsText())
            }
            delay(1_000)
        }
        error("Timed out waiting for Fal Inference ($providerModelId)")
    }

    private suspend fun extractFalImageBytes(raw: String): ByteArray {
        val root = json.parseToJsonElement(raw).jsonObject
        val url = root["images"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("url")?.jsonPrimitive?.content
            ?: root["image"]?.jsonObject?.get("url")?.jsonPrimitive?.content
            ?: error("Fal response contained no image URL")
        if (url.startsWith("data:")) {
            return Base64.decode(url.substringAfter("base64,"))
        }
        val imageResponse = http.get(url)
        if (!imageResponse.status.isSuccess()) {
            error("Cannot download Fal image (HTTP ${imageResponse.status.value})")
        }
        return imageResponse.readRawBytes()
    }

    private suspend fun postNscaleEdit(
        providerModelId: String,
        prompt: String,
        imageB64: String,
        hfToken: String,
        width: Int,
        height: Int,
    ): ByteArray {
        val body = buildJsonObject {
            put("response_format", "b64_json")
            put("prompt", prompt)
            put("model", providerModelId)
            put("size", "${width}x$height")
            put("image", "data:image/png;base64,$imageB64")
        }
        val response = http.post("https://router.huggingface.co/nscale/v1/images/edits") {
            header("Authorization", "Bearer $hfToken")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val raw = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw inferenceHttpError(response.status.value, raw, providerModelId)
        }
        val b64 = json.parseToJsonElement(raw).jsonObject["data"]
            ?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("b64_json")
            ?.jsonPrimitive?.content
            ?: error("HF Inference edit ($providerModelId) returned no image data")
        return Base64.decode(b64)
    }

    private suspend fun postFalEdit(
        providerModelId: String,
        prompt: String,
        imageB64: String,
        hfToken: String,
        width: Int,
        height: Int,
    ): ByteArray {
        val submitBody = buildJsonObject {
            put("prompt", prompt)
            put("image_url", "data:image/png;base64,$imageB64")
            put("image_size", buildJsonObject {
                put("width", width)
                put("height", height)
            })
        }
        val submit = http.post(
            "https://router.huggingface.co/fal-ai/$providerModelId?_subdomain=queue",
        ) {
            header("Authorization", "Bearer $hfToken")
            contentType(ContentType.Application.Json)
            setBody(submitBody)
        }
        val submitRaw = submit.bodyAsText()
        if (!submit.status.isSuccess()) {
            throw inferenceHttpError(submit.status.value, submitRaw, providerModelId)
        }
        val submitJson = json.parseToJsonElement(submitRaw).jsonObject
        val statusUrl = submitJson["status_url"]?.jsonPrimitive?.content
            ?: error("Fal edit queue did not return status_url")
        val resultUrl = submitJson["response_url"]?.jsonPrimitive?.content
            ?: error("Fal edit queue did not return response_url")

        repeat(120) {
            val statusResponse = http.get(statusUrl) {
                header("Authorization", "Bearer $hfToken")
            }
            val statusRaw = statusResponse.bodyAsText()
            if (!statusResponse.status.isSuccess()) {
                throw inferenceHttpError(statusResponse.status.value, statusRaw, providerModelId)
            }
            val status = json.parseToJsonElement(statusRaw).jsonObject["status"]?.jsonPrimitive?.content
            if (status == "COMPLETED") {
                val resultResponse = http.get(resultUrl) {
                    header("Authorization", "Bearer $hfToken")
                }
                if (!resultResponse.status.isSuccess()) {
                    throw inferenceHttpError(resultResponse.status.value, resultResponse.bodyAsText(), providerModelId)
                }
                return extractFalImageBytes(resultResponse.bodyAsText())
            }
            delay(1_000)
        }
        error("Timed out waiting for Fal Inference edit ($providerModelId)")
    }

    private fun editRoutesFor(modelId: String): List<ProviderRoute> = when (modelId) {
        "timbrooks/instruct-pix2pix" -> listOf(
            ProviderRoute(RouteKind.FAL_QUEUE_EDIT, "fal-ai/instruct-pix2pix"),
            ProviderRoute(RouteKind.NSCALE_EDIT, "timbrooks/instruct-pix2pix"),
        )
        else -> emptyList()
    }

    private fun routesFor(modelId: String): List<ProviderRoute> = when (modelId) {
        "stabilityai/sdxl-turbo" -> listOf(
            ProviderRoute(RouteKind.NSCALE, "stabilityai/sdxl-turbo"),
        )
        "black-forest-labs/FLUX.1-schnell" -> listOf(
            ProviderRoute(RouteKind.NSCALE, "black-forest-labs/FLUX.1-schnell"),
            ProviderRoute(RouteKind.FAL_QUEUE, "fal-ai/flux/schnell"),
        )
        "Tongyi-MAI/Z-Image-Turbo" -> listOf(
            ProviderRoute(RouteKind.FAL_QUEUE, "fal-ai/z-image/turbo"),
        )
        else -> emptyList() // unknown models are RouteUnsupported — do not guess nscale (finding F)
    }

    private fun inferenceHttpError(status: Int, raw: String, modelId: String): Throwable {
        val detail = extractErrorMessage(raw) ?: raw.take(220)
        return when (status) {
            402 -> IllegalStateException(
                "Your Hugging Face Inference Providers monthly credits are used up. " +
                    "Credits reset each month — or use Groq / OpenRouter keys in Settings. ($detail)",
            )
            401, 403 -> IllegalStateException(
                "HF token rejected for Inference Providers. Create a classic token with " +
                    "'Make calls to Inference Providers' enabled, then Save in Settings. ($detail)",
            )
            else -> IllegalStateException("HF Inference ($modelId) HTTP $status: $detail")
        }
    }

    private fun extractErrorMessage(raw: String): String? = runCatching {
        when (val el = json.parseToJsonElement(raw)) {
            is JsonObject -> el["error"]?.let { err ->
                when (err) {
                    is JsonPrimitive -> err.content
                    is JsonObject -> err["message"]?.jsonPrimitive?.content ?: err.toString()
                    else -> null
                }
            }
            is JsonPrimitive -> el.content
            else -> null
        }
    }.getOrNull()

    private fun Exception.isNonRetryableInferenceError(): Boolean {
        val msg = message.orEmpty()
        return msg.contains("402", ignoreCase = true) ||
            msg.contains("depleted your monthly", ignoreCase = true) ||
            msg.contains("token rejected", ignoreCase = true) ||
            msg.contains("401", ignoreCase = true) ||
            msg.contains("403", ignoreCase = true)
    }

    private enum class RouteKind { NSCALE, FAL_QUEUE, NSCALE_EDIT, FAL_QUEUE_EDIT }

    private data class ProviderRoute(val kind: RouteKind, val providerModelId: String)
}
