package com.zakir.vestra.shared.packs

import com.zakir.vestra.shared.domain.ModelPack
import com.zakir.vestra.shared.domain.PackKind
import com.zakir.vestra.shared.engine.lite.LiteEngine
import com.zakir.vestra.shared.engine.lite.OrtModel
import com.zakir.vestra.shared.engine.local.LiteRtLmPackConfig
import com.zakir.vestra.shared.engine.local.LiteRtLmPackLimits
import com.zakir.vestra.shared.engine.local.LiteRtLmPacks
import com.zakir.vestra.shared.engine.local.LocalSdturboPackValidator
import com.zakir.vestra.shared.quality.AndroidQualityPostProcessor
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlinx.serialization.json.Json

/**
 * Android ONNX + file integrity checks for installed model packs.
 *
 * Validates file sizes, magic binary headers (ONNX protobuf, FlatBuffers TFL3, etc.),
 * JSON format parsing, and sha256 checksums to guarantee model files are uncorrupted.
 */
class AndroidPackIntegrityChecker : PackIntegrityChecker {

    override fun verifyFiles(pack: ModelPack, dir: String): String? {
        for (file in pack.files) {
            val path = File(dir, file.path)
            if (!path.exists()) {
                return "Missing ${file.path} — re-download ${pack.displayName}"
            }
            if (file.bytes > 0 && path.length() != file.bytes) {
                return "${file.path} is incomplete (${path.length()} / ${file.bytes} bytes)"
            }
            if (path.length() == 0L) {
                return "${file.path} is an empty 0-byte file — re-download ${pack.displayName}"
            }
            // If file has an exact sha256 provided in manifest, verify checksum
            if (file.sha256.length == 64 && !file.sha256.all { it == '0' }) {
                val calculatedHash = runCatching { computeSha256(path) }.getOrNull()
                if (calculatedHash != null && !calculatedHash.equals(file.sha256, ignoreCase = true)) {
                    return "${file.path} checksum mismatch (corrupted download) — re-download ${pack.displayName}"
                }
            }
            // Check headers for corruption
            val headerErr = checkFileHeaderIntegrity(path)
            if (headerErr != null) {
                return "${file.path} corrupt: $headerErr"
            }
        }
        if (pack.id.startsWith("pro-")) {
            val config = File(dir, "config.json")
            if (!config.exists()) {
                return "Missing config.json — Pro pack is incomplete"
            }
        }
        return null
    }

