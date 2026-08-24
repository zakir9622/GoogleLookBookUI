package com.zakir.vestra.shared.engine.pro

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LcmSchedulerTest {

    /**
     * A subset of the model's distillation trajectory (diffusers' `set_timesteps`), not an
     * independent linspace to 0 — the previous version of this scheduler ended every run at
     * timestep 0, which is a mathematical no-op under the LCM boundary condition (cSkip=1,
     * cOut=0 there), silently wasting the whole final step.
     */
    @Test
    fun timestepsAreASubsetOfTheDistillationTrajectoryNotLinspaceToZero() {
        val steps = LcmScheduler().timesteps(4)
        assertEquals(4, steps.size)
        assertEquals(999, steps[0])
        assertTrue(steps[3] != 0, "last step must not degenerate to the boundary timestep 0")
        // Every value must land on the model's actual 50-point distillation grid
        // (trainSteps=1000, originalInferenceSteps=50 -> step 20: 999, 979, 959, ..., 19).
        for (t in steps) assertEquals(19, t % 20, "timestep $t is off the distillation grid")
    }

    @Test
    fun stepConvertsNoisePredictionToADenoisedSampleNotUseItDirectly() {
        val scheduler = LcmScheduler()
        // At a high timestep, cOut is close to 1 and cSkip close to 0, so step() collapses to
        // roughly `predictedX0 = (sample - sqrtBeta*noisePred) / sqrtAlpha` — nothing like the
        // raw noisePred value itself. A step that used noisePred directly (the original bug)
        // would land on noisePred's own value here; the fixed math must not.
        val sample = floatArrayOf(1f, 0f)
        val noisePred = floatArrayOf(0.5f, -0.5f)
        scheduler.step(sample, noisePred, 999, nextTimestep = null, random = Random(1))
        assertTrue(sample[0] != 1f || sample[1] != 0f, "step must change the sample")
        assertTrue(
            sample[0] != noisePred[0] || sample[1] != noisePred[1],
            "step must not just copy the raw noise prediction through",
        )
    }

    @Test
    fun stepReinjectsNoiseWhenThereIsANextTimestepButNotOnTheFinalStep() {
        val sample1 = floatArrayOf(1f, 0f)
        LcmScheduler().step(sample1, floatArrayOf(0.5f, -0.5f), 999, nextTimestep = 500, random = Random(1))
        val sample2 = floatArrayOf(1f, 0f)
        LcmScheduler().step(sample2, floatArrayOf(0.5f, -0.5f), 999, nextTimestep = null, random = Random(1))
        // Same inputs, only nextTimestep differs -> the re-noising branch must change the result.
        assertTrue(sample1[0] != sample2[0] || sample1[1] != sample2[1])
    }
}
