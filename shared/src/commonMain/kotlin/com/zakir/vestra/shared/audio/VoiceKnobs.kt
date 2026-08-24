package com.zakir.vestra.shared.audio

/**
 * Voice-changer metric knobs applied after TTS (local DSP) or as Space hints.
 * All values are clamped in [sanitized].
 */
data class VoiceKnobs(
    /** Pitch shift in semitones (−12 … +12). */
    val pitchSemitones: Float = 0f,
    /** Playback / synthesis rate (0.5 … 2.0). */
    val speed: Float = 1f,
    /** Formant / timbre shift (0.5 … 1.5); 1 = unchanged. */
    val formant: Float = 1f,
    /** Low-mid warmth (0 … 1). */
    val warmth: Float = 0.45f,
    /** High-end clarity / presence (0 … 1). */
    val clarity: Float = 0.55f,
) {
    fun sanitized(): VoiceKnobs = VoiceKnobs(
        pitchSemitones = pitchSemitones.coerceIn(-12f, 12f),
        speed = speed.coerceIn(0.5f, 2f),
        formant = formant.coerceIn(0.5f, 1.5f),
        warmth = warmth.coerceIn(0f, 1f),
        clarity = clarity.coerceIn(0f, 1f),
    )

    val isIdentity: Boolean
        get() = pitchSemitones == 0f && speed == 1f && formant == 1f &&
            warmth in 0.4f..0.5f && clarity in 0.5f..0.6f

    companion object {
        val Default = VoiceKnobs()
    }
}
