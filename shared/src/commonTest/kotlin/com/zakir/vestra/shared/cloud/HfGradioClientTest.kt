package com.zakir.vestra.shared.cloud

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HfGradioClientTest {

    private fun client(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout)
    }

    @Test
    fun predictSendsJsonObjectNotMap() = runTest {
        var requestBody = ""
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/gradio_api/call/predict") &&
                    request.method.value == "POST" -> {
                    requestBody = (request.body as? TextContent)?.text.orEmpty()
                    respond(
                        """{"event_id":"evt-1"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                else -> respond(
                    "event: complete\ndata: [\"https://example.com/out.png\"]\n\n",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "text/event-stream"),
                )
            }
        }
        val result = HfGradioClient(client(engine)).predict(
            spaceHost = "example.hf.space",
            apiName = "predict",
            data = listOf(kotlinx.serialization.json.JsonPrimitive("hello")),
            hfToken = null,
            maxPolls = 2,
            pollDelayMs = 1,
        )
        assertTrue(requestBody.startsWith("{"), requestBody)
        assertTrue(requestBody.contains("\"data\""), requestBody)
        assertEquals("https://example.com/out.png", result.jsonPrimitive.content)
    }

    @Test
    fun predictRetriesWithTokenAfterAnonymousEmptyError() = runTest {
        // Hugging Face answers an exhausted ZeroGPU allowance with an *empty* error rather than
        // a quota message, so a tokened call fails instantly while the anonymous shared quota
        // still runs the same payload.
        val seenAuthHeaders = mutableListOf<String?>()
        val engine = MockEngine { request ->
            val authorized = request.headers[HttpHeaders.Authorization] != null
            if (request.method.value == "POST") {
                seenAuthHeaders += request.headers[HttpHeaders.Authorization]
                respond(
                    """{"event_id":"evt-1"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                val body = if (!authorized) {
                    "event: error\ndata: null\n\n"
                } else {
                    "event: complete\ndata: [\"https://example.com/out.png\"]\n\n"
                }
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))
            }
        }
        val result = HfGradioClient(client(engine)).predict(
            spaceHost = "example.hf.space",
            apiName = "predict",
            data = listOf(kotlinx.serialization.json.JsonPrimitive("hello")),
            hfToken = "hf_spent_quota",
            maxPolls = 2,
            pollDelayMs = 1,
            wakeRetries = 0,
        )
        assertEquals("https://example.com/out.png", result.jsonPrimitive.content)
        assertEquals(listOf(null, "Bearer hf_spent_quota"), seenAuthHeaders)
    }

    @Test
    fun emptyGradioErrorMentionsTheZeroGpuAllowance() = runTest {
        val engine = MockEngine { request ->
            if (request.method.value == "POST") {
                respond(
                    """{"event_id":"evt-1"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond(
                    "event: error\ndata: null\n\n",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "text/event-stream"),
                )
            }
        }
        val failure = assertFailsWith<IllegalStateException> {
            HfGradioClient(client(engine)).predict(
                spaceHost = "example.hf.space",
                apiName = "predict",
                data = listOf(kotlinx.serialization.json.JsonPrimitive("hello")),
                hfToken = null,
                maxPolls = 2,
                pollDelayMs = 1,
                wakeRetries = 0,
            )
        }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("ZeroGPU", ignoreCase = true), message)
    }

    @Test
    fun predictSkipsAnonymousRetryWhenQuotaIsExplicit() = runTest {
        val seenAuth = mutableListOf<String?>()
        val engine = MockEngine { request ->
            val auth = request.headers[HttpHeaders.Authorization]
            if (request.method.value == "POST") {
                seenAuth += auth
                respond(
                    """{"event_id":"evt-1"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                seenAuth += auth
                val body = if (auth != null) {
                    """event: error
data: {"error": "You have exceeded your free ZeroGPU quota (60s requested vs. 0s left)."}

"""
                } else {
                    "event: error\ndata: null\n\n"
                }
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))
            }
        }
        val failure = assertFailsWith<IllegalStateException> {
            HfGradioClient(client(engine)).predict(
                spaceHost = "example.hf.space",
                apiName = "predict",
                data = listOf(kotlinx.serialization.json.JsonPrimitive("hello")),
                hfToken = "hf_spent",
                maxPolls = 2,
                pollDelayMs = 1,
                wakeRetries = 0,
            )
        }
        assertTrue(failure.message.orEmpty().contains("ZeroGPU", ignoreCase = true))
        assertEquals(listOf("Bearer hf_spent"), seenAuth.filterNotNull().distinct())
        assertTrue(seenAuth.contains(null), "Anonymous should be tried before token")
    }

    @Test
    fun predictRejectsEmptyPayload() = runTest {
        val http = client(MockEngine { respond("", HttpStatusCode.OK) })
        assertFailsWith<IllegalArgumentException> {
            HfGradioClient(http).predict("host", "predict", emptyList(), null)
        }
    }

    @Test
    fun predictHonorsDeadlineBeforeBurningWakeRetries() = runTest {
        var posts = 0
        val engine = MockEngine { request ->
            if (request.method.value == "POST") {
                posts++
                respond(
                    """{"event_id":"evt-1"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond(
                    "event: error\ndata: null\n\n",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "text/event-stream"),
                )
            }
        }
        val deadline = com.zakir.vestra.shared.time.EpochClock.System.nowMs() - 1
        val failure = assertFailsWith<IllegalStateException> {
            HfGradioClient(client(engine)).predict(
                spaceHost = "example.hf.space",
                apiName = "predict",
                data = listOf(kotlinx.serialization.json.JsonPrimitive("hello")),
                hfToken = null,
                maxPolls = 20,
                pollDelayMs = 1,
                wakeRetries = 2,
                deadlineMs = deadline,
            )
        }
        assertTrue(failure.message.orEmpty().contains("Timed out", ignoreCase = true), failure.message)
        assertEquals(0, posts, "Expired deadline must not submit Gradio jobs")
    }

    @Test
    fun pendingPollsAdvanceInsteadOfStallingOnPollOne() = runTest {
        var polls = 0
        val engine = MockEngine { request ->
            if (request.method.value == "POST") {
                respond(
                    """{"event_id":"evt-1"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                polls++
                val body = if (polls >= 3) {
                    "event: complete\ndata: [\"https://example.com/out.png\"]\n\n"
                } else {
                    "event: pending\ndata: null\n\n"
                }
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))
            }
        }
        val seen = mutableListOf<Int>()
        val result = HfGradioClient(client(engine)).predict(
            spaceHost = "example.hf.space",
            apiName = "predict",
            data = listOf(kotlinx.serialization.json.JsonPrimitive("hello")),
            hfToken = null,
            maxPolls = 5,
            pollDelayMs = 1,
            wakeRetries = 0,
            onPoll = { index, _ -> seen += index },
        )
        assertEquals("https://example.com/out.png", result.jsonPrimitive.content)
        assertEquals(listOf(0, 1, 2), seen)
        assertEquals(3, polls)
    }
}
