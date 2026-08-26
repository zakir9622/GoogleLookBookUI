package com.zakir.vestra.shared.logging

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HttpLoggingInterceptorTest {

    @Test
    fun testInterceptorLogsRequestAndResponse() = runTest {
        val capturedLogs = mutableListOf<String>()
        val logManager = LogStateManager()

        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    respond(
                        content = """{"status": "ok"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
            installHttpLogging {
                this.logManager = logManager
                this.customLogger = { _, msg -> capturedLogs.add(msg) }
                this.sanitizeAuthHeaders = true
            }
        }

        val response = client.get("https://api-inference.huggingface.co/models/test") {
            header(HttpHeaders.Authorization, "Bearer hf_secret_123456789")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(capturedLogs.any { it.contains("--> GET") })
        assertTrue(capturedLogs.any { it.contains("auth=Bearer[***]") })
        assertTrue(capturedLogs.any { it.contains("<-- 200 OK") })

        val entries = logManager.entries.value
        assertEquals(2, entries.size)
        assertEquals(LogSource.CLOUD_API, entries[0].source)
    }

    @Test
    fun testInterceptorLogsErrors() = runTest {
        val capturedLogs = mutableListOf<String>()
        val logManager = LogStateManager()

        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = """{"error": "rate limit"}""",
                        status = HttpStatusCode.TooManyRequests,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
            installHttpLogging {
                this.logManager = logManager
                this.customLogger = { _, msg -> capturedLogs.add(msg) }
            }
        }

        val response = client.get("https://openrouter.ai/api/v1/chat/completions")
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertTrue(capturedLogs.any { it.contains("<-- 429") })

        val entries = logManager.entries.value
        assertTrue(entries.any { it.level == LogLevel.WARN && it.message.contains("429") })
    }
}
