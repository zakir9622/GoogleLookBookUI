package com.zakir.vestra.shared.engine.pro

/**
 * Shared step-count policy for Pro / local diffusion packs.
 * LCM / Hyper-SD distilled packs use 4–8 steps instead of full DDIM.
 */
object DiffusionSteps {
    fun resolve(inferenceSteps: Int, lcmDistilled: Boolean): Int =
        if (lcmDistilled) {
            minOf(8, maxOf(4, inferenceSteps / 4))
        } else {
            inferenceSteps
        }
}
