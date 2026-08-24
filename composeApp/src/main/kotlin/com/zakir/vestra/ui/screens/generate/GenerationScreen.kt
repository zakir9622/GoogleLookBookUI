package com.zakir.vestra.ui.screens.generate

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zakir.vestra.shared.content.LookbookCopy
import com.zakir.vestra.shared.domain.GenerationState
import com.zakir.vestra.shared.domain.TryOnError
import com.zakir.vestra.shared.engine.pipeline.ConditioningStage
import com.zakir.vestra.ui.TryOnViewModel
import com.zakir.vestra.ui.components.GlassPrimaryButton
import com.zakir.vestra.ui.components.GlassSecondaryButton
import com.zakir.vestra.ui.components.LiveGenConsole
import com.zakir.vestra.ui.effects.DevelopStage
import com.zakir.vestra.ui.theme.VestraColors
import com.zakir.vestra.ui.util.rememberReduceMotion

@Composable
fun GenerationScreen(
    viewModel: TryOnViewModel,
    onComplete: () -> Unit,
    onAbort: () -> Unit,
    onOpenHelp: (() -> Unit)? = null,
) {
    val shoot by viewModel.shoot.collectAsState()
    val liveLog by viewModel.liveLog.collectAsState()
    val generationStartedAtMs by viewModel.generationStartedAtMs.collectAsState()
    val haptics = LocalHapticFeedback.current
    val reduceMotion = rememberReduceMotion()

    fun abortGeneration() {
        viewModel.cancelShoot()
        onAbort()
    }

    BackHandler { abortGeneration() }

    LaunchedEffect(Unit) {
        if (shoot == null) viewModel.startShoot()
    }
    LaunchedEffect(shoot) {
        val current = shoot ?: return@LaunchedEffect
        if (current.inner is GenerationState.Complete) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        if (current.isFinished) onComplete()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(VestraColors.AtelierCanvas),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                LookbookCopy.LABEL_VIRTUAL_TRY_ON,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            val current = shoot
            if (current != null && current.totalShots > 1) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Look ${current.shotIndex + 1} of ${current.totalShots}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VestraColors.IvoryMuted,
                )
            }
            Spacer(Modifier.height(28.dp))

            Box(
                Modifier
                    .size(268.dp, 356.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(VestraColors.AtelierContainer)
                    .border(1.dp, VestraColors.GlassBorder, RoundedCornerShape(24.dp))
                    .padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (val inner = current?.inner) {
                    is GenerationState.Failed -> FailureContent(
                        error = inner.error,
                        onRetry = {
                            viewModel.cancelShoot()
                            viewModel.startShoot()
                        },
                        onAbort = ::abortGeneration,
                    )
                    else -> {
                        val shotFraction = when (val s = current?.inner) {
                            is GenerationState.Preparing -> 0.05f
                            is GenerationState.Running -> s.fraction
                            else -> 0f
                        }
                        val animatedShot by animateFloatAsState(
                            targetValue = shotFraction,
                            animationSpec = if (reduceMotion) snap() else tween(450),
                            label = "shot",
                        )
                        DevelopStage(
                            progress = animatedShot,
                            reduceMotion = reduceMotion,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp))
                                .semantics { contentDescription = "Generation progress ${(animatedShot * 100).toInt()} percent" },
                        )
                    }
                }
            }

            if (current?.inner !is GenerationState.Failed) {
                Spacer(Modifier.height(28.dp))
                val (shotFraction, label) = when (val s = current?.inner) {
                    is GenerationState.Preparing -> 0.05f to s.message
                    is GenerationState.Running -> s.fraction to s.stage
                    else -> 0f to ConditioningStage.STRUCTURE.label
                }
                val overall = if (current == null) {
                    0f
                } else {
                    (current.shotIndex + shotFraction) / current.totalShots
                }
                val animatedOverall by animateFloatAsState(
                    targetValue = overall,
                    animationSpec = if (reduceMotion) snap() else tween(450),
                    label = "overall",
                )
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { animatedOverall },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Overall progress ${(animatedOverall * 100).toInt()} percent" },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = VestraColors.GlassBorder,
                )
                Spacer(Modifier.height(16.dp))
                val elapsedSec = generationStartedAtMs?.let { start ->
                    ((System.currentTimeMillis() - start) / 1_000L).coerceAtLeast(0L)
                }
                val statusLine = buildString {
                    append(label)
                    if (elapsedSec != null) append(" · ${elapsedSec}s elapsed")
                }
                Text(
                    text = statusLine,
                    style = MaterialTheme.typography.bodyLarge,
                    color = VestraColors.Ivory,
                    textAlign = TextAlign.Center,
                )
                LiveGenConsole(liveLog, generationStartedAtMs)
                Spacer(Modifier.height(20.dp))
                GlassSecondaryButton(
                    text = LookbookCopy.ACTION_CANCEL_GENERATION,
                    onClick = ::abortGeneration,
                )
                onOpenHelp?.let { open ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        LookbookCopy.ACTION_OPEN_HELP,
                        style = MaterialTheme.typography.labelMedium,
                        color = VestraColors.Accent,
                        modifier = Modifier
                            .clickable(onClick = open)
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FailureContent(error: TryOnError, onRetry: () -> Unit, onAbort: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = error.userMessage(),
            style = MaterialTheme.typography.bodyMedium,
            color = VestraColors.IvoryMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        GlassPrimaryButton(text = LookbookCopy.ACTION_RETRY, onClick = onRetry)
        Spacer(Modifier.height(10.dp))
        GlassSecondaryButton(text = LookbookCopy.ACTION_BACK_ATELIER, onClick = onAbort)
    }
}

private fun TryOnError.userMessage(): String = when (this) {
    TryOnError.ModelPackMissing ->
        "The model pack for this engine isn’t installed yet. Download it from Settings → Model packs."
    TryOnError.DeviceNotCapable ->
        "This device can’t run the selected engine. Switch to Lite or Auto in Settings."
    TryOnError.NetworkUnavailable ->
        "Cloud generation needs internet. Check your network or switch to Lite/Pro (offline)."
    is TryOnError.SafetyBlocked -> "This image can’t be used: $reason"
    is TryOnError.Internal -> message.ifBlank { "Generation failed. Please try again or open Help & FAQ." }
}