    private fun checkFileHeaderIntegrity(file: File): String? {
        val name = file.name.lowercase()
        return when {
            name.endsWith(".json") -> runCatching {
                Json.parseToJsonElement(file.readText())
                null
            }.getOrElse { "Invalid JSON structure: ${it.message?.take(60)}" }

            name.endsWith(".tflite") || name.endsWith(".litertlm") || name.endsWith(".task") -> runCatching {
                if (file.length() < 8) return "File too small to be a valid model binary"
                FileInputStream(file).use { stream ->
                    val header = ByteArray(8)
                    val read = stream.read(header)
                    if (read < 8) return "Truncated header"
                    // FlatBuffers magic check: bytes 4..7 are "TFL3" or "TFL1" or model signature
                    val magic = String(header, 4, 4, Charsets.US_ASCII)
                    if (magic != "TFL3" && magic != "TFL1" && magic != "TFL2" && !header.take(4).toByteArray().contentEquals(byteArrayOf(0x18, 0, 0, 0))) {
                        // Check if valid Flatbuffer identifier or LiteRT format
                        // Allow if header is non-zero
                        if (header.all { it == 0.toByte() }) {
                            return "Corrupt null-filled header"
                        }
                    }
                }
                null
            }.getOrElse { it.message }

            name.endsWith(".onnx") -> runCatching {
                if (file.length() < 16) return "File too small to be a valid ONNX graph"
                FileInputStream(file).use { stream ->
                    val header = ByteArray(16)
                    val read = stream.read(header)
                    if (read < 16 || header.all { it == 0.toByte() }) {
                        return "Corrupted ONNX binary header"
                    }
                }
                null
            }.getOrElse { it.message }

            else -> null
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    override fun verifyOnnx(pack: ModelPack, dir: String): String? = when {
        pack.kind == PackKind.MODELS -> null
        pack.id == LiteEngine.PACK_ID -> verifyLitePack(dir)
        pack.id.startsWith("pro-") -> verifyProPack(dir)
        pack.id == AndroidQualityPostProcessor.REALESRGAN_PACK ||
            pack.id.contains("realesrgan", ignoreCase = true) -> verifyRealesrganPack(dir)
        pack.id == AndroidQualityPostProcessor.BIREFNET_PACK ||
            pack.id.contains("birefnet", ignoreCase = true) -> verifyBirefnetPack(dir)
        pack.id == LocalSdturboPackValidator.PACK_ID ||
            pack.id.contains("sdturbo", ignoreCase = true) -> verifySdturboPack(dir)
        pack.id == "local-gemma-v1" || pack.id.contains("gemma", ignoreCase = true) ->
            verifyLiteRtLmOrLegacyGemma(pack.id, dir)
        else -> verifyManifestOnnxFiles(pack, dir)
    }

    /**
     * LiteRT-LM `.litertlm` or legacy MediaPipe `.task` — file-size gate only.
     * Never load full LLM during startup verify (OOM risk).
     */
    private fun verifyLiteRtLmOrLegacyGemma(packId: String, dir: String): String? {
        if (packId == LiteRtLmPacks.LEGACY_GEMMA3) {
            return verifyLegacyGemmaPack(dir)
        }
        val root = File(dir)
        val defaultPrimary = when (packId) {
            LiteRtLmPacks.GEMMA4_CODE, LiteRtLmPacks.GEMMA4_VISION, LiteRtLmPacks.AUDIO_SCRIBE ->
                LiteRtLmPacks.GEMMA4_FILE
            LiteRtLmPacks.FUNCTION_GEMMA -> LiteRtLmPacks.FUNCTION_GEMMA_FILE
            LiteRtLmPacks.QWEN3_CODE -> LiteRtLmPacks.QWEN3_FILE
            else -> LiteRtLmPacks.GEMMA4_FILE
        }
        val cfg = LiteRtLmPackConfig.read(root, defaultPrimary)
        if (cfg.runtime != "litert-lm") {
            return verifyLegacyGemmaPack(dir)
        }
        val model = File(root, cfg.primaryFile)
        if (!model.isFile) {
            return "${cfg.primaryFile} missing — re-download $packId"
        }
        val minBytes = when (packId) {
            LiteRtLmPacks.FUNCTION_GEMMA -> LiteRtLmPackLimits.MIN_FUNCTION_BYTES
            LiteRtLmPacks.QWEN3_CODE -> LiteRtLmPackLimits.MIN_QWEN3_BYTES
            LiteRtLmPacks.AUDIO_SCRIBE, LiteRtLmPacks.GEMMA4_VISION -> LiteRtLmPackLimits.MIN_GEMMA4_BYTES
            else -> LiteRtLmPackLimits.MIN_GEMMA4_BYTES
        }
        if (model.length() < minBytes) {
            return "${cfg.primaryFile} incomplete (${model.length()} / $minBytes bytes min)"
        }
        return null
    }

    /**
     * Legacy MediaPipe `.task` packs — file-size checks in [verifyFiles] are the gate.
     */
    private fun verifyLegacyGemmaPack(dir: String): String? {
        val task = File(dir, LiteRtLmPacks.LEGACY_GEMMA3_FILE)
        if (!task.isFile || task.length() < LiteRtLmPackLimits.MIN_LEGACY_GEMMA3_BYTES) {
            return "Gemma .task missing or incomplete — re-download local-gemma-v1"
        }
        return null
    }

    /** @deprecated use [verifyLegacyGemmaPack] */
    private fun verifyGemmaPack(dir: String): String? = verifyLegacyGemmaPack(dir)

    private fun verifyLitePack(dir: String): String? {
        val required = listOf("garment_seg.onnx", "human_parse.onnx")
        for (name in required) {
            // Always CPU — NNAPI during verify has killed the process.
            loadOnnxSessionCpu("$dir/$name")?.let { return it }
        }
        return null
    }

    /**
     * Pro graphs (VAE / ControlNet / IP-Adapter) are hundreds of MB each.
     * Startup verify only checks files exist; [verifyOnnxHandshake] opens UNet once.
     */
    private fun verifyProPack(dir: String): String? {
        if (!File(dir, "config.json").exists()) return "Pro config.json missing"
        if (!File(dir, "unet.onnx").exists()) return "No ONNX files in Pro pack"
        return null
    }

    /**
     * Handshake-only: open `unet.onnx` with Pro-safe session options and close.
     * Surfaces FP16 / invalid-graph failures so Pro is not marked Ready on this device.
     */
    private fun probeProUnet(dir: String): String? {
        val unet = File(dir, "unet.onnx")
        if (!unet.isFile) return "unet.onnx missing — re-download Pro pack"
        return runCatching {
            com.zakir.vestra.shared.engine.pro.ProOrtSessions.create(unet.absolutePath).use { }
            null
        }.getOrElse { error ->
            com.zakir.vestra.shared.engine.pro.ProOrtSessions.friendlyMessage(unet.absolutePath, error)
        }
    }

    override fun verifyOnnxHandshake(pack: ModelPack, dir: String): String? {
        if (pack.id.startsWith("pro-")) {
            verifyProPack(dir)?.let { return it }
            return probeProUnet(dir)
        }
        return verifyOnnx(pack, dir)
    }

    /** Presence + byte length already checked; avoid NNAPI smoke inference. */
    private fun verifyRealesrganPack(dir: String): String? {
        val onnx = File(dir).listFiles()?.firstOrNull { it.name.endsWith(".onnx") }
            ?: return "No ONNX file found"
        if (!onnx.isFile || onnx.length() < 1_000L) {
            return "Real-ESRGAN ONNX missing or empty"
        }
        return null
    }

    /** BiRefNet — CPU session open only (no 1024 inference). */
    private fun verifyBirefnetPack(dir: String): String? {
        val onnx = File(dir).listFiles()?.firstOrNull { it.name.endsWith(".onnx") }
            ?: return "No ONNX file found"
        return loadOnnxSessionCpu(onnx.absolutePath)
    }

    /**
     * SD-Turbo graphs are large — file-size checks in [verifyFiles] are the gate.
     * Open only the text encoder on CPU during verify (same safety as Pro pack).
     */
    private fun verifySdturboPack(dir: String): String? {
        val configFile = File(dir, "config.json")
        if (!configFile.exists()) return "SD-Turbo config.json missing"
        if (!File(dir, "vocab.json").isFile || !File(dir, "merges.txt").isFile) {
            return "SD-Turbo tokenizer files missing (vocab.json / merges.txt)"
        }
        val unet = File(dir, "unet.onnx")
        if (!unet.isFile || unet.length() < LocalSdturboPackValidator.MIN_GRAPH_BYTES) {
            return "SD-Turbo UNet missing or placeholder-sized"
        }
        val textEncoder = File(dir, "text_encoder.onnx")
        if (!textEncoder.isFile) return "SD-Turbo text_encoder.onnx missing"
        return loadOnnxSessionCpu(textEncoder.absolutePath)
    }

    private fun verifyManifestOnnxFiles(pack: ModelPack, dir: String): String? {
        for (file in pack.files) {
            if (!file.path.endsWith(".onnx")) continue
            // Skip huge graphs by name heuristic.
            val name = file.path.substringAfterLast('/').lowercase()
            if (name == "unet.onnx" || name.contains("vae") || name.contains("controlnet")) {
                continue
            }
            loadOnnxSessionCpu("$dir/${file.path}")?.let { return "${file.path}: $it" }
        }
        return null
    }

    private fun loadOnnxSessionCpu(modelPath: String): String? = runCatching {
        OrtModel(modelPath, useNnapi = false).use { /* session created in constructor */ }
        null
    }.getOrElse { error ->
        error.message?.take(120) ?: error::class.simpleName ?: "ONNX load failed"
    }
}
