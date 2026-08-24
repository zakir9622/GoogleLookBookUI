package com.zakir.vestra.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zakir.vestra.shared.cloud.AiCapability
import com.zakir.vestra.shared.engine.local.LiteRtLmPacks
import com.zakir.vestra.shared.packs.ModelPackManager
import com.zakir.vestra.ui.GenerativeViewModel
import com.zakir.vestra.ui.theme.VestraColors

/**
 * High-visibility status indicator for on-device LiteRT models (Gemma 4, Qwen3, FunctionGemma).
 * Shows whether the model pack is downloaded, warm/ready in memory for instant low-latency inference,
 * currently warming up, or requiring user attention.
 */
@Composable
fun LiteRtStatusIndicator(
    modelName: String = "LiteRT Gemma",
    isInstalled: Boolean,
    isLoaded: Boolean,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    backend: String? = null,
    onWarmUp: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    onOpenPacks: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    // Pulsing animation for active warmup / loaded glow
    val infiniteTransition = rememberInfiniteTransition(label = "litert_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isLoading) 1.35f else 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isLoading) 600 else 1400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_scale",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = if (isLoading) 0.9f else 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isLoading) 600 else 1400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )

    val emerald = Color(0xFF10B981)
    val cyan = Color(0xFF35E0E0)
    val amber = Color(0xFFF59E0B)
    val rose = Color(0xFFEF4444)

    val statusColor by animateColorAsState(
        targetValue = when {
            errorMessage != null -> rose
            isLoading -> amber
            isLoaded -> emerald
            isInstalled -> cyan
            else -> VestraColors.IvoryMuted.copy(alpha = 0.5f)
        },
        label = "status_color",
    )

    val containerBackground = Brush.horizontalGradient(
        colors = listOf(
            VestraColors.AtelierContainer.copy(alpha = 0.95f),
            statusColor.copy(alpha = 0.08f),
        ),
    )

    Box(
        modifier = modifier
            .testTag("litert_status_indicator")
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(containerBackground)
            .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable(role = Role.Button) {
                if (errorMessage != null) {
                    onRetry?.invoke()
                } else if (!isInstalled && onOpenPacks != null) {
                    onOpenPacks()
                } else if (!isLoaded && !isLoading && onWarmUp != null) {
                    onWarmUp()
                } else {
                    expanded = !expanded
                }
            }
            .padding(14.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    // Pulsing Status Dot or Spinner
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(24.dp),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = amber,
                            )
                        } else {
                            if (isLoaded || isInstalled) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .scale(pulseScale)
                                        .alpha(pulseAlpha)
                                        .clip(CircleShape)
                                        .background(statusColor.copy(alpha = 0.25f)),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(statusColor),
                            )
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = modelName,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = VestraColors.Ivory,
                            )
                            if (isLoaded) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(emerald.copy(alpha = 0.15f))
                                        .border(0.5.dp, emerald.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp),
                                ) {
                                    Text(
                                        text = "WARM IN RAM",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = emerald,
                                    )
                                }
                            }
                        }

                        val statusSubtitle = when {
                            errorMessage != null -> "Failed to initialize · Tap to retry"
                            isLoading -> "Initializing LiteRT engine · Cold loading…"
                            isLoaded -> "Ready for instant on-device inference ${backend?.let { "($it)" }.orEmpty()}"
                            isInstalled -> "Installed on disk · Tap to warm up"
                            else -> "Pack not downloaded · Tap to get pack"
                        }

                        Text(
                            text = statusSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                errorMessage != null -> rose
                                isLoaded -> emerald
                                else -> VestraColors.IvoryMuted
                            },
                        )
                    }
                }

                // Action Affordance Chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.12f))
                        .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (icon, label) = when {
                            errorMessage != null -> Icons.Outlined.Refresh to "Retry"
                            isLoading -> Icons.Outlined.Speed to "Loading"
                            isLoaded -> Icons.Outlined.CheckCircle to "Ready"
                            isInstalled -> Icons.Outlined.Memory to "Warm Up"
                            else -> Icons.Outlined.CloudDownload to "Packs"
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = statusColor,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = statusColor,
                        )
                    }
                }
            }

            // Expanded Details Section
            AnimatedVisibility(
                visible = expanded || errorMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .background(VestraColors.AtelierCanvas.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                ) {
                    if (errorMessage != null) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = "Error",
                                tint = rose,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = rose,
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Runtime Backend:",
                                style = MaterialTheme.typography.labelSmall,
                                color = VestraColors.IvoryMuted,
                            )
                            Text(
                                text = backend ?: "LiteRT-LM (CPU/GPU Auto)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = VestraColors.Ivory,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Privacy & Latency:",
                                style = MaterialTheme.typography.labelSmall,
                                color = VestraColors.IvoryMuted,
                            )
                            Text(
                                text = "100% On-Device · Zero Network",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = emerald,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Convenient state-backed Composable that binds directly to [GenerativeViewModel] and [ModelPackManager].
 */
@Composable
fun LiteRtGemmaStatusIndicator(
    viewModel: GenerativeViewModel,
    packManager: ModelPackManager?,
    onOpenPacks: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val warmup by viewModel.warmup.collectAsState()
    val packStates by packManager?.states?.collectAsState() ?: remember { mutableStateOf(emptyMap()) }

    val gemmaPackReady = packStates[LiteRtLmPacks.GEMMA4_CODE]?.isReady() == true ||
        packStates[LiteRtLmPacks.QWEN3_CODE]?.isReady() == true ||
        packStates[LiteRtLmPacks.FUNCTION_GEMMA]?.isReady() == true ||
        packStates[LiteRtLmPacks.LEGACY_GEMMA3]?.isReady() == true

    val isWarmReady = warmup is GenerativeViewModel.Warmup.Ready
    val isLoading = warmup is GenerativeViewModel.Warmup.Loading
    val errorMessage = (warmup as? GenerativeViewModel.Warmup.Failed)?.reason

    val modelLabel = when (warmup) {
        is GenerativeViewModel.Warmup.Ready -> (warmup as GenerativeViewModel.Warmup.Ready).label
        is GenerativeViewModel.Warmup.Loading -> (warmup as GenerativeViewModel.Warmup.Loading).label
        is GenerativeViewModel.Warmup.Failed -> (warmup as GenerativeViewModel.Warmup.Failed).label
        else -> "LiteRT Gemma 4 (On-Device)"
    }

    LiteRtStatusIndicator(
        modelName = modelLabel,
        isInstalled = gemmaPackReady,
        isLoaded = isWarmReady,
        isLoading = isLoading,
        errorMessage = errorMessage,
        backend = "LiteRT GPU / CPU Fallback",
        onWarmUp = { viewModel.warmUpLocal(AiCapability.CODE) },
        onRetry = { viewModel.warmUpLocal(AiCapability.CODE) },
        onOpenPacks = onOpenPacks,
        modifier = modifier,
    )
}
