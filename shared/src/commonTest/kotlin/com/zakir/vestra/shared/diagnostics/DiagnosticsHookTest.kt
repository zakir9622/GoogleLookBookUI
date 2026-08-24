package com.zakir.vestra.shared.diagnostics

import com.zakir.vestra.shared.domain.EngineTier
import com.zakir.vestra.shared.testutil.TestMemorySettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DiagnosticsHookTest {
    @Test
    fun concurrentRunsDoNotClobber() {
        val store = RunDiagnostics(TestMemorySettings())
        DiagnosticsHook.store = store
        val a = DiagnosticsHook.startTryOn(EngineTier.LITE, modelLabel = "a")
        val b = DiagnosticsHook.startTryOn(EngineTier.PRO, modelLabel = "b")
        assertNotNull(a)
        assertNotNull(b)
        assertEquals(2, DiagnosticsHook.activeCount())
        DiagnosticsHook.completeTryOn(a, success = true, note = "a-done")
        assertEquals(1, DiagnosticsHook.activeCount())
        DiagnosticsHook.completeTryOn(b, success = false, error = "b-fail")
        assertEquals(0, DiagnosticsHook.activeCount())
        assertEquals(2, store.records.value.size)
        DiagnosticsHook.store = null
    }
}
