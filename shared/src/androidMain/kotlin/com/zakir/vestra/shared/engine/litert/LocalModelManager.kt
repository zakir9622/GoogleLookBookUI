package com.zakir.vestra.shared.engine.litert

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.ToolSet
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Manages the lifecycle of on-device LiteRT Gemma models (loading, status tracking,
 * and memory unloading) using the litertlm.android library.
 */
class LocalModelManager(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AutoCloseable {

    sealed interface ModelLifecycleState {
        data object Unloaded : ModelLifecycleState
        data class Loading(val modelPath: String, val useGpu: Boolean) : ModelLifecycleState
        data class Ready(
            val modelPath: String,
            val usedGpu: Boolean,
            val backendLabel: String,
            val loadDurationMs: Long,
        ) : ModelLifecycleState
        data class Error(
            val modelPath: String,
            val message: String,
            val cause: Throwable? = null,
        ) : ModelLifecycleState
    }

    private val mutex = Mutex()
    private val _state = MutableStateFlow<ModelLifecycleState>(ModelLifecycleState.Unloaded)
    val state: StateFlow<ModelLifecycleState> = _state.asStateFlow()

    private var activeEngine: LiteRtLmEngine? = null
    private var activeModelPath: String? = null

    /**
     * Checks if a model at [modelPath] or any model is currently loaded and ready for inference.
     */
    fun isModelLoaded(modelPath: String? = null): Boolean {
        val currentEngine = activeEngine
        return if (modelPath != null) {
            activeModelPath == modelPath && currentEngine != null && currentEngine.isInitialized()
        } else {
            currentEngine != null && currentEngine.isInitialized()
        }
    }

    /**
     * Returns the currently active [LiteRtLmEngine] if loaded and initialized.
     */
    fun getActiveEngine(): LiteRtLmEngine? = activeEngine?.takeIf { it.isInitialized() }

    /**
     * Current lifecycle state snapshot.
     */
    fun getLifecycleState(): ModelLifecycleState = _state.value

    /**
     * Loads and initializes the LiteRT Gemma model from [modelPath].
     * If another model is already loaded, it will be safely unloaded first.
     *
     * @param modelPath Absolute file path to the Gemma model directory or .bin / .tflite weight file.
     * @param useGpu Whether to attempt GPU acceleration delegate (falls back automatically to CPU if unavailable).
     * @param visionEnabled Enables vision/multimodal encoder if supported by the model pack.
     * @param audioEnabled Enables audio encoder if supported by the model pack.
     * @param tools Optional native tools for function calling.
     */
    suspend fun loadModel(
        modelPath: String,
        useGpu: Boolean = true,
        visionEnabled: Boolean = false,
        audioEnabled: Boolean = false,
        tools: List<ToolSet> = emptyList(),
    ): Result<LiteRtLmEngine> = withContext(dispatcher) {
        mutex.withLock {
            val file = File(modelPath)
            if (!file.exists()) {
                val err = "Model file does not exist at path: $modelPath"
                Log.e(TAG, err)
                _state.value = ModelLifecycleState.Error(modelPath, err)
                return@withContext Result.failure(IllegalArgumentException(err))
            }

            // If already loaded with the same configuration, return existing
            if (activeModelPath == modelPath && activeEngine?.isInitialized() == true) {
                Log.i(TAG, "Model already loaded and ready: $modelPath")
                return@withContext Result.success(activeEngine!!)
            }

            // Safely unload any previously active model
            unloadInternal()

            _state.value = ModelLifecycleState.Loading(modelPath, useGpu)
            val startTime = System.currentTimeMillis()

            try {
                Log.i(TAG, "Initializing LiteRT Gemma engine at: $modelPath (GPU: $useGpu)")
                val engine = LiteRtLmEngine(
                    context = context.applicationContext,
                    modelPath = modelPath,
                    useGpu = useGpu,
                    visionEnabled = visionEnabled,
                    audioEnabled = audioEnabled,
                    tools = tools,
                    managedByCache = false,
                )

                engine.initialize()

                val duration = System.currentTimeMillis() - startTime
                val usedGpu = engine.usedGpuBackend()
                val backendLabel = if (usedGpu) "LiteRT GPU" else "LiteRT CPU"

                activeEngine = engine
                activeModelPath = modelPath

                _state.value = ModelLifecycleState.Ready(
                    modelPath = modelPath,
                    usedGpu = usedGpu,
                    backendLabel = backendLabel,
                    loadDurationMs = duration,
                )

                Log.i(TAG, "LiteRT Gemma successfully loaded in ${duration}ms via $backendLabel")
                Result.success(engine)
            } catch (t: Throwable) {
                val errorMsg = t.message ?: "Unknown error while initializing LiteRT model"
                Log.e(TAG, "Failed to initialize LiteRT Gemma: $errorMsg", t)
                _state.value = ModelLifecycleState.Error(modelPath, errorMsg, t)
                unloadInternal()
                Result.failure(t)
            }
        }
    }

    /**
     * Unloads the specified model (or the currently loaded model if [modelPath] is null),
     * freeing up device RAM and GPU memory resources.
     */
    suspend fun unloadModel(modelPath: String? = null) = withContext(dispatcher) {
        mutex.withLock {
            if (modelPath == null || activeModelPath == modelPath) {
                unloadInternal()
            }
        }
    }

    /**
     * Synchronous / AutoCloseable unload for cleanup.
     */
    fun unloadSync() {
        unloadInternal()
    }

    private fun unloadInternal() {
        try {
            activeEngine?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Exception closing active LiteRtLmEngine: ${e.message}")
        } finally {
            activeEngine = null
            activeModelPath = null
            _state.value = ModelLifecycleState.Unloaded
            Log.i(TAG, "LiteRT Gemma model unloaded from memory.")
        }
    }

    override fun close() {
        unloadSync()
    }

    companion object {
        private const val TAG = "LocalModelManager"
    }
}
