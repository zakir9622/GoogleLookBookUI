package com.zakir.vestra.shared.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.zakir.vestra.shared.engine.local.LocalAudioResult
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * True on-device TTS using the phone's system engine (Google TTS / OEM voices).
 * No neural pack download — works offline after the device language pack is installed.
 *
 * Personas map to locale + preferred voice gender when the engine exposes it.
 */
class AndroidSystemTts(
    context: Context,
    private val outputDir: File,
) {
    private val appContext = context.applicationContext
    private val readyLatch = CountDownLatch(1)
    private val engineOk = AtomicReference(false)
    private var tts: TextToSpeech? = null

    init {
        outputDir.mkdirs()
        tts = TextToSpeech(appContext) { status ->
            engineOk.set(status == TextToSpeech.SUCCESS)
            readyLatch.countDown()
        }
    }

    fun isReady(): Boolean {
        // Fast path after init — avoids blocking Compose recomposition on the main thread.
        if (readyLatch.count == 0L) {
            return engineOk.get() && tts != null
        }
        readyLatch.await(4, TimeUnit.SECONDS)
        return engineOk.get() && tts != null
    }

    fun speakToFile(text: String, persona: VoicePersona): LocalAudioResult {
        if (!isReady()) {
            return LocalAudioResult.Unavailable(
                "System TTS not ready — install a Text-to-speech engine / language in Android Settings.",
            )
        }
        val engine = tts ?: return LocalAudioResult.Unavailable("System TTS unavailable")
        val trimmed = text.trim().take(MAX_CHARS)
        if (trimmed.isEmpty()) {
            return LocalAudioResult.Unavailable("Nothing to speak.")
        }
        return runCatching {
            applyPersona(engine, persona)
            val out = File(outputDir, "sys_tts_${System.currentTimeMillis()}.wav")
            val done = CountDownLatch(1)
            val error = AtomicReference<String?>(null)
            val utteranceId = "vestra-${System.nanoTime()}"
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    done.countDown()
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    error.set("System TTS failed")
                    done.countDown()
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    error.set("System TTS error $errorCode")
                    done.countDown()
                }
            })
            val params = android.os.Bundle()
            val result = engine.synthesizeToFile(trimmed, params, out, utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                return LocalAudioResult.Unavailable("System TTS rejected synthesizeToFile ($result)")
            }
            if (!done.await(90, TimeUnit.SECONDS)) {
                return LocalAudioResult.Unavailable("System TTS timed out")
            }
            error.get()?.let { return LocalAudioResult.Unavailable(it) }
            if (!out.isFile || out.length() < 44L) {
                return LocalAudioResult.Unavailable("System TTS produced an empty audio file")
            }
            LocalAudioResult.Ok(out.absolutePath)
        }.getOrElse { err ->
            LocalAudioResult.Unavailable(err.message?.take(160) ?: "System TTS failed")
        }
    }

    fun shutdown() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
    }

    private fun applyPersona(engine: TextToSpeech, persona: VoicePersona) {
        val locale = localeFor(persona)
        engine.language = locale
        val preferFemale = persona.variety.name.startsWith("FEMALE") ||
            persona.variety == VoiceVariety.STORYTELLER ||
            persona.variety == VoiceVariety.NEUTRAL
        val voices = runCatching { engine.voices }.getOrNull().orEmpty()
        val match = voices.firstOrNull { voice ->
            voice.locale.language.equals(locale.language, ignoreCase = true) &&
                when {
                    preferFemale -> !voice.name.contains("male", ignoreCase = true) ||
                        voice.name.contains("female", ignoreCase = true)
                    else -> voice.name.contains("male", ignoreCase = true) &&
                        !voice.name.contains("female", ignoreCase = true)
                }
        } ?: voices.firstOrNull {
            it.locale.language.equals(locale.language, ignoreCase = true)
        }
        if (match != null) {
            runCatching { engine.voice = match }
        }
        // Slight persona spice via speech rate (knobs still applied as DSP after).
        engine.setSpeechRate(
            when (persona.variety) {
                VoiceVariety.ANNOUNCER -> 0.95f
                VoiceVariety.STORYTELLER -> 0.9f
                VoiceVariety.FEMALE_SOFT -> 0.92f
                else -> 1.0f
            },
        )
        engine.setPitch(
            when (persona.variety) {
                VoiceVariety.FEMALE_BRIGHT -> 1.08f
                VoiceVariety.MALE_BARITONE -> 0.88f
                VoiceVariety.MALE_TENOR -> 1.02f
                else -> 1.0f
            },
        )
    }

    private fun localeFor(persona: VoicePersona): Locale = when (persona.id) {
        "rana", "kai" -> Locale.UK
        else -> Locale.US
    }

    companion object {
        private const val MAX_CHARS = 2_000
    }
}
