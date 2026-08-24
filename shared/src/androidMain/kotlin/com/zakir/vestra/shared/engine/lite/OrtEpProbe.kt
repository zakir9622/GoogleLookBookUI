package com.zakir.vestra.shared.engine.lite

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtProvider
import ai.onnxruntime.OrtSession
import android.util.Log

/**
 * Logs which ONNX execution providers are available / requested / accepted.
 *
 * Production Pro sessions intentionally skip QNN (FP16 graph rewrite hazards) and
 * only opt into NNAPI when [OrtEpPolicy.preferNnapi] is true. A0 benchmarks probe
 * every EP the binary advertises so we know whether accelerators are real or silent
 * CPU fallbacks.
 */
object OrtEpProbe {
    const val TAG = "LookbookOrtEp"

    data class ProbeResult(
        val available: List<String>,
        val attempted: List<EpAttempt>,
    )

    data class EpAttempt(
        val name: String,
        val registered: Boolean,
        val error: String? = null,
    )

    fun availableProviderNames(): List<String> =
        runCatching {
            OrtEnvironment.getAvailableProviders().map { it.name }.sorted()
        }.getOrElse { emptyList() }

    /**
     * Probe EP registration only (no model load). Safe to call from tests and cold start.
     * Does **not** enable QNN/NNAPI on the production hot path — see [OrtEpPolicy].
     */
    fun probeRegistration(): ProbeResult {
        val available = availableProviderNames()
        val attempted = mutableListOf<EpAttempt>()
        OrtSession.SessionOptions().use { opts ->
            attempted += tryEp("CPU") {
                // CPU is always present; addCPU is optional explicit registration.
                runCatching { opts.addCPU(true) }
                true
            }
            attempted += tryEp("NNAPI") {
                if (!availableContains(available, OrtProvider.NNAPI)) return@tryEp false
                opts.addNnapi()
                true
            }
            attempted += tryEp("XNNPACK") {
                if (!availableContains(available, OrtProvider.XNNPACK)) return@tryEp false
                opts.addXnnpack(emptyMap())
                true
            }
            attempted += tryEp("QNN") {
                if (!availableContains(available, OrtProvider.QNN)) return@tryEp false
                opts.addQnn(emptyMap())
                true
            }
        }
        val result = ProbeResult(available = available, attempted = attempted)
        Log.i(TAG, "available=${available.joinToString()}")
        result.attempted.forEach { a ->
            Log.i(
                TAG,
                "ep=${a.name} registered=${a.registered}" +
                    (a.error?.let { " err=${it.take(120)}" } ?: ""),
            )
        }
        return result
    }

    /** Describe what production Pro session options will request. */
    fun productionProEpIntent(): String {
        val available = availableProviderNames()
        val parts = mutableListOf("CPU", "opt=NO_OPT")
        if (OrtEpPolicy.preferNnapi && availableContains(available, OrtProvider.NNAPI)) {
            parts += "NNAPI(opt-in)"
        } else {
            parts += "NNAPI=off(preferNnapi=${OrtEpPolicy.preferNnapi})"
        }
        parts += "QNN=never"
        return parts.joinToString(", ")
    }

    fun logSessionCreated(modelPath: String, kind: String, epIntent: String) {
        val name = modelPath.substringAfterLast('/')
        Log.i(TAG, "session_created kind=$kind model=$name ep_intent=[$epIntent]")
    }

    private fun availableContains(names: List<String>, provider: OrtProvider): Boolean =
        names.any { it.equals(provider.name, ignoreCase = true) }

    private fun tryEp(name: String, block: () -> Boolean): EpAttempt =
        try {
            EpAttempt(name = name, registered = block())
        } catch (t: Throwable) {
            EpAttempt(name = name, registered = false, error = t.message ?: t.javaClass.simpleName)
        }
}
