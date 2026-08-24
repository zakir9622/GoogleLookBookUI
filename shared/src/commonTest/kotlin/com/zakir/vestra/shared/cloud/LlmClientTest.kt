package com.zakir.vestra.shared.cloud

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LlmClientTest {

    private fun clientWith(bodyCapture: MutableList<String>, responseJson: String): HttpClient {
        val engine = MockEngine { request ->
            val text = when (val content = request.body) {
                is TextContent -> content.text
                else -> content.toString()
            }
            bodyCapture += text
            respond(
                content = responseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    @Test
    fun chatSerializesJsonObjectBodyWithoutMaps() = runTest {
        val captured = mutableListOf<String>()
        val http = clientWith(
            captured,
            """{"choices":[{"message":{"content":"fun ok() = 1"}}],"usage":{"prompt_tokens":3,"completion_tokens":5}}""",
        )
        val result = LlmClient(http).chat(
            platform = CloudPlatform.GROQ,
            model = "llama-3.3-70b-versatile",
            prompt = "Write kotlin",
            apiKey = "gsk_test",
        )
        assertEquals("fun ok() = 1", result.text)
        assertEquals(3, result.tokensIn)
        assertEquals(5, result.tokensOut)
        val body = captured.single()
        assertTrue(body.contains("\"model\""), body)
        assertTrue(body.contains("\"messages\""), body)
        assertTrue(body.startsWith("{"), body)
    }

    @Test
    fun chatRejectsBlankKeyAndPrompt() = runTest {
        val http = clientWith(mutableListOf(), "{}")
        assertFailsWith<IllegalArgumentException> {
            LlmClient(http).chat(CloudPlatform.GROQ, "m", "hi", "")
        }
        assertFailsWith<IllegalArgumentException> {
            LlmClient(http).chat(CloudPlatform.GROQ, "m", "  ", "gsk_x")
        }
    }

    @Test
    fun chatRejectsUnsupportedPlatform() = runTest {
        val http = clientWith(mutableListOf(), "{}")
        assertFailsWith<IllegalStateException> {
            LlmClient(http).chat(CloudPlatform.HF_SPACE, "space", "hi", "token")
        }
    }

    @Test
    fun chatExtractsOpenRouterReasoningWhenContentIsNull() = runTest {
        val captured = mutableListOf<String>()
        val http = clientWith(
            captured,
            """
            {
              "choices": [{
                "message": {
                  "role": "assistant",
                  "content": null,
                  "reasoning": "fun ok() = 1"
                }
              }],
              "usage": {"prompt_tokens": 2, "completion_tokens": 4}
            }
            """.trimIndent(),
        )
        val result = LlmClient(http).chat(
            platform = CloudPlatform.OPENROUTER,
            model = "openrouter/free",
            prompt = "Say OK",
            apiKey = "sk-or-test",
        )
        assertEquals("fun ok() = 1", result.text)
    }

    @Test
    fun chatWorksForHfInferenceAndOpenRouterUrls() = runTest {
        val captured = mutableListOf<String>()
        val http = clientWith(
            captured,
            """{"choices":[{"message":{"content":"ok"}}],"usage":{}}""",
        )
        LlmClient(http).chat(CloudPlatform.HF_INFERENCE, "meta-llama/Llama-3.1-8B-Instruct", "hi", "hf_x")
        LlmClient(http).chat(CloudPlatform.OPENROUTER, "openrouter/free", "hi", "sk-or-x")
        assertEquals(2, captured.size)
    }
}
