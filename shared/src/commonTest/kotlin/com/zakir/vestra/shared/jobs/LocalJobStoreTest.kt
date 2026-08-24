package com.zakir.vestra.shared.jobs

import com.zakir.vestra.shared.diagnostics.RunCapability
import com.zakir.vestra.shared.testutil.TestMemorySettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalJobStoreTest {

    @Test
    fun completedJobIsNotInterrupted() {
        val store = LocalJobStore(TestMemorySettings())
        val id = store.start(RunCapability.IMAGE_GEN, "a red silk dress")
        assertEquals(1, store.interruptedJobs().size)

        store.complete(id, success = true)

        assertTrue(store.interruptedJobs().isEmpty())
        assertEquals(LocalJobStatus.DONE, store.jobs.value.first { it.id == id }.status)
    }

    @Test
    fun failedAndCancelledJobsAreNotInterrupted() {
        val store = LocalJobStore(TestMemorySettings())
        val failedId = store.start(RunCapability.CODE, "write a function")
        val cancelledId = store.start(RunCapability.VIDEO, "a slow pan over a garden")

        store.complete(failedId, success = false)
        store.cancel(cancelledId)

        assertTrue(store.interruptedJobs().isEmpty())
    }

    @Test
    fun jobStillRunningAcrossAStoreReloadIsInterrupted() {
        val settings = TestMemorySettings()
        val firstRun = LocalJobStore(settings)
        val id = firstRun.start(RunCapability.AUDIO, "calm narration")
        // Simulate the process being killed mid-generation: a fresh store instance, backed by
        // the same persisted settings, is what app relaunch actually looks like.
        val afterRelaunch = LocalJobStore(settings)

        val interrupted = afterRelaunch.interruptedJobs()

        assertEquals(1, interrupted.size)
        assertEquals(id, interrupted.first().id)
        assertEquals(LocalJobStatus.RUNNING, interrupted.first().status)
    }

    @Test
    fun promptPreviewIsTruncated() {
        val store = LocalJobStore(TestMemorySettings())
        val longPrompt = "x".repeat(500)
        store.start(RunCapability.IMAGE_GEN, longPrompt)

        assertTrue(store.jobs.value.first().promptPreview.length <= 80)
    }

    @Test
    fun dismissRemovesJobFromInterruptedList() {
        val store = LocalJobStore(TestMemorySettings())
        val id = store.start(RunCapability.CODE, "refactor this")

        store.dismiss(id)

        assertTrue(store.interruptedJobs().isEmpty())
    }
}
