package com.zakir.vestra.shared.cloud

import com.zakir.vestra.shared.time.EpochClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GenerationBudgetTest {
    @Test
    fun expiresAfterDeadline() {
        val budget = GenerationBudget(deadlineMs = 1_000L)
        assertTrue(budget.expired(nowMs = 1_000L))
        assertFailsWith<CloudFailureException> {
            budget.throwIfExpired(nowMs = 1_001L)
        }
    }

    @Test
    fun maxPollsScalesWithRemaining() {
        val now = EpochClock.System.nowMs()
        val budget = GenerationBudget(deadlineMs = now + 60_000L)
        val polls = budget.maxPolls(pollDelayMs = 2_000, floor = 5, ceiling = 60)
        assertTrue(polls in 5..60, "polls=$polls")
        assertEquals(0, GenerationBudget(deadlineMs = now - 1).maxPolls())
    }

    @Test
    fun allowWakeRetryRequiresHeadroom() {
        val now = EpochClock.System.nowMs()
        assertTrue(
            GenerationBudget(now + 60_000L).allowWakeRetry(nowMs = now),
        )
        assertTrue(
            !GenerationBudget(now + 10_000L).allowWakeRetry(nowMs = now),
        )
    }

    @Test
    fun factoryDeadlinesMatchUiContracts() {
        val now = 1_000_000L
        assertEquals(now + GenerationBudget.IMAGE_DEADLINE_MS, GenerationBudget.forImage(now).deadlineMs)
        assertEquals(120_000L, GenerationBudget.IMAGE_DEADLINE_MS)
        assertEquals(now + GenerationBudget.VIDEO_DEADLINE_MS, GenerationBudget.forVideo(now).deadlineMs)
        assertEquals(300_000L, GenerationBudget.VIDEO_DEADLINE_MS)
        assertEquals(now + GenerationBudget.AUDIO_DEADLINE_MS, GenerationBudget.forAudio(now).deadlineMs)
        assertEquals(45_000L, GenerationBudget.AUDIO_DEADLINE_MS)
        assertTrue(GenerationBudget.GRADIO_POLL_REQUEST_TIMEOUT_MS < GenerationBudget.IMAGE_DEADLINE_MS)
    }
}
