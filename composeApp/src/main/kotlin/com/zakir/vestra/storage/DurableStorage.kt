package com.zakir.vestra.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import java.io.File

/**
 * App data that must survive uninstall / reinstall:
 *   Documents/TheLookbook/packs/     — model packs (+ .complete markers)
 *   Documents/TheLookbook/tokens.json — API token sidecar
 *
 * Requires all-files access on API 30+ so we can keep a real directory tree
 * for multi-GB packs. Until granted, falls back to app-private filesDir/packs
 * (wiped on uninstall).
 */
object DurableStorage {

    const val ROOT_FOLDER = "TheLookbook"
    private const val PACKS_FOLDER = "packs"
    const val TOKENS_FILE = "tokens.json"

    fun lookbookRoot(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), ROOT_FOLDER)

    fun durablePacksRoot(): File = File(lookbookRoot(), PACKS_FOLDER)

    fun tokensSidecar(): File = File(lookbookRoot(), TOKENS_FILE)

    fun privatePacksRoot(context: Context): File = File(context.filesDir, PACKS_FOLDER)

    fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

    fun manageAllFilesIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Prefer durable Documents tree when permitted; otherwise private storage.
     * Migrates any previously downloaded private packs into the durable tree.
     */
    fun resolvePacksRoot(context: Context): File {
        if (hasAllFilesAccess()) {
            val durable = durablePacksRoot().also { it.mkdirs() }
            migratePrivatePacksIfNeeded(context, durable)
            lookbookRoot().mkdirs()
            return durable
        }
        return privatePacksRoot(context).also { it.mkdirs() }
    }

    fun isUsingDurablePacks(context: Context): Boolean =
        hasAllFilesAccess() && resolvePacksRoot(context).absolutePath == durablePacksRoot().absolutePath

    private fun migratePrivatePacksIfNeeded(context: Context, durable: File) {
        val private = privatePacksRoot(context)
        if (!private.exists() || private.absolutePath == durable.absolutePath) return
        private.listFiles()?.forEach { child ->
            val dest = File(durable, child.name)
            if (!dest.exists()) {
                runCatching {
                    child.copyRecursively(dest, overwrite = false)
                }
            }
        }
        // Leave private copy as backup until user clears app storage; durable is canonical.
    }
}
