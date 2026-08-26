package com.zakir.vestra.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zakir.vestra.shared.cloud.AiCapability
import com.zakir.vestra.shared.domain.PackState
import com.zakir.vestra.shared.domain.PackStatus
import com.zakir.vestra.shared.domain.PackVerifyStatus
import com.zakir.vestra.shared.engine.local.LiteRtLmPacks
import com.zakir.vestra.shared.packs.ModelPackManager
import com.zakir.vestra.shared.packs.PackDownloadWorker
import com.zakir.vestra.ui.GenerativeViewModel
import com.zakir.vestra.ui.TestTags
import com.zakir.vestra.ui.theme.VestraColors
import com.zakir.vestra.ui.util.rememberPackDownloadStarter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Metadata definition for on-device LiteRT-LM models.
 */
data class LiteRtModelMeta(
    val packId: String,
    val displayName: String,
    val tagline: String,
    val approxSize: String,
    val minRam: String,
    val capability: AiCapability,
    val badge: String,
    val icon: ImageVector,
    val accentColor: Color,
    val isPrimaryRecommendation: Boolean = false,
)

/**
 * Catalog of known LiteRT-LM models runnable fully on-device.
 */
object LiteRtModelCatalog {
    val QWEN3_FAST = LiteRtModelMeta(
        packId = LiteRtLmPacks.QWEN3_CODE,
        displayName = "Qwen3 0.6B INT4",
        tagline = "Lightning fast cold-loads on CPU · Ultra-low latency chat & coding",
        approxSize = "331 MB",
        minRam = "2+ GB RAM",
        capability = AiCapability.CODE,
        badge = "FASTEST ON-DEVICE",
        icon = Icons.Outlined.Bolt,
        accentColor = Color(0xFF38BDF8),
        isPrimaryRecommendation = true,
    )

    val GEMMA4_STANDARD = LiteRtModelMeta(
        packId = LiteRtLmPacks.GEMMA4_CODE,
        displayName = "Gemma 4 E2B",
        tagline = "Gallery-class reasoning, structured coding, and multimodal vision assist",
        approxSize = "2.6 GB",
        minRam = "4+ GB RAM",
        capability = AiCapability.CODE,
        badge = "HIGH REASONING",
        icon = Icons.Outlined.Code,
        accentColor = Color(0xFF10B981),
    )

    val FUNCTION_GEMMA = LiteRtModelMeta(
        packId = LiteRtLmPacks.FUNCTION_GEMMA,
        displayName = "FunctionGemma Tools",
        tagline = "On-device function calling, prompt append, backdrop & setting triggers",
        approxSize = "300 MB",
        minRam = "2+ GB RAM",
        capability = AiCapability.CODE,
        badge = "TOOL CALLING",
        icon = Icons.Outlined.Tune,
        accentColor = Color(0xFFF59E0B),
    )

    val GEMMA3_LEGACY = LiteRtModelMeta(
        packId = LiteRtLmPacks.LEGACY_GEMMA3,
        displayName = "Gemma 3 1B (Legacy)",
        tagline = "MediaPipe LLM format on-device fallback engine",
        approxSize = "530 MB",
        minRam = "2+ GB RAM",
        capability = AiCapability.CODE,
        badge = "LEGACY FALLBACK",
        icon = Icons.Outlined.Memory,
        accentColor = Color(0xFF94A3B8),
    )

    val BONSAI_IMAGE = LiteRtModelMeta(
        packId = "local-bonsai-image-v1",
        displayName = "Bonsai Image 4B (LiteRT)",
        tagline = "Ternary FLUX.2-klein diffusion transformer via LiteRT · 512x512 offline",
        approxSize = "4.0 GB",
        minRam = "8+ GB RAM",
        capability = AiCapability.IMAGE_GEN,
        badge = "LOCAL DIFFUSION",
        icon = Icons.Outlined.Speed,
        accentColor = Color(0xFFA855F7),
    )

