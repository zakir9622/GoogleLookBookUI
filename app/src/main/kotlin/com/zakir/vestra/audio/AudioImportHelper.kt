package com.zakir.vestra.audio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility to copy imported or recorded external audio files to the app's local cache
 * so they can be processed by the on-device voice changer and player pipelines.
 */
object AudioImportHelper {

    suspend fun copyUriToCache(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val contentResolver = context.contentResolver
            val displayName = queryFileName(context, uri) ?: "imported_audio_${System.currentTimeMillis()}.wav"
            val targetDir = File(context.cacheDir, "audio_recordings").also { it.mkdirs() }
            val targetFile = File(targetDir, displayName)

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (targetFile.exists() && targetFile.length() > 0) {
                targetFile.absolutePath
            } else {
                null
            }
        }.getOrNull()
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.lastPathSegment
        }
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            return cursor.getString(nameIndex)
                        }
                    }
                }
            }
        }
        return null
    }
}
