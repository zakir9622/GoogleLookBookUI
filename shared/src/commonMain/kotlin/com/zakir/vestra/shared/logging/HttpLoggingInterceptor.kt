package com.zakir.vestra.shared.logging

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.util.date.getTimeMillis

/**
 * Log level for the HTTP logging interceptor.
 */
enum class HttpLogLevel {
    NONE,
    INFO,
    HEADERS,
    BODY_SUMMARY,
}

/**
 * Configuration for [HttpLoggingInterceptor].
 */
class HttpLoggingConfig {
    var level: HttpLogLevel = HttpLogLevel.INFO
    var logManager: LogStateManager? = null
    var customLogger: ((level: LogLevel, message: String) -> Unit)? = null
    var tag: String = "CloudHttp"

    /**
     * Whether to redact Authorization / API key headers in logs for security.
     */
    var sanitizeAuthHeaders: Boolean = true

    /**
     * Max characters of URL or error messages to display.
     */
    var maxMessageLength: Int = 350
}

/**
 * Ktor Client Plugin for tracking, timing, and debugging API calls
 * made to Generative AI services, Hugging Face Spaces, OpenRouter, and model repositories.
 */
val HttpLoggingInterceptor = createClientPlugin("HttpLoggingInterceptor", ::HttpLoggingConfig) {
    val level = pluginConfig.level
    if (level == HttpLogLevel.NONE) return@createClientPlugin

    val logManager = pluginConfig.logManager
    val customLogger = pluginConfig.customLogger
    val tag = pluginConfig.tag
    val sanitize = pluginConfig.sanitizeAuthHeaders
    val maxLen = pluginConfig.maxMessageLength

    fun dispatchLog(logLevel: LogLevel, msg: String) {
        val sanitized = if (msg.length > maxLen) msg.take(maxLen) + "..." else msg
        println("[$tag] $sanitized")
        customLogger?.invoke(logLevel, sanitized)
        logManager?.log(
            source = LogSource.CLOUD_API,
            message = sanitized,
            level = logLevel,
            tag = tag,
        )
    }

    on(Send) { request ->
        val method = request.method.value
        val url = request.url.toString()
        val startTime = getTimeMillis()

        // 1. Log outgoing request
        val authHeader = request.headers[HttpHeaders.Authorization]
        val authStatus = when {
            authHeader.isNullOrBlank() -> "no-auth"
            sanitize -> "auth=Bearer[***]"
            else -> "auth=present"
        }

        val requestDesc = "$method $url ($authStatus)"
        dispatchLog(LogLevel.DEBUG, "--> $requestDesc")

        if (level == HttpLogLevel.HEADERS) {
            val headersStr = request.headers.entries()
                .joinToString(", ") { (k, v) ->
                    val valueStr = if (sanitize && k.equals(HttpHeaders.Authorization, ignoreCase = true)) {
                        "[REDACTED]"
                    } else {
                        v.joinToString(";")
                    }
                    "$k=$valueStr"
                }
            dispatchLog(LogLevel.DEBUG, "    Headers: $headersStr")
        }

        // 2. Execute call & log response
        val response: HttpResponse
        try {
            val call = proceed(request)
            response = call.response
            val durationMs = getTimeMillis() - startTime
            val status = response.status.value
            val statusDesc = response.status.description

            val outcomeDesc = "<-- $status $statusDesc in ${durationMs}ms ($method $url)"
            val logLevel = when {
                status in 200..299 -> LogLevel.INFO
                status in 400..499 -> LogLevel.WARN
                else -> LogLevel.ERROR
            }
            dispatchLog(logLevel, outcomeDesc)

            call
        } catch (t: Throwable) {
            val durationMs = getTimeMillis() - startTime
            val errorMsg = "<-- FAILED after ${durationMs}ms: ${t.message ?: t::class.simpleName} ($method $url)"
            dispatchLog(LogLevel.ERROR, errorMsg)
            throw t
        }
    }
}

/**
 * Extension helper to install [HttpLoggingInterceptor] on any Ktor [HttpClientConfig].
 */
fun HttpClientConfig<*>.installHttpLogging(
    configure: HttpLoggingConfig.() -> Unit = {},
) {
    install(HttpLoggingInterceptor) {
        configure()
    }
}
