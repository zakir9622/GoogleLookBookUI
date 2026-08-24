package com.zakir.vestra.shared.packs

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File
import java.security.MessageDigest

/**
 * Pack filesystem. [rootProvider] should return a durable Documents tree when
 * all-files access is granted so installs survive app uninstall/reinstall.
 * The provider is invoked on each access so granting permission mid-session remounts.
 */
class AndroidPackFileSystem(
    context: Context,
    private val rootProvider: () -> File = { File(context.filesDir, "packs") },
) : PackFileSystem {

    private fun root(): File = rootProvider().also { it.mkdirs() }

    override fun packsRoot(): String = root().absolutePath

    override fun exists(path: String): Boolean = File(path).exists()

    override fun fileSize(path: String): Long = File(path).length()

    override fun delete(path: String) {
        File(path).deleteRecursively()
    }

    override fun mkdirs(path: String) {
        File(path).mkdirs()
    }

    override fun move(from: String, to: String) {
        val src = File(from)
        val dst = File(to)
        if (!src.renameTo(dst)) {
            src.copyRecursively(dst, overwrite = true)
            src.deleteRecursively()
        }
    }

    override fun sha256(path: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        File(path).inputStream().use { stream ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    override fun listFiles(path: String): List<String> =
        File(path).listFiles()?.map { it.absolutePath } ?: emptyList()

    override fun readText(path: String): String? =
        File(path).takeIf { it.exists() }?.readText()

    override fun writeText(path: String, content: String) {
        File(path).apply { parentFile?.mkdirs() }.writeText(content)
    }

    override fun freeBytes(): Long = root().usableSpace
}

class AndroidDeviceProbe(private val context: Context) : DeviceProbe {

    override fun totalRamMb(): Long {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return info.totalMem / (1024 * 1024)
    }

    override fun hasNpu(): Boolean {
        val is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
        return is64Bit && Build.VERSION.SDK_INT >= 31 && totalRamMb() >= 6 * 1024
    }

    override fun sdkInt(): Int = Build.VERSION.SDK_INT
}
