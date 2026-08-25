package com.zakir.vestra.media

import android.content.Context
import java.io.File

/** Clears regenerable caches without touching installed model packs or wardrobe index. */
object CacheCleanup {

    data class Result(val deletedFiles: Int, val freedBytes: Long)

    fun clearAppCaches(context: Context): Result {
        var files = 0
        var bytes = 0L
        val roots = listOf(
            context.cacheDir,
            File(context.filesDir, "generations"),
            File(context.filesDir, "create"),
            File(context.filesDir, "video"),
            File(context.cacheDir, "image_cache"),
            File(context.cacheDir, "coil"),
        )
        roots.forEach { root ->
            if (!root.exists()) return@forEach
            root.walkBottomUp().forEach { file ->
                if (file.isFile) {
                    val size = file.length()
                    if (file.delete()) {
                        files++
                        bytes += size
                    }
                } else if (file.isDirectory && file != root && file.list().isNullOrEmpty()) {
                    file.delete()
                }
            }
        }
        return Result(files, bytes)
    }

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1e9)
        bytes >= 1_000_000 -> "%.0f MB".format(bytes / 1e6)
        bytes >= 1_000 -> "%.0f KB".format(bytes / 1e3)
        else -> "$bytes B"
    }
}
