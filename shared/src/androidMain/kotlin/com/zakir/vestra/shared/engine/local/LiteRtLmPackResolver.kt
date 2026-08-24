package com.zakir.vestra.shared.engine.local

import com.zakir.vestra.shared.packs.ModelPackManager
import java.io.File

/**
 * Resolves an installed LiteRT-LM pack directory — prefers [primaryPackId], then [fallbackPackIds].
 */
object LiteRtLmPackResolver {
    fun installedDir(
        packs: ModelPackManager,
        primaryPackId: String,
        vararg fallbackPackIds: String,
    ): Pair<String, File>? {
        for (id in listOf(primaryPackId) + fallbackPackIds) {
            if (!packs.isReady(id)) continue
            val dir = packs.installedDir(id) ?: continue
            return id to File(dir)
        }
        return null
    }

    fun modelPath(
        packs: ModelPackManager,
        primaryPackId: String,
        defaultPrimaryFile: String,
        vararg fallbackPackIds: String,
    ): Pair<String, String>? {
        val resolved = resolveWithConfig(packs, primaryPackId, defaultPrimaryFile, *fallbackPackIds)
            ?: return null
        return resolved.packId to resolved.modelPath
    }

    data class ResolvedPack(
        val packId: String,
        val modelPath: String,
        val config: LiteRtLmPackConfig,
    )

    fun resolveWithConfig(
        packs: ModelPackManager,
        primaryPackId: String,
        defaultPrimaryFile: String,
        vararg fallbackPackIds: String,
    ): ResolvedPack? {
        val installed = installedDir(packs, primaryPackId, *fallbackPackIds) ?: return null
        val (packId, dir) = installed
        val cfg = LiteRtLmPackConfig.read(dir, defaultPrimaryFile)
        val path = LiteRtLmPackConfig.modelPath(dir, cfg.primaryFile) ?: return null
        val file = File(path)
        if (!file.isFile) return null
        return ResolvedPack(packId, path, cfg)
    }
}
