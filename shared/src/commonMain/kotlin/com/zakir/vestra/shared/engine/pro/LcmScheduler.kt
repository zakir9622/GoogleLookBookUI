package com.zakir.vestra.shared.engine.pro

import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * LCM (Latent Consistency Model) scheduler for distilled tiny-SD packs — ported from Hugging
 * Face diffusers' `LCMScheduler` (`scheduling_lcm.py`), against the primary source rather than
 * from memory: an earlier version of this class applied the boundary-condition combination
 * (`c_out * noisePred + c_skip * sample`) directly to the UNet's raw epsilon-prediction output,
 * skipping the step diffusers always does first — converting that noise prediction into a
 * predicted denoised sample (`predicted_original_sample`) via the standard alphas_cumprod
 * relationship — and never re-injected noise between steps the way diffusers' multi-step
 * sampling does. Both diverge further at every added step, and together they made local
 * image generation produce statistically pure noise: verified by decoding the old pipeline's
 * final latent and finding it indistinguishable (mean/std) from a never-denoised Gaussian
 * sample decoded directly. This version, cross-checked against a real desktop run of the
 * published local-sdturbo-v1 pack, produces a real (if soft, at only 4 steps on a small model)
 * image instead.
 */
class LcmScheduler(
    trainSteps: Int = 1000,
    private val sigmaData: Float = 0.5f,
    private val timestepScaling: Float = 10f,
    private val originalInferenceSteps: Int = 50,
) {
    private val ddim = DdimScheduler(trainSteps)
    private val trainStepsCount = trainSteps

    /**
     * A subset of the model's distillation trajectory, matching diffusers' `set_timesteps`:
     * evenly-spaced *indices* into the descending `original_inference_steps`-point schedule —
     * not an independent linspace down to 0, which lands on timesteps the model was never
     * distilled to denoise from.
     */
    fun timesteps(inferenceSteps: Int): IntArray {
        val k = trainStepsCount / originalInferenceSteps
        val originDescending = IntArray(originalInferenceSteps) { i -> trainStepsCount - 1 - i * k }
        return IntArray(inferenceSteps) { i ->
            val idx = (originDescending.size.toLong() * i / inferenceSteps).toInt()
            originDescending[idx.coerceIn(0, originDescending.size - 1)]
        }
    }

    /**
     * One LCM reverse step. [noisePred] is the UNet's raw epsilon-prediction; this converts it
     * to a predicted denoised sample first, applies the boundary-condition combination, then —
     * unless [nextTimestep] is null (the final scheduled step) — re-injects fresh noise scaled
     * to that next timestep's level, matching diffusers' stochastic multi-step sampling.
     * Updates [sample] in place; [random] drives the re-noising draw.
     */
    fun step(sample: FloatArray, noisePred: FloatArray, timestep: Int, nextTimestep: Int?, random: Random) {
        val alphaT = ddim.alphasCumprod[timestep]
        val sqrtAlphaT = sqrt(alphaT).toFloat()
        val sqrtBetaT = sqrt(1.0 - alphaT).toFloat()
        val scaledT = timestep.toFloat() * timestepScaling
        val cSkip = (sigmaData * sigmaData) / (scaledT * scaledT + sigmaData * sigmaData)
        val cOut = scaledT / sqrt(scaledT * scaledT + sigmaData * sigmaData)

        for (i in sample.indices) {
            val predX0 = (sample[i] - sqrtBetaT * noisePred[i]) / sqrtAlphaT
            sample[i] = cOut * predX0 + cSkip * sample[i]
        }
        if (nextTimestep != null) {
            val alphaPrev = ddim.alphasCumprod[nextTimestep]
            val sqrtAlphaPrev = sqrt(alphaPrev).toFloat()
            val sqrtOneMinusAlphaPrev = sqrt(1.0 - alphaPrev).toFloat()
            for (i in sample.indices) {
                sample[i] = sqrtAlphaPrev * sample[i] + sqrtOneMinusAlphaPrev * gaussian(random)
            }
        }
    }

    /** Forward-noise a clean latent (img2img init). */
    fun addNoise(clean: FloatArray, noise: FloatArray, timestep: Int): FloatArray =
        ddim.addNoise(clean, noise, timestep)

    private fun gaussian(random: Random): Float {
        var u = 0f
        var v = 0f
        while (u <= Float.MIN_VALUE) {
            u = random.nextFloat()
            v = random.nextFloat()
        }
        return sqrt(-2f * ln(u)) * cos((2.0 * kotlin.math.PI * v).toFloat())
    }
}
