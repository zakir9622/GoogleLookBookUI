package com.zakir.vestra.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zakir.vestra.shared.cloud.GenerativeState
import com.zakir.vestra.shared.content.LookbookCopy
import com.zakir.vestra.shared.domain.GenerationState
import com.zakir.vestra.ui.TestTags
import com.zakir.vestra.ui.theme.RadiusTokens
import com.zakir.vestra.ui.theme.VestraColors
import com.zakir.vestra.ui.util.rememberReduceMotion
import kotlinx.coroutines.delay

/**
 * Parsed step progression info for on-device generation pipelines.
 */
data class GenerationStepInfo(
    val currentStep: Int,
    val totalSteps: Int,
    val stageName: String,
) {
    val remainingSteps: Int get() = (totalSteps - currentStep).coerceAtLeast(0)
    val fraction: Float get() = if (totalSteps > 0) (currentStep.toFloat() / totalSteps).coerceIn(0f, 1f) else 0f
}

/**
 * Helper to parse step counts from standard AI pipeline strings like "Denoising step 4/8",
 * "Tokenizing pass (2/4)", or fallback to default step fractions.
 */
fun parseStepInfo(stage: String, fraction: Float, defaultTotalSteps: Int = 8): GenerationStepInfo {
    val regex = Regex("""(?:step|pass|stage)\s*(\d+)\s*(?:/|of)\s*(\d+)""", RegexOption.IGNORE_CASE)
    val match = regex.find(stage)
    return if (match != null) {
        val cur = match.groupValues[1].toIntOrNull() ?: 1
        val tot = match.groupValues[2].toIntOrNull() ?: defaultTotalSteps
        GenerationStepInfo(
            currentStep = cur.coerceIn(0, tot),
            totalSteps = tot.coerceAtLeast(1),
            stageName = stage.replace(match.value, "").trim().removeSuffix("·").trim().ifBlank { "Processing" },
        )
    } else {
        val total = defaultTotalSteps
        val current = (fraction * total).toInt().coerceIn(0, total)
        GenerationStepInfo(
            currentStep = current,
            totalSteps = total,
            stageName = stage.ifBlank { "Generating" },
        )
    }
}

/**
 * Production-ready [GenerationStateOverlay] that replaces static placeholders during
 * on-device LLM (code/text) or image/video generation.
 */
@Composable
fun GenerationStateOverlay(
    state: GenerativeState?,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
    totalStepsOverride: Int? = null,
    hardwareTag: String = "ON-DEVICE NPU",
    generationStartedAtMs: Long? = null,
) {
    val isVisible = state is GenerativeState.Preparing ||
        state is GenerativeState.Running ||
        state is GenerativeState.CodeStreaming

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 8 },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 8 },
        modifier = modifier.testTag(TestTags.GENERATION_STATE_OVERLAY),
    ) {
        when (state) {
            is GenerativeState.Preparing -> {
                GenerationOverlayContent(
                    stage = state.message.ifBlank { "Staging on-device model weights…" },
                    fraction = 0.08f,
                    currentStep = 1,
                    totalSteps = totalStepsOverride ?: 8,
                    hardwareTag = hardwareTag,
                    isPreparing = true,
                    onCancel = onCancel,
                    generationStartedAtMs = generationStartedAtMs,
                    deadlineEpochMs = null,
                )
            }
            is GenerativeState.Running -> {
                val stepInfo = remember(state.stage, state.fraction, totalStepsOverride) {
                    parseStepInfo(state.stage, state.fraction, totalStepsOverride ?: 8)
                }
                GenerationOverlayContent(
                    stage = stepInfo.stageName,
                    fraction = state.fraction.coerceAtLeast(stepInfo.fraction),
                    currentStep = stepInfo.currentStep,
                    totalSteps = stepInfo.totalSteps,
                    hardwareTag = hardwareTag,
                    isPreparing = false,
                    onCancel = onCancel,
                    generationStartedAtMs = generationStartedAtMs,
                    deadlineEpochMs = state.deadlineEpochMs,
                )
            }
            is GenerativeState.CodeStreaming -> {
                val tokenEstimate = state.text.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                GenerationOverlayContent(
                    stage = "Streaming tokens ($tokenEstimate words generated)",
                    fraction = 0.65f,
                    currentStep = (tokenEstimate % 8) + 1,
                    totalSteps = 8,
                    hardwareTag = "LOCAL LLM",
                    isPreparing = false,
                    isLlm = true,
                    tokenCount = tokenEstimate,
                    onCancel = onCancel,
                    generationStartedAtMs = generationStartedAtMs,
                    deadlineEpochMs = null,
                )
            }
            else -> Unit
        }
    }
}

