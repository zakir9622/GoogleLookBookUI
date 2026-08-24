package com.zakir.vestra.shared.engine.litert

import android.content.Context
import com.google.ai.edge.litertlm.ToolSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Reuses LiteRT-LM [Engine] instances per model spec — avoids cold-loading ~2.6 GB on every shot.
 * Single-flight init per key; never evict while inference is active (rc16 trim-safe pattern).
 */
object LiteRtLmEngineCache {
    private val inferenceDepth = AtomicInteger(0)
    private val pendingClose = ConcurrentHashMap.newKeySet<String>()
    private val engines = ConcurrentHashMap<EngineSpec, LiteRtLmEngine>()
    private val initLocks = ConcurrentHashMap<EngineSpec, Any>()

    data class EngineSpec(
        val modelPath: String,
        val useGpu: Boolean,
        val visionEnabled: Boolean,
        val audioEnabled: Boolean,
        val toolsKey: String = "",
    )

    fun enterInference() {
        inferenceDepth.incrementAndGet()
    }

    fun leaveInference() {
        inferenceDepth.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
    }

    fun hasActiveInference(): Boolean = inferenceDepth.get() > 0

    fun isModelLoaded(modelPath: String): Boolean =
        engines.entries.any { (spec, engine) -> spec.modelPath == modelPath && engine.isInitialized() }

    fun isAnyLoaded(): Boolean =
        engines.values.any { it.isInitialized() }

    fun getLoadedModelPaths(): Set<String> =
        engines.entries.filter { it.value.isInitialized() }.map { it.key.modelPath }.toSet()

    fun getEngine(spec: EngineSpec): LiteRtLmEngine? =
        engines[spec]?.takeIf { it.isInitialized() }

    fun requestClose(modelPath: String) {
        if (hasActiveInference()) {
            pendingClose.add(modelPath)
            android.util.Log.w("LookbookLiteRtLm", "Deferring engine close — inference active")
        }
    }

    fun drainPendingClose(onClose: (String) -> Unit) {
        if (hasActiveInference()) return
        val paths = pendingClose.toList()
        pendingClose.clear()
        paths.forEach(onClose)
    }

    /** Looks up (or cold-loads) the warm engine for [spec]. Does not touch inference depth. */
    private fun warmEngine(context: Context, spec: EngineSpec, tools: List<ToolSet>): LiteRtLmEngine {
        val lock = initLocks.getOrPut(spec) { Any() }
        val engine = engines.getOrPut(spec) {
            LiteRtLmEngine(
                context = context,
                modelPath = spec.modelPath,
                useGpu = spec.useGpu,
                visionEnabled = spec.visionEnabled,
                audioEnabled = spec.audioEnabled,
                tools = tools,
                managedByCache = true,
            )
        }
        synchronized(lock) {
            if (!engine.isInitialized()) {
                engine.initialize()
            }
        }
        return engine
    }

    /** Borrow a warm engine; [block] runs while inference depth is elevated. */
    fun <T> withEngine(
        context: Context,
        spec: EngineSpec,
        tools: List<ToolSet> = emptyList(),
        block: (LiteRtLmEngine) -> T,
    ): T {
        val engine = warmEngine(context, spec, tools)
        enterInference()
        return try {
            block(engine)
        } finally {
            leaveInference()
            drainPendingClose { path -> evictModelPath(path) }
        }
    }

    /**
     * Streaming counterpart of [withEngine]. A [Flow] builder runs its body lazily, only once
     * collected, so `enterInference()` must happen inside that body — calling it before
     * returning the flow (as a plain `withEngine { engine -> engine.someStreamingCall() }`
     * would) marks inference "done" before a single chunk has actually streamed, letting
     * [evictModelPath] close the engine mid-generation.
     */
    fun <T> withEngineFlow(
        context: Context,
        spec: EngineSpec,
        tools: List<ToolSet> = emptyList(),
        block: (LiteRtLmEngine) -> Flow<T>,
    ): Flow<T> = flow {
        val engine = warmEngine(context, spec, tools)
        enterInference()
        try {
            emitAll(block(engine))
        } finally {
            leaveInference()
            drainPendingClose { path -> evictModelPath(path) }
        }
    }

    fun evictModelPath(modelPath: String) {
        if (hasActiveInference()) return
        engines.entries.removeIf { (spec, eng) ->
            if (spec.modelPath == modelPath) {
                eng.closeNow()
                initLocks.remove(spec)
                true
            } else {
                false
            }
        }
    }

    fun clearAll() {
        if (hasActiveInference()) {
            android.util.Log.w("LookbookLiteRtLm", "Skipping clearAll — LiteRT inference active")
            return
        }
        engines.values.forEach { it.closeNow() }
        engines.clear()
        initLocks.clear()
    }
}
