package com.zakir.vestra.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File

/**
 * App data that must survive uninstall / reinstall:
 *   Documents/TheLookbook/packs/     — model packs (+ .complete markers)
 *   Documents/TheLookbook/tokens.json — API token sidecar
 *
 * Android 11+ uses all-files access so we can keep a real directory tree for
 * multi-GB packs. Older Android versions use app-private filesDir/packs to
 * avoid requesting unsupported broad-storage access.
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

    fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

    fun manageAllFilesIntent(context: Context): Intent {
        val appUri = Uri.parse("package:${context.packageName}")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, appUri)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

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
