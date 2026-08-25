package com.zakir.vestra.shared.engine.local

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.zakir.vestra.shared.audio.VoiceKnobs
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Offline voice changer — true on-device DSP (pitch, speed, formant, warmth, clarity)
 * on mono/stereo 16-bit WAV, MP3, M4A, AAC, and OGG audio. No neural pack required.
 */
class AndroidLocalVoiceChanger(
    private val outputDir: File,
) : LocalVoiceChanger {

    override fun isReady(): Boolean = true

    override fun transform(inputPath: String, knobs: VoiceKnobs): LocalAudioResult {
        val k = knobs.sanitized()
        val input = File(inputPath)
        if (!input.isFile) {
            return LocalAudioResult.Unavailable("Audio file missing: $inputPath")
        }
        return runCatching {
            val wav = readPcm16Wav(input) ?: decodeWithMediaCodec(input)
                ?: return LocalAudioResult.Unavailable(
                    "Could not decode audio file: $inputPath. Please select a valid WAV, MP3, or M4A audio clip.",
                )
            var samples = wav.samples
            samples = applyPitchAndSpeed(samples, k.pitchSemitones, k.speed, k.formant)
            samples = applyTone(samples, k.warmth, k.clarity)
            val out = File(outputDir, "voice_${System.currentTimeMillis()}.wav")
            writePcm16MonoWav(out, samples, wav.sampleRate)
            LocalAudioResult.Ok(out.absolutePath)
        }.getOrElse { err ->
            LocalAudioResult.Unavailable(err.message?.take(160) ?: "Voice changer failed")
        }
    }

    private data class PcmWav(val samples: ShortArray, val sampleRate: Int)

    private fun readPcm16Wav(file: File): PcmWav? {
        val bytes = file.readBytes()
        if (bytes.size < 44) return null
        if (String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") return null
        var offset = 12
        var sampleRate = 22050
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
            offset = next + (size % 2) // word align
            if (dataOffset >= 0 && id == "data") break
        }
        if (dataOffset < 0 || dataSize <= 0) return null
        val totalShorts = dataSize / 2
        val bb = ByteBuffer.wrap(bytes, dataOffset, totalShorts * 2).order(ByteOrder.LITTLE_ENDIAN)

        val samples = if (channels == 1) {
            ShortArray(totalShorts) { bb.short }
        } else {
            // Stereo -> Mono average
            val monoCount = totalShorts / 2
            val mono = ShortArray(monoCount)
            for (i in 0 until monoCount) {
                val left = bb.short.toInt()
                val right = bb.short.toInt()
                mono[i] = ((left + right) / 2).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            mono
        }
        return PcmWav(samples, sampleRate)
    }

    /**
     * Decodes any supported Android audio format (M4A, MP3, AAC, OGG) to 16-bit PCM mono.
     */
    private fun decodeWithMediaCodec(file: File): PcmWav? = runCatching {
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
            22050
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

        val samples = if (channelCount <= 1) {
            ShortArray(totalShorts) { bb.short }
        } else {
            val monoCount = totalShorts / channelCount
            val mono = ShortArray(monoCount)
            for (i in 0 until monoCount) {
                var sum = 0
                for (c in 0 until channelCount) {
                    sum += bb.short.toInt()
                }
                mono[i] = (sum / channelCount).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            mono
        }
        PcmWav(samples, sampleRate)
    }.getOrNull()

    private fun writePcm16MonoWav(file: File, samples: ShortArray, sampleRate: Int) {
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
            writeShortLE(1)
            writeIntLE(sampleRate)
            writeIntLE(sampleRate * 2)
            writeShortLE(2)
            writeShortLE(16)
            writeString("data")
            writeIntLE(dataSize)
            for (s in samples) writeShortLE(s.toInt())
        }
        file.parentFile?.mkdirs()
        file.writeBytes(out.toByteArray())
    }

    /**
     * Combined pitch + speed + formant via resample ratio.
     * pitch↑ shortens period; formant scales independently as a mild ratio bias.
     */
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

    /** Simple shelving EQ: warmth boosts lows, clarity boosts highs. */
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
        // Tiny DC block
        var mean = 0.0
        for (s in out) mean += s
        mean /= out.size.coerceAtLeast(1)
        if (kotlin.math.abs(mean) > 1.0) {
            val m = mean.roundToInt()
            for (i in out.indices) {
                out[i] = (out[i] - m).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
        return out
    }
}
