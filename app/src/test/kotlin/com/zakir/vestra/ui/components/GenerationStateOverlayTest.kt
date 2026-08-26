package com.zakir.vestra.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM tests for step parsing and progression calculation in [GenerationStateOverlay].
 */
class GenerationStateOverlayTest {

    @Test
    fun parseStepInfoExtractsExplicitSteps() {
        val info = parseStepInfo("Denoising step 3/8", 0.375f, defaultTotalSteps = 8)
        assertEquals(3, info.currentStep)
        assertEquals(8, info.totalSteps)
        assertEquals(5, info.remainingSteps)
        assertEquals(3f / 8f, info.fraction, 0.001f)
    }

    @Test
    fun parseStepInfoExtractsPassFormat() {
        val info = parseStepInfo("Tokenizing pass 2 of 4", 0.5f, defaultTotalSteps = 4)
        assertEquals(2, info.currentStep)
        assertEquals(4, info.totalSteps)
        assertEquals(2, info.remainingSteps)
    }

    @Test
    fun parseStepInfoCalculatesFractionFallback() {
        val info = parseStepInfo("Generating latent tensors", 0.75f, defaultTotalSteps = 8)
        assertEquals(6, info.currentStep)
        assertEquals(8, info.totalSteps)
        assertEquals(2, info.remainingSteps)
    }

    @Test
    fun remainingStepsNeverNegative() {
        val stepInfo = GenerationStepInfo(
            currentStep = 10,
            totalSteps = 8,
            stageName = "Finalizing",
        )
        assertEquals(0, stepInfo.remainingSteps)
    }
}
