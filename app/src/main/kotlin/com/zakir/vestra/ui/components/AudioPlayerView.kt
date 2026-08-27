package com.zakir.vestra.ui.components

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zakir.vestra.audio.AudioEditorEngine
import com.zakir.vestra.media.MediaExport
import com.zakir.vestra.ui.theme.VestraColors
import java.io.File
import kotlinx.coroutines.delay

@Composable
fun AudioPlayerView(
    audioFile: File,
    title: String? = null,
    badgeText: String? = null,
    autoPlay: Boolean = true,
    showSaveButton: Boolean = true,
    showShareButton: Boolean = true,
    onSaved: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(1) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var waveform by remember(audioFile.absolutePath) {
        mutableStateOf<List<Float>>(emptyList())
    }

    LaunchedEffect(audioFile.absolutePath) {
        waveform = AudioEditorEngine.extractWaveform(audioFile, barCount = 36)
    }

    fun stopPlayer() {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        isPlaying = false
        currentPositionMs = 0
    }

    fun startPlayback() {
        if (!audioFile.exists()) return
        stopPlayer()
        runCatching {
            val mp = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                setOnCompletionListener {
                    isPlaying = false
                    currentPositionMs = 0
                }
                prepare()
                durationMs = duration.coerceAtLeast(1)
                start()
            }
            player = mp
            isPlaying = true
        }
    }

    fun togglePlayback() {
        if (isPlaying) {
            player?.pause()
            isPlaying = false
        } else {
            if (player != null) {
                player?.start()
                isPlaying = true
            } else {
                startPlayback()
            }
        }
    }

    fun seekTo(progressFraction: Float) {
        val targetMs = (progressFraction * durationMs).toInt().coerceIn(0, durationMs)
        player?.seekTo(targetMs)
        currentPositionMs = targetMs
    }

    LaunchedEffect(audioFile.absolutePath, autoPlay) {
        if (autoPlay) {
            delay(150)
            startPlayback()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPositionMs = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)
            durationMs = runCatching { player?.duration ?: durationMs }.getOrDefault(durationMs).coerceAtLeast(1)
            delay(120)
        }
    }

    DisposableEffect(audioFile.absolutePath) {
        onDispose { stopPlayer() }
    }

    val progressFraction = (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(VestraColors.SurfaceRaised)
            .border(1.dp, VestraColors.Accent.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        // Header Row: Title & Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(VestraColors.Accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.GraphicEq,
                        contentDescription = null,
                        tint = VestraColors.Accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = title ?: audioFile.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = VestraColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${formatTime(currentPositionMs)} / ${formatTime(durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = VestraColors.InkMuted,
                    )
                }
            }

            if (badgeText != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(VestraColors.Accent.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = VestraColors.Accent,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Visual Waveform Bars
        if (waveform.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(VestraColors.Canvas.copy(alpha = 0.6f))
                    .clickable {
                        // Clicking anywhere seeks proportionally
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                waveform.forEachIndexed { index, amp ->
                    val barFraction = index.toFloat() / waveform.size.toFloat()
                    val isPast = barFraction <= progressFraction
                    val barHeightFraction = amp.coerceIn(0.12f, 1f)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 1.dp)
                            .fillMaxHeight(barHeightFraction)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (isPast) VestraColors.Accent
                                else VestraColors.InkMuted.copy(alpha = 0.35f),
                            ),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Progress Slider
        Slider(
            value = progressFraction,
            onValueChange = { seekTo(it) },
            colors = SliderDefaults.colors(
                thumbColor = VestraColors.Accent,
                activeTrackColor = VestraColors.Accent,
                inactiveTrackColor = VestraColors.GlassBorder,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp),
        )

        Spacer(Modifier.height(8.dp))

        // Action Controls Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Play/Pause & Replay
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(VestraColors.Accent)
                        .clickable { togglePlayback() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = VestraColors.Canvas,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        seekTo(0f)
                        if (!isPlaying) startPlayback()
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Outlined.Replay,
                        contentDescription = "Replay",
                        tint = VestraColors.InkMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Save MP3 & Share Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (showSaveButton) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(VestraColors.GlassFill)
                            .border(1.dp, VestraColors.GlassBorder, RoundedCornerShape(12.dp))
                            .clickable {
                                val ok = MediaExport.saveAudioToMusic(context, audioFile, title = title)
                                if (ok) onSaved?.invoke()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Download,
                                contentDescription = "Save audio",
                                tint = VestraColors.Accent,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Save MP3",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = VestraColors.Ink,
                            )
                        }
                    }
                }

                if (showShareButton) {
                    IconButton(
                        onClick = {
                            MediaExport.share(context, audioFile, chooserTitle = "Share ${title ?: "Audio"}")
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Share,
                            contentDescription = "Share",
                            tint = VestraColors.InkMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Int): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
