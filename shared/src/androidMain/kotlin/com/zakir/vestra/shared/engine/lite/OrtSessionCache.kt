package com.zakir.vestra.shared.engine.lite

import com.zakir.vestra.shared.engine.pro.OrtGraph
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Caches ONNX sessions per model path to avoid cold-load latency on every shot.
 * Invalidated when pack version changes (path includes version directory).
 */
object OrtSessionCache {
    private val cache = ConcurrentHashMap<String, OrtModel>()
    private val graphCache = ConcurrentHashMap<String, OrtGraph>()
    private val inferenceDepth = AtomicInteger(0)

    /** Call while local ORT inference is active — blocks unsafe [clearAll] during trim. */
    fun enterInference() {
        inferenceDepth.incrementAndGet()
    }

    fun leaveInference() {
        inferenceDepth.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
    }

    fun hasActiveInference(): Boolean = inferenceDepth.get() > 0

    fun open(modelPath: String): OrtModel =
        cache.getOrPut(modelPath) {
            // Construction may throw IllegalStateException on bad graphs / native link errors.
            OrtModel(modelPath)
        }

    /** Multi-input Pro / local-image graphs — reused across denoise steps and generations. */
    fun openGraph(modelPath: String): OrtGraph =
        graphCache.getOrPut(modelPath) {
            OrtGraph(modelPath)
        }

    fun invalidateContaining(packRoot: String) {
        if (hasActiveInference()) return
        cache.keys.filter { it.startsWith(packRoot) }.forEach { key ->
            cache.remove(key)?.close()
        }
        graphCache.keys.filter { it.startsWith(packRoot) }.forEach { key ->
            graphCache.remove(key)?.close()
        }
    }

    fun clearAll() {
        if (hasActiveInference()) {
            android.util.Log.w("OrtSessionCache", "Skipping clearAll — ORT inference active")
            return
        }
        cache.values.forEach { it.close() }
        cache.clear()
        graphCache.values.forEach { it.close() }
        graphCache.clear()
    }
}
