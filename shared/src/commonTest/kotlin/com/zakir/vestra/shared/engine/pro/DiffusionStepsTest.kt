package com.zakir.vestra.shared.engine.pro

import kotlin.test.Test
import kotlin.test.assertEquals

class DiffusionStepsTest {

    @Test
    fun fullScheduleUsesConfiguredSteps() {
        assertEquals(22, DiffusionSteps.resolve(inferenceSteps = 22, lcmDistilled = false))
    }

    @Test
    fun lcmClampsToFourThroughEight() {
        assertEquals(4, DiffusionSteps.resolve(inferenceSteps = 8, lcmDistilled = true))
        assertEquals(5, DiffusionSteps.resolve(inferenceSteps = 20, lcmDistilled = true))
        assertEquals(8, DiffusionSteps.resolve(inferenceSteps = 40, lcmDistilled = true))
        assertEquals(4, DiffusionSteps.resolve(inferenceSteps = 4, lcmDistilled = true))
    }
}
