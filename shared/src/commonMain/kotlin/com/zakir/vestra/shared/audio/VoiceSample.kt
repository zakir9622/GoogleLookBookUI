package com.zakir.vestra.shared.audio

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Configuration options for capturing raw PCM voice samples.
 */
@Serializable
data class VoiceSampleConfig(
    /** Audio sample rate in Hz (e.g. 44100, 48000, 24000, 16000). Standard default is 44.1kHz. */
    val sampleRate: Int = 44_100,
    /** Number of audio channels. Default is 1 (Mono, required for voice cloning models). */
    val channelCount: Int = 1,
    /** Bits per sample. Default is 16 (Linear PCM 16-bit). */
    val bitsPerSample: Int = 16,
    /** Minimum recommended sample duration in milliseconds for voice cloning (default 1000ms). */
    val minDurationMs: Long = 1_000L,
    /** Maximum allowable sample recording duration in milliseconds (default 30000ms). */
    val maxDurationMs: Long = 30_000L,
    /** Multiplier applied to AudioRecord.getMinBufferSize to prevent buffer overruns. */
    val bufferSizeMultiplier: Int = 2,
)

/**
 * Quality and acoustic validation metrics calculated from captured raw PCM samples.
 */
@Serializable
data class VoiceSampleQualityReport(
    /** Duration in milliseconds of captured PCM audio. */
    val durationMs: Long,
    /** Total number of 16-bit audio samples. */
    val sampleCount: Int,
    /** Peak normalized amplitude (0.0 to 1.0). */
    val peakAmplitude: Float,
    /** Peak level in dBFS (-96 dBFS to 0 dBFS). */
    val peakDbFs: Float,
    /** Root Mean Square (RMS) energy level in dBFS. */
    val rmsDbFs: Float,
    /** Estimated Signal-to-Noise Ratio (SNR) in dB. */
    val snrEstimateDb: Float,
    /** Indicates if sample values clipped near digital full scale (abs > 32400). */
    val clippingDetected: Boolean,
    /** High-level determination of whether this sample provides high fidelity for voice cloning. */
    val isSuitableForCloning: Boolean,
    /** Descriptive feedback notes and recommendations for the user. */
    val advisoryNotes: List<String> = emptyList(),
)

/**
 * Represents a high-fidelity voice sample recorded and saved to disk in both raw PCM and WAV formats.
 */
@Serializable
data class VoiceSample(
    val id: String,
    val name: String,
    /** Absolute path to the raw headerless 16-bit little-endian PCM file (.pcm). */
    val pcmPath: String,
    /** Absolute path to the standard RIFF 16-bit PCM WAV file (.wav). */
    val wavPath: String,
    val sampleRate: Int,
    val channels: Int = 1,
    val bitsPerSample: Int = 16,
    val durationMs: Long,
    val pcmByteSize: Long,
    val quality: VoiceSampleQualityReport,
    val createdAt: Long = 0L,
)

/**
 * Reactive state of the VoiceSampleManager capture lifecycle.
 */
sealed interface VoiceSampleCaptureState {
    data object Idle : VoiceSampleCaptureState
    data class Recording(
        val elapsedMs: Long,
        val currentAmplitude: Float,
        val currentDb: Float,
    ) : VoiceSampleCaptureState
    data object Processing : VoiceSampleCaptureState
    data class Completed(val sample: VoiceSample) : VoiceSampleCaptureState
    data class Error(val message: String, val details: String? = null) : VoiceSampleCaptureState
}

/**
 * Result of stopping or saving a voice sample capture session.
 */
sealed interface VoiceSampleResult {
    data class Success(val sample: VoiceSample) : VoiceSampleResult
    data class Error(val message: String, val details: String? = null) : VoiceSampleResult
}

/**
 * Acoustic analysis and quality verification utilities for PCM byte buffers.
 */
object PcmQualityAnalyzer {

