package com.zakir.vestra.shared.domain

import kotlinx.serialization.Serializable

/** Which generation engine executes a try-on. Selected in Settings; AUTO routes per device. */
enum class EngineTier {
    AUTO,

    /** On-device segmentation + warp + harmonization pipeline. Works on all supported devices, fully offline. */
    LITE,

    /** On-device quantized try-on diffusion. Flagship devices, fully offline. */
    PRO,

    /** Cloud free open-source models (HF Spaces). Requires network; explicit opt-in. */
    CLOUD,
}

/** Where the person in the output image comes from. */
sealed interface PersonSource {
    /** A photo the user picked or captured. Requires the likeness-consent acknowledgement. */
    data class UserPhoto(val uri: String) : PersonSource

    /** A base model from the bundled/downloadable AI-model gallery. */
    data class AiModel(val modelId: String) : PersonSource
}

@Serializable
data class GarmentImage(
    val uri: String,
    /** Best-effort category hint used to pick warp regions; null = auto-detect. */
    val category: GarmentCategory? = null,
)

@Serializable
enum class GarmentCategory {
    /** Abaya — full-length modest outer garment. */
    ABAYA,
    JILBAB,
    KAFTAN,

    /** Head and face coverage. */
    HIJAB,
    NIQAB,
    DUPATTA,

    /** Pakistani / South Asian traditional wear. */
    SHALWAR_KAMEEZ,
    KURTA,
    LEHENGA,

    UPPER_BODY,
    LOWER_BODY,
    DRESS,

    /** Legacy aliases kept for pack compatibility. */
    FULL_COVERAGE,
    HEADSCARF,
}

/**
 * Order garments are layered onto the model in a multi-piece outfit (a South
 * Asian suit is trousers → kurta → dupatta). Lower rank is applied first so
 * later pieces sit on top; null (Auto) is treated as a mid torso layer.
 */
fun GarmentCategory?.layerRank(): Int = when (this) {
    GarmentCategory.LOWER_BODY -> 0
    GarmentCategory.UPPER_BODY, GarmentCategory.KURTA, GarmentCategory.SHALWAR_KAMEEZ,
    GarmentCategory.DRESS, GarmentCategory.ABAYA, GarmentCategory.JILBAB,
    GarmentCategory.KAFTAN, GarmentCategory.FULL_COVERAGE, GarmentCategory.LEHENGA, null -> 1
    GarmentCategory.HIJAB, GarmentCategory.NIQAB, GarmentCategory.DUPATTA,
    GarmentCategory.HEADSCARF -> 2
}

/** Maps legacy category values to the expanded taxonomy for parsing/masking. */
fun GarmentCategory.effectiveCategory(): GarmentCategory = when (this) {
    GarmentCategory.FULL_COVERAGE -> GarmentCategory.ABAYA
    GarmentCategory.HEADSCARF -> GarmentCategory.HIJAB
    else -> this
}

data class TryOnRequest(
    val garment: GarmentImage,
    val person: PersonSource,
    val tier: EngineTier,
    val backdrop: Backdrop = Backdrop.STUDIO_WHITE,
    val seed: Long? = null,
    val casting: CastingProfile = CastingProfile(),
)

/**
 * The scene behind the model in a shot. [ORIGINAL] keeps the person photo's own
 * background; every other value segments the subject and re-composites it over a
 * generated backdrop — the "studio" part of the photoshoot.
 */
@Serializable
enum class Backdrop(val displayName: String) {
    ORIGINAL("As shot"),
    STUDIO_WHITE("Studio white"),
    EDITORIAL("Editorial"),
    GOLDEN_HOUR("Golden hour"),
    RUNWAY("Runway"),
    MONOCHROME("Monochrome"),
}

/**
 * A photoshoot: the same garment rendered as a set of shots (one per person
 * source — different poses of the AI model, or a single user photo).
 */
data class ShootPlan(
    val garment: GarmentImage,
    val shots: List<PersonSource>,
    val tier: EngineTier,
    val casting: CastingProfile = CastingProfile(),
) {
    init {
        require(shots.isNotEmpty()) { "A shoot needs at least one shot" }
    }
}

/** Progress of a multi-shot photoshoot; [inner] is the active shot's engine state. */
data class ShootState(
    val shotIndex: Int,
    val totalShots: Int,
    val inner: GenerationState,
    val completed: List<TryOnResult>,
) {
    val isFinished: Boolean get() = completed.size == totalShots
}

data class TryOnResult(
    /** Absolute path of the generated image on local storage. */
    val imagePath: String,
    /** Tier that actually ran (relevant when the request used AUTO). */
    val executedTier: EngineTier,
    val durationMillis: Long,
    /** True when the output carries the AI-generated watermark + metadata tag. */
    val watermarked: Boolean,
)

/** Progress states surfaced to the cinematic generation screen. */
sealed interface GenerationState {
    data object Idle : GenerationState
    data class Preparing(val message: String) : GenerationState

    /** [fraction] in 0..1; [stage] is a UI-facing label like "Fitting garment". */
    data class Running(val fraction: Float, val stage: String) : GenerationState
    data class Complete(val result: TryOnResult) : GenerationState
    data class Failed(val error: TryOnError) : GenerationState
}

sealed interface TryOnError {
    data object ModelPackMissing : TryOnError
    data object DeviceNotCapable : TryOnError
    data object NetworkUnavailable : TryOnError
    data class SafetyBlocked(val reason: String) : TryOnError
    data class Internal(val message: String) : TryOnError
}
