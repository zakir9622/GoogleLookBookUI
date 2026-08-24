package com.zakir.vestra.shared.cloud

import com.russhwolf.settings.Settings
import com.zakir.vestra.shared.settings.AppSettings
import com.zakir.vestra.shared.usage.UsageLedger
import com.zakir.vestra.shared.audio.VoiceCatalog
import com.zakir.vestra.shared.audio.VoiceKnobs
import com.zakir.vestra.shared.engine.local.LocalAudioGenerator
import com.zakir.vestra.shared.engine.local.LocalAudioResult
import com.zakir.vestra.shared.engine.local.LocalAssistResult
import com.zakir.vestra.shared.engine.local.LocalAudioTranscriber
import com.zakir.vestra.shared.engine.local.LocalCodeGenerator
import com.zakir.vestra.shared.engine.local.LocalCodeResult
import com.zakir.vestra.shared.engine.local.LocalTranscribeResult
import com.zakir.vestra.shared.engine.local.LocalVisionAssist
import com.zakir.vestra.shared.engine.local.LiteRtLmPacks
import com.zakir.vestra.shared.engine.local.LocalImageGenerator
import com.zakir.vestra.shared.engine.local.LocalImageResult
import com.zakir.vestra.shared.engine.local.LocalVideoGenerator
import com.zakir.vestra.shared.engine.local.LocalVideoResult
import com.zakir.vestra.shared.engine.local.LocalVoiceChanger
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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class TestMemorySettings : Settings {
    private val map = mutableMapOf<String, Any?>()
    override val keys: Set<String> get() = map.keys
    override val size: Int get() = map.size
    override fun clear() = map.clear()
    override fun remove(key: String) { map.remove(key) }
    override fun hasKey(key: String): Boolean = map.containsKey(key)
    override fun putInt(key: String, value: Int) { map[key] = value }
    override fun getInt(key: String, defaultValue: Int): Int = map[key] as? Int ?: defaultValue
    override fun getIntOrNull(key: String): Int? = map[key] as? Int
    override fun putLong(key: String, value: Long) { map[key] = value }
    override fun getLong(key: String, defaultValue: Long): Long = map[key] as? Long ?: defaultValue
    override fun getLongOrNull(key: String): Long? = map[key] as? Long
    override fun putString(key: String, value: String) { map[key] = value }
    override fun getString(key: String, defaultValue: String): String = map[key] as? String ?: defaultValue
    override fun getStringOrNull(key: String): String? = map[key] as? String
    override fun putFloat(key: String, value: Float) { map[key] = value }
    override fun getFloat(key: String, defaultValue: Float): Float = map[key] as? Float ?: defaultValue
    override fun getFloatOrNull(key: String): Float? = map[key] as? Float
    override fun putDouble(key: String, value: Double) { map[key] = value }
    override fun getDouble(key: String, defaultValue: Double): Double = map[key] as? Double ?: defaultValue
    override fun getDoubleOrNull(key: String): Double? = map[key] as? Double
    override fun putBoolean(key: String, value: Boolean) { map[key] = value }
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = map[key] as? Boolean ?: defaultValue
    override fun getBooleanOrNull(key: String): Boolean? = map[key] as? Boolean
}

private class FakeIo(
    private val localPath: String? = null,
) : CloudImageIo {
    override suspend fun loadImageBytes(person: com.zakir.vestra.shared.domain.PersonSource): ByteArray? =
        byteArrayOf(1, 2, 3)

    override suspend fun loadImageBytes(uri: String): ByteArray? = byteArrayOf(1, 2, 3)

    override fun toDataUrl(jpegBytes: ByteArray): String = "data:image/jpeg;base64,abc"

    override suspend fun downloadResult(urlOrPath: String, spaceHost: String?): String = "/tmp/out.png"

    override fun resolveLocalPath(uri: String): String? = localPath
}

class GenerativeCloudServiceTest {