    val allModels: List<LiteRtModelMeta> = listOf(
        QWEN3_FAST,
        GEMMA4_STANDARD,
        FUNCTION_GEMMA,
        BONSAI_IMAGE,
        GEMMA3_LEGACY,
    )

    fun find(packId: String): LiteRtModelMeta? = allModels.firstOrNull { it.packId == packId }
}

/**
 * Dedicated Card component that tracks and displays the download progress, verification,
 * and readiness state of a specific LiteRT-LM model.
 */
@Composable
fun LiteRtModelDownloadCard(
    meta: LiteRtModelMeta,
    packState: PackState?,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onVerify: (() -> Unit)? = null,
    onWarmUp: (() -> Unit)? = null,
    onUninstall: (() -> Unit)? = null,
    isWarmedUp: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val status = packState?.status ?: PackStatus.NOT_INSTALLED
    val rawProgress = packState?.progress ?: 0f
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "download_progress_${meta.packId}",
    )
    val isReady = packState?.isReady() == true
    val isDownloading = status == PackStatus.DOWNLOADING
    val isVerifying = packState?.verifyStatus == PackVerifyStatus.VERIFYING
    val isVerifyFailed = packState?.verifyStatus == PackVerifyStatus.FAILED
    val hasError = isVerifyFailed || status == PackStatus.INCOMPATIBLE

    val emerald = Color(0xFF10B981)
    val amber = Color(0xFFF59E0B)
    val sky = Color(0xFF38BDF8)
    val rose = Color(0xFFEF4444)

    val statusBorderColor by animateColorAsState(
        targetValue = when {
            hasError -> rose.copy(alpha = 0.55f)
            isReady -> emerald.copy(alpha = 0.6f)
            isDownloading -> amber.copy(alpha = 0.65f)
            isVerifying -> sky.copy(alpha = 0.6f)
            status == PackStatus.INSTALLED -> sky.copy(alpha = 0.4f)
            else -> VestraColors.GlassBorder
        },
        label = "card_border_${meta.packId}",
    )

    val cardBackground = Brush.verticalGradient(
        colors = listOf(
            VestraColors.AtelierContainer.copy(alpha = 0.95f),
            when {
                hasError -> rose.copy(alpha = 0.08f)
                isReady -> emerald.copy(alpha = 0.07f)
                isDownloading -> amber.copy(alpha = 0.09f)
                isVerifying -> sky.copy(alpha = 0.08f)
                else -> VestraColors.AtelierContainer.copy(alpha = 0.7f)
            },
        ),
    )

    // Pulsing transition for active download or warm state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_${meta.packId}")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = if (isDownloading || isVerifying) 0.9f else 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isDownloading) 800 else 1400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow_alpha_${meta.packId}",
    )

    Box(
        modifier = modifier
            .testTag(TestTags.litertDownloadCard(meta.packId))
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBackground)
            .border(1.dp, statusBorderColor, RoundedCornerShape(20.dp))
            .padding(18.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row: Icon + Title + Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(meta.accentColor.copy(alpha = 0.16f))
                            .border(1.dp, meta.accentColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = meta.icon,
                            contentDescription = meta.displayName,
                            tint = meta.accentColor,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = meta.displayName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = VestraColors.Ivory,
                            )
                            if (meta.isPrimaryRecommendation) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(meta.accentColor.copy(alpha = 0.2f))
                                        .padding(horizontal = 5.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = "FAST",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = meta.accentColor,
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                        }

                        Text(
                            text = "${meta.approxSize} · ${meta.minRam} · LiteRT-LM",
                            style = MaterialTheme.typography.bodySmall,
                            color = VestraColors.IvoryMuted,
                        )
                    }
                }

                // State Badge Pill
                val (badgeBg, badgeBorder, badgeText, badgeColor) = when {
                    hasError -> Quad(rose.copy(alpha = 0.15f), rose.copy(alpha = 0.4f), "ERROR", rose)
                    isReady && isWarmedUp -> Quad(emerald.copy(alpha = 0.2f), emerald.copy(alpha = 0.6f), "WARM IN RAM", emerald)
                    isReady -> Quad(emerald.copy(alpha = 0.15f), emerald.copy(alpha = 0.5f), "READY", emerald)
                    isVerifying -> Quad(sky.copy(alpha = 0.15f), sky.copy(alpha = 0.4f), "VERIFYING", sky)
                    isDownloading -> Quad(amber.copy(alpha = 0.18f), amber.copy(alpha = 0.5f), "${(animatedProgress * 100).toInt()}% DOWNLOADING", amber)
                    status == PackStatus.INSTALLED -> Quad(sky.copy(alpha = 0.12f), sky.copy(alpha = 0.3f), "INSTALLED", sky)
                    status == PackStatus.UPDATE_AVAILABLE -> Quad(amber.copy(alpha = 0.15f), amber.copy(alpha = 0.4f), "UPDATE", amber)
                    else -> Quad(VestraColors.IvoryMuted.copy(alpha = 0.12f), VestraColors.IvoryMuted.copy(alpha = 0.25f), "OFFLINE READY", VestraColors.IvoryMuted)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeBg)
                        .border(0.5.dp, badgeBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = badgeColor,
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Description / Tagline
            Text(
                text = meta.tagline,
                style = MaterialTheme.typography.bodySmall,
                color = VestraColors.IvoryMuted,
            )

            Spacer(Modifier.height(14.dp))

            // Dynamic State Presentation
            when {
                // 1. ACTIVE DOWNLOADING STATE
                isDownloading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(VestraColors.AtelierCanvas.copy(alpha = 0.6f))
                            .border(1.dp, amber.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = amber,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Streaming model weights…",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = VestraColors.Ivory,
                                )
                            }
                            Text(
                                text = "${(animatedProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = amber,
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // High precision smooth progress bar
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = amber,
                            trackColor = amber.copy(alpha = 0.2f),
                            strokeCap = StrokeCap.Round,
                        )

                        Spacer(Modifier.height(8.dp))

                        // Detailed byte & resilience info
                        val totalBytes = packState?.pack?.totalBytes ?: 0L
                        val downloadedBytes = (totalBytes * animatedProgress).toLong()
                        val bytesText = if (totalBytes > 0) {
                            "${formatByteString(downloadedBytes)} / ${formatByteString(totalBytes)}"
                        } else {
                            "Approx ${meta.approxSize}"
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = bytesText,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = VestraColors.IvoryMuted,
                            )

                            OutlinedButton(
                                onClick = onCancelDownload,
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag(TestTags.litertCancelButton(meta.packId)),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = rose,
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, rose.copy(alpha = 0.4f)),
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = "Cancel", modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Pause / Stop", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Security,
                                contentDescription = null,
                                tint = VestraColors.IvoryMuted.copy(alpha = 0.7f),
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Resumes if interrupted · Background WorkManager",
                                style = MaterialTheme.typography.labelSmall,
                                color = VestraColors.IvoryMuted.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                            )
                        }
                    }
                }

                // 2. VERIFYING INTEGRITY STATE
                isVerifying -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(sky.copy(alpha = 0.08f))
                            .border(1.dp, sky.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = sky,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Verifying LiteRT model integrity…",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = VestraColors.Ivory,
                                )
                                Text(
                                    text = "Validating SHA-256 signatures & LiteRT-LM runtime engine",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VestraColors.IvoryMuted,
                                )
                            }
                        }
                    }
                }

                // 3. READY / INSTALLED STATE
                isReady -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(emerald.copy(alpha = 0.08f))
                            .border(1.dp, emerald.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(emerald.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = "Ready",
                                        tint = emerald,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Ready for On-Device Inference",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = emerald,
                                    )
                                    Text(
                                        text = "100% Offline · Zero latency · \$0 tokens",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = VestraColors.IvoryMuted,
                                    )
                                }
                            }

                            if (onWarmUp != null && !isWarmedUp) {
                                Button(
                                    onClick = onWarmUp,
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag(TestTags.litertWarmupButton(meta.packId)),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = emerald,
                                        contentColor = Color.Black,
                                    ),
                                ) {
                                    Icon(Icons.Outlined.Memory, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Warm Up", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }

                        if (onUninstall != null) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                if (onVerify != null) {
                                    OutlinedButton(
                                        onClick = onVerify,
                                        modifier = Modifier
                                            .height(30.dp)
                                            .testTag(TestTags.litertVerifyButton(meta.packId)),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                                    ) {
                                        Icon(Icons.Outlined.Refresh, contentDescription = "Verify", modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Re-verify", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                }
                                OutlinedButton(
                                    onClick = onUninstall,
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VestraColors.IvoryMuted),
                                ) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Remove", modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Remove Pack", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                // 4. ERROR / INCOMPATIBLE STATE
                hasError -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(rose.copy(alpha = 0.08f))
                            .border(1.dp, rose.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = "Error",
                                tint = rose,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (status == PackStatus.INCOMPATIBLE) "Device Incompatible" else "Verification Failed",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = rose,
                                )
                                Text(
                                    text = packState?.verifyError
                                        ?: (if (status == PackStatus.INCOMPATIBLE) "This device doesn't satisfy minimum RAM / NPU spec" else "Check storage and tap retry"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VestraColors.IvoryMuted,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Button(
                                onClick = onStartDownload,
                                modifier = Modifier.height(34.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = rose,
                                    contentColor = Color.White,
                                ),
                            ) {
                                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Retry Download", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // 5. NOT INSTALLED / AVAILABLE TO DOWNLOAD STATE
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Zero Token Cost",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = VestraColors.Ivory,
                            )
                            Text(
                                text = if (rawProgress > 0f) "Partial cache: ${(rawProgress * 100).toInt()}%" else "One-time download (${meta.approxSize})",
                                style = MaterialTheme.typography.bodySmall,
                                color = VestraColors.IvoryMuted,
                            )
                        }

                        Button(
                            onClick = onStartDownload,
                            modifier = Modifier
                                .testTag(TestTags.litertDownloadButton(meta.packId))
                                .height(38.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = meta.accentColor,
                                contentColor = Color.Black,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudDownload,
                                contentDescription = "Download",
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (rawProgress > 0f) "Resume" else "Download",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact active download banner for in-flight LiteRT-LM model downloads.
 * Appears seamlessly at the top or bottom of studio/chat panes when a model is downloading.
 */
@Composable
fun LiteRtActiveDownloadBanner(
    packManager: ModelPackManager,
    onOpenPacks: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val states by packManager.states.collectAsState()
    val context = LocalContext.current

    // Find any actively downloading LiteRT model pack
    val downloadingEntry = states.values.firstOrNull {
        it.status == PackStatus.DOWNLOADING && LiteRtModelCatalog.find(it.pack.id) != null
    } ?: states.values.firstOrNull { it.status == PackStatus.DOWNLOADING }

    AnimatedVisibility(
        visible = downloadingEntry != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier.testTag(TestTags.LITERT_ACTIVE_DOWNLOAD_BANNER),
    ) {
        if (downloadingEntry != null) {
            val meta = LiteRtModelCatalog.find(downloadingEntry.pack.id)
            val displayName = meta?.displayName ?: downloadingEntry.pack.displayName
            val progress = downloadingEntry.progress
            val amber = Color(0xFFF59E0B)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF1E293B),
                                Color(0xFF0F172A),
                            ),
                        ),
                    )
                    .border(1.dp, amber.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .then(
                        if (onOpenPacks != null) {
                            Modifier.clickable(role = Role.Button) { onOpenPacks() }
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = amber,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Downloading $displayName",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = VestraColors.Ivory,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = amber,
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    PackDownloadWorker.cancel(context, downloadingEntry.pack.id)
                                    packManager.markCancelled(downloadingEntry.pack.id)
                                },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "Stop",
                                    tint = VestraColors.IvoryMuted,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape),
                        color = amber,
                        trackColor = amber.copy(alpha = 0.2f),
                        strokeCap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

/**
 * Multi-model LiteRT Suite Download Tracker Component.
 * Displays all on-device LiteRT models with download triggers, active progress gauges,
 * verification statuses, and storage metrics in a single unified view.
 */
@Composable
fun LiteRtSuiteDownloadTracker(
    packManager: ModelPackManager,
    viewModel: GenerativeViewModel? = null,
    onOpenPacks: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val states by packManager.states.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val startDownload = rememberPackDownloadStarter(showToast = true)
    val warmup by viewModel?.warmup?.collectAsState() ?: remember { mutableStateOf(null) }

    var selectedFilter by remember { mutableStateOf<String?>("all") }

    val activeDownloads = states.values.count { it.status == PackStatus.DOWNLOADING }
    val readyModels = states.values.count { it.isReady() }
    val freeBytes = remember(states) { packManager.freeBytesOnDevice() }

    Column(
        modifier = modifier
            .testTag(TestTags.LITERT_DOWNLOAD_TRACKER)
            .fillMaxWidth(),
    ) {
        // Suite Overview Header Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            VestraColors.AtelierContainer,
                            VestraColors.AtelierCanvas,
                        ),
                    ),
                )
                .border(1.dp, VestraColors.GlassBorder, RoundedCornerShape(18.dp))
                .padding(16.dp),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Memory,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "LiteRT-LM On-Device Suite",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = VestraColors.Ivory,
                            )
                        }
                        Text(
                            text = "Download once · Fully private · \$0 Tokens",
                            style = MaterialTheme.typography.bodySmall,
                            color = VestraColors.IvoryMuted,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF38BDF8).copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "$readyModels/${LiteRtModelCatalog.allModels.size} READY",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF38BDF8),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StatPill(
                        label = "Active Downloads",
                        value = if (activeDownloads > 0) "$activeDownloads active" else "Idle",
                        accentColor = if (activeDownloads > 0) Color(0xFFF59E0B) else VestraColors.IvoryMuted,
                    )
                    StatPill(
                        label = "Device Free Disk",
                        value = formatByteString(freeBytes),
                        accentColor = Color(0xFF10B981),
                    )
                    StatPill(
                        label = "Engine Runtime",
                        value = "LiteRT CPU/GPU",
                        accentColor = Color(0xFF38BDF8),
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Model Cards List
        LiteRtModelCatalog.allModels.forEach { model ->
            val packState = states[model.packId]
            val isWarmed = when (warmup) {
                is GenerativeViewModel.Warmup.Ready -> (warmup as GenerativeViewModel.Warmup.Ready).label.contains(model.displayName, ignoreCase = true)
                else -> false
            }

            LiteRtModelDownloadCard(
                meta = model,
                packState = packState,
                onStartDownload = { startDownload(model.packId) },
                onCancelDownload = {
                    PackDownloadWorker.cancel(context, model.packId)
                    packManager.markCancelled(model.packId)
                },
                onVerify = {
                    scope.launch {
                        withContext(Dispatchers.Default) {
                            packManager.verifyInstalled(model.packId)
                        }
                    }
                },
                onWarmUp = {
                    viewModel?.warmUpLocal(model.capability)
                },
                onUninstall = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            packManager.uninstall(model.packId)
                        }
                    }
                },
                isWarmedUp = isWarmed,
            )

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    accentColor: Color,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = VestraColors.IvoryMuted,
            fontSize = 11.sp,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = accentColor,
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private fun formatByteString(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1e9)
    bytes >= 1_000_000 -> "%.0f MB".format(bytes / 1e6)
    bytes > 0 -> "%.0f KB".format(bytes / 1e3)
    else -> "0 MB"
}
