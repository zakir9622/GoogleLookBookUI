package com.zakir.vestra.shared.engine.local

import com.zakir.vestra.shared.audio.VoiceCatalog
import com.zakir.vestra.shared.audio.VoiceKnobs
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalAudioGeneratorTest {
    @Test
    fun tts_runner_not_wired_in_r2() {
        assertFalse(LocalAudioFlags.TTS_RUNNER_WIRED)
        assertFalse(UnimplementedLocalAudioGenerator.isReady())
        val result = UnimplementedLocalAudioGenerator.generate(
            "hello",
            VoiceCatalog.byId("amina"),
            VoiceKnobs.Default,
        )
        assertTrue(result is LocalAudioResult.Unavailable)
    }

    @Test
    fun unimplemented_voice_changer_unavailable() {
        assertFalse(UnimplementedLocalVoiceChanger.isReady())
        val result = UnimplementedLocalVoiceChanger.transform("/tmp/a.wav", VoiceKnobs.Default)
        assertTrue(result is LocalAudioResult.Unavailable)
    }
}
