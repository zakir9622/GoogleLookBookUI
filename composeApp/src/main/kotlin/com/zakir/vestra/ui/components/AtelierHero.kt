package com.zakir.vestra.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.zakir.vestra.ui.theme.VestraColors
import com.zakir.vestra.ui.util.rememberReduceMotion
import kotlin.math.abs

/**
 * Full-bleed atelier hero — brand dominates; loom-silk collage is the visual plane.
 * One headline, one support, one CTA. No dashboard clutter in the first viewport.
 */
@Composable
fun AtelierHero(
    brand: String,
    headline: String,
    support: String,
    cta: String,
    onCta: () -> Unit,
    modifier: Modifier = Modifier,
    statusLine: String? = null,
) {
    val reduceMotion = rememberReduceMotion()
    val infinite = rememberInfiniteTransition(label = "heroScan")
    val scan by infinite.animateFloat(
        initialValue = 0f,
        targetValue = if (reduceMotion) 0f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (reduceMotion) 1 else 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "scan",
    )
    val drift by infinite.animateFloat(
        initialValue = if (reduceMotion) 0f else -8f,
        targetValue = if (reduceMotion) 0f else 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (reduceMotion) 1 else 7600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "panelDrift",
    )
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(480.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        VestraColors.AtelierContainer,
                        VestraColors.AtelierCanvas,
                        Color(0xFF030608),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        VestraColors.AccentSoft.copy(alpha = 0.5f),
                        VestraColors.SaffronDeep.copy(alpha = 0.35f),
                        VestraColors.Accent.copy(alpha = 0.35f),
                    ),
                ),
                shape = shape,
            ),
    ) {
        // Loom silk panels — cool teal warp + brass weft (not warm cream collage).
        SilkPanel(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (30 + drift).dp, y = (-16).dp)
                .rotate(14f)
                .size(168.dp, 220.dp),
            colors = listOf(
                Color(0xFF1A3A42),
                VestraColors.SaffronDeep.copy(alpha = 0.9f),
                Color(0xFF0A181C),
            ),
        )
        SilkPanel(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = (-36 + drift * 0.45f).dp, y = 40.dp)
                .rotate(-10f)
                .size(136.dp, 188.dp),
            colors = listOf(
                Color(0xFF152028),
                Color(0xFF2A4050),
                VestraColors.SilkMist.copy(alpha = 0.4f),
            ),
        )
        SilkPanel(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 20.dp, y = (48 - drift).dp)
                .rotate(7f)
                .size(118.dp, 158.dp),
            colors = listOf(
                Color(0xFF2A2418),
                VestraColors.Accent.copy(alpha = 0.65f),
                Color(0xFF1A140C),
            ),
        )

        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            VestraColors.AtelierCanvas.copy(alpha = 0.2f),
                            VestraColors.AtelierCanvas.copy(alpha = 0.94f),
                        ),
                    ),
                ),
        )
        Box(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(0.58f)
                .height(2.dp)
                .offset(y = (40 + scan * 300).dp)
                .graphicsLayer { alpha = 0.18f + (1f - abs(scan - 0.5f)) * 0.42f }
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, VestraColors.AccentSoft, Color.Transparent),
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp),
        ) {
            Text(
                brand,
                style = MaterialTheme.typography.displayLarge,
                color = VestraColors.Ivory,
            )
            Text(
                headline,
                style = MaterialTheme.typography.titleMedium,
                color = VestraColors.AccentSoft,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                support,
                style = MaterialTheme.typography.bodyMedium,
                color = VestraColors.IvoryMuted,
                modifier = Modifier.padding(top = 10.dp, end = 12.dp),
            )
            if (statusLine != null) {
                Text(
                    statusLine,
                    style = MaterialTheme.typography.labelMedium,
                    color = VestraColors.AccentSoft.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
            GlassPrimaryButton(
                text = cta,
                onClick = onCta,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
    }
}

@Composable
private fun SilkPanel(
    modifier: Modifier,
    colors: List<Color>,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(colors))
            .border(
                1.dp,
                VestraColors.AccentSoft.copy(alpha = 0.22f),
                RoundedCornerShape(18.dp),
            ),
    )
}
