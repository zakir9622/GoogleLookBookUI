package com.zakir.vestra.shared.engine.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Offline Create Studio contract (txt2img + optional img2img edit).
 *
 * Pack id: `local-sdturbo-v1`.
 */
interface LocalImageGenerator {
    fun isReady(): Boolean
    /** True when VAE encoder is present — enables offline image edit. */
    fun isEditReady(): Boolean = false
    fun generate(prompt: String, seed: Long? = null, referenceImageUri: String? = null): LocalImageResult

    /**
     * Opens the pack's graphs so a selected model is proven loadable before the first prompt.
     *
     * isReady() only stats files, which is how a pack could advertise "Ready offline" and then
     * fail at generate time with a tensor-type error. This actually constructs the sessions.
     * Returns the failure reason, or null on success.
     */
    fun warmUp(): String? = if (isReady()) null else "Local image pack not installed"

    /**
     * Streaming variant — emits progress per denoising step instead of one static message
     * before a multi-minute blocking call. Default wraps [generate] as a single terminal
     * emission for generators that don't report per-step progress.
     */
    fun generateStream(
        prompt: String,
        seed: Long? = null,
        referenceImageUri: String? = null,
    ): Flow<LocalImageStreamEvent> = flow {
        when (val result = generate(prompt, seed, referenceImageUri)) {
            is LocalImageResult.Ok -> emit(LocalImageStreamEvent.Done(result.imagePath))
            is LocalImageResult.Unavailable -> emit(LocalImageStreamEvent.Unavailable(result.reason))
        }
    }
}

sealed class LocalImageResult {
    data class Ok(val imagePath: String) : LocalImageResult()
    data class Unavailable(val reason: String) : LocalImageResult()
}

/** Emissions from [LocalImageGenerator.generateStream]. */
sealed class LocalImageStreamEvent {
    data class Progress(val stage: String, val fraction: Float) : LocalImageStreamEvent()
    data class Done(val imagePath: String) : LocalImageStreamEvent()
    data class Unavailable(val reason: String) : LocalImageStreamEvent()
}

/** Placeholder until SD-Turbo / LCM pack graphs ship. */
object UnimplementedLocalImageGenerator : LocalImageGenerator {
    override fun isReady(): Boolean = false
    override fun generate(prompt: String, seed: Long?, referenceImageUri: String?): LocalImageResult =
        LocalImageResult.Unavailable(
            "Local image pack not published yet — use cloud Create Studio, " +
                "or wait for local-sdturbo-v1 weights on Model packs.",
        )
}

/**
 * Tracks pack install state for future use. [isReady] stays false until the
 * ONNX runner is implemented — otherwise Create Studio would skip cloud and
 * always fail with “runner not wired.”
 */
class PackAwareLocalImageGenerator(
    private val packReady: () -> Boolean,
    private val runnerImplemented: Boolean = false,
) : LocalImageGenerator {
    override fun isReady(): Boolean = runnerImplemented && packReady()

    override fun generate(prompt: String, seed: Long?, referenceImageUri: String?): LocalImageResult {
        if (!runnerImplemented) {
            return LocalImageResult.Unavailable(
                "Local SD-Turbo runner not wired yet — using cloud Create Studio.",
            )
        }
        if (!packReady()) {
            return LocalImageResult.Unavailable(
                "Local image pack not ready — install local-sdturbo-v1 from Model packs.",
            )
        }
        return LocalImageResult.Unavailable(
            "Local SD-Turbo runner not wired yet — using cloud Create Studio.",
        )
    }
}
