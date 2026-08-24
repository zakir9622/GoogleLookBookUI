package com.zakir.vestra.shared.engine.lite

/**
 * Process-wide ONNX execution-provider policy.
 * Default is CPU-only: NNAPI session create has been observed to SIGSEGV/OOM
 * the whole process on Pixel 9 during lite pack verify (uncatchable).
 */
object OrtEpPolicy {
    @Volatile
    var preferNnapi: Boolean = false
}
