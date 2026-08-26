package com.zakir.vestra.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.zakir.vestra.ui.theme.RadiusTokens
import com.zakir.vestra.ui.theme.VestraColors
import com.zakir.vestra.ui.util.rememberReduceMotion

/**
 * One shimmer-loading rectangle — lookbookweb's `shimmer`/`blur-placeholder` language ported at
 * Compose-native cost: a soft gradient sweep across a rounded, muted-fill box. Falls back to a
 * static muted fill (no animation) when reduced motion is on, same discipline as every other
 * animation in this app (see `rememberReduceMotion()`).
 *
 * Use this wherever a result/history panel is loading instead of rolling an ad hoc
 * `CircularProgressIndicator` or blank space — see `NewsChatScreen`'s headline list for the
 * reference call site.
 */
@Composable
fun ShimmerBlock(modifier: Modifier = Modifier, height: Dp = 56.dp) {
    ShimmerBox(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(14.dp),
    )
}

/** A generic shimmer placeholder box matching any shape and dimensions. */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(RadiusTokens.md),
) {
    val reduceMotion = rememberReduceMotion()
    if (reduceMotion) {
        Box(
            modifier
                .clip(shape)
                .background(VestraColors.GlassFill),
        )
        return
    }
    val infinite = rememberInfiniteTransition(label = "shimmer")
    val sweep by infinite.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-sweep",
    )
    val brush = Brush.linearGradient(
        colors = listOf(
            VestraColors.GlassFill,
            VestraColors.GlassHighlight.copy(alpha = 0.45f),
            VestraColors.GlassFill,
        ),
        start = Offset(sweep * 350f - 175f, 0f),
        end = Offset(sweep * 350f + 175f, 0f),
    )
    Box(
        modifier
            .clip(shape)
            .background(brush),
    )
}

/**
 * AsyncImage enhanced with real-time shimmering placeholder animation while
 * loading cached trials and generated looks from disk cache.
 */
@Composable
fun ShimmerAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    shape: Shape = RoundedCornerShape(0.dp),
) {
    SubcomposeAsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier.clip(shape),
        contentScale = contentScale,
        loading = {
            ShimmerBox(modifier = Modifier.fillMaxSize(), shape = shape)
        },
    )
}

/** A short stack of [ShimmerBlock]s — for a placeholder that reads as "a few rows loading". */
@Composable
fun ShimmerRows(modifier: Modifier = Modifier, count: Int = 3, rowHeight: Dp = 56.dp) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { ShimmerBlock(height = rowHeight) }
    }
}
