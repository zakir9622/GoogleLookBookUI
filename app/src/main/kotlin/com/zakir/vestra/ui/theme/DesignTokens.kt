package com.zakir.vestra.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared spacing rhythm for the regenerated creative-studio UI. */
object VestraSpacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
    val xxl: Dp = 48.dp
    val dockClearance: Dp = 104.dp
}

/** Shape tiers keep cards, controls, and sheets visually related. */
object VestraShapes {
    val control = RadiusTokens.sm
    val card = RadiusTokens.md
    val feature = RadiusTokens.lg
    val sheet = RadiusTokens.xl
}

/** Motion tokens: short feedback, calm navigation, and a reduced-motion escape hatch. */
object VestraMotion {
    const val pressMillis = 120
    const val contentEnterMillis = 280
    const val contentExitMillis = 180
    const val progressMillis = 240
    val standardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

@Immutable
data class VestraSurfaceTokens(
    val cardAlpha: Float,
    val borderAlpha: Float,
    val elevatedAlpha: Float,
)

fun VestraPalette.surfaceTokens(): VestraSurfaceTokens = if (isDark) {
    VestraSurfaceTokens(cardAlpha = 0.96f, borderAlpha = 0.30f, elevatedAlpha = 0.99f)
} else {
    VestraSurfaceTokens(cardAlpha = 0.98f, borderAlpha = 0.18f, elevatedAlpha = 1f)
}
