package com.zakir.vestra.shared.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max

/**
 * High-fidelity Voice Sample Capture Manager for AI Voice Cloning models.
 * Uses standard Android [AudioRecord] APIs to capture raw, uncompressed 16-bit linear PCM audio
 * and outputs both headerless .pcm binaries (for direct neural tensor ingestion) and standard .wav files.
 */
class VoiceSampleManager(
    private val outputDir: File,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {
    private val _state = MutableStateFlow<VoiceSampleCaptureState>(VoiceSampleCaptureState.Idle)
    val state: StateFlow<VoiceSampleCaptureState> = _state.asStateFlow()

    private val _amplitudeFlow = MutableStateFlow(0f)
    val amplitudeFlow: StateFlow<Float> = _amplitudeFlow.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val recordingFlag = AtomicBoolean(false)
    private var workerThread: Thread? = null
    private var audioRecordInstance: AudioRecord? = null

    @Volatile
    private var activeConfig: VoiceSampleConfig = VoiceSampleConfig()
    @Volatile
    private var activeName: String = "Voice Sample"
    @Volatile
    private var activeSessionId: String = ""
    @Volatile
    private var startTimeMs: Long = 0L

    private var capturedPcmBuffer = ByteArrayOutputStream()

    /**
     * Initiates audio recording using standard Android [AudioRecord] API.
     *
     * @param name Descriptive label for the voice sample
     * @param config Capture settings (sample rate, bit depth, channel configuration)
     * @return true if recording successfully started, false otherwise
     */
    fun startRecording(
        name: String = "Voice Sample",
        config: VoiceSampleConfig = VoiceSampleConfig(),
    ): Boolean {
        if (!recordingFlag.compareAndSet(false, true)) {
            return false
        }

        activeName = name
        activeConfig = config
        activeSessionId = "sample_${System.currentTimeMillis()}"
        startTimeMs = System.currentTimeMillis()
        capturedPcmBuffer = ByteArrayOutputStream()

        val sampleRate = config.sampleRate
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )

        if (minBufferSize <= 0) {
            recordingFlag.set(false)
            _isRecording.value = false
            _state.value = VoiceSampleCaptureState.Error(
                "AudioRecord buffer initialization failed for sample rate $sampleRate Hz.",
            )
            return false
        }

        val bufferSize = minBufferSize * config.bufferSizeMultiplier

        // Try primary voice recognition source, fallback to mic if needed
        val audioSources = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT,
        )

        var record: AudioRecord? = null
        var lastErrorMsg: String? = null

        for (source in audioSources) {
            try {
                val candidate = AudioRecord(
                    source,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                )
                if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                    record = candidate
                    break
                } else {
                    candidate.release()
                }
            } catch (sec: SecurityException) {
                lastErrorMsg = "RECORD_AUDIO permission not granted: ${sec.message}"
                break
            } catch (ex: Exception) {
                lastErrorMsg = ex.message
            }
        }

        if (record == null) {
            recordingFlag.set(false)
            _isRecording.value = false
            _state.value = VoiceSampleCaptureState.Error(
                lastErrorMsg ?: "Failed to initialize AudioRecord with supported sample rates.",
            )
            return false
        }

        audioRecordInstance = record
        _isRecording.value = true
        _state.value = VoiceSampleCaptureState.Recording(0L, 0f, -96f)

        workerThread = thread(name = "voice-sample-recorder", isDaemon = true) {
            val readBuffer = ByteArray(minBufferSize)
            try {
                record.startRecording()
                val deadline = System.currentTimeMillis() + config.maxDurationMs

                while (recordingFlag.get() && System.currentTimeMillis() < deadline) {
                    val bytesRead = record.read(readBuffer, 0, readBuffer.size)
                    if (bytesRead > 0) {
                        capturedPcmBuffer.write(readBuffer, 0, bytesRead)

                        // Compute instantaneous peak & RMS amplitude for meters
                        var peak = 0
                        val shortsCount = bytesRead / 2
                        for (i in 0 until shortsCount) {
                            val low = readBuffer[i * 2].toInt() and 0xFF
                            val high = readBuffer[i * 2 + 1].toInt()
                            val sample = abs((high shl 8) or low)
                            if (sample > peak) peak = sample
                        }
                        val normAmp = (peak.toFloat() / 32767f).coerceIn(0f, 1f)
                        val db = if (normAmp > 0.0001f) (20.0 * log10(normAmp.toDouble())).toFloat() else -96f

                        _amplitudeFlow.value = normAmp
                        val elapsed = System.currentTimeMillis() - startTimeMs
                        _state.value = VoiceSampleCaptureState.Recording(elapsed, normAmp, db)
                    }
                }
            } catch (err: Exception) {
                _state.value = VoiceSampleCaptureState.Error(
                    err.message ?: "Error during AudioRecord streaming",
                )
            } finally {
                runCatching {
                    if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        record.stop()
                    }
                }
                record.release()
                audioRecordInstance = null
                _isRecording.value = false
            }
        }

        return true
    }

    /**
     * Stops the active recording, saves raw PCM (.pcm) and WAV (.wav) files,
     * performs acoustic quality validation, and returns the resulting [VoiceSampleResult].
     */
    fun stopRecording(): VoiceSampleResult {
        if (!recordingFlag.compareAndSet(true, false)) {
            val current = _state.value
            return if (current is VoiceSampleCaptureState.Completed) {
                VoiceSampleResult.Success(current.sample)
            } else {
                VoiceSampleResult.Error("No active voice capture in progress.")
            }
        }

        _isRecording.value = false
        _state.value = VoiceSampleCaptureState.Processing

        workerThread?.join(3000)
        workerThread = null

        val pcmBytes = capturedPcmBuffer.toByteArray()
        val config = activeConfig
        val sampleRate = config.sampleRate
        val totalShorts = pcmBytes.size / 2

        if (totalShorts < (sampleRate * config.minDurationMs / 1000L)) {
            val err = "Recording too short (${(totalShorts * 1000L) / sampleRate}ms). Hold recording for at least ${config.minDurationMs}ms."
            _state.value = VoiceSampleCaptureState.Error(err)
            return VoiceSampleResult.Error(err)
        }

        outputDir.mkdirs()
        val safeName = activeName.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(32)
        val pcmFile = File(outputDir, "${activeSessionId}_${safeName}.pcm")
        val wavFile = File(outputDir, "${activeSessionId}_${safeName}.wav")

        try {
            // 1. Write headerless raw PCM (little endian 16-bit)
            FileOutputStream(pcmFile).use { it.write(pcmBytes) }

            // 2. Write standard 16-bit mono RIFF WAV
            writePcm16MonoWav(wavFile, pcmBytes, sampleRate)

            // 3. Acoustic quality analysis
            val quality = PcmQualityAnalyzer.analyze(pcmBytes, sampleRate, config.minDurationMs)

            val sample = VoiceSample(
                id = activeSessionId,
                name = activeName,
                pcmPath = pcmFile.absolutePath,
                wavPath = wavFile.absolutePath,
                sampleRate = sampleRate,
                channels = 1,
                bitsPerSample = 16,
                durationMs = quality.durationMs,
                pcmByteSize = pcmBytes.size.toLong(),
                quality = quality,
                createdAt = System.currentTimeMillis(),
            )

            _state.value = VoiceSampleCaptureState.Completed(sample)
            _amplitudeFlow.value = 0f
            return VoiceSampleResult.Success(sample)
        } catch (e: Exception) {
            val msg = e.message ?: "Failed to write raw PCM or WAV file"
            _state.value = VoiceSampleCaptureState.Error(msg)
            return VoiceSampleResult.Error(msg)
        }
    }

    /**
     * Cancels active recording and discards buffered audio.
     */
    fun cancelRecording() {
        recordingFlag.set(false)
        _isRecording.value = false
        _amplitudeFlow.value = 0f
        runCatching {
            audioRecordInstance?.stop()
            audioRecordInstance?.release()
        }
        audioRecordInstance = null
        workerThread = null
        capturedPcmBuffer.reset()
        _state.value = VoiceSampleCaptureState.Idle
    }

    /**
     * Resets the capture manager state to Idle.
     */
    fun reset() {
        cancelRecording()
    }

    // =========================================================================
    // VOICE CLONING DATA PARSERS & UTILITIES
    // =========================================================================

    /**
     * Reads raw PCM bytes directly from the stored .pcm file.
     */
    fun readRawPcmBytes(sample: VoiceSample): ByteArray {
        val file = File(sample.pcmPath)
        return if (file.exists()) file.readBytes() else ByteArray(0)
    }

    /**
     * Reads raw 16-bit linear PCM samples as a [ShortArray].
     */
    fun readRawPcmShorts(sample: VoiceSample): ShortArray {
        val bytes = readRawPcmBytes(sample)
        if (bytes.size < 2) return ShortArray(0)
        val shortCount = bytes.size / 2
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return ShortArray(shortCount) { bb.short }
    }

    /**
     * Converts the raw PCM samples into a normalized 32-bit float array in range [-1.0f, 1.0f],
     * ready to be ingested directly as an input tensor into neural voice cloning pipelines
     * (e.g. XTTS, VITS, Bark, OpenVoice, Tortoise, or LiteRT/ONNX voice models).
     */
    fun readNormalizedFloatTensor(sample: VoiceSample): FloatArray {
        val shorts = readRawPcmShorts(sample)
        val floats = FloatArray(shorts.size)
        for (i in shorts.indices) {
            floats[i] = (shorts[i].toFloat() / 32768.0f).coerceIn(-1.0f, 1.0f)
        }
        return floats
    }

    /**
     * Extracts normalized amplitude bars for waveform visualizers.
     */
    fun extractNormalizedWaveform(sample: VoiceSample, barCount: Int = 32): List<Float> {
        val shorts = readRawPcmShorts(sample)
        if (shorts.isEmpty()) return List(barCount) { 0.1f }

        val chunkSize = max(1, shorts.size / barCount)
        val bars = mutableListOf<Float>()

        for (i in 0 until barCount) {
            val start = i * chunkSize
            val end = (start + chunkSize).coerceAtMost(shorts.size)
            if (start >= shorts.size) {
                bars.add(0.1f)
                continue
            }
            var maxAmp = 0
            for (j in start until end) {
                val absVal = abs(shorts[j].toInt())
                if (absVal > maxAmp) maxAmp = absVal
            }
            val norm = (maxAmp.toFloat() / 32767f).coerceIn(0.08f, 1.0f)
            bars.add(norm)
        }
        return bars
    }

    companion object {
        /**
         * Writes a 16-bit mono PCM byte array to a standard RIFF WAV file.
         */
        fun writePcm16MonoWav(file: File, pcmBytes: ByteArray, sampleRate: Int) {
            val dataSize = pcmBytes.size
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
                writeIntLE(16) // Subchunk1Size for PCM
                writeShortLE(1) // AudioFormat: 1 = PCM
                writeShortLE(1) // NumChannels: 1 = Mono
                writeIntLE(sampleRate)
                writeIntLE(sampleRate * 2) // ByteRate = SampleRate * NumChannels * BitsPerSample/8
                writeShortLE(2) // BlockAlign = NumChannels * BitsPerSample/8
                writeShortLE(16) // BitsPerSample = 16
                writeString("data")
                writeIntLE(dataSize)
                dos.write(pcmBytes)
            }
            file.parentFile?.mkdirs()
            file.writeBytes(out.toByteArray())
        }
    }
}
