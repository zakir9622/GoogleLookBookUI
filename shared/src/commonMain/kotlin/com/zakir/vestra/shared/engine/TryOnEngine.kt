package com.zakir.vestra.shared.engine

import com.zakir.vestra.shared.domain.EngineTier
import com.zakir.vestra.shared.domain.GenerationState
import com.zakir.vestra.shared.domain.TryOnRequest
import kotlinx.coroutines.flow.Flow

/**
 * One implementation per on-device tier. Implementations emit [GenerationState]
 * updates and finish with either [GenerationState.Complete] or
 * [GenerationState.Failed] — they never throw for expected failure modes.
 */
interface TryOnEngine {
    val tier: EngineTier

    fun isAvailable(): Availability

    fun generate(request: TryOnRequest): Flow<GenerationState>
}

sealed interface Availability {
    data object Ready : Availability
    data class Unavailable(val reason: UnavailableReason) : Availability
}

enum class UnavailableReason {
    PACK_NOT_INSTALLED,

    /**
     * Pack files are on disk but failed integrity / ONNX verification.
     * User should re-download from Settings → Model packs.
     */
    PACK_VERIFY_FAILED,

    /** Installed but verification has not finished yet (startup or re-check in progress). */
    PACK_VERIFY_PENDING,

    /**
     * The tier's own pack is installed but a pack it depends on is not — Pro reads the person
     * mask from the Lite pack, so Pro alone cannot run.
     */
    COMPANION_PACK_MISSING,
    DEVICE_NOT_CAPABLE,
    OFFLINE,
    NOT_CONFIGURED,
}
