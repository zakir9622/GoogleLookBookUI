package com.zakir.vestra.ui.components

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zakir.vestra.shared.content.LookbookCopy
import com.zakir.vestra.ui.TestTags
import com.zakir.vestra.ui.theme.RadiusTokens
import com.zakir.vestra.ui.theme.SpatialElevation
import com.zakir.vestra.ui.theme.VestraColors
import com.zakir.vestra.ui.theme.VestraMotion
import com.zakir.vestra.ui.theme.VestraShapes
import com.zakir.vestra.ui.theme.VestraSpacing
import com.zakir.vestra.ui.util.rememberReduceMotion

/** Full-screen spatial canvas with restrained creative light behind content. */
@Composable
fun SpatialBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val infinite = rememberInfiniteTransition(label = "spatial")
    val breathe by infinite.animateFloat(
        initialValue = if (reduceMotion) 1f else 0.9f,
        targetValue = if (reduceMotion) 1f else 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (reduceMotion) 1 else 5600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orbBreathe",
    )
    val drift by infinite.animateFloat(
        initialValue = if (reduceMotion) 0f else -18f,
        targetValue = if (reduceMotion) 0f else 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (reduceMotion) 1 else 8800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orbDrift",
    )
    val density = LocalDensity.current

    Surface(
        modifier = modifier.fillMaxSize(),
        color = VestraColors.Canvas,
        contentColor = VestraColors.Ink,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(
                        x = with(density) { (36f + drift).toDp() },
                        y = (-48).dp,
                    )
                    .size((300 * breathe).dp)
                    .graphicsLayer { alpha = 0.5f }
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                VestraColors.ModalityVideo.copy(alpha = 0.22f),
                                VestraColors.Accent.copy(alpha = 0.05f),
                                Color.Transparent,
                            ),
                        ),
                        shape = CircleShape,
                    ),
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(
                        x = (-72).dp,
                        y = with(density) { (64f - drift * 0.5f).toDp() },
                    )
                    .size((340 * (2f - breathe).coerceIn(0.92f, 1.15f)).dp)
                    .graphicsLayer { alpha = 0.42f }
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                VestraColors.ModalityCode.copy(alpha = 0.14f),
                                Color.Transparent,
                            ),
                        ),
                        shape = CircleShape,
                    ),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                VestraColors.Canvas.copy(alpha = 0.38f),
                            ),
                        ),
                    ),
            )
            content()
        }
    }
}

/**
 * Frosted glass card — semi-transparent fill, highlight border, spatial shadow.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    elevation: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(VestraShapes.card)
    val glassFill = VestraColors.GlassFill
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = rememberReduceMotion()
    val pressAlpha by animateFloatAsState(
        targetValue = if (onClick != null && pressed) 0.84f else 1f,
        animationSpec = tween(if (reduceMotion) 0 else VestraMotion.pressMillis),
        label = "glassCardPressAlpha",
    )
    val base = modifier
        .fillMaxWidth()
        .graphicsLayer { alpha = pressAlpha }
        .then(
            if (elevation > 0.dp) {
                Modifier.graphicsLayer {
                    shadowElevation = elevation.toPx().coerceAtMost(12f)
                }
            } else {
                Modifier
            },
        )
        .clip(shape)
        .background(glassFill)
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    VestraColors.GlassHighlight,
                    VestraColors.GlassBorder,
                ),
            ),
            shape = shape,
        )

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = base,
            color = Color.Transparent,
            contentColor = VestraColors.Ink,
            shape = shape,
            interactionSource = interactionSource,
        ) {
            Column(Modifier.padding(18.dp), content = content)
        }
    } else {
        Surface(
            modifier = base,
            color = Color.Transparent,
            contentColor = VestraColors.Ink,
            shape = shape,
        ) {
            Column(Modifier.padding(18.dp), content = content)
        }
    }
}

@Composable
fun GlassSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = VestraColors.Ink,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(bottom = 6.dp),
    )
}

@Composable
fun GlassTopBar(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    navigation: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        navigation()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = VestraColors.Ink)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = VestraColors.InkMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Row(content = actions)
    }
}

@Composable
fun GlassPill(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    accent: Color = VestraColors.Accent,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .background(if (active) VestraColors.GlassFillStrong else VestraColors.GlassFill)
            .border(
                1.dp,
                if (active) accent.copy(alpha = 0.5f) else VestraColors.GlassBorder,
                shape,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .padding(end = 8.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(if (active) accent else VestraColors.InkMuted.copy(alpha = 0.4f)),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) VestraColors.Ink else VestraColors.InkMuted,
        )
    }
}

/** Saffron FilterChip — avoids Material3 purple secondaryContainer defaults. */
@Composable
fun AtelierFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        enabled = enabled,
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            containerColor = VestraColors.GlassFill,
            labelColor = VestraColors.Ink,
            iconColor = VestraColors.InkMuted,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = VestraColors.GlassBorder,
            selectedBorderColor = VestraColors.Accent.copy(alpha = 0.65f),
        ),
    )
}

