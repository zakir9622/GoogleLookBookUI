package com.zakir.vestra.shared.engine.pro

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DdimSchedulerTest {

    @Test
    fun alphaScheduleIsMonotonicallyDecreasingFromNearOne() {
        val scheduler = DdimScheduler()
        assertTrue(scheduler.alphasCumprod.first() > 0.99)
        assertTrue(scheduler.alphasCumprod.last() < 0.02)
        for (t in 1 until scheduler.alphasCumprod.size) {
            assertTrue(scheduler.alphasCumprod[t] < scheduler.alphasCumprod[t - 1])
        }
    }

    @Test
    fun timestepsDescendAndCoverTheSchedule() {
        val scheduler = DdimScheduler()
        val ts = scheduler.timesteps(20)
        assertEquals(20, ts.size)
        assertEquals(950, ts.first())
        assertEquals(0, ts.last())
        for (i in 1 until ts.size) assertTrue(ts[i] < ts[i - 1])
    }

    @Test
    fun perfectNoisePredictionRecoversCleanLatent() {
        // If the model always predicts the exact noise that was added, DDIM
        // must walk back to the clean latent (deterministic, eta = 0).
        val scheduler = DdimScheduler()
        val random = Random(7)
        val clean = FloatArray(64) { random.nextFloat() * 2 - 1 }
        val noise = FloatArray(64) { (random.nextFloat() * 2 - 1) }
        val steps = 20
        val ts = scheduler.timesteps(steps)

        val sample = scheduler.addNoise(clean, noise, ts.first())
        for (t in ts) {
            // Oracle noise prediction for the current sample given x0 = clean:
            // eps = (x_t - sqrt(acp)*x0) / sqrt(1-acp)
            val acp = scheduler.alphasCumprod[t]
            val eps = FloatArray(64) { i ->
                ((sample[i] - kotlin.math.sqrt(acp).toFloat() * clean[i]) /
                    kotlin.math.sqrt(1 - acp).toFloat())
            }
            scheduler.step(sample, eps, t, steps)
        }
        for (i in clean.indices) {
            assertTrue(abs(sample[i] - clean[i]) < 1e-3, "index $i: ${sample[i]} vs ${clean[i]}")
        }
    }
}
