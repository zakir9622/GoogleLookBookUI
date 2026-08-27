package com.zakir.vestra.audio

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class CustomVoiceProfile(
    val id: String,
    val name: String,
    val samplePath: String,
    val emoji: String = "🎙️",
    val detectedPitchHz: Float = 140f,
    val formantScale: Float = 1.0f,
    val warmth: Float = 0.5f,
    val clarity: Float = 0.6f,
    val tremorRateHz: Float = 0f,
    val tremorDepth: Float = 0f,
    val raspyMidGain: Float = 0f,
    val breathiness: Float = 0f,
    val createdAt: Long = System.currentTimeMillis(),
)

object CustomVoiceStorage {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun loadProfiles(dir: File): List<CustomVoiceProfile> {
        val file = File(dir, "custom_voices.json")
        if (!file.exists()) {
            val defaults = defaultSampleProfiles(dir)
            saveProfiles(dir, defaults)
            return defaults
        }
        return runCatching {
            json.decodeFromString<List<CustomVoiceProfile>>(file.readText())
        }.getOrElse { defaultSampleProfiles(dir) }
    }

    fun saveProfiles(dir: File, profiles: List<CustomVoiceProfile>) {
        dir.mkdirs()
        val file = File(dir, "custom_voices.json")
        runCatching {
            file.writeText(json.encodeToString(profiles))
        }
    }

    private fun defaultSampleProfiles(dir: File): List<CustomVoiceProfile> = listOf(
        CustomVoiceProfile(
            id = "sample_grandpa_arthur",
            name = "Grandpa Arthur (Sample)",
            samplePath = "",
            emoji = "👴",
            detectedPitchHz = 95f,
            formantScale = 0.83f,
            warmth = 0.85f,
            clarity = 0.50f,
            tremorRateHz = 5.2f,
            tremorDepth = 0.22f,
            raspyMidGain = 0.40f,
        ),
        CustomVoiceProfile(
            id = "sample_emma_bright",
            name = "Emma Girl (Sample)",
            samplePath = "",
            emoji = "👧",
            detectedPitchHz = 240f,
            formantScale = 1.24f,
            warmth = 0.35f,
            clarity = 0.90f,
        ),
        CustomVoiceProfile(
            id = "sample_marcus_deep",
            name = "Marcus Baritone (Sample)",
            samplePath = "",
            emoji = "👨",
            detectedPitchHz = 105f,
            formantScale = 0.88f,
            warmth = 0.90f,
            clarity = 0.65f,
        ),
    )
}
