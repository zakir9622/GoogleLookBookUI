package com.zakir.vestra

import android.content.Context
import com.zakir.vestra.shared.domain.EngineTier
import com.zakir.vestra.shared.domain.ModelPack
import com.zakir.vestra.shared.domain.PackFile
import com.zakir.vestra.shared.packs.AndroidPackIntegrityChecker
import com.zakir.vestra.shared.packs.ModelPackManager
import java.io.File

/**
 * Makes the app generate images out-of-the-box for manual testing, before the
 * Hugging Face packs repo is set up. The Lite pack is bundled in the debug
 * APK's assets (`src/debug/assets/packs/lite-v1/`); on first launch this copies
 * it into the pack directory and seeds a local manifest so the pack manager
 * reports it INSTALLED **after ONNX verification**.
 *
 * In release builds the asset is absent, so [seedLitePack] no-ops — production
 * still downloads packs from Hugging Face as normal.
 */
object DebugPackBootstrap {

    private const val PACK_ID = "lite-v1"
    private const val VERSION = 1
    private val FILES = listOf("garment_seg.onnx" to 1_321_751L, "human_parse.onnx" to 67_287_788L)
    private val checker = AndroidPackIntegrityChecker()

    /**
     * Copies ~68 MB, so it must never run on the main thread — doing so ANRs startup and
     * leaves a half-written pack that later looks installed. [onSeeded] runs once the files
     * land, because the pack manager reads its catalog long before that.
     */
    fun seedLitePackAsync(
        context: Context,
        packsRoot: File = File(context.filesDir, "packs"),
        onSeeded: () -> Unit = {},
    ) {
        Thread({
            seedLitePack(context, packsRoot)
            onSeeded()
        }, "lite-pack-seed").apply {
            priority = Thread.MIN_PRIORITY
            isDaemon = true
        }.start()
    }

    fun seedLitePack(context: Context, packsRoot: File = File(context.filesDir, "packs")) {
        val versionDir = File(packsRoot, "$PACK_ID/$VERSION")
        val completeMarker = File(versionDir, ModelPackManager.COMPLETE_MARKER)

        runCatching {
            // Probe: absent in release builds → IOException → skip entirely.
            context.assets.open("packs/$PACK_ID/garment_seg.onnx").close()

            writeBundledManifest(packsRoot)

            if (completeMarker.exists()) {
                // Re-verify on every launch — wipe corrupt installs from interrupted copies.
                if (verifyDir(versionDir.absolutePath) == null) {
                    File(versionDir, ModelPackManager.ONNX_OK_MARKER).writeText("ok")
                    return
                }
                com.zakir.vestra.shared.engine.lite.OrtSessionCache.invalidateContaining(versionDir.absolutePath)
                versionDir.deleteRecursively()
            }

            val staging = File(packsRoot, "$PACK_ID/.staging-$VERSION")
            staging.deleteRecursively()
            staging.mkdirs()
            for ((name, _) in FILES) {
                context.assets.open("packs/$PACK_ID/$name").use { input ->
                    File(staging, name).outputStream().use { input.copyTo(it) }
                }
            }
            versionDir.parentFile?.let {
                com.zakir.vestra.shared.engine.lite.OrtSessionCache.invalidateContaining(it.absolutePath)
            }
            versionDir.deleteRecursively()
            versionDir.parentFile?.mkdirs()
            if (!staging.renameTo(versionDir)) {
                staging.copyRecursively(versionDir, overwrite = true)
                staging.deleteRecursively()
            }

            val verifyError = verifyDir(versionDir.absolutePath)
            if (verifyError != null) {
                com.zakir.vestra.shared.engine.lite.OrtSessionCache.invalidateContaining(versionDir.absolutePath)
                versionDir.deleteRecursively()
                return
            }
            completeMarker.writeText(VERSION.toString())
            File(versionDir, ModelPackManager.ONNX_OK_MARKER).writeText("ok")
        }
    }

    private fun verifyDir(dir: String): String? {
        val pack = bundledLitePack()
        return checker.verifyFiles(pack, dir) ?: checker.verifyOnnx(pack, dir)
    }

    private fun bundledLitePack(): ModelPack {
        val files = FILES.map { (name, bytes) ->
            PackFile(path = name, url = "bundled", sha256 = "bundled", bytes = bytes)
        }
        return ModelPack(
            id = PACK_ID,
            version = VERSION,
            tier = EngineTier.LITE,
            displayName = "Lite engine",
            description = "Bundled for testing.",
            totalBytes = FILES.sumOf { it.second },
            files = files,
        )
    }

    /**
     * Kept separate from manifest.cache.json so a remote refresh — whose published catalog
     * does not list the bundled pack — cannot drop it again.
     */
    private fun writeBundledManifest(packsRoot: File) {
        packsRoot.mkdirs()
        File(packsRoot, ModelPackManager.BUNDLED_MANIFEST).writeText(localManifest())
    }

    private fun localManifest(): String {
        val files = FILES.joinToString(",") { (name, bytes) ->
            """{"path":"$name","url":"bundled","sha256":"bundled","bytes":$bytes}"""
        }
        return """
            {"schemaVersion":1,"packs":[
              {"id":"$PACK_ID","version":$VERSION,"tier":"LITE",
               "displayName":"Lite engine","description":"Bundled for testing.",
               "totalBytes":68609539,"files":[$files],
               "minSpec":{"minRamMb":0,"requiresNpu":false,"minSdk":26}}
            ]}
        """.trimIndent()
    }
}
