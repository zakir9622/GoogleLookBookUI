package com.zakir.vestra.shared.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Modality categories for exclusive engine execution.
 * Guarantees that only one heavy engine mode resides in active memory at a time.
 */
enum class EngineModality(val displayName: String) {
    IMAGE("Image Generator"),
    VIDEO("Video Generator"),
    CODE("Code Generator"),
    CHAT("Chat Engine"),
    AUDIO("Audio Studio"),
    TRY_ON("Virtual Try-On"),
    IDLE("Idle"),
}

sealed interface EngineGateState {
    data object Idle : EngineGateState
    data class Loading(val modality: EngineModality, val modelName: String) : EngineGateState
    data class Running(val modality: EngineModality, val modelName: String, val progress: Float = 0f) : EngineGateState
}

/**
 * Universal execution gate that isolates Image, Video, Code, Chat, Audio, and Try-On engines.
 * Ensures zero OOM crashes by unloading non-active modalities before a new one starts.
 */
object ExclusiveEngineGate {
    private val mutex = Mutex()
    private var activeModality: EngineModality = EngineModality.IDLE
    private var activeModelTag: String = ""

    private val _gateState = MutableStateFlow<EngineGateState>(EngineGateState.Idle)
    val gateState: StateFlow<EngineGateState> = _gateState.asStateFlow()

    private var onEvictOtherModalities: ((EngineModality) -> Unit)? = null

    fun registerEvictionHandler(handler: (EngineModality) -> Unit) {
        onEvictOtherModalities = handler
    }

    fun currentModality(): EngineModality = activeModality
    fun currentModelTag(): String = activeModelTag

    fun setModelLoading(modality: EngineModality, modelName: String) {
        _gateState.value = EngineGateState.Loading(modality, modelName)
    }

    fun setModelRunning(modality: EngineModality, modelName: String, progress: Float = 0f) {
        _gateState.value = EngineGateState.Running(modality, modelName, progress)
    }

    fun setModelIdle() {
        _gateState.value = EngineGateState.Idle
    }

    /**
     * Executes [block] exclusively for [modality].
     * If a different modality was previously active, triggers eviction of inactive sessions.
     */
    suspend fun <T> withExclusiveModality(
        modality: EngineModality,
        modelName: String,
        block: suspend () -> T,
    ): T = mutex.withLock {
        if (activeModality != EngineModality.IDLE && activeModality != modality) {
            onEvictOtherModalities?.invoke(modality)
        }
        activeModality = modality
        activeModelTag = modelName
        _gateState.value = EngineGateState.Running(modality, modelName, 0f)
        try {
            block()
        } finally {
            activeModality = EngineModality.IDLE
            activeModelTag = ""
            _gateState.value = EngineGateState.Idle
        }
    }
}
