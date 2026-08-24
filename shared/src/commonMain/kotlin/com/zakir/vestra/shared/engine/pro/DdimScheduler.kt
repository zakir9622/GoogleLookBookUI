package com.zakir.vestra.shared.engine.pro

import kotlin.math.sqrt

/**
 * Deterministic DDIM (eta = 0) over Stable Diffusion's scaled-linear beta
 * schedule. Pure Kotlin so the math is unit-tested on the JVM; the Android
 * engine feeds it UNet outputs.
 */
class DdimScheduler(
    trainSteps: Int = 1000,
    betaStart: Double = 0.00085,
    betaEnd: Double = 0.012,
) {
    /** Cumulative products of (1 - beta_t) for every training step. */
    val alphasCumprod: DoubleArray = DoubleArray(trainSteps).also { acp ->
        var running = 1.0
        val sqrtStart = sqrt(betaStart)
        val sqrtEnd = sqrt(betaEnd)
        for (t in 0 until trainSteps) {
            val beta = (sqrtStart + (sqrtEnd - sqrtStart) * t / (trainSteps - 1)).let { it * it }
            running *= (1.0 - beta)
            acp[t] = running
        }
    }

    /** Descending timesteps with SD's "leading" spacing. */
    fun timesteps(inferenceSteps: Int): IntArray {
        val ratio = alphasCumprod.size / inferenceSteps
        return IntArray(inferenceSteps) { i -> (inferenceSteps - 1 - i) * ratio }
    }

    /**
     * One reverse step: given the noise prediction at [timestep], updates
     * [sample] in place toward the previous (less noisy) latent.
     */
    fun step(sample: FloatArray, noisePred: FloatArray, timestep: Int, inferenceSteps: Int) {
        val ratio = alphasCumprod.size / inferenceSteps
        val acpT = alphasCumprod[timestep]
        val prevT = timestep - ratio
        val acpPrev = if (prevT >= 0) alphasCumprod[prevT] else 1.0

        val sqrtAcpT = sqrt(acpT).toFloat()
        val sqrtOneMinusAcpT = sqrt(1.0 - acpT).toFloat()
        val sqrtAcpPrev = sqrt(acpPrev).toFloat()
        val sqrtOneMinusAcpPrev = sqrt(1.0 - acpPrev).toFloat()

        for (i in sample.indices) {
            val predX0 = (sample[i] - sqrtOneMinusAcpT * noisePred[i]) / sqrtAcpT
            sample[i] = sqrtAcpPrev * predX0 + sqrtOneMinusAcpPrev * noisePred[i]
        }
    }

    /** Forward-noises a clean latent to [timestep] (used to init from the person image). */
    fun addNoise(clean: FloatArray, noise: FloatArray, timestep: Int): FloatArray {
        val acp = alphasCumprod[timestep]
        val sqrtAcp = sqrt(acp).toFloat()
        val sqrtOneMinus = sqrt(1.0 - acp).toFloat()
        return FloatArray(clean.size) { i -> sqrtAcp * clean[i] + sqrtOneMinus * noise[i] }
    }
}
