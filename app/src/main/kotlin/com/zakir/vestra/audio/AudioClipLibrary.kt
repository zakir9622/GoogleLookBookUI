package com.zakir.vestra.audio

import android.media.MediaMetadataRetriever
import java.io.File

/**
 * What produced a clip. Derived from the filename prefixes the producers already use
 * (`mic_`, `voice_`, `sys_tts_`) rather than a parallel database, so the list stays correct
 * even for clips written before this existed.
 */
enum class AudioClipKind(val label: String) {
    RECORDING("Recording"),
    CONVERTED("Voice-changed"),
    SPEECH("Generated speech"),
    OTHER("Audio"),
}

data class AudioClip(
    val path: String,
    val kind: AudioClipKind,
    val savedAtMs: Long,
    val bytes: Long,
    val durationMs: Long?,
) {
    val fileName: String get() = path.substringAfterLast('/')

    /** "1.4 MB" / "812 KB" — size is what the user can actually act on (share, delete). */
    fun sizeLabel(): String = when {
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }

    fun durationLabel(): String? = durationMs?.let {
        val totalSec = it / 1000
        "%d:%02d".format(totalSec / 60, totalSec % 60)
    }
}

/**
 * Lists produced audio across the two directories the app writes to: generated speech and
 * voice-changed output in `generations/`, mic captures in the recordings cache.
 */
object AudioClipLibrary {

    private val AUDIO_EXTENSIONS = setOf("wav", "mp3", "m4a", "ogg", "aac")

    fun kindOf(name: String): AudioClipKind = when {
        name.startsWith("mic_") -> AudioClipKind.RECORDING
        name.startsWith("voice_") -> AudioClipKind.CONVERTED
        name.startsWith("sys_tts_") || name.startsWith("tts_") -> AudioClipKind.SPEECH
        else -> AudioClipKind.OTHER
    }

    /** Newest first. Directories that do not exist are skipped rather than throwing. */
    fun scan(dirs: List<File>, limit: Int = 40, withDuration: Boolean = true): List<AudioClip> =
        dirs.asSequence()
            .filter { it.isDirectory }
            .flatMap { it.listFiles()?.asSequence() ?: emptySequence() }
            .filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS && it.length() > 0 }
            .sortedByDescending { it.lastModified() }
            .take(limit)
            .map { file ->
                AudioClip(
                    path = file.absolutePath,
                    kind = kindOf(file.name),
                    savedAtMs = file.lastModified(),
                    bytes = file.length(),
                    durationMs = if (withDuration) readDurationMs(file) else null,
                )
            }
            .toList()

    /**
     * Duration via MediaMetadataRetriever. Returns null instead of throwing — a partially
     * written or codec-unsupported clip should still be listed, just without a duration.
     */
    private fun readDurationMs(file: File): Long? = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        }
    }.getOrNull()

    fun delete(clip: AudioClip): Boolean = runCatching { File(clip.path).delete() }.getOrDefault(false)
}
