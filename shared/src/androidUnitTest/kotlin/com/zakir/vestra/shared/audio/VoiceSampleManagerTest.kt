package com.zakir.vestra.shared.audio

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class VoiceSampleManagerTest {

    @Test
    fun writePcm16MonoWavProducesValidRiffWavHeader() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "voice_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        val wavFile = File(tempDir, "test.wav")
        val sampleRate = 44100
        val numSamples = 4410 // 100ms
        val pcmBytes = ByteArray(numSamples * 2)

        for (i in 0 until numSamples) {
            val sample = (10000 * sin(2.0 * Math.PI * 440.0 * i / sampleRate)).toInt().toShort()
            pcmBytes[i * 2] = (sample.toInt() and 0xFF).toByte()
            pcmBytes[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        VoiceSampleManager.writePcm16MonoWav(wavFile, pcmBytes, sampleRate)

        assertTrue(wavFile.exists())
        val wavBytes = wavFile.readBytes()
        assertEquals(44 + pcmBytes.size, wavBytes.size)

        // Check RIFF and WAVE header markers
        val riff = String(wavBytes, 0, 4)
        val wave = String(wavBytes, 8, 4)
        val fmt = String(wavBytes, 12, 4)
        val data = String(wavBytes, 36, 4)

        assertEquals("RIFF", riff)
        assertEquals("WAVE", wave)
        assertEquals("fmt ", fmt)
        assertEquals("data", data)
    }

    @Test
    fun tensorConversionProducesNormalizedFloats() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "voice_tensor_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        val pcmFile = File(tempDir, "test.pcm")
        val wavFile = File(tempDir, "test.wav")

        val pcmBytes = ByteArray(4)
        // Sample 1: 16384 (approx 0.5f)
        val s1: Short = 16384
        pcmBytes[0] = (s1.toInt() and 0xFF).toByte()
        pcmBytes[1] = ((s1.toInt() shr 8) and 0xFF).toByte()
        // Sample 2: -16384 (approx -0.5f)
        val s2: Short = -16384
        pcmBytes[2] = (s2.toInt() and 0xFF).toByte()
        pcmBytes[3] = ((s2.toInt() shr 8) and 0xFF).toByte()

        pcmFile.writeBytes(pcmBytes)

        val manager = VoiceSampleManager(tempDir)
        val sample = VoiceSample(
            id = "test_1",
            name = "Test",
            pcmPath = pcmFile.absolutePath,
            wavPath = wavFile.absolutePath,
            sampleRate = 44100,
            channels = 1,
            bitsPerSample = 16,
            durationMs = 1000L,
            pcmByteSize = pcmBytes.size.toLong(),
            quality = PcmQualityAnalyzer.analyze(pcmBytes, 44100),
        )

        val floats = manager.readNormalizedFloatTensor(sample)
        assertEquals(2, floats.size)
        assertTrue(floats[0] in 0.49f..0.51f)
        assertTrue(floats[1] in -0.51f..-0.49f)

        val shorts = manager.readRawPcmShorts(sample)
        assertEquals(2, shorts.size)
        assertEquals(16384.toShort(), shorts[0])
        assertEquals((-16384).toShort(), shorts[1])
    }
}
