package com.zakir.vestra.ui.components

import android.media.MediaPlayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zakir.vestra.audio.AudioClip
import com.zakir.vestra.audio.AudioClipKind
import com.zakir.vestra.ui.theme.VestraColors
import kotlinx.coroutines.delay

/**
 * Produced-audio list with in-place playback.
 *
 * Playback is deliberately in-app rather than an intent into a system player: the point of the
 * Audio studio is comparing a recording against its voice-changed result, and bouncing out to
 * another app for each one makes that comparison impractical.
 *
 * A single MediaPlayer is shared across rows, so starting one clip stops whatever was playing.
 */
@Composable
fun AudioClipList(
    clips: List<AudioClip>,
    modifier: Modifier = Modifier,
    onShare: ((AudioClip) -> Unit)? = null,
    onDelete: ((AudioClip) -> Unit)? = null,
) {
    if (clips.isEmpty()) {
        GlassCard(modifier) {
            Text(
                "No clips yet — record from the mic, apply a voice change, or generate speech.",
                style = MaterialTheme.typography.bodyMedium,
                color = VestraColors.InkMuted,
            )
        }
        return
    }

    var playingPath by remember { mutableStateOf<String?>(null) }
    var positionMs by remember { mutableStateOf(0) }
    var durationMs by remember { mutableStateOf(0) }
    val player = remember { mutableStateOf<MediaPlayer?>(null) }

    fun stop() {
        runCatching { player.value?.stop() }
        runCatching { player.value?.release() }
        player.value = null
        playingPath = null
        positionMs = 0
        durationMs = 0
    }

    // Release the player when the list leaves composition, otherwise audio keeps playing after
    // the user navigates away.
    DisposableEffect(Unit) { onDispose { stop() } }

    fun toggle(clip: AudioClip) {
        if (playingPath == clip.path) {
            stop()
            return
        }
        stop()
        runCatching {
            MediaPlayer().apply {
                setDataSource(clip.path)
                setOnCompletionListener { stop() }
                prepare()
                start()
                player.value = this
                playingPath = clip.path
                durationMs = duration.coerceAtLeast(0)
            }
        }.onFailure { stop() }
    }

    // Drive the progress bar while something is playing.
    LaunchedEffect(playingPath) {
        while (playingPath != null) {
            positionMs = runCatching { player.value?.currentPosition ?: 0 }.getOrDefault(0)
            delay(200)
        }
    }

    Column(modifier) {
        clips.forEach { clip ->
            val isPlaying = playingPath == clip.path
            GlassCard {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IconButton(onClick = { toggle(clip) }) {
                        Icon(
                            if (isPlaying) Icons.Outlined.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) {
                                "Pause ${clip.fileName}"
                            } else {
                                "Play ${clip.fileName}"
                            },
                            tint = VestraColors.Accent,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            clip.kind.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = when (clip.kind) {
                                AudioClipKind.CONVERTED -> VestraColors.Accent
                                else -> VestraColors.InkMuted
                            },
                        )
                        Text(
                            clip.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = VestraColors.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(clip.durationLabel(), clip.sizeLabel())
                                .joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = VestraColors.InkMuted,
                        )
                    }
                    // Compact, not GlassSecondaryButton: that fills its width and starved the
                    // text column beside it down to one-character-wide vertical text — the same
                    // failure as the studio header, caught here by the screenshot test.
                    onShare?.let { share ->
                        Text(
                            "Share",
                            style = MaterialTheme.typography.labelSmall,
                            color = VestraColors.Accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { share(clip) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    onDelete?.let {
                        IconButton(onClick = { if (isPlaying) stop(); it(clip) }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete ${clip.fileName}",
                                tint = VestraColors.InkMuted,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                if (isPlaying && durationMs > 0) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = VestraColors.Accent,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
