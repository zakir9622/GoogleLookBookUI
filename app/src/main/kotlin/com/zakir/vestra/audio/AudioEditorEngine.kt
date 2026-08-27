package com.zakir.vestra.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.zakir.vestra.shared.audio.VoiceEffectPreset
import com.zakir.vestra.shared.audio.VoiceKnobs
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class AudioOutputFormat(val extension: String, val displayName: String, val mimeType: String) {
    MP3("mp3", "MP3 Audio (.mp3)", "audio/mpeg"),
    WAV("wav", "WAV Lossless (.wav)", "audio/wav"),
    M4A("m4a", "M4A AAC (.m4a)", "audio/mp4"),
}

enum class VocalMode(val title: String, val description: String, val emoji: String) {
    KARAOKE_INSTRUMENTAL(
        title = "Remove Vocals (Karaoke)",
        description = "Cancels center vocals while preserving stereo instruments, punchy drums & bassline",
        emoji = "🎤",
    ),
    ISOLATE_VOCALS(
        title = "Isolate Vocals (Acapella)",
        description = "Extracts lead vocals and suppresses background musical instruments",
        emoji = "🗣️",
    ),
    BASS_BOOST(
        title = "Instrumental Bass Boost",
        description = "Enhances low-end bass frequencies and punch for beats and backtracks",
        emoji = "🔊",
    ),
}

data class PcmAudioTrack(
    val samples: ShortArray,
    val channels: Int,
    val sampleRate: Int,
    val durationMs: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PcmAudioTrack) return false
        if (channels != other.channels) return false
        if (sampleRate != other.sampleRate) return false
        if (durationMs != other.durationMs) return false
        return samples.contentEquals(other.samples)
    }

    override fun hashCode(): Int {
        var result = channels
        result = 31 * result + sampleRate
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + samples.contentHashCode()
        return result
    }
}

object AudioEditorEngine {

    /**
     * Decode any standard Android audio format (WAV, MP3, M4A, AAC, OGG) to 16-bit PCM.
     */
    fun decodeAudio(file: File): PcmAudioTrack? {
        if (!file.exists() || file.length() == 0L) return null
        return readPcm16Wav(file) ?: decodeWithMediaCodec(file)
    }

    /**
     * Trim an audio file between [startMs] and [endMs] and export to requested format.
     */
    fun trimAudio(
        inputFile: File,
        startMs: Long,
        endMs: Long,
        outputDir: File,
        outputFormat: AudioOutputFormat = AudioOutputFormat.MP3,
        customName: String? = null,
    ): File? {
        val track = decodeAudio(inputFile) ?: return null
        val totalMs = track.durationMs
        val validStartMs = startMs.coerceIn(0L, totalMs)
        val validEndMs = endMs.coerceIn(validStartMs + 50L, totalMs)

        val samplesPerMs = (track.sampleRate * track.channels) / 1000.0
        val startIndex = (validStartMs * samplesPerMs).toInt().coerceIn(0, track.samples.size)
        val endIndex = (validEndMs * samplesPerMs).toInt().coerceIn(startIndex + 1, track.samples.size)

        val sliceLength = endIndex - startIndex
        if (sliceLength <= 0) return null

        val trimmedSamples = ShortArray(sliceLength)
        System.arraycopy(track.samples, startIndex, trimmedSamples, 0, sliceLength)

        // Apply smooth 10ms micro-fade in/out to eliminate clicks
        val fadeSamples = ((track.sampleRate * track.channels) * 0.010).toInt()
        applyFade(trimmedSamples, fadeSamples)

        val baseName = customName?.takeIf { it.isNotBlank() }
            ?: "${inputFile.nameWithoutExtension}_trim_${System.currentTimeMillis()}"
        val sanitizedBase = baseName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val outFile = File(outputDir, "$sanitizedBase.${outputFormat.extension}")
        outputDir.mkdirs()

        writePcmWav(outFile, trimmedSamples, track.sampleRate, track.channels)
        return outFile
    }

