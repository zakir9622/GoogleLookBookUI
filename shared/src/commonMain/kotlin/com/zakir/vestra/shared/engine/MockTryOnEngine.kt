package com.zakir.vestra.shared.engine

import com.zakir.vestra.shared.domain.EngineTier
import com.zakir.vestra.shared.domain.GenerationState
import com.zakir.vestra.shared.domain.TryOnRequest
import com.zakir.vestra.shared.domain.TryOnResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Development stand-in used until the real engines land (M3+). Walks through the
 * same states the real pipeline emits so the cinematic generation screen can be
 * built and demoed against realistic timing.
 */
class MockTryOnEngine(
    override val tier: EngineTier,
    private val stepDelayMillis: Long = 350,
    private val producePlaceholder: suspend (TryOnRequest) -> String = { "" },
) : TryOnEngine {

    override fun isAvailable(): Availability = Availability.Ready

    override fun generate(request: TryOnRequest): Flow<GenerationState> = flow {
        emit(GenerationState.Preparing("Reading garment"))
        delay(stepDelayMillis)
        val stages = listOf("Extracting garment", "Analyzing pose", "Fitting garment", "Harmonizing light")
        stages.forEachIndexed { index, stage ->
            emit(GenerationState.Running(fraction = (index + 1) / (stages.size + 1f), stage = stage))
            delay(stepDelayMillis)
        }
        val started = 0L
        emit(
            GenerationState.Complete(
                TryOnResult(
                    imagePath = producePlaceholder(request),
                    executedTier = tier,
                    durationMillis = stepDelayMillis * (stages.size + 1) + started,
                    watermarked = true,
                ),
            ),
        )
    }
}
