package com.zakir.vestra.shared.engine.lite

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.File
import java.nio.FloatBuffer

/**
 * Thin wrapper around an ONNX Runtime session for single-input image models.
 * Input geometry is read from the model itself so exports can change size
 * without touching app code.
 *
 * Defaults to CPU. NNAPI is opt-in via [OrtEpPolicy.preferNnapi] — opportunistic
 * NNAPI previously caused native process deaths during pack verify on Pixel 9.
 */
class OrtModel(
    modelPath: String,
    useNnapi: Boolean = OrtEpPolicy.preferNnapi,
) : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = createSessionSafely(modelPath, useNnapi)

    private val inputName: String = session.inputNames.firstOrNull()
        ?: error("ONNX model has no inputs: $modelPath")

    /** [height, width] the model expects; dynamic dims fall back to [defaultSize]. */
    fun inputSize(defaultSize: Int = 320): Pair<Int, Int> {
        // Prefer names-only path; getInputInfo() JNI can abort if R8 stripped NodeInfo.
        return try {
            val info = session.inputInfo[inputName]?.info
            val shape = (info as? TensorInfo)?.shape ?: return defaultSize to defaultSize
            // NCHW: [batch, channels, height, width]
            val h = shape.getOrNull(2)?.toInt()?.takeIf { it > 0 } ?: defaultSize
            val w = shape.getOrNull(3)?.toInt()?.takeIf { it > 0 } ?: defaultSize
            h to w
        } catch (_: Throwable) {
            defaultSize to defaultSize
        }
    }

    /**
     * Runs the model on an NCHW float tensor and returns the first output as
     * (flatData, shape).
     */
    fun run(chw: FloatArray, height: Int, width: Int): Pair<FloatArray, LongArray> {
        val tensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(chw),
            longArrayOf(1, 3, height.toLong(), width.toLong()),
        )
        tensor.use {
            session.run(mapOf(inputName to tensor)).use { results ->
                val output = results[0] as? OnnxTensor
                    ?: error("ONNX output 0 is not a tensor")
                val shape = output.info.shape
                val count = elementCount(shape)
                require(count in 1..MAX_OUTPUT_ELEMENTS) {
                    "ONNX output size $count outside safe range (shape=${shape.contentToString()})"
                }
                val data = FloatArray(count)
                output.floatBuffer.get(data)
                return data to shape
            }
        }
    }

    override fun close() {
        runCatching { session.close() }
    }

    companion object {
        /** ~256 MB float32 ceiling — refuse pathological / dynamic-dim explosions. */
        const val MAX_OUTPUT_ELEMENTS = 64 * 1024 * 1024

        internal fun elementCount(shape: LongArray): Int {
            var acc = 1L
            for (d in shape) {
                if (d <= 0L) return -1
                if (acc > Long.MAX_VALUE / d) return -1
                acc *= d
                if (acc > Int.MAX_VALUE) return -1
            }
            return acc.toInt()
        }

        /**
         * Session create can throw [UnsatisfiedLinkError] / ORT exceptions.
         * Native SIGSEGV from NNAPI is uncatchable — keep [OrtEpPolicy.preferNnapi] false
         * unless the user opts in from Settings.
         */
        private fun createSessionSafely(modelPath: String, useNnapi: Boolean): OrtSession {
            val env = OrtEnvironment.getEnvironment()
            val wantNnapi = useNnapi && OrtEpPolicy.preferNnapi
            val epIntent = buildString {
                append("CPU")
                append(if (wantNnapi) ", NNAPI(opt-in)" else ", NNAPI=off")
            }
            return try {
                val session = env.createSession(
                    modelPath,
                    OrtSession.SessionOptions().apply {
                        // Never force NNAPI on the generate hot path unless explicitly enabled.
                        if (wantNnapi) {
                            runCatching { addNnapi() }
                        }
                    },
                )
                OrtEpProbe.logSessionCreated(modelPath, kind = "lite", epIntent = epIntent)
                session
            } catch (error: UnsatisfiedLinkError) {
                throw IllegalStateException(
                    "ONNX Runtime native library failed to load — reinstall the app or re-download lite-v1.",
                    error,
                )
            } catch (error: Exception) {
                throw IllegalStateException(
                    "Could not open ONNX session (${File(modelPath).name}): ${error.message?.take(100) ?: "unknown"}",
                    error,
                )
            } catch (error: Error) {
                // Soft-wrap LinkageError subclasses that are not UnsatisfiedLinkError.
                throw IllegalStateException(
                    "Native ONNX failure opening ${File(modelPath).name} — try Lite again or re-download the pack.",
                    error,
                )
            }
        }
    }
}