    /**
     * Vocal Remover DSP:
     * - Karaoke / Instrumental: Stereo center channel cancellation (L - R) + low-pass bass reinforcement (<220 Hz)
     * - Vocal Isolation: Center channel extraction + vocal formant bandpass filter (250Hz - 3.8kHz)
     * - Bass Boost: Low frequency amplification and warmth
     */
    fun processVocals(
        inputFile: File,
        outputDir: File,
        mode: VocalMode,
        customName: String? = null,
        outputFormat: AudioOutputFormat = AudioOutputFormat.MP3,
    ): File? {
        val track = decodeAudio(inputFile) ?: return null
        val samples = track.samples
        val channels = track.channels
        val sampleRate = track.sampleRate

        val processedSamples: ShortArray

        if (channels >= 2) {
            // Stereo Track Processing
            val frameCount = samples.size / channels
            processedSamples = ShortArray(samples.size)

            when (mode) {
                VocalMode.KARAOKE_INSTRUMENTAL -> {
                    // Center vocal cancellation: difference of channels (L - R) removes center vocals.
                    // Preserve bassline (<220 Hz) by summing and low-passing bass into both channels.
                    var lowLeft = 0.0
                    var lowRight = 0.0
                    val bassAlpha = (2.0 * Math.PI * 220.0 / sampleRate).coerceIn(0.01, 0.5)

                    for (i in 0 until frameCount) {
                        val left = samples[i * 2].toDouble()
                        val right = samples[i * 2 + 1].toDouble()

                        // Low-pass filter for bass preservation
                        lowLeft += bassAlpha * (left - lowLeft)
                        lowRight += bassAlpha * (right - lowRight)
                        val monoBass = (lowLeft + lowRight) * 0.5

                        // Stereo side channels (instruments, reverbs, pans)
                        val diff = (left - right) * 0.85
                        val outL = (diff + monoBass * 0.8).coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble())
                        val outR = (-diff + monoBass * 0.8).coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble())

                        processedSamples[i * 2] = outL.toInt().toShort()
                        processedSamples[i * 2 + 1] = outR.toInt().toShort()
                    }
                }
                VocalMode.ISOLATE_VOCALS -> {
                    // Center channel vocal extractor: sum (L + R) bandpass filtered
                    var lp1 = 0.0
                    var lp2 = 0.0
                    val lowCutAlpha = (2.0 * Math.PI * 300.0 / sampleRate).coerceIn(0.01, 0.4)
                    val highCutAlpha = (2.0 * Math.PI * 3600.0 / sampleRate).coerceIn(0.05, 0.9)

                    for (i in 0 until frameCount) {
                        val left = samples[i * 2].toDouble()
                        val right = samples[i * 2 + 1].toDouble()
                        val center = (left + right) * 0.5

                        lp1 += lowCutAlpha * (center - lp1)
                        val highPassed = center - lp1
                        lp2 += highCutAlpha * (highPassed - lp2)
                        val vocal = (lp2 * 1.3).coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble()).toInt().toShort()

                        processedSamples[i * 2] = vocal
                        processedSamples[i * 2 + 1] = vocal
                    }
                }
                VocalMode.BASS_BOOST -> {
                    var lowL = 0.0
                    var lowR = 0.0
                    val bassAlpha = (2.0 * Math.PI * 180.0 / sampleRate).coerceIn(0.01, 0.3)
                    for (i in 0 until frameCount) {
                        val left = samples[i * 2].toDouble()
                        val right = samples[i * 2 + 1].toDouble()
                        lowL += bassAlpha * (left - lowL)
                        lowR += bassAlpha * (right - lowR)

                        val outL = (left + lowL * 1.2).coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble())
                        val outR = (right + lowR * 1.2).coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble())
                        processedSamples[i * 2] = outL.toInt().toShort()
                        processedSamples[i * 2 + 1] = outR.toInt().toShort()
                    }
                }
            }
        } else {
            // Mono Track Processing
            processedSamples = ShortArray(samples.size)
            when (mode) {
                VocalMode.KARAOKE_INSTRUMENTAL -> {
                    // Notch filter / band-stop on vocal formant frequencies (350Hz - 3kHz)
                    var lp1 = 0.0
                    var lp2 = 0.0
                    val lowCutAlpha = (2.0 * Math.PI * 350.0 / sampleRate).coerceIn(0.01, 0.4)
                    val highCutAlpha = (2.0 * Math.PI * 3200.0 / sampleRate).coerceIn(0.05, 0.9)

                    for (i in samples.indices) {
                        val x = samples[i].toDouble()
                        lp1 += lowCutAlpha * (x - lp1)
                        val highPassed = x - lp1
                        lp2 += highCutAlpha * (highPassed - lp2)
                        val vocalBand = lp2
                        val out = (x - vocalBand * 0.75).coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble())
                        processedSamples[i] = out.toInt().toShort()
                    }
                }
                VocalMode.ISOLATE_VOCALS -> {
                    var lp1 = 0.0
                    var lp2 = 0.0
                    val lowCutAlpha = (2.0 * Math.PI * 280.0 / sampleRate).coerceIn(0.01, 0.4)
                    val highCutAlpha = (2.0 * Math.PI * 3500.0 / sampleRate).coerceIn(0.05, 0.9)
                    for (i in samples.indices) {
                        val x = samples[i].toDouble()
                        lp1 += lowCutAlpha * (x - lp1)
                        val highPassed = x - lp1
                        lp2 += highCutAlpha * (highPassed - lp2)
                        val out = (lp2 * 1.4).coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble())
                        processedSamples[i] = out.toInt().toShort()
                    }
                }
                VocalMode.BASS_BOOST -> {
                    var low = 0.0
                    val bassAlpha = (2.0 * Math.PI * 180.0 / sampleRate).coerceIn(0.01, 0.3)
                    for (i in samples.indices) {
                        val x = samples[i].toDouble()
                        low += bassAlpha * (x - low)
                        val out = (x + low * 1.3).coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble())
                        processedSamples[i] = out.toInt().toShort()
                    }
                }
            }
        }

        val suffix = when (mode) {
            VocalMode.KARAOKE_INSTRUMENTAL -> "instrumental"
            VocalMode.ISOLATE_VOCALS -> "acapella"
            VocalMode.BASS_BOOST -> "bassboost"
        }
        val baseName = customName?.takeIf { it.isNotBlank() }
            ?: "${inputFile.nameWithoutExtension}_$suffix"
        val sanitized = baseName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val outFile = File(outputDir, "$sanitized.${outputFormat.extension}")
        outputDir.mkdirs()

        writePcmWav(outFile, processedSamples, sampleRate, channels)
        return outFile
    }

    /**
     * Transform voice using preset DSP with instantaneous processing.
     */
    fun transformVoicePreset(
        inputFile: File,
        preset: VoiceEffectPreset,
        outputDir: File,
        outputFormat: AudioOutputFormat = AudioOutputFormat.MP3,
    ): File? {
        val track = decodeAudio(inputFile) ?: return null
        val k = preset.knobs.sanitized()
        var monoSamples = if (track.channels == 1) {
            track.samples
        } else {
            val monoCount = track.samples.size / track.channels
            ShortArray(monoCount) { i ->
                val sum = (track.samples[i * 2].toInt() + track.samples[i * 2 + 1].toInt()) / 2
                sum.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }

        // Apply pitch, speed, and formant shift
        monoSamples = applyPitchAndSpeed(monoSamples, k.pitchSemitones, k.speed, k.formant)
        // Apply EQ tone
        monoSamples = applyTone(monoSamples, k.warmth, k.clarity, k.raspyMidGain)

        // Apply elderly / stylistic vocal tremor LFO if specified
        if (k.tremorDepth > 0f && k.tremorRateHz > 0f) {
            monoSamples = applyVocalTremor(monoSamples, track.sampleRate, k.tremorRateHz, k.tremorDepth)
        }

        // Apply breathiness for whisper preset
        if (k.breathiness > 0f) {
            monoSamples = applyBreathiness(monoSamples, track.sampleRate, k.breathiness)
        }

        // Special effect DSP: Echo / Reverb for cave / spacious presets
        if (preset.id == "echo_cave" || preset.id == "space_droid" || preset.id == "monster_shadow") {
            monoSamples = applyEchoDelay(monoSamples, track.sampleRate, delayMs = 180, decay = 0.38f)
        }

        outputDir.mkdirs()
        val outFile = File(outputDir, "voice_${preset.id}_${System.currentTimeMillis()}.${outputFormat.extension}")
        writePcmWav(outFile, monoSamples, track.sampleRate, 1)
        return outFile
    }

    /**
     * Analyzes an uploaded or recorded voice sample to extract its true acoustic signature:
     * - Fundamental pitch frequency (F0)
     * - Formant / vocal tract resonance ratio
     * - Spectral warmth vs clarity balance
     * - Natural vocal tremor / jitter
     */
    fun analyzeVoiceSample(
        sampleFile: File,
        name: String = "Custom Voice",
        emoji: String = "🎙️",
    ): CustomVoiceProfile? {
        val track = decodeAudio(sampleFile) ?: return null
        if (track.samples.isEmpty()) return null

        val sampleRate = track.sampleRate
        val monoSamples = if (track.channels == 1) {
            track.samples
        } else {
            val monoCount = track.samples.size / track.channels
            ShortArray(monoCount) { i ->
                val sum = (track.samples[i * 2].toInt() + track.samples[i * 2 + 1].toInt()) / 2
                sum.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }

        val estimatedF0 = estimateFundamentalFrequency(monoSamples, sampleRate)
        val (warmth, clarity) = estimateSpectralBalance(monoSamples, sampleRate)
        val formantScale = when {
            estimatedF0 > 210f -> (1.0f + ((estimatedF0 - 210f) / 400f) * 0.35f).coerceIn(1.10f, 1.40f)
            estimatedF0 < 125f -> (1.0f - ((125f - estimatedF0) / 100f) * 0.22f).coerceIn(0.78f, 0.95f)
            else -> 1.0f
        }

        val isElderly = estimatedF0 < 115f && warmth > 0.65f
        val tremorRate = if (isElderly) 5.3f else 0f
        val tremorDepth = if (isElderly) 0.18f else 0f
        val raspy = if (isElderly) 0.35f else 0f

        return CustomVoiceProfile(
            id = "custom_${System.currentTimeMillis()}",
            name = name,
            samplePath = sampleFile.absolutePath,
            emoji = emoji,
            detectedPitchHz = estimatedF0,
            formantScale = formantScale,
            warmth = warmth,
            clarity = clarity,
            tremorRateHz = tremorRate,
            tremorDepth = tremorDepth,
            raspyMidGain = raspy,
            createdAt = System.currentTimeMillis(),
        )
    }

    /**
     * Transforms input audio to match a cloned [CustomVoiceProfile] acoustic profile.
     */
    fun transformToCustomVoice(
        inputFile: File,
        profile: CustomVoiceProfile,
        outputDir: File,
        outputFormat: AudioOutputFormat = AudioOutputFormat.MP3,
    ): File? {
        val track = decodeAudio(inputFile) ?: return null
        val sampleRate = track.sampleRate
        var monoSamples = if (track.channels == 1) {
            track.samples
        } else {
            val monoCount = track.samples.size / track.channels
            ShortArray(monoCount) { i ->
                val sum = (track.samples[i * 2].toInt() + track.samples[i * 2 + 1].toInt()) / 2
                sum.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }

        // Estimate source pitch to compute exact pitch difference
        val sourceF0 = estimateFundamentalFrequency(monoSamples, sampleRate)
        val targetF0 = profile.detectedPitchHz.coerceIn(65f, 500f)
        val pitchSemitones = (12.0 * (Math.log((targetF0 / sourceF0).toDouble()) / Math.log(2.0)))
            .toFloat()
            .coerceIn(-12f, 12f)

        monoSamples = applyPitchAndSpeed(
            samples = monoSamples,
            pitchSemitones = pitchSemitones,
            speed = 1.0f,
            formant = profile.formantScale,
        )

        monoSamples = applyTone(
            samples = monoSamples,
            warmth = profile.warmth,
            clarity = profile.clarity,
            raspyMidGain = profile.raspyMidGain,
        )

        if (profile.tremorDepth > 0f && profile.tremorRateHz > 0f) {
            monoSamples = applyVocalTremor(monoSamples, sampleRate, profile.tremorRateHz, profile.tremorDepth)
        }

        outputDir.mkdirs()
        val safeName = profile.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val outFile = File(outputDir, "custom_${safeName}_${System.currentTimeMillis()}.${outputFormat.extension}")
        writePcmWav(outFile, monoSamples, sampleRate, 1)
        return outFile
    }

    /**
     * Estimates fundamental frequency (F0) using Normalized Autocorrelation with parabolic interpolation.
     */
    private fun estimateFundamentalFrequency(samples: ShortArray, sampleRate: Int): Float {
        if (samples.size < 512) return 140f
        val windowSize = min(2048, samples.size)
        val minLag = (sampleRate / 550).coerceAtLeast(2) // 550 Hz max
        val maxLag = (sampleRate / 65).coerceAtMost(windowSize / 2) // 65 Hz min

        var bestLag = -1
        var maxCorr = 0.0

        for (lag in minLag..maxLag) {
            var sum = 0.0
            var normA = 0.0
            var normB = 0.0
            for (i in 0 until (windowSize - lag)) {
                val a = samples[i].toDouble()
                val b = samples[i + lag].toDouble()
                sum += a * b
                normA += a * a
                normB += b * b
            }
            val norm = sqrt(normA * normB)
            val corr = if (norm > 0.0001) sum / norm else 0.0
            if (corr > maxCorr) {
                maxCorr = corr
                bestLag = lag
            }
        }

        if (bestLag > 0 && maxCorr > 0.35) {
            return (sampleRate.toFloat() / bestLag).coerceIn(65f, 500f)
        }
        return 140f // Default male/neutral midpoint
    }

    /**
     * Estimates spectral warmth and clarity from spectral distribution.
     */
    private fun estimateSpectralBalance(samples: ShortArray, sampleRate: Int): Pair<Float, Float> {
        var lowEnergy = 0.0
        var midEnergy = 0.0
        var highEnergy = 0.0

        val step = max(1, samples.size / 4000)
        var prev = 0.0
        for (i in 0 until samples.size step step) {
            val curr = samples[i].toDouble()
            val diff = curr - prev
            val absCurr = abs(curr)
            val absDiff = abs(diff)

            lowEnergy += absCurr
            highEnergy += absDiff
            midEnergy += (absCurr + absDiff) * 0.5
            prev = curr
        }

        val total = (lowEnergy + highEnergy + 0.001)
        val warmth = ((lowEnergy / total) * 1.5).coerceIn(0.2, 0.95).toFloat()
        val clarity = ((highEnergy / total) * 1.6).coerceIn(0.3, 0.95).toFloat()
        return Pair(warmth, clarity)
    }

    /**
     * Applies vocal tremor / aging LFO modulation.
     */
    private fun applyVocalTremor(samples: ShortArray, sampleRate: Int, rateHz: Float, depth: Float): ShortArray {
        val out = ShortArray(samples.size)
        val twoPi = 2.0 * Math.PI
        val step = (twoPi * rateHz) / sampleRate

        for (i in samples.indices) {
            val lfo = 1.0 + depth * 0.45 * Math.sin(i * step)
            val v = (samples[i] * lfo).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            out[i] = v.toShort()
        }
        return out
    }

    /**
     * Applies breathiness and high frequency airy formant modulation.
     */
    private fun applyBreathiness(samples: ShortArray, sampleRate: Int, breathFactor: Float): ShortArray {
        val out = ShortArray(samples.size)
        var lp = 0.0
        val alpha = (2.0 * Math.PI * 400.0 / sampleRate).coerceIn(0.01, 0.3)
        var rng = 12345

        for (i in samples.indices) {
            val x = samples[i].toDouble()
            lp += alpha * (x - lp)
            val highPass = x - lp

            // Pseudo random noise for breath simulation
            rng = (rng * 1103515245 + 12345) and 0x7fffffff
            val noise = ((rng.toFloat() / 0x7fffffff) - 0.5f) * 1500f * breathFactor

            val y = (highPass * (1.0f + breathFactor * 0.5f) + noise).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            out[i] = y.toShort()
        }
        return out
    }

    private fun applyTone(samples: ShortArray, warmth: Float, clarity: Float, raspyMidGain: Float = 0f): ShortArray {
        val out = ShortArray(samples.size)
        var low = 0f
        var mid = 0f
        var high = 0f
        val lowAlpha = 0.08f + warmth * 0.12f
        val highAlpha = 0.15f + clarity * 0.25f
        val midAlpha = 0.18f
        val lowGain = 0.7f + warmth * 0.8f
        val highGain = 0.7f + clarity * 0.9f
        val raspyGain = 1.0f + raspyMidGain * 0.65f

        for (i in samples.indices) {
            val x = samples[i] / 32768f
            low += lowAlpha * (x - low)
            high = highAlpha * (x - low) + (1f - highAlpha) * high
            mid += midAlpha * (x - mid)
            val raspyTexture = (mid - low) * raspyMidGain * 0.4f
            val y = (low * lowGain + high * highGain + raspyTexture * raspyGain).coerceIn(-1f, 1f)
            out[i] = (y * 32767f).roundToInt().toShort()
        }
        return out
    }

    /**
     * Extract normalized waveform amplitude bars (0.05 to 1.0) for UI visualization.
     */
    fun extractWaveform(inputFile: File, barCount: Int = 48): List<Float> {
        val track = decodeAudio(inputFile) ?: return List(barCount) { 0.2f }
        val samples = track.samples
        if (samples.isEmpty()) return List(barCount) { 0.2f }

        val chunkSize = max(1, samples.size / barCount)
        val amplitudes = ArrayList<Float>(barCount)

        for (i in 0 until barCount) {
            val start = i * chunkSize
            val end = min(samples.size, start + chunkSize)
            if (start >= samples.size) {
                amplitudes.add(0.1f)
                continue
            }
            var sumSquare = 0.0
            var count = 0
            for (j in start until end) {
                val s = samples[j] / 32768.0
                sumSquare += s * s
                count++
            }
            val rms = if (count > 0) sqrt(sumSquare / count).toFloat() else 0.1f
            val normalized = (rms * 3.5f).coerceIn(0.08f, 1.0f)
            amplitudes.add(normalized)
        }
        return amplitudes
    }

    private fun applyFade(samples: ShortArray, fadeSamples: Int) {
        val count = min(fadeSamples, samples.size / 2)
        for (i in 0 until count) {
            val factor = i.toFloat() / count
            samples[i] = (samples[i] * factor).toInt().toShort()
            val endIdx = samples.size - 1 - i
            samples[endIdx] = (samples[endIdx] * factor).toInt().toShort()
        }
    }

    private fun applyEchoDelay(samples: ShortArray, sampleRate: Int, delayMs: Int, decay: Float): ShortArray {
        val delaySamples = (sampleRate * (delayMs / 1000.0)).toInt()
        val outLen = samples.size + delaySamples
        val out = ShortArray(outLen)
        for (i in samples.indices) {
            out[i] = samples[i]
        }
        for (i in delaySamples until outLen) {
            val srcIdx = i - delaySamples
            if (srcIdx < samples.size) {
                val echo = (samples[srcIdx] * decay).toInt()
                val current = out[i].toInt()
                out[i] = (current + echo).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
        return out
    }

    private fun applyPitchAndSpeed(
        samples: ShortArray,
        pitchSemitones: Float,
        speed: Float,
        formant: Float,
    ): ShortArray {
        val pitchRatio = 2.0.pow((pitchSemitones / 12.0)).toFloat()
        val readStep = (pitchRatio * formant.coerceIn(0.85f, 1.15f) / speed.coerceAtLeast(0.01f))
            .coerceIn(0.25f, 4f)
        val outLen = (samples.size / readStep).roundToInt().coerceAtLeast(1)
        val out = ShortArray(outLen)
        var pos = 0.0
        for (i in 0 until outLen) {
            val idx = pos.toInt().coerceIn(0, samples.size - 1)
            val frac = (pos - idx).toFloat()
            val a = samples[idx].toInt()
            val b = samples[(idx + 1).coerceAtMost(samples.size - 1)].toInt()
            out[i] = (a + (b - a) * frac).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            pos += readStep
            if (pos >= samples.size - 1) break
        }
        return out
    }

    private fun applyTone(samples: ShortArray, warmth: Float, clarity: Float): ShortArray {
        val out = ShortArray(samples.size)
        var low = 0f
        var high = 0f
        val lowAlpha = 0.08f + warmth * 0.12f
        val highAlpha = 0.15f + clarity * 0.25f
        val lowGain = 0.7f + warmth * 0.8f
        val highGain = 0.7f + clarity * 0.9f
        for (i in samples.indices) {
            val x = samples[i] / 32768f
            low += lowAlpha * (x - low)
            high = highAlpha * (x - low) + (1f - highAlpha) * high
            val y = (low * lowGain + high * highGain).coerceIn(-1f, 1f)
            out[i] = (y * 32767f).roundToInt().toShort()
        }
        return out
    }

    private fun readPcm16Wav(file: File): PcmAudioTrack? {
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        if (bytes.size < 44) return null
        if (String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") return null
        var offset = 12
        var sampleRate = 44100
        var channels = 1
        var bits = 16
        var dataOffset = -1
        var dataSize = 0
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4)
            val size = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val next = offset + 8 + size
            when (id) {
                "fmt " -> {
                    val bb = ByteBuffer.wrap(bytes, offset + 8, size).order(ByteOrder.LITTLE_ENDIAN)
                    val format = bb.short.toInt() and 0xffff
                    channels = bb.short.toInt() and 0xffff
                    sampleRate = bb.int
                    bb.int // byte rate
                    bb.short // block align
                    bits = bb.short.toInt() and 0xffff
                    if (format != 1 || (channels != 1 && channels != 2) || bits != 16) return null
                }
                "data" -> {
                    dataOffset = offset + 8
                    dataSize = size
                }
            }
            offset = next + (size % 2)
            if (dataOffset >= 0 && id == "data") break
        }
        if (dataOffset < 0 || dataSize <= 0) return null
        val totalShorts = min(dataSize / 2, (bytes.size - dataOffset) / 2)
        val bb = ByteBuffer.wrap(bytes, dataOffset, totalShorts * 2).order(ByteOrder.LITTLE_ENDIAN)
        val samples = ShortArray(totalShorts) { bb.short }
        val durationMs = ((totalShorts.toLong() * 1000L) / (sampleRate * channels)).coerceAtLeast(1L)
        return PcmAudioTrack(samples, channels, sampleRate, durationMs)
    }

    private fun decodeWithMediaCodec(file: File): PcmAudioTrack? = runCatching {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        var audioTrackIndex = -1
        var audioFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                audioFormat = format
                break
            }
        }
        if (audioTrackIndex < 0 || audioFormat == null) {
            extractor.release()
            return@runCatching null
        }
        extractor.selectTrack(audioTrackIndex)
        val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: return@runCatching null
        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(audioFormat, null, null, 0)
        decoder.start()

        val sampleRate = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        } else {
            44100
        }
        var channelCount = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        } else {
            1
        }

        val pcmOut = ByteArrayOutputStream()
        val info = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false
        val timeoutUs = 5000L

        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                val inputIndex = decoder.dequeueInputBuffer(timeoutUs)
                if (inputIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
            }

            val outputIndex = decoder.dequeueOutputBuffer(info, timeoutUs)
            if (outputIndex >= 0) {
                val outputBuffer = decoder.getOutputBuffer(outputIndex)
                if (outputBuffer != null && info.size > 0) {
                    outputBuffer.position(info.offset)
                    outputBuffer.limit(info.offset + info.size)
                    val chunk = ByteArray(info.size)
                    outputBuffer.get(chunk)
                    pcmOut.write(chunk)
                }
                decoder.releaseOutputBuffer(outputIndex, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true
                }
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = decoder.outputFormat
                if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    channelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
            }
        }

        decoder.stop()
        decoder.release()
        extractor.release()

        val rawPcm = pcmOut.toByteArray()
        if (rawPcm.size < 4) return@runCatching null
        val totalShorts = rawPcm.size / 2
        val bb = ByteBuffer.wrap(rawPcm).order(ByteOrder.LITTLE_ENDIAN)
        val samples = ShortArray(totalShorts) { bb.short }
        val durationMs = ((totalShorts.toLong() * 1000L) / (sampleRate * channelCount)).coerceAtLeast(1L)
        PcmAudioTrack(samples, channelCount, sampleRate, durationMs)
    }.getOrNull()

    private fun writePcmWav(file: File, samples: ShortArray, sampleRate: Int, channels: Int) {
        val dataSize = samples.size * 2
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { dos ->
            fun writeString(s: String) = dos.writeBytes(s)
            fun writeIntLE(v: Int) {
                dos.write(v and 0xff)
                dos.write((v shr 8) and 0xff)
                dos.write((v shr 16) and 0xff)
                dos.write((v shr 24) and 0xff)
            }
            fun writeShortLE(v: Int) {
                dos.write(v and 0xff)
                dos.write((v shr 8) and 0xff)
            }
            writeString("RIFF")
            writeIntLE(36 + dataSize)
            writeString("WAVE")
            writeString("fmt ")
            writeIntLE(16)
            writeShortLE(1)
            writeShortLE(channels)
            writeIntLE(sampleRate)
            writeIntLE(sampleRate * channels * 2)
            writeShortLE(channels * 2)
            writeShortLE(16)
            writeString("data")
            writeIntLE(dataSize)
            for (s in samples) writeShortLE(s.toInt())
        }
        file.parentFile?.mkdirs()
        file.writeBytes(out.toByteArray())
    }
}
