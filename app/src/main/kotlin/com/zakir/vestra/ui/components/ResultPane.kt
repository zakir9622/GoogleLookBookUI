package com.zakir.vestra.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.zakir.vestra.ui.components.ShimmerAsyncImage
import com.zakir.vestra.data.LocalReportStore
import com.zakir.vestra.data.ReportReason
import com.zakir.vestra.media.MediaExport
import com.zakir.vestra.shared.cloud.GenerativeState
import com.zakir.vestra.shared.cloud.ImageEditIntent
import com.zakir.vestra.shared.content.LookbookCopy
import com.zakir.vestra.ui.TestTags
import com.zakir.vestra.ui.theme.VestraColors
import java.io.File
import kotlinx.coroutines.delay

@Composable
fun ResultPane(
    state: GenerativeState?,
    liveLog: List<String> = emptyList(),
    generationStartedAtMs: Long? = null,
    referenceUri: String? = null,
    prompt: String? = null,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onRemix: (() -> Unit)? = null,
    onSelectCandidate: ((candidateId: String) -> Unit)? = null,
    onCreateVariation: ((candidateId: String) -> Unit)? = null,
    onEditIntent: ((ImageEditIntent) -> Unit)? = null,
    onQuickTweak: ((modifier: String) -> Unit)? = null,
    retryLabel: String = LookbookCopy.ACTION_RETRY,
) {
    val context = LocalContext.current
    val reportStore = remember { LocalReportStore(context) }
    var reportPath by remember { mutableStateOf<String?>(null) }
    var showFullScreenImage by remember { mutableStateOf(false) }
    var previewCandidateId by remember { mutableStateOf<String?>(null) }

    if (showFullScreenImage && state is GenerativeState.ImageReady) {
        FullScreenImageViewer(
            imagePath = state.path,
            prompt = prompt,
            onDismiss = { showFullScreenImage = false },
            onRemix = onRemix,
            onEditIntent = onEditIntent,
            onReport = {
                showFullScreenImage = false
                reportPath = state.path
            },
        )
    }
    if (showFullScreenImage && state is GenerativeState.ImageBatchReady) {
        val candidate = state.batch.candidates
            .firstOrNull { it.id == previewCandidateId }
            ?: state.batch.selectedCandidate
        if (candidate != null) {
            FullScreenImageViewer(
                imagePath = candidate.path,
                prompt = candidate.prompt,
                onDismiss = { showFullScreenImage = false },
                onRemix = onCreateVariation?.let { createVariation ->
                    {
                        showFullScreenImage = false
                        createVariation(candidate.id)
                    }
                },
                onEditIntent = onEditIntent,
                onReport = {
                    showFullScreenImage = false
                    reportPath = candidate.path
                },
            )
        }
    }

    reportPath?.let { path ->
        AlertDialog(
            onDismissRequest = { reportPath = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text("Report content", modifier = Modifier.weight(1f))
                    IconButton(onClick = { reportPath = null }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close")
                    }
                }
            },
            text = {
                Column {
                    Text("Reports are stored on this device only (no paid services). Why are you reporting?")
                    Spacer(Modifier.height(8.dp))
                    ReportReason.entries.forEach { reason ->
                        TextButton(
                            onClick = {
                                reportStore.submit(path, reason)
                                reportPath = null
                                Toast.makeText(context, "Report saved locally", Toast.LENGTH_SHORT).show()
                            },
                        ) { Text(reason.label) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { reportPath = null }) { Text("Cancel") }
            },
        )
    }

    when (state) {
        null -> Unit
        is GenerativeState.Preparing, is GenerativeState.Running -> {
            GenerationStateOverlay(
                state = state,
                onCancel = onCancel,
                generationStartedAtMs = generationStartedAtMs,
            )
            LiveGenConsole(liveLog, generationStartedAtMs)
        }
        is GenerativeState.ImageReady -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.RESULT_IMAGE_READY),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column {
                    GlassSectionLabel("YOUR GENERATION")
                    Text(
                        "Ready to review",
                        style = MaterialTheme.typography.bodySmall,
                        color = VestraColors.InkMuted,
                    )
                }
                Text(
                    "Tap to open",
                    style = MaterialTheme.typography.labelSmall,
                    color = VestraColors.Accent,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassPill(text = "Generated locally", active = true)
                GlassPill(text = "Saved to gallery", active = true, accent = VestraColors.Accent)
            }
            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { showFullScreenImage = true }
                    .testTag("expandable_result_image"),
            ) {
                ShimmerAsyncImage(
                    model = File(state.path),
                    contentDescription = "Generated image. Double tap to open fullscreen viewer.",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Open fullscreen to save, remix, share, or report this image.",
                style = MaterialTheme.typography.labelSmall,
                color = VestraColors.InkMuted,
            )
        }
        is GenerativeState.ImageBatchReady -> {
            val batch = state.batch
            val selectedId = batch.selectedCandidateId ?: batch.candidates.firstOrNull()?.id
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.RESULT_IMAGE_READY),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column {
                        GlassSectionLabel("CREATIVE OPTIONS")
                        Text(
                            "${batch.candidates.size} of ${batch.requestedCandidateCount} candidates ready",
                            style = MaterialTheme.typography.bodySmall,
                            color = VestraColors.InkMuted,
                        )
                    }
                    Text(
                        "Select a direction",
                        style = MaterialTheme.typography.labelSmall,
                        color = VestraColors.Accent,
                    )
                }
                batch.selectedCandidate?.referenceReceipt?.let { receipt ->
                    Spacer(Modifier.height(7.dp))
                    GlassPill(
                        text = "Reference used · ${receipt.requestMode}",
                        active = true,
                        accent = VestraColors.Accent,
                    )
                }
                Spacer(Modifier.height(12.dp))
                ImageCandidateGrid(
                    batch = batch,
                    selectedCandidateId = selectedId,
                    onOpenCandidate = { candidate ->
                        previewCandidateId = candidate.id
                        onSelectCandidate?.invoke(candidate.id)
                        showFullScreenImage = true
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tap an option to review it fullscreen, then save, share, report, or create a variation.",
                    style = MaterialTheme.typography.labelSmall,
                    color = VestraColors.InkMuted,
                )
            }
        }
        is GenerativeState.VideoReady -> GlassCard(modifier = Modifier.testTag(TestTags.RESULT_VIDEO_READY)) {
            GlassSectionLabel("VIDEO READY")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassPill(text = "AI-generated", active = true)
                GlassPill(text = "In looks gallery", active = true, accent = VestraColors.Accent)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Saved on device. Save to Movies/The Lookbook or open with a system player.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(state.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassSecondaryButton(
                    text = "Save to Gallery",
                    onClick = { MediaExport.saveVideoToGallery(context, File(state.path)) },
                    modifier = Modifier.weight(1f),
                )
                GlassSecondaryButton(
                    text = LookbookCopy.ACTION_OPEN_VIDEO,
                    onClick = { MediaExport.openVideo(context, File(state.path)) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassSecondaryButton(
                    text = LookbookCopy.ACTION_SHARE,
                    onClick = { MediaExport.share(context, File(state.path), "Share clip") },
                    modifier = Modifier.weight(1f),
                )
                GlassSecondaryButton(
                    text = LookbookCopy.ACTION_REPORT,
                    onClick = { reportPath = state.path },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        is GenerativeState.AudioReady -> GlassCard(modifier = Modifier.testTag(TestTags.RESULT_AUDIO_READY)) {
            GlassSectionLabel("AUDIO READY")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassPill(text = "AI voice", active = true)
                GlassPill(text = "Knobs applied locally", active = true, accent = VestraColors.Accent)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Saved on device. Play with the system audio player or share the clip.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(state.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassSecondaryButton(
                    text = "Play",
                    onClick = { MediaExport.openAudio(context, File(state.path)) },
                    modifier = Modifier.weight(1f),
                )
                GlassSecondaryButton(
                    text = LookbookCopy.ACTION_SHARE,
                    onClick = { MediaExport.share(context, File(state.path), "Share audio") },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            GlassSecondaryButton(
                text = LookbookCopy.ACTION_REPORT,
                onClick = { reportPath = state.path },
            )
        }
        is GenerativeState.CodeStreaming -> GlassCard(modifier = Modifier.testTag(TestTags.RESULT_CODE_STREAMING)) {
            GlassSectionLabel("CODE · generating…")
            Spacer(Modifier.height(8.dp))
            // Live output as the model streams it, not a spinner that resolves into a wall of
            // text only once the whole response is done — the same CodeOutput used for the
            // finished result, just re-rendered on every chunk while it's still growing.
            CodeOutput(
                text = state.text,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            )
        }
        is GenerativeState.CodeReady -> GlassCard(modifier = Modifier.testTag(TestTags.RESULT_CODE_READY)) {
            GlassSectionLabel("CODE · ${state.tokensIn + state.tokensOut} free tokens")
            Text(
                "${state.tokensIn} in · ${state.tokensOut} out",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            // Segmented output: prose stays readable, each fenced block gets its own language
            // label and Copy, so pasting code no longer drags the explanation along with it.
            CodeOutput(
                text = state.text,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            )
            Spacer(Modifier.height(10.dp))
            GlassSecondaryButton(
                text = "Copy all",
                onClick = {
                    val cm = context.getSystemService(ClipboardManager::class.java)
                    cm?.setPrimaryClip(ClipData.newPlainText("lookbook-code", state.text))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                },
            )
        }
        is GenerativeState.TranscribeReady -> GlassCard(modifier = Modifier.testTag(TestTags.RESULT_TRANSCRIBE_READY)) {
            GlassSectionLabel("TRANSCRIPTION")
            Text(
                "On-device · ${state.providerId}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                state.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            )
            Spacer(Modifier.height(10.dp))
            GlassSecondaryButton(
                text = "Copy transcript",
                onClick = {
                    val cm = context.getSystemService(ClipboardManager::class.java)
                    cm?.setPrimaryClip(ClipData.newPlainText("lookbook-transcript", state.text))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                },
            )
        }
        is GenerativeState.Failed -> {
            if (liveLog.isNotEmpty()) LiveGenConsole(liveLog, generationStartedAtMs)
            GlassErrorBanner(
                message = state.message,
                onRetry = onRetry,
                retryLabel = retryLabel,
                onDismiss = onDismiss,
            )
        }
    }
}
