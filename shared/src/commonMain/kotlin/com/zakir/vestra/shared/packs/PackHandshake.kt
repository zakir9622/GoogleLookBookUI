package com.zakir.vestra.shared.packs

/**
 * Device ↔ pack handshake: confirms files are on disk, integrity passed, and
 * which studio / engine wires this pack unlocks.
 *
 * UI shows [signal] as the machine-readable ACK/NACK (e.g. `HANDSHAKE_OK`).
 */
data class PackHandshakeResult(
    val packId: String,
    val displayName: String,
    val ok: Boolean,
    /** Machine-readable signal for diagnostics / UI chips. */
    val signal: String,
    val message: String,
    /** Studio / engine surfaces this pack wires when linked. */
    val wires: List<String>,
    val verifiedAtMs: Long,
) {
    companion object {
        const val SIGNAL_OK = "HANDSHAKE_OK"
        const val SIGNAL_FAIL = "HANDSHAKE_FAIL"
        const val SIGNAL_SKIP = "HANDSHAKE_SKIP"
    }
}

data class PackHandshakeReport(
    val results: List<PackHandshakeResult>,
    val startedAtMs: Long,
    val finishedAtMs: Long,
) {
    val okCount: Int get() = results.count { it.ok }
    val failCount: Int get() = results.count { !it.ok && it.signal == PackHandshakeResult.SIGNAL_FAIL }
    val allOk: Boolean get() = results.isNotEmpty() && results.all { it.ok }

    /** Aggregate signal for the Settings / Packs banner. */
    val signal: String
        get() = when {
            results.isEmpty() -> PackHandshakeResult.SIGNAL_SKIP
            allOk -> PackHandshakeResult.SIGNAL_OK
            else -> PackHandshakeResult.SIGNAL_FAIL
        }

    val summary: String
        get() = when {
            results.isEmpty() -> "No installed packs to handshake"
            allOk -> "Handshake OK · $okCount pack(s) linked to device"
            else -> "Handshake FAIL · $okCount ok · $failCount failed"
        }
}

/**
 * Maps pack IDs to the on-device wires they unlock (honest, catalog-aligned).
 */
object PackHandshakeWires {
    fun forPackId(packId: String): List<String> = when {
        packId == "lite-v1" || packId.startsWith("lite-") ->
            listOf("Try-on Lite", "Human parsing")
        packId.startsWith("pro-") ->
            listOf("Try-on Pro")
        packId == "local-sdturbo-v1" ->
            listOf("Image Create", "Image Edit (img2img)", "Video still-clip")
        packId == "local-gemma-v1" ->
            listOf("Code Studio (legacy Gemma 3)")
        packId == "local-gemma-4-e2b-v1" ->
            listOf("Code Studio (Gemma 4 E2B)", "Vision assist · Analyze reference", "Audio · Transcribe (STT)")
        packId == "local-gemma-4-vision-v1" ->
            listOf("Vision assist · Analyze reference (alias — install local-gemma-4-e2b-v1)")
        packId == "local-audio-scribe-v1" ->
            listOf("Audio · Transcribe (alias — install local-gemma-4-e2b-v1)")
        packId == "local-functiongemma-v1" ->
            listOf("Local tools (experimental)")
        packId.contains("birefnet", ignoreCase = true) ->
            listOf("Quality · BiRefNet matting")
        packId.contains("realesrgan", ignoreCase = true) ->
            listOf("Quality · Real-ESRGAN upscale")
        packId.contains("studio-models", ignoreCase = true) ->
            listOf("Studio model gallery")
        packId == "local-tts-v1" ->
            listOf("Audio · neural TTS (when wired)")
        else -> listOf("On-device pack")
    }

    fun formatDetail(result: PackHandshakeResult): String = buildString {
        if (result.ok) {
            append("Linked to this device")
            if (result.wires.isNotEmpty()) {
                append(" · ")
                append(result.wires.joinToString(", "))
            }
        } else {
            append(result.message)
        }
    }

    /** Short chip for Settings / Packs (no machine ACK codes). */
    fun formatUserSummary(result: PackHandshakeResult): String =
        if (result.ok) {
            "Ready · ${result.displayName}"
        } else {
            "Not linked · ${result.message.take(80)}"
        }
}
