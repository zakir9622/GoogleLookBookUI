package com.zakir.vestra.shared.cloud

import com.zakir.vestra.shared.time.EpochClock

/**
 * Wall-clock budget for a single generation attempt (finding D).
 * Default: 120s image, 300s video.
 *
 * Image edits on busy ZeroGPU Spaces often burn the whole primary window on one hung
 * long-poll; [IMAGE_FALLBACK_GRACE_MS] lets one alternate model still run after that.
 */
class GenerationBudget(
    val deadlineMs: Long,
) {
    fun remainingMs(nowMs: Long = EpochClock.System.nowMs()): Long =
        (deadlineMs - nowMs).coerceAtLeast(0)

    fun expired(nowMs: Long = EpochClock.System.nowMs()): Boolean = nowMs >= deadlineMs

    fun throwIfExpired(nowMs: Long = EpochClock.System.nowMs()) {
        if (expired(nowMs)) throw CloudFailureException(CloudFailure.Timeout)
    }

    /** Gradio poll count derived from remaining budget. */
    fun maxPolls(pollDelayMs: Long = 2_000, floor: Int = 5, ceiling: Int = 60): Int {
        val rem = remainingMs()
        if (rem <= 0) return 0
        return ((rem / pollDelayMs).toInt() - 1).coerceIn(floor, ceiling)
    }

    /** Wake the Space only when enough wall time remains for a retry + a few polls. */
    fun allowWakeRetry(
        wakeDelayMs: Long = 8_000,
        minPollBudgetMs: Long = 25_000,
        nowMs: Long = EpochClock.System.nowMs(),
    ): Boolean = remainingMs(nowMs) >= wakeDelayMs + minPollBudgetMs

    companion object {
        const val IMAGE_DEADLINE_MS = 120_000L
        /** Extra window after primary image Space stalls so InstructPix2Pix / etc. can run. */
        const val IMAGE_FALLBACK_GRACE_MS = 45_000L
        const val VIDEO_DEADLINE_MS = 300_000L
        const val AUDIO_DEADLINE_MS = 45_000L
        /** Cap each Gradio SSE poll so one hung GET cannot burn the whole deadline. */
        const val GRADIO_POLL_REQUEST_TIMEOUT_MS = 12_000L

        fun forImage(nowMs: Long = EpochClock.System.nowMs()): GenerationBudget =
            GenerationBudget(nowMs + IMAGE_DEADLINE_MS)

        fun forVideo(nowMs: Long = EpochClock.System.nowMs()): GenerationBudget =
            GenerationBudget(nowMs + VIDEO_DEADLINE_MS)

        fun forAudio(nowMs: Long = EpochClock.System.nowMs()): GenerationBudget =
            GenerationBudget(nowMs + AUDIO_DEADLINE_MS)
    }
}
