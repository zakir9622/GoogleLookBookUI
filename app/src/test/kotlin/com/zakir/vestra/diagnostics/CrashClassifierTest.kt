package com.zakir.vestra.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashClassifierTest {

    @Test
    fun oomClassifies() {
        val cause = CrashClassifier.classify(OutOfMemoryError("Failed to allocate"))
        assertTrue(cause.contains("OutOfMemory"))
    }

    @Test
    fun ortClassifies() {
        val cause = CrashClassifier.classify(
            RuntimeException("session failed"),
            "at ai.onnxruntime.OrtSession.run\nat com.zakir.vestra.shared.engine.lite.OrtModel",
        )
        assertTrue(cause.contains("ONNX"))
    }

    @Test
    fun cancelClassifies() {
        val cause = CrashClassifier.classify(
            RuntimeException("StandaloneCoroutine was cancelled"),
            "cancellationexception in kotlinx.coroutines",
        )
        assertTrue(cause.contains("cancelled", ignoreCase = true))
    }

    @Test
    fun abruptPacksOnnxIsActionable() {
        assertTrue(CrashClassifier.abruptIsActionable("packs", "onnxruntime nnapi"))
        val cause = CrashClassifier.classifyAbrupt("packs", "W onnxruntime: session_state")
        assertTrue(cause.contains("ONNX", ignoreCase = true) || cause.contains("Abrupt", ignoreCase = true))
    }

    @Test
    fun abruptSignalClassifiesNative() {
        val cause = CrashClassifier.classifyAbrupt(
            "studio/{tab}#tryon",
            "Fatal signal 11 (SIGSEGV) at 0x0 (code=1)",
        )
        assertTrue(cause.contains("Native", ignoreCase = true))
    }

    @Test
    fun abruptLmkWithLowMemory() {
        val cause = CrashClassifier.classifyAbrupt("packs", "", lowMemorySeen = true)
        assertTrue(cause.contains("OOM", ignoreCase = true) || cause.contains("LMK", ignoreCase = true))
    }

    @Test
    fun garmentScreenIsRisky() {
        assertTrue(CrashClassifier.riskyScreen("garment"))
        assertTrue(CrashClassifier.abruptIsActionable("garment", ""))
    }

    @Test
    fun backgroundAbruptNotActionableWithoutHints() {
        assertFalse(CrashClassifier.abruptIsActionable("settings", ""))
        assertTrue(CrashClassifier.riskyScreen("packs"))
        assertFalse(CrashClassifier.riskyScreen("settings/diagnostics"))
    }
}