/**
 * Overload for Domain Try-On Shoot [GenerationState].
 */
@Composable
fun GenerationStateOverlay(
    state: GenerationState?,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
    totalStepsOverride: Int? = null,
    hardwareTag: String = "ON-DEVICE NPU",
    generationStartedAtMs: Long? = null,
) {
    val isVisible = state is GenerationState.Preparing || state is GenerationState.Running

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 8 },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 8 },
        modifier = modifier.testTag(TestTags.GENERATION_STATE_OVERLAY),
    ) {
        when (state) {
            is GenerationState.Preparing -> {
                GenerationOverlayContent(
                    stage = state.message.ifBlank { "Staging try-on model…" },
                    fraction = 0.05f,
                    currentStep = 1,
                    totalSteps = totalStepsOverride ?: 8,
                    hardwareTag = hardwareTag,
                    isPreparing = true,
                    onCancel = onCancel,
                    generationStartedAtMs = generationStartedAtMs,
                    deadlineEpochMs = null,
                )
            }
            is GenerationState.Running -> {
                val stepInfo = remember(state.stage, state.fraction, totalStepsOverride) {
                    parseStepInfo(state.stage, state.fraction, totalStepsOverride ?: 8)
                }
                GenerationOverlayContent(
                    stage = stepInfo.stageName,
                    fraction = state.fraction.coerceAtLeast(stepInfo.fraction),
                    currentStep = stepInfo.currentStep,
                    totalSteps = stepInfo.totalSteps,
                    hardwareTag = hardwareTag,
                    isPreparing = false,
                    onCancel = onCancel,
                    generationStartedAtMs = generationStartedAtMs,
                    deadlineEpochMs = null,
                )
            }
            else -> Unit
        }
    }
}

/**
 * Direct parameter invocation for custom LLM, Diffusion, or Vision pipelines.
 */
@Composable
fun GenerationStateOverlay(
    isGenerating: Boolean,
    progress: Float,
    currentStep: Int,
    totalSteps: Int,
    stageDescription: String,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
    hardwareTag: String = "ON-DEVICE AI",
    isLlm: Boolean = false,
    tokenCount: Int? = null,
    generationStartedAtMs: Long? = null,
    deadlineEpochMs: Long? = null,
) {
    AnimatedVisibility(
        visible = isGenerating,
        enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 8 },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 8 },
        modifier = modifier.testTag(TestTags.GENERATION_STATE_OVERLAY),
    ) {
        GenerationOverlayContent(
            stage = stageDescription,
            fraction = progress,
            currentStep = currentStep,
            totalSteps = totalSteps,
            hardwareTag = hardwareTag,
            isPreparing = false,
            isLlm = isLlm,
            tokenCount = tokenCount,
            onCancel = onCancel,
            generationStartedAtMs = generationStartedAtMs,
            deadlineEpochMs = deadlineEpochMs,
        )
    }
}

/**
 * Core visual presentation for the generation overlay.
 */
