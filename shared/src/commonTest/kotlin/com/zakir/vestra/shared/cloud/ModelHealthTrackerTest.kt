package com.zakir.vestra.shared.cloud

import com.zakir.vestra.shared.testutil.TestMemorySettings
import com.zakir.vestra.shared.time.EpochClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelHealthTrackerTest {

    @Test
    fun cooldownEscalatesAndObservedLabelShowsCoolingDown() {
        val previous = EpochClock.System
        var now = 1_000_000L
        EpochClock.System = EpochClock { now }
        try {
            val tracker = ModelHealthTracker(TestMemorySettings())
            tracker.recordFailure("m1")
            assertTrue(tracker.isInCooldown("m1"))
            assertEquals(30_000L, tracker.cooldownRemainingMs("m1"))
            val cooling = tracker.observedLabel("m1")
            assertNotNull(cooling)
            assertTrue(cooling!!.contains("Cooling"))

            now += 31_000L
            assertFalse(tracker.isInCooldown("m1"))

            tracker.recordFailure("m1")
            assertEquals(120_000L, ModelHealthTracker.cooldownFor(2))
            assertTrue(tracker.isInCooldown("m1"))
        } finally {
            EpochClock.System = previous
        }
    }

    @Test
    fun successClearsCooldownAndShowsVerified() {
        val previous = EpochClock.System
        EpochClock.System = EpochClock { 5_000_000L }
        try {
            val tracker = ModelHealthTracker(TestMemorySettings())
            tracker.recordFailure("m2")
            tracker.recordSuccess("m2")
            assertFalse(tracker.isInCooldown("m2"))
            val label = tracker.observedLabel("m2")
            assertNotNull(label)
            assertTrue(label!!.contains("Ready"))
            assertTrue(label.contains("verified"))
        } finally {
            EpochClock.System = previous
        }
    }

    @Test
    fun quotaAccountShowsZeroGpuLabelNotCoolingDown() {
        val previous = EpochClock.System
        EpochClock.System = EpochClock { 2_000_000L }
        try {
            val tracker = ModelHealthTracker(TestMemorySettings())
            tracker.recordFailure("qwen-edit", ModelHealthTracker.FailureKind.QUOTA_ACCOUNT)
            assertTrue(tracker.isInCooldown("qwen-edit"))
            val label = tracker.observedLabel("qwen-edit")
            assertNotNull(label)
            assertTrue(label!!.contains("ZeroGPU", ignoreCase = true))
            assertFalse(label.contains("Cooling", ignoreCase = true))
            assertEquals(
                ModelHealthTracker.FailureKind.QUOTA_ACCOUNT,
                tracker.failureKind("qwen-edit"),
            )
        } finally {
            EpochClock.System = previous
        }
    }

    @Test
    fun offlineShowsOfflineLabelNotCoolingDown() {
        val previous = EpochClock.System
        EpochClock.System = EpochClock { 3_000_000L }
        try {
            val tracker = ModelHealthTracker(TestMemorySettings())
            tracker.recordFailure("qwen-edit", ModelHealthTracker.FailureKind.OFFLINE)
            assertTrue(tracker.isInCooldown("qwen-edit"))
            val label = tracker.observedLabel("qwen-edit")
            assertNotNull(label)
            assertTrue(label!!.contains("Offline", ignoreCase = true))
            assertFalse(label.contains("Cooling", ignoreCase = true))
            assertEquals(5_000L, tracker.cooldownRemainingMs("qwen-edit"))
        } finally {
            EpochClock.System = previous
        }
    }

    @Test
    fun observedLabelNullWhenNoHistory() {
        val tracker = ModelHealthTracker(TestMemorySettings())
        assertNull(tracker.observedLabel("unknown-model"))
    }
}
