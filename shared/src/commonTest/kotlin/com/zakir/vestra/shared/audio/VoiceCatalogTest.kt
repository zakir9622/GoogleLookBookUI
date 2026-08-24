package com.zakir.vestra.shared.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoiceCatalogTest {
    @Test
    fun personas_cover_varieties() {
        assertTrue(VoiceCatalog.personas.size >= 6)
        assertEquals("amina", VoiceCatalog.defaultId)
        assertEquals("Amina", VoiceCatalog.byId("amina").displayName)
        assertEquals(VoiceCatalog.personas.first(), VoiceCatalog.byId("missing"))
    }
}

class VoiceKnobsTest {
    @Test
    fun sanitized_clamps_metrics() {
        val knobs = VoiceKnobs(
            pitchSemitones = 40f,
            speed = 0.1f,
            formant = 3f,
            warmth = 2f,
            clarity = -1f,
        ).sanitized()
        assertEquals(12f, knobs.pitchSemitones)
        assertEquals(0.5f, knobs.speed)
        assertEquals(1.5f, knobs.formant)
        assertEquals(1f, knobs.warmth)
        assertEquals(0f, knobs.clarity)
    }

    @Test
    fun default_is_near_identity() {
        assertTrue(VoiceKnobs.Default.isIdentity)
        assertFalse(VoiceKnobs(pitchSemitones = 3f).isIdentity)
    }
}