    /**
     * Analyzes raw 16-bit linear PCM byte buffer (little-endian mono) and produces a [VoiceSampleQualityReport].
     */
    fun analyze(pcmBytes: ByteArray, sampleRate: Int, minDurationMs: Long = 1000L): VoiceSampleQualityReport {
        val totalShorts = pcmBytes.size / 2
        if (totalShorts == 0) {
            return VoiceSampleQualityReport(
                durationMs = 0L,
                sampleCount = 0,
                peakAmplitude = 0f,
                peakDbFs = -96f,
                rmsDbFs = -96f,
                snrEstimateDb = 0f,
                clippingDetected = false,
                isSuitableForCloning = false,
                advisoryNotes = listOf("No audio data recorded."),
            )
        }

        var peakInt = 0
        var sumSquares = 0.0
        var clipCount = 0
        val clipThreshold = 32_000 // Close to 32767

        // Frame analysis for SNR estimation (100ms frames)
        val frameSamples = max(1, sampleRate / 10)
        val frameEnergies = mutableListOf<Double>()
        var currentFrameEnergy = 0.0
        var frameCount = 0

        for (i in 0 until totalShorts) {
            val byteIndex = i * 2
            val low = pcmBytes[byteIndex].toInt() and 0xFF
            val high = pcmBytes[byteIndex + 1].toInt()
            val sample = (high shl 8) or low
            val absSample = abs(sample)

            if (absSample > peakInt) peakInt = absSample
            if (absSample >= clipThreshold) clipCount++

            val sDouble = sample.toDouble()
            sumSquares += sDouble * sDouble
            currentFrameEnergy += sDouble * sDouble
            frameCount++

            if (frameCount >= frameSamples) {
                frameEnergies.add(currentFrameEnergy / frameCount)
                currentFrameEnergy = 0.0
                frameCount = 0
            }
        }
        if (frameCount > 0) {
            frameEnergies.add(currentFrameEnergy / frameCount)
        }

        val durationMs = (totalShorts.toLong() * 1000L) / sampleRate
        val peakAmp = (peakInt.toFloat() / 32767f).coerceIn(0f, 1f)
        val peakDbFs = if (peakAmp > 0.00001f) (20.0 * log10(peakAmp.toDouble())).toFloat().coerceIn(-96f, 0f) else -96f

        val meanSquare = sumSquares / totalShorts
        val rms = sqrt(meanSquare).toFloat() / 32767f
        val rmsDbFs = if (rms > 0.00001f) (20.0 * log10(rms.toDouble())).toFloat().coerceIn(-96f, 0f) else -96f

        // SNR estimate: Compare top 30% loudest frames (speech) vs lowest 20% quietest frames (noise floor)
        val sortedFrames = frameEnergies.sorted()
        val noiseFloor = if (sortedFrames.isNotEmpty()) {
            val noiseCount = max(1, (sortedFrames.size * 0.2).toInt())
            sortedFrames.take(noiseCount).average()
        } else 1.0

        val signalLevel = if (sortedFrames.isNotEmpty()) {
            val signalCount = max(1, (sortedFrames.size * 0.3).toInt())
            sortedFrames.takeLast(signalCount).average()
        } else 1.0

        val snrDb = if (noiseFloor > 0.0001 && signalLevel > noiseFloor) {
            (10.0 * log10(signalLevel / noiseFloor)).toFloat().coerceIn(0f, 60f)
        } else 0f

        val isClipping = clipCount > 5
        val notes = mutableListOf<String>()

        if (durationMs < minDurationMs) {
            notes.add("Duration is short (${durationMs}ms). 3-10s is recommended for optimal voice cloning.")
        }
        if (peakAmp < 0.15f) {
            notes.add("Input level is low. Speak closer to the microphone for clear vocal harmonics.")
        } else if (isClipping) {
            notes.add("Digital clipping detected ($clipCount peaks). Reduce input volume or speak slightly further.")
        }
        if (snrDb < 10f && durationMs >= minDurationMs) {
            notes.add("High background noise detected. A quieter environment improves cloning clarity.")
        }
        if (notes.isEmpty()) {
            notes.add("High quality vocal capture: Clear dynamic range and low background noise.")
        }

        val suitable = durationMs >= (minDurationMs * 0.75) && peakAmp >= 0.10f && !isClipping

        return VoiceSampleQualityReport(
            durationMs = durationMs,
            sampleCount = totalShorts,
            peakAmplitude = peakAmp,
            peakDbFs = peakDbFs,
            rmsDbFs = rmsDbFs,
            snrEstimateDb = snrDb,
            clippingDetected = isClipping,
            isSuitableForCloning = suitable,
            advisoryNotes = notes,
        )
    }
}