@Composable
fun GenerationOverlayContent(
    stage: String,
    fraction: Float,
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier,
    hardwareTag: String = "ON-DEVICE NPU",
    isPreparing: Boolean = false,
    isLlm: Boolean = false,
    tokenCount: Int? = null,
    onCancel: (() -> Unit)? = null,
    generationStartedAtMs: Long? = null,
    deadlineEpochMs: Long? = null,
) {
    val reduceMotion = rememberReduceMotion()
    val remainingSteps = (totalSteps - currentStep).coerceAtLeast(0)
    val pct = (fraction.coerceIn(0f, 1f) * 100).toInt()

    // Live 1-second ticker for elapsed & remaining countdown
    var tick by remember(deadlineEpochMs, generationStartedAtMs) { mutableIntStateOf(0) }
    LaunchedEffect(deadlineEpochMs, generationStartedAtMs) {
        while (true) {
            delay(1000)
            tick++
        }
    }
    @Suppress("UNUSED_EXPRESSION")
    tick

    val remSeconds = deadlineEpochMs?.let {
        ((it - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
    }
    val elapsedSeconds = generationStartedAtMs?.let {
        ((System.currentTimeMillis() - it) / 1000L).coerceAtLeast(0L)
    }

    // Infinite Shimmer Animation
    val infiniteTransition = rememberInfiniteTransition(label = "overlay_shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerSweep",
    )

    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseGlow",
    )

    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "progressFraction",
    )

    val contentDesc = buildString {
        append("Generating. ")
        append("$stage. ")
        append("Step $currentStep of $totalSteps, $remainingSteps steps remaining. ")
        append("$pct percent completed.")
        if (remSeconds != null) append(" $remSeconds seconds remaining.")
    }

    val shape = RoundedCornerShape(RadiusTokens.lg)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF0F1118).copy(alpha = 0.94f))
            .border(1.dp, VestraColors.GlassBorder.copy(alpha = 0.6f), shape)
            .semantics { contentDescription = contentDesc }
            .padding(16.dp),
    ) {
        // Shimmer Background Sweep (Active during generation)
        if (!reduceMotion) {
            val shimmerBrush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    VestraColors.Accent.copy(alpha = 0.08f * pulseGlow),
                    VestraColors.AccentSoft.copy(alpha = 0.15f * pulseGlow),
                    Color.Transparent,
                ),
                start = Offset(shimmerOffset * 500f - 250f, 0f),
                end = Offset(shimmerOffset * 500f + 250f, 0f),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(shimmerBrush),
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row: Status Tag + Hardware Badge + Cancel Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Pulsing Status Orb
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (isPreparing) Color(0xFFFFB800) else VestraColors.Accent.copy(alpha = pulseGlow),
                            ),
                    )

                    Text(
                        text = if (isPreparing) "PREPARING ENGINE" else if (isLlm) "STREAMING TOKENS" else "ON-DEVICE GENERATION",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.6.sp,
                        ),
                        color = VestraColors.Accent,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Hardware Chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E2130))
                            .border(1.dp, Color(0xFF2C3147), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                imageVector = if (isLlm) Icons.Outlined.Code else Icons.Outlined.Bolt,
                                contentDescription = null,
                                tint = VestraColors.IvoryMuted,
                                modifier = Modifier.size(11.dp),
                            )
                            Text(
                                text = hardwareTag,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = VestraColors.IvoryMuted,
                            )
                        }
                    }

                    // Cancel Action Button
                    if (onCancel != null) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF232532))
                                .clickable { onCancel() }
                                .testTag(TestTags.RESULT_CANCEL_BUTTON),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = LookbookCopy.ACTION_CANCEL_GENERATION,
                                tint = VestraColors.IvoryMuted,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Stage Description & Progress Percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stage,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        ),
                        color = VestraColors.Ivory,
                    )
                }

                Text(
                    text = "$pct%",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    ),
                    color = VestraColors.Accent,
                )
            }

            Spacer(Modifier.height(10.dp))

            // Progressive Determinate Step Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF1E2130)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedFraction)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    VestraColors.AccentSoft,
                                    VestraColors.Accent,
                                ),
                            ),
                        ),
                )
            }

            Spacer(Modifier.height(12.dp))

            // Segmented Step Progression Nodes (Visual Remaining Steps Indicator)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(totalSteps) { index ->
                    val stepNumber = index + 1
                    val isDone = stepNumber < currentStep
                    val isCurrent = stepNumber == currentStep
                    val isPending = stepNumber > currentStep

                    val nodeColor = when {
                        isDone -> VestraColors.Accent
                        isCurrent -> VestraColors.Accent.copy(alpha = pulseGlow)
                        else -> Color(0xFF262B3F)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(nodeColor),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Footer Row: Steps Status Badge + Remaining Countdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Remaining Steps Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(VestraColors.Accent.copy(alpha = 0.12f))
                        .border(1.dp, VestraColors.Accent.copy(alpha = 0.3f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = if (isPreparing) {
                            "Initializing tensors…"
                        } else if (remainingSteps == 0) {
                            "Decoding final output…"
                        } else {
                            "Step $currentStep of $totalSteps · $remainingSteps remaining"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = VestraColors.Accent,
                    )
                }

                // Timers & Token Telemetry
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (tokenCount != null) {
                        Text(
                            text = "$tokenCount tokens",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                            ),
                            color = VestraColors.IvoryMuted,
                        )
                    }

                    if (remSeconds != null && remSeconds > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Timer,
                                contentDescription = null,
                                tint = VestraColors.IvoryMuted,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = "~${remSeconds}s left",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                ),
                                color = VestraColors.IvoryMuted,
                            )
                        }
                    } else if (elapsedSeconds != null && elapsedSeconds > 0) {
                        Text(
                            text = "${elapsedSeconds}s elapsed",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                            ),
                            color = VestraColors.IvoryMuted,
                        )
                    }
                }
            }
        }
    }
}
