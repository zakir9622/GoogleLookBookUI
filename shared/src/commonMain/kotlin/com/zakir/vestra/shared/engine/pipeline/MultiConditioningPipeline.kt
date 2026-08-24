package com.zakir.vestra.shared.engine.pipeline

/**
 * The staged, multi-conditioning try-on architecture that replaces the naive
 * image-to-image path. Decoupling the stages is what buys structural alignment
 * and kills the "hallucinated garment pasted onto a body" failure mode:
 *
 *  1. [ConditioningStage.STRUCTURE] — establish structural bounds with an
 *     on-device pose/landmark (or depth) map. This is the ControlNet condition:
 *     it locks the generated body to the target model's pose so limbs and drape
 *     stay geometrically correct.
 *  2. [ConditioningStage.TEXTURE] — inject the garment's visual features via an
 *     IP-Adapter directly into the UNet cross-attention, so garment identity
 *     (colour, embroidery, fabric) is carried by image features rather than a
 *     text description that hallucinates.
 *  3. [ConditioningStage.SYNTHESIS] — run the diffusion loop conditioned on the
 *     structure map + IP-Adapter tokens + [PromptStyle] guidance.
 *
 * This interface is the porting seam: the Android Pro engine implements it with
 * ONNX Runtime (structure + IP-Adapter + UNet as pack files), the Cloud engine
 * implements it by mapping the same [ConditioningInputs] onto a hosted
 * IP-Adapter+ControlNet try-on model, and iOS later supplies a CoreML actual.
 */
interface MultiConditioningPipeline {

    /** Cheap check that the pipeline's required models are present and runnable. */
    fun requirements(): PipelineRequirements

    /**
     * Runs the three stages, reporting progress through [onStage]. Returns the
     * absolute path of the generated image, or throws for a genuine failure
     * (callers translate to GenerationState.Failed).
     */
    suspend fun run(
        inputs: ConditioningInputs,
        onStage: suspend (ConditioningStage, Float) -> Unit,
    ): String
}

enum class ConditioningStage(val label: String) {
    STRUCTURE("Mapping structural skeleton…"),
    TEXTURE("Extracting garment texture…"),
    SYNTHESIS("Synthesizing localized NPU diffusion…"),
}

/**
 * Everything the pipeline conditions on. Image handles are platform file paths
 * so this stays in commonMain (no bitmap type leaks across the KMP boundary).
 */
data class ConditioningInputs(
    /** The person the garment is rendered onto (studio model or user photo). */
    val personImagePath: String,
    /** The garment image whose features the IP-Adapter injects. */
    val garmentImagePath: String,
    /** Region the garment occupies on the person (inpaint mask), if precomputed. */
    val maskPath: String?,
    val prompt: PromptSpec,
    val seed: Long?,
)

/** Resolved inference parameters for one run. */
data class PromptSpec(
    val positive: String = PromptStyle.POSITIVE,
    val negative: String = PromptStyle.NEGATIVE,
    val cfgScale: Float = PromptStyle.CFG_SCALE,
    val steps: Int = PromptStyle.STEPS,
)

/** Which conditioning models the active pack provides; drives graceful degradation. */
data class PipelineRequirements(
    val hasStructureModel: Boolean,
    val hasIpAdapter: Boolean,
    val hasUnet: Boolean,
) {
    /** Full IP-Adapter + ControlNet quality only when every model is present. */
    val isFullyConditioned: Boolean get() = hasStructureModel && hasIpAdapter && hasUnet
}