    private fun httpClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout)
    }

    /** Valid 64×64 PNG header + padding above the 2 KB download floor. */
    private val validPngBytes: ByteArray = byteArrayOf(
        0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(),
        0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D,
        'I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte(),
        0x00, 0x00, 0x00, 0x40,
        0x00, 0x00, 0x00, 0x40,
        0x08, 0x02, 0x00, 0x00, 0x00,
    ) + ByteArray(2_100)

    private val validPngB64: String =
        java.util.Base64.getEncoder().encodeToString(validPngBytes)

    @Test
    fun imageGenFallsBackToHfInferenceWhenSpacesFail() = runTest {
        val hostsCalled = mutableListOf<String>()
        val engine = MockEngine { request ->
            hostsCalled += request.url.host
            when {
                request.url.host.contains("router.huggingface.co") -> respond(
                    """{"data":[{"b64_json":"$validPngB64"}]}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
                request.method.value == "POST" -> respond(
                    """{"event_id":"evt-1"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> respond(
                    "event: error\ndata: null\n\n",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "text/event-stream"),
                )
            }
        }
        val http = httpClient(engine)
        val settings = AppSettings(TestMemorySettings()).apply {
            // Cloud is off by default app-wide; these cases exercise cloud routing.
            setCloudModelsEnabled(true)
            setImageGenProvider("flux-schnell-hf")
            setHfToken("hf_test")
        }
        val service = GenerativeCloudService(http, FakeIo(), settings, UsageLedger(TestMemorySettings()))
        val states = service.generateImage(
            "abaya lookbook",
            referenceUri = null,
            assists = GenerativeAssists(qualityGuard = false),
        ).toList()
        assertTrue(states.any { it is GenerativeState.ImageReady })
        assertTrue(hostsCalled.any { it.contains("router.huggingface.co") })
    }

    @Test
    fun imageGenRejectsBlankInferenceOutputWhenQualityGuardOn() = runTest {
        val engine = MockEngine { request ->
            when {
                request.url.host.contains("router.huggingface.co") -> respond(
                    """{"data":[{"b64_json":"$validPngB64"}]}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
                request.method.value == "POST" -> respond(
                    """{"event_id":"evt-1"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> respond(
                    "event: error\ndata: null\n\n",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "text/event-stream"),
                )
            }
        }
        val settings = AppSettings(TestMemorySettings()).apply {
            // Cloud is off by default app-wide; these cases exercise cloud routing.
            setCloudModelsEnabled(true)
            setImageGenProvider("flux-schnell-hf")
            setHfToken("hf_test")
        }
        val service = GenerativeCloudService(httpClient(engine), FakeIo(), settings, UsageLedger(TestMemorySettings()))
        val states = service.generateImage("abaya lookbook", referenceUri = null).toList()
        assertTrue(states.none { it is GenerativeState.ImageReady })
        assertTrue(states.any { it is GenerativeState.Failed })
    }

    @Test
    fun imageGenDoesNotFallbackToDegradedSdxlWhenFluxFails() = runTest {
        val hostsCalled = mutableListOf<String>()
        val engine = MockEngine { request ->
            hostsCalled += request.url.host
            if (request.method.value == "POST") {
                respond(
                    """{"event_id":"evt-${hostsCalled.size}"}""",
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
        val http = httpClient(engine)
        val settings = AppSettings(TestMemorySettings()).apply {
            // Cloud is off by default app-wide; these cases exercise cloud routing.
            setCloudModelsEnabled(true)
            setImageGenProvider("flux-schnell-hf")
        }
        val service = GenerativeCloudService(http, FakeIo(), settings, UsageLedger(TestMemorySettings()))
        val states = service.generateImage("abaya lookbook", referenceUri = null).toList()
        val failed = states.filterIsInstance<GenerativeState.Failed>().single()
        assertTrue(failed.message.contains("ZeroGPU", ignoreCase = true), failed.message)
        assertTrue(hostsCalled.all { it.contains("flux") }, "Should only hit FLUX, got $hostsCalled")
        assertTrue(hostsCalled.none { it.contains("sdxl", ignoreCase = true) })
    }

    @Test
    fun codeGenFallsBackFromGroqToHfWhenGroqKeyMissing() = runTest {
        var modelSeen = ""
        val engine = MockEngine { request ->
            val body = (request.body as? TextContent)?.text.orEmpty()
            modelSeen = when {
                body.contains("llama-3.3-70b") -> "groq"
                body.contains("Qwen2.5-Coder") -> "hf"
                else -> "other"
            }
            respond(
                """{"choices":[{"message":{"content":"print('hi')"}}],"usage":{"prompt_tokens":1,"completion_tokens":2}}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val http = httpClient(engine)
        val settings = AppSettings(TestMemorySettings()).apply {
            // Cloud is off by default app-wide; these cases exercise cloud routing.
            setCloudModelsEnabled(true)
            setCodeProvider("llama33-70b-groq")
            setHfToken("hf_test")
        }
        val service = GenerativeCloudService(http, FakeIo(), settings, UsageLedger(TestMemorySettings()))
        val states = service.generateCode("hello world").toList()
        val ready = states.filterIsInstance<GenerativeState.CodeReady>().single()
        assertEquals("hf", modelSeen)
        assertEquals("qwen25-coder-7b-hf", ready.providerId)
    }

    @Test
    fun chatWithFallbackUsesNextProviderWhenGroqMissing() = runTest {
        var modelSeen = ""
        val engine = MockEngine { request ->
            val body = (request.body as? TextContent)?.text.orEmpty()
            modelSeen = when {
                body.contains("llama-3.3-70b") -> "groq"
                body.contains("openrouter/free") -> "openrouter"
                else -> "other"
            }
            respond(
                """{"choices":[{"message":{"content":"Hello from chat"}}],"usage":{"prompt_tokens":2,"completion_tokens":3}}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val http = httpClient(engine)
        val settings = AppSettings(TestMemorySettings()).apply {
            // Cloud is off by default app-wide; these cases exercise cloud routing.
            setCloudModelsEnabled(true)
            setCodeProvider("llama33-70b-groq")
            setOpenRouterApiKey("sk-or-test")
        }
        val service = GenerativeCloudService(http, FakeIo(), settings, UsageLedger(TestMemorySettings()))
        val (result, provider) = service.chatWithFallback("hi", system = "Be brief.")
        assertEquals("openrouter", modelSeen)
        assertEquals("openrouter-free", provider.id)
        assertTrue(result.text.contains("Hello"))
    }

    private class FakeLocalImage(
        private val path: String = "/tmp/local.png",
        private val editReady: Boolean = false,
    ) : LocalImageGenerator {
        override fun isReady(): Boolean = true
        override fun isEditReady(): Boolean = editReady
        override fun generate(prompt: String, seed: Long?, referenceImageUri: String?): LocalImageResult =
            LocalImageResult.Ok(path)
    }

    private class FakeLocalCode(
        private val text: String = "fun main() {}",
        private val id: String = "local-gemma-4-e2b-v1",
    ) : LocalCodeGenerator {
        override fun providerId(): String = id
        override fun isReady(): Boolean = true
        override fun generate(prompt: String, system: String): LocalCodeResult =
            LocalCodeResult.Ok(text, tokensIn = 1, tokensOut = 2)
    }

    private class FakeLocalVideo(private val path: String = "/tmp/local.mp4") : LocalVideoGenerator {
        override fun isReady(): Boolean = true
        override fun generate(prompt: String, seed: Long?): LocalVideoResult =
            LocalVideoResult.Ok(path)
    }

    private class FakeLocalVision(private val text: String = "navy silk abaya") : LocalVisionAssist {
        override fun isReady(): Boolean = true
        override fun describeImage(imagePath: String, question: String): LocalAssistResult =
            LocalAssistResult.Ok(text)
    }

    private class FakeLocalTranscriber(private val text: String = "hello world") : LocalAudioTranscriber {
        override fun isReady(): Boolean = true
        override fun transcribe(audioPath: String, prompt: String): LocalTranscribeResult =
            LocalTranscribeResult.Ok(text)
    }

    private class FakeLocalAudio(private val path: String = "/tmp/local.wav") : LocalAudioGenerator {
        override fun isReady(): Boolean = true
        override fun generate(
            text: String,
            persona: com.zakir.vestra.shared.audio.VoicePersona,
            knobs: VoiceKnobs,
            seed: Long?,
        ): LocalAudioResult = LocalAudioResult.Ok(path)
    }

    private class FakeVoiceChanger(private val path: String = "/tmp/changed.wav") : LocalVoiceChanger {
        override fun isReady(): Boolean = true
        override fun transform(inputPath: String, knobs: VoiceKnobs): LocalAudioResult =
            LocalAudioResult.Ok(path)
    }

    @Test
    fun imageGenUsesLocalWhenReadyWithoutHttp() = runTest {
        var httpCalled = false
        val engine = MockEngine {
            httpCalled = true
            respond("{}", HttpStatusCode.OK)
        }
        val http = httpClient(engine)
        val settings = AppSettings(TestMemorySettings()).apply {
            // Cloud is off by default app-wide; these cases exercise cloud routing.
            setCloudModelsEnabled(true)
            networkProbe = { false }
        }
        val service = GenerativeCloudService(
            http,
            FakeIo(),
            settings,
            UsageLedger(TestMemorySettings()),
            localImage = FakeLocalImage(),
        )
        val states = service.generateImage("abaya lookbook", referenceUri = null).toList()
        val ready = states.filterIsInstance<GenerativeState.ImageReady>().single()
        assertEquals("local-sdturbo-v1", ready.providerId)
        assertEquals("/tmp/local.png", ready.path)
        assertTrue(!httpCalled, "Cloud HTTP should not run when local image succeeds offline")
        // Regression: a user's real device log showed "Connecting to FLUX.1 Schnell" (the
        // selected cloud provider) even though this exact scenario ran fully on-device — the
        // message was emitted unconditionally before the local-vs-cloud decision was made.
        assertTrue(
            states.filterIsInstance<GenerativeState.Preparing>().none { it.message.startsWith("Connecting to") },
            "must not announce a cloud provider when local generation is about to run: $states",
        )
    }

    @Test
    fun codeGenUsesLocalWhenReadyWithoutConnectingToCloudMessage() = runTest {
        val engine = MockEngine { respond("{}", HttpStatusCode.OK) }
        val settings = AppSettings(TestMemorySettings()).apply {
            setCloudModelsEnabled(true)
            networkProbe = { false }
        }
        val service = GenerativeCloudService(
            httpClient(engine),
            FakeIo(),
            settings,
            UsageLedger(TestMemorySettings()),
            localCode = FakeLocalCode(),
        )
        val states = service.generateCode("write a function").toList()
        assertTrue(states.filterIsInstance<GenerativeState.CodeReady>().isNotEmpty())
        assertTrue(
            states.filterIsInstance<GenerativeState.Preparing>().none { it.message.startsWith("Connecting to") },
            "must not announce a cloud provider when local code generation is about to run: $states",
        )
    }

    @Test
    fun videoGenUsesLocalWhenReadyWithoutConnectingToCloudMessage() = runTest {
        val engine = MockEngine { respond("{}", HttpStatusCode.OK) }
        val settings = AppSettings(TestMemorySettings()).apply {
            setCloudModelsEnabled(true)
            networkProbe = { false }
        }
        val service = GenerativeCloudService(
            httpClient(engine),
            FakeIo(),
            settings,
            UsageLedger(TestMemorySettings()),
            localVideo = FakeLocalVideo(),
        )
        val states = service.generateVideo("a short clip").toList()
        assertTrue(states.filterIsInstance<GenerativeState.VideoReady>().isNotEmpty())
        assertTrue(
            states.filterIsInstance<GenerativeState.Preparing>().none { it.message.startsWith("Connecting to") },
            "must not announce a cloud provider when local video generation is about to run: $states",
        )
    }

    @Test
    fun videoGenHardStopsOfflineWhenNoLocalPack() = runTest {
        // Regression: video used to soft-continue to cloud with "Network probe uncertain —
        // trying cloud anyway…" when offline, unlike image/code/audio which all hard-stop.
        var httpCalled = false
        val engine = MockEngine {
            httpCalled = true
            respond("{}", HttpStatusCode.OK)
        }
        val settings = AppSettings(TestMemorySettings()).apply {
            setCloudModelsEnabled(true)
            networkProbe = { false }
        }
        val service = GenerativeCloudService(
            httpClient(engine),
            FakeIo(),
            settings,
            UsageLedger(TestMemorySettings()),
        )
        val states = service.generateVideo("abaya clip").toList()
        val failed = states.filterIsInstance<GenerativeState.Failed>().single()
        assertTrue(failed.message.contains("offline", ignoreCase = true), failed.message)
        assertTrue(!httpCalled, "offline with no local pack must not attempt cloud")
    }

    @Test
    fun videoGenHardStopsOfflineWhenLocalFails() = runTest {
        var httpCalled = false
        val engine = MockEngine {
            httpCalled = true
            respond("{}", HttpStatusCode.OK)
        }
        val settings = AppSettings(TestMemorySettings()).apply {
            setCloudModelsEnabled(true)
            networkProbe = { false }
        }
        val service = GenerativeCloudService(
            httpClient(engine),
            FakeIo(),
            settings,
            UsageLedger(TestMemorySettings()),
            localVideo = object : LocalVideoGenerator {
                override fun isReady(): Boolean = true
                override fun generate(prompt: String, seed: Long?) =
                    LocalVideoResult.Unavailable("pack broken")
            },
        )
        val states = service.generateVideo("abaya clip").toList()
        val failed = states.filterIsInstance<GenerativeState.Failed>().single()
        assertTrue(failed.message.contains("offline", ignoreCase = true), failed.message)
        assertTrue(!httpCalled, "offline with a broken local pack must not attempt cloud")
    }

    @Test
    fun audioGenUsesLocalWithoutConnectingToCloudMessage() = runTest {
        val engine = MockEngine { respond("{}", HttpStatusCode.OK) }
        val settings = AppSettings(TestMemorySettings()).apply {
            setCloudModelsEnabled(true)
            networkProbe = { false }
        }
        val service = GenerativeCloudService(
            httpClient(engine),
            FakeIo(),
            settings,
            UsageLedger(TestMemorySettings()),
            localAudio = FakeLocalAudio(),
        )
        val states = service.generateAudio("hello there").toList()
        assertTrue(states.filterIsInstance<GenerativeState.AudioReady>().isNotEmpty())
        assertTrue(
            states.filterIsInstance<GenerativeState.Preparing>().none { it.message.startsWith("Connecting to") },
            "must not announce a cloud provider when local audio generation is about to run: $states",
        )
    }

    @Test
    fun audioGenUsesSystemTtsWhenReadyWithoutHttp() = runTest {
        var httpCalled = false
        val engine = MockEngine {
            httpCalled = true
            respond("{}", HttpStatusCode.OK)
        }
        val http = httpClient(engine)
        val settings = AppSettings(TestMemorySettings())
        val service = GenerativeCloudService(
            http,
            FakeIo(),
            settings,
            UsageLedger(TestMemorySettings()),
            localAudio = FakeLocalAudio(),
        )
        val states = service.generateAudio(
            prompt = "Hello from device",
            persona = VoiceCatalog.byId(VoiceCatalog.defaultId),
        ).toList()
        val ready = states.filterIsInstance<GenerativeState.AudioReady>().single()
        assertEquals("local-tts-system", ready.providerId)
        assertTrue(!httpCalled, "Cloud HTTP should not run when local TTS succeeds")
    }

    @Test
    fun voiceChangeUsesLocalChangerWithoutHttp() = runTest {
        var httpCalled = false
        val engine = MockEngine {
            httpCalled = true
            respond("{}", HttpStatusCode.OK)
        }
        val http = httpClient(engine)
        val settings = AppSettings(TestMemorySettings())
        val service = GenerativeCloudService(
            http,
            FakeIo(),
            settings,
            UsageLedger(TestMemorySettings()),
            localVoiceChanger = FakeVoiceChanger(),
        )
        val states = service.generateAudio(
            prompt = "voice-change",
            persona = VoiceCatalog.byId(VoiceCatalog.defaultId),
            referenceAudioUri = "file:///tmp/input.wav",
        ).toList()
        val ready = states.filterIsInstance<GenerativeState.AudioReady>().single()
        assertEquals("local-voice-changer", ready.providerId)
        assertTrue(!httpCalled, "Cloud HTTP should not run for local voice change")
    }

    @Test
    fun imageEditUsesLocalWhenEditReadyWithoutHttp() = runTest {
        var httpCalled = false
        val engine = MockEngine {
            httpCalled = true
            respond("{}", HttpStatusCode.OK)
        }
        val http = httpClient(engine)
        val settings = AppSettings(TestMemorySettings()).apply {
            // Cloud is off by default app-wide; these cases exercise cloud routing.
            setCloudModelsEnabled(true)
            networkProbe = { false }
        }
        val service = GenerativeCloudService(
            http,
            FakeIo(),
            settings,
            UsageLedger(TestMemorySettings()),
            localImage = FakeLocalImage(editReady = true),
        )
        val states = service.generateImage(
            "make it navy silk",
            referenceUri = "file:///tmp/ref.png",
        ).toList()
        val ready = states.filterIsInstance<GenerativeState.ImageReady>().single()
        assertEquals("local-sdturbo-edit", ready.providerId)
        assertTrue(!httpCalled)
    }

    @Test
    fun codeGenUsesLocalWhenReadyWithoutHttp() = runTest {
        var httpCalled = false
        val engine = MockEngine {
            httpCalled = true
            respond("{}", HttpStatusCode.OK)
        }
        val http = httpClient(engine)
        val settings = AppSettings(TestMemorySettings()).apply {
            // Cloud is off by default app-wide; these cases exercise cloud routing.
            setCloudModelsEnabled(true)
            networkProbe = { false }
        }
        val service = GenerativeCloudService(
            http,
            FakeIo(),
            settings,
            UsageLedger(TestMemorySettings()),
            localCode = FakeLocalCode(),
        )
        val states = service.generateCode("hello world").toList()
        val ready = states.filterIsInstance<GenerativeState.CodeReady>().single()
        assertEquals("local-gemma-4-e2b-v1", ready.providerId)
        assertEquals("fun main() {}", ready.text)
        assertTrue(!httpCalled)
    }

    @Test
    fun videoGenUsesLocalStillClipWhenReadyWithoutHttp() = runTest {
        var httpCalled = false
        val engine = MockEngine {
            httpCalled = true
            respond("{}", HttpStatusCode.OK)
        }
        val http = httpClient(engine)
        val settings = AppSettings(TestMemorySettings()).apply {
            // Cloud is off by default app-wide; these cases exercise cloud routing.
            setCloudModelsEnabled(true)
            networkProbe = { false }
        }
        val service = GenerativeCloudService(
            http,
            FakeIo(),
            settings,
            UsageLedger(TestMemorySettings()),
            localVideo = FakeLocalVideo(),
        )
        val states = service.generateVideo("abaya walking").toList()
        val ready = states.filterIsInstance<GenerativeState.VideoReady>().single()
        assertEquals("local-stillclip-v1", ready.providerId)
        assertEquals("/tmp/local.mp4", ready.path)
        assertTrue(!httpCalled)
    }

    @Test
    fun imageGenUsesLocalWhenUserPrefersLocalWhileOnline() = runTest {
        var httpCalled = false
        val engine = MockEngine {
            httpCalled = true
            respond("{}", HttpStatusCode.OK)
        }
        val http = httpClient(engine)
        val settings = AppSettings(TestMemorySettings()).apply {
            // Cloud is off by default app-wide; these cases exercise cloud routing.
            setCloudModelsEnabled(true)
            networkProbe = { true }
            setLocalGenerator(AiCapability.IMAGE_GEN, "local-sdturbo-v1")
        }
        assertTrue(settings.prefersLocal(AiCapability.IMAGE_GEN))
        val service = GenerativeCloudService(
            http,
            FakeIo(),
            settings,
            UsageLedger(TestMemorySettings()),
            localImage = FakeLocalImage(),
        )
        val states = service.generateImage("emerald abaya", referenceUri = null).toList()
        val ready = states.filterIsInstance<GenerativeState.ImageReady>().single()
        assertEquals("local-sdturbo-v1", ready.providerId)
        assertTrue(!httpCalled, "Explicit local pick must skip cloud while pack is ready")
    }

    @Test
    fun codeGenUsesLocalWhenUserPrefersLocalWhileOnline() = runTest {
        var httpCalled = false
        val engine = MockEngine {
            httpCalled = true
            respond("{}", HttpStatusCode.OK)
        }
        val http = httpClient(engine)
        val settings = AppSettings(TestMemorySettings()).apply {
            // Cloud is off by default app-wide; these cases exercise cloud routing.
            setCloudModelsEnabled(true)
            networkProbe = { true }
            setLocalGenerator(AiCapability.CODE, "local-gemma-v1")
        }
        val service = GenerativeCloudService(
            http,
            FakeIo(),
            settings,
            UsageLedger(TestMemorySettings()),
            localCode = FakeLocalCode(id = "local-gemma-v1"),
        )
        val states = service.generateCode("hello world").toList()
        val ready = states.filterIsInstance<GenerativeState.CodeReady>().single()
        assertEquals("local-gemma-v1", ready.providerId)
        assertTrue(!httpCalled)
    }

    private class UnavailableLocalImage : LocalImageGenerator {
        override fun isReady(): Boolean = true
        override fun isEditReady(): Boolean = false
        override fun generate(prompt: String, seed: Long?, referenceImageUri: String?): LocalImageResult =
            LocalImageResult.Unavailable("pack broken")
    }

    private class CapturingLocalAudio : LocalAudioGenerator {
        var lastText: String? = null
        override fun isReady(): Boolean = true
        override fun generate(
            text: String,
            persona: com.zakir.vestra.shared.audio.VoicePersona,
            knobs: VoiceKnobs,
            seed: Long?,
        ): LocalAudioResult {
            lastText = text
            return LocalAudioResult.Ok("/tmp/captured.wav")
        }
    }

    @Test
    fun imageGenHardStopsOfflineWhenNoLocalPack() = runTest {
        var httpCalled = false
        val engine = MockEngine {
            httpCalled = true
            respond("{}", HttpStatusCode.OK)
        }
        val settings = AppSettings(TestMemorySettings()).apply {
            // Cloud is off by default app-wide; these cases exercise cloud routing.
            setCloudModelsEnabled(true)
            networkProbe = { false }
        }
        val service = GenerativeCloudService(
            httpClient(engine),
            FakeIo(),
            settings,
            UsageLedger(TestMemorySettings()),
            // default localImage is unimplemented / not ready
        )
        val states = service.generateImage("abaya lookbook", referenceUri = null).toList()
        val failed = states.filterIsInstance<GenerativeState.Failed>().single()
        assertTrue(failed.message.contains("offline", ignoreCase = true), failed.message)
        assertTrue(failed.message.contains("on-device", ignoreCase = true), failed.message)
        assertTrue(!httpCalled, "Must not attempt cloud HTTP when offline without local pack")
    }

    @Test
    fun imageGenHardStopsOfflineWhenLocalPackFails() = runTest {
        var httpCalled = false
        val engine = MockEngine {
            httpCalled = true
            respond("{}", HttpStatusCode.OK)
        }
        val settings = AppSettings(TestMemorySettings()).apply {
            // Cloud is off by default app-wide; these cases exercise cloud routing.
            setCloudModelsEnabled(true)
            networkProbe = { false }
        }
        val service = GenerativeCloudService(
            httpClient(engine),
            FakeIo(),
            settings,
            UsageLedger(TestMemorySettings()),
            localImage = UnavailableLocalImage(),
        )
        val states = service.generateImage("abaya lookbook", referenceUri = null).toList()
        val failed = states.filterIsInstance<GenerativeState.Failed>().single()
        assertTrue(failed.message.contains("offline", ignoreCase = true), failed.message)
        assertTrue(!httpCalled)
    }

    @Test
    fun audioFashionAssistChangesSpokenScript() = runTest {
        val capturing = CapturingLocalAudio()
        val settings = AppSettings(TestMemorySettings())
        val service = GenerativeCloudService(
            httpClient(MockEngine { respond("{}", HttpStatusCode.OK) }),
            FakeIo(),
            settings,
            UsageLedger(TestMemorySettings()),
            localAudio = capturing,
        )
        service.generateAudio(
            prompt = "Describe the abaya",
            persona = VoiceCatalog.byId(VoiceCatalog.defaultId),
            assists = GenerativeAssists(fashionContext = true),
        ).toList()
        // The assist must still shape the script, but without the modest-wear framing that used
        // to be injected into every generation — that belongs to try-on, not general TTS.
        val spoken = capturing.lastText.orEmpty()
        assertTrue(
            spoken.contains("voiceover", ignoreCase = true),
            "Fashion assist must enrich the spoken script: $spoken",
        )
        assertTrue(
            !spoken.contains("modest", ignoreCase = true),
            "General audio must not carry modest-wear framing: $spoken",
        )

        capturing.lastText = null
        service.generateAudio(
            prompt = "Describe the abaya",
            persona = VoiceCatalog.byId(VoiceCatalog.defaultId),
            assists = GenerativeAssists(fashionContext = false),
        ).toList()
        assertEquals("Describe the abaya", capturing.lastText)
    }

    @Test
    fun codeGenHardStopsOfflineWhenNoLocalPack() = runTest {
        var httpCalled = false
        val engine = MockEngine {
            httpCalled = true
            respond("{}", HttpStatusCode.OK)
        }
        val settings = AppSettings(TestMemorySettings()).apply {
            // Cloud is off by default app-wide; these cases exercise cloud routing.
            setCloudModelsEnabled(true)
            networkProbe = { false }
        }
        val service = GenerativeCloudService(
            httpClient(engine),
            FakeIo(),
            settings,
            UsageLedger(TestMemorySettings()),
        )
        val states = service.generateCode("hello world").toList()
        val failed = states.filterIsInstance<GenerativeState.Failed>().single()
        assertTrue(failed.message.contains("offline", ignoreCase = true), failed.message)
        assertTrue(!httpCalled)
    }

    @Test
    fun chatUsesLocalWhenReadyWithoutHttp() = runTest {
        var httpCalled = false
        val engine = MockEngine {
            httpCalled = true
            respond("{}", HttpStatusCode.OK)
        }
        val settings = AppSettings(TestMemorySettings()).apply {
            // Cloud is off by default app-wide; these cases exercise cloud routing.
            setCloudModelsEnabled(true)
            networkProbe = { false }
        }
        val service = GenerativeCloudService(
            httpClient(engine),
            FakeIo(),
            settings,
            UsageLedger(TestMemorySettings()),
            localCode = FakeLocalCode(),
        )
        val (result, provider) = service.chatWithFallback("hi", system = "Be brief.")
        assertEquals("fun main() {}", result.text)
        assertEquals("local-gemma-4-e2b-v1", provider.id)
        assertTrue(!httpCalled)
    }

    @Test
    fun imageGenUsesVisionAssistWhenAnalyzeReferenceEnabled() = runTest {
        var httpCalled = false
        val engine = MockEngine {
            httpCalled = true
            respond("{}", HttpStatusCode.OK)
        }
        val settings = AppSettings(TestMemorySettings()).apply {
            // Cloud is off by default app-wide; these cases exercise cloud routing.
            setCloudModelsEnabled(true)
            networkProbe = { false }
            setLocalGenerator(AiCapability.IMAGE_GEN, "local-sdturbo-v1")
        }
        val service = GenerativeCloudService(
            httpClient(engine),
            FakeIo(localPath = "/tmp/ref.png"),
            settings,
            UsageLedger(TestMemorySettings()),
            localImage = FakeLocalImage(editReady = true),
            localVision = FakeLocalVision("emerald linen"),
        )
        val states = service.generateImage(
            "portrait",
            referenceUri = "file:///tmp/ref.png",
            assists = GenerativeAssists(analyzeReference = true),
        ).toList()
        assertTrue(states.any { it is GenerativeState.ImageReady })
        assertTrue(!httpCalled)
    }

    // audioScribePickerTranscribesAttachedClip removed: local-audio-scribe-v1 is no longer
    // catalog-selectable (the published local-gemma-4-e2b-v1 pack ships with audio disabled, so
    // AndroidLocalAudioTranscriber.isReady() can never be true). Beyond setLocalGenerator now
    // rejecting the id, AppSettings.migrateProviderId scrubs any raw-stored non-selectable local
    // id back to a cloud default at construction time — the state this test built is unreachable
    // through any path, public API or raw settings store alike.

    @Test
    fun audioCloudOfflineRecordsOfflineHealthKind() = runTest {
        val engine = MockEngine {
            error("Unable to resolve host \"innoai-Edge-TTS-Text-to-Speech.hf.space\"")
        }
        val settings = AppSettings(TestMemorySettings()).apply {
            // Cloud is off by default app-wide; these cases exercise cloud routing.
            setCloudModelsEnabled(true)
            networkProbe = { true }
            setHfToken("hf_test_token_for_unit")
        }
        val service = GenerativeCloudService(
            httpClient(engine),
            FakeIo(),
            settings,
            UsageLedger(TestMemorySettings()),
        )
        val provider = settings.selectedProvider(AiCapability.AUDIO)
        val states = service.generateAudio(
            prompt = "Hello",
            persona = VoiceCatalog.byId(VoiceCatalog.defaultId),
        ).toList()
        assertTrue(states.any { it is GenerativeState.Failed })
        assertEquals(
            ModelHealthTracker.FailureKind.OFFLINE,
            settings.modelHealth.failureKind(provider.id),
        )
    }
}
