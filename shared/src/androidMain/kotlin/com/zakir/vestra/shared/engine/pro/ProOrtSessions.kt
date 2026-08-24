package com.zakir.vestra.shared.engine.pro

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.zakir.vestra.shared.engine.lite.OrtEpPolicy
import com.zakir.vestra.shared.engine.lite.OrtEpProbe
import java.io.File

/**
 * Shared ONNX session factory for Pro FP16 packs (ControlNet / legacy UNet).
 *
 * pro-v1 ships mixed-precision graphs; ORT's default ALL_OPT rewriter often inserts
 * `_to_copy` Cast nodes that disagree on float16 vs float and fail at load time.
 * QNN EP can also rewrite ControlNet into an invalid topology (`node_Conv_*`).
 * Prefer CPU + [NO_OPT] so Pixel devices can open the weights; callers still soft-fail
 * with [friendlyMessage] when the pack itself is broken.
 */
internal object ProOrtSessions {

    fun create(modelPath: String): OrtSession {
        val env = OrtEnvironment.getEnvironment()
        val epIntent = OrtEpProbe.productionProEpIntent()
        return try {
            val session = env.createSession(modelPath, sessionOptions())
            OrtEpProbe.logSessionCreated(modelPath, kind = "pro", epIntent = epIntent)
            session
        } catch (error: UnsatisfiedLinkError) {
            throw IllegalStateException(
                "ONNX Runtime native library failed to load — reinstall the app or re-download the Pro pack.",
                error,
            )
        } catch (error: Exception) {
            throw IllegalStateException(friendlyMessage(modelPath, error), error)
        } catch (error: Error) {
            throw IllegalStateException(
                "Native ONNX failure opening ${File(modelPath).name} — re-download pro-v1 or use Lite try-on.",
                error,
            )
        }
    }

    fun sessionOptions(): OrtSession.SessionOptions =
        OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setInterOpNumThreads(2)
            // Critical for FP16 pro-v1: disable graph opts that invent mismatched Casts.
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
            // NNAPI only when the user opts in — opportunistic, never QNN on Pro.
            if (OrtEpPolicy.preferNnapi) {
                runCatching { addNnapi() }
            }
        }

    fun friendlyMessage(modelPath: String, error: Throwable): String {
        val raw = error.message.orEmpty()
        val file = File(modelPath).name
        return when {
            raw.contains("float16", ignoreCase = true) &&
                (raw.contains("float", ignoreCase = true) || raw.contains("_to_copy")) ->
                "Pro pack UNet ($file) is incompatible with this ONNX Runtime " +
                    "(FP16 type mismatch). Use Lite try-on, or re-download pro-v1 / try pro-v2-int8 " +
                    "in Settings → Model packs."
            raw.contains("not a graph input", ignoreCase = true) ||
                raw.contains("Invalid model", ignoreCase = true) ->
                "Pro pack graph ($file) failed to load. Use Lite try-on, or re-download the Pro pack."
            raw.contains("ORT_FAIL", ignoreCase = true) ||
                raw.contains("ORT_INVALID_ARGUMENT", ignoreCase = true) ->
                "Pro pack could not open $file on this device. Switch to Lite, or re-download pro-v1."
            else ->
                "Could not open ONNX session ($file): ${raw.take(100).ifBlank { "unknown" }}"
        }
    }

    /** True when a Pro failure should trigger AUTO → Lite retry. */
    fun isPackIncompatible(message: String?): Boolean =
        com.zakir.vestra.shared.engine.ProOrtFailure.isPackIncompatible(message)
}
