package com.zakir.vestra.shared.audio

import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PcmQualityAnalyzerTest {

    @Test
    fun analyzeEmptyBufferReturnsZeroDuration() {
        val emptyBytes = ByteArray(0)
        val report = PcmQualityAnalyzer.analyze(emptyBytes, 44100)
        assertEquals(0L, report.durationMs)
        assertEquals(0, report.sampleCount)
        assertEquals(0f, report.peakAmplitude)
        assertFalse(report.isSuitableForCloning)
    }

    @Test
    fun analyzeSineWavePcmCalculatesCorrectMetrics() {
        val sampleRate = 44100
        val durationSeconds = 2.0
        val totalSamples = (sampleRate * durationSeconds).toInt()
        val pcmBytes = ByteArray(totalSamples * 2)

        val frequency = 440.0 // 440 Hz standard tone
        val amplitude = 16000.0 // approx 0.5 peak amp

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val sampleVal = (amplitude * sin(2.0 * Math.PI * frequency * t)).toInt().toShort()
            val byteIndex = i * 2
            pcmBytes[byteIndex] = (sampleVal.toInt() and 0xFF).toByte()
            pcmBytes[byteIndex + 1] = ((sampleVal.toInt() shr 8) and 0xFF).toByte()
        }

        val report = PcmQualityAnalyzer.analyze(pcmBytes, sampleRate, minDurationMs = 1000L)

        assertEquals(2000L, report.durationMs)
        assertEquals(totalSamples, report.sampleCount)
        assertTrue(report.peakAmplitude in 0.48f..0.52f)
        assertFalse(report.clippingDetected)
        assertTrue(report.isSuitableForCloning)
    }

    @Test
    fun analyzeClippedPcmDetectsClipping() {
        val sampleRate = 44100
        val totalSamples = 44100
        val pcmBytes = ByteArray(totalSamples * 2)

        // Saturate samples to 32767
        for (i in 0 until totalSamples) {
            val sampleVal: Short = 32767
            val byteIndex = i * 2
            pcmBytes[byteIndex] = (sampleVal.toInt() and 0xFF).toByte()
            pcmBytes[byteIndex + 1] = ((sampleVal.toInt() shr 8) and 0xFF).toByte()
        }

        val report = PcmQualityAnalyzer.analyze(pcmBytes, sampleRate)
        assertTrue(report.clippingDetected)
        assertFalse(report.isSuitableForCloning)
        assertTrue(report.advisoryNotes.any { it.contains("clipping", ignoreCase = true) })
    }

    @Test
    fun analyzeShortSampleFlagsDuration() {
        val sampleRate = 44100
        val totalSamples = 4410 // 100ms
        val pcmBytes = ByteArray(totalSamples * 2)

        val report = PcmQualityAnalyzer.analyze(pcmBytes, sampleRate, minDurationMs = 1500L)
        assertEquals(100L, report.durationMs)
        assertFalse(report.isSuitableForCloning)
        assertTrue(report.advisoryNotes.any { it.contains("short", ignoreCase = true) })
    }
}