/** Standard spatial screen shell: gradient canvas, glass top bar, scrollable body. */
@Composable
fun GlassScreen(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    SpatialBackground(modifier) {
        val scroll = rememberScrollState()
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = VestraSpacing.md)
                .imePadding()
                .then(if (scrollable) Modifier.verticalScroll(scroll) else Modifier),
        ) {
            GlassTopBar(
                title = title,
                subtitle = subtitle,
                navigation = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back",
                                tint = VestraColors.Ink,
                            )
                        }
                    }
                },
                actions = actions,
            )
            Spacer(Modifier.padding(top = 16.dp))
            content()
        }
    }
}

/** Elevated glass frame for image previews and model cards. */
@Composable
fun GlassImageFrame(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .clip(shape)
            .background(VestraColors.GlassFillStrong)
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(VestraColors.GlassHighlight, VestraColors.GlassBorder),
                ),
                shape,
            )
            .graphicsLayer {
                shadowElevation = 0f
            },
        content = content,
    )
}

@Composable
fun GlassPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(VestraShapes.control)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        tonalElevation = if (enabled) 2.dp else 0.dp,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = VestraSpacing.md, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * Generate CTA that swaps to an in-button spinner + Force stop while work runs
 * (cloud jobs continue if you leave the screen; Stop cancels the ViewModel job).
 */
@Composable
fun GlassGenerateActions(
    busy: Boolean,
    generateLabel: String,
    onGenerate: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    statusText: String = "Working… you can leave this screen — tap Cancel generation to stop",
) {
    Column(modifier.fillMaxWidth()) {
        if (busy) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = VestraColors.Accent,
                )
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            GlassSecondaryButton(text = LookbookCopy.ACTION_CANCEL_GENERATION, onClick = onStop)
        } else {
            GlassPrimaryButton(text = generateLabel, onClick = onGenerate, enabled = enabled)
        }
    }
}

/** Compact toggle chip for model assist options (pragmatic, fashion context, etc.). */
@Composable
fun GlassOptionToggle(
    text: String,
    active: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onToggle,
        enabled = enabled,
        modifier = modifier,
        shape = shape,
        color = if (active) VestraColors.Accent.copy(alpha = 0.18f) else VestraColors.GlassFill,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (active) VestraColors.Accent.copy(alpha = 0.75f) else VestraColors.GlassBorder,
        ),
        contentColor = if (active) VestraColors.Ink else VestraColors.InkMuted,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            maxLines = 1,
        )
    }
}

@Composable
fun GlassSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(VestraShapes.control)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, VestraColors.GlassBorder, shape),
        shape = shape,
        color = VestraColors.GlassFillStrong,
        contentColor = VestraColors.Ink,
    ) {
        Box(Modifier.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun GlassEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    GlassCard(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Architectural Atelier Icon Cluster
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    VestraColors.Accent.copy(alpha = 0.22f),
                                    VestraColors.AccentSoft.copy(alpha = 0.06f),
                                    Color.Transparent,
                                ),
                            ),
                        )
                        .border(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(
                                    VestraColors.Accent.copy(alpha = 0.5f),
                                    VestraColors.GlassBorder.copy(alpha = 0.2f),
                                ),
                            ),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = VestraColors.Accent,
                        modifier = Modifier.size(30.dp),
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    ),
                    color = VestraColors.Ink,
                )

                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(16.dp))
                    GlassPrimaryButton(
                        text = actionLabel,
                        onClick = onAction,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun GlassErrorBanner(
    message: String,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    retryLabel: String = LookbookCopy.ACTION_RETRY,
) {
    GlassCard(modifier = Modifier.testTag(TestTags.RESULT_FAILED)) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.padding(top = 8.dp))
        Row {
            if (onRetry != null) {
                GlassSecondaryButton(
                    text = retryLabel,
                    onClick = onRetry,
                    modifier = Modifier.weight(1f).testTag(TestTags.RESULT_RETRY_BUTTON),
                )
            }
            if (onDismiss != null) {
                if (onRetry != null) Spacer(Modifier.padding(horizontal = 6.dp))
                GlassSecondaryButton(text = "Dismiss", onClick = onDismiss, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun GlassLoadingCard(
    message: String,
    progress: Float? = null,
    onCancel: (() -> Unit)? = null,
) {
    val progressLabel = progress?.let { "Generation progress ${(it.coerceIn(0f, 1f) * 100).toInt()} percent" }
        ?: "Generation in progress"
    GlassCard {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { contentDescription = progressLabel },
        )
        Spacer(Modifier.padding(top = 10.dp))
        if (progress != null) {
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = progressLabel },
                color = VestraColors.Accent,
                trackColor = VestraColors.GlassBorder,
            )
        } else {
            androidx.compose.material3.CircularProgressIndicator(
                color = VestraColors.Accent,
                modifier = Modifier.semantics { contentDescription = progressLabel },
            )
        }
        if (onCancel != null) {
            Spacer(Modifier.padding(top = 12.dp))
            GlassSecondaryButton(
                text = LookbookCopy.ACTION_CANCEL_GENERATION,
                onClick = onCancel,
                modifier = Modifier.testTag(TestTags.RESULT_CANCEL_BUTTON),
            )
        }
    }
}
