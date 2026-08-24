package com.zakir.vestra.shared

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Default HTTP client for this platform; keeps engine artifacts out of the app module. */
fun platformHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(ContentNegotiation) {
        // Required so setBody(JsonObject / maps) and body<JsonObject>() work on Android
        // without kotlin-reflect (LinkedHashMap otherwise fails to serialize).
        json(
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
                explicitNulls = false
            },
        )
    }
    install(HttpTimeout) {
        // Default high for pack downloads / long try-on; Gradio polls override per-request
        // to ~12s so one hung ZeroGPU SSE cannot burn the whole image deadline.
        requestTimeoutMillis = 180_000
        connectTimeoutMillis = 20_000
        socketTimeoutMillis = 180_000
    }
}
