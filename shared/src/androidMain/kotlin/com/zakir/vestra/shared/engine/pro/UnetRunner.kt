package com.zakir.vestra.shared.engine.pro

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/**
 * Runs the inpainting UNet over the try-on concat latent. Inputs follow the
 * SD-inpaint 9-channel convention: [noisy latent | mask | masked latent],
 * all already concatenated with their garment halves by LatentCodec.
 *
 * Classifier-free guidance runs the UNet twice (batch 1 keeps peak memory
 * down on phones) and mixes: uncond + scale * (cond - uncond).
 */
class UnetRunner(
    private val session: OrtSession,
    private val env: OrtEnvironment,
    private val channels: Int,
    private val height: Int,
    private val width: Int,
) : AutoCloseable {

    private val plane = height * width
    private val inputNames = session.inputNames.toList()

    fun predictNoise(
        sample: FloatArray,
        conditionLatent: FloatArray,
        unconditionalLatent: FloatArray,
        maskConcat: FloatArray,
        timestep: Int,
        guidanceScale: Float,
    ): FloatArray {
        val conditional = forward(sample, conditionLatent, maskConcat, timestep)
        if (guidanceScale <= 1f) return conditional
        val unconditional = forward(sample, unconditionalLatent, maskConcat, timestep)
        return FloatArray(conditional.size) { i ->
            unconditional[i] + guidanceScale * (conditional[i] - unconditional[i])
        }
    }

    private fun forward(
        sample: FloatArray,
        maskedLatent: FloatArray,
        maskConcat: FloatArray,
        timestep: Int,
    ): FloatArray {
        // 9-channel packed input: 4 sample + 1 mask + 4 masked-image latent.
        val packed = FloatArray(9 * plane)
        sample.copyInto(packed, 0)
        maskConcat.copyInto(packed, channels * plane)
        maskedLatent.copyInto(packed, (channels + 1) * plane)

        val latentTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(packed),
            longArrayOf(1, 9, height.toLong(), width.toLong()),
        )
        val timestepTensor = OnnxTensor.createTensor(env, longArrayOf(timestep.toLong()))
        // Text-free try-on models shouldn't take encoder_hidden_states, but many
        // exports keep the input; feed zeros rather than fail.
        val hiddenName = inputNames.firstOrNull { it.contains("hidden") || it.contains("encoder") }
        val hiddenTensor = hiddenName?.let {
            OnnxTensor.createTensor(env, FloatBuffer.wrap(FloatArray(768)), longArrayOf(1, 1, 768))
        }

        val inputs = buildMap {
            put(inputNames[0], latentTensor)
            // Exports name these "sample"/"timestep"; fall back positionally.
            put(inputNames.firstOrNull { it.contains("time") } ?: inputNames[1], timestepTensor)
            if (hiddenName != null && hiddenTensor != null) put(hiddenName, hiddenTensor)
        }
        try {
            session.run(inputs).use { results ->
                val output = results[0] as OnnxTensor
                val out = FloatArray(output.info.shape.fold(1L) { a, d -> a * d }.toInt())
                output.floatBuffer.get(out)
                return out
            }
        } finally {
            latentTensor.close()
            timestepTensor.close()
            hiddenTensor?.close()
        }
    }

    override fun close() {
        session.close()
    }
}
