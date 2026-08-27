package com.zakir.vestra.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spatial Material 3 elevation tokens (dp). Prefer flat glass — shadows are GPU-risky. */
object SpatialElevation {
    const val Surface = 0f
    const val Raised = 0f
    const val Floating = 2f
    const val GlassOverlay = 0f
}

/**
 * Derived corner-radius scale off one base — lookbookweb's `--radius` + `calc(var(--radius) ± N)`
 * pattern (generous, consistent rounding as the dominant shape language), ported so cards, chips,
 * and sheets stop each hand-picking a `RoundedCornerShape` value ad hoc. `lg` matches the corner
 * radius `GlassCard` already used before this token existed, so adopting it is visually a no-op
 * for existing cards; new components should reach for one of these rather than a bare `.dp` value.
 */
object RadiusTokens {
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
}

/**
 * Modern atelier palette — crisp high-contrast text, sleek obsidian/slate dark surfaces,
 * pearl light mode, and clean gold/amber accents.
 */
@Immutable
data class VestraPalette(
    val canvas: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceFloating: Color,
    val ink: Color,
    val inkMuted: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentGlow: Color,
    val glassFill: Color,
    val glassFillStrong: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val glassShadow: Color,
    val danger: Color,
    val atelierCanvas: Color,
    val atelierContainer: Color,
    val ivory: Color,
    val ivoryMuted: Color,
    val saffronDeep: Color,
    val silkMist: Color,
    /** Per-modality accent — Create/Image Studio. Brass-family, same as the base [accent]. */
    val modalityImage: Color,
    /** Per-modality accent — Video Studio. Warm copper shift off the brass family. */
    val modalityVideo: Color,
    /** Per-modality accent — Code Studio. Reuses the existing teal loom ([saffronDeep]). */
    val modalityCode: Color,
    /** Per-modality accent — Audio Studio. Muted dusty rose — warm, not the brand's avoided purple. */
    val modalityAudio: Color,
    val isDark: Boolean,
)

val LocalVestraPalette = staticCompositionLocalOf { LightPalette }

// Pearl day (#F8FAFC) · slate ink (#0F172A) · warm gold (#D97706)
private val LightPalette = VestraPalette(
    canvas = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFF5F3FF),
    surfaceFloating = Color(0xFFEEF2FF),
    ink = Color(0xFF111827),
    inkMuted = Color(0xFF4B5563),
    accent = Color(0xFF7C3AED),
    accentSoft = Color(0xFFA78BFA),
    accentGlow = Color(0x337C3AED),
    glassFill = Color(0xF7FFFFFF),
    glassFillStrong = Color(0xFFFFFFFF),
    glassBorder = Color(0x24111827),
    glassHighlight = Color(0x80FFFFFF),
    glassShadow = Color(0x10111827),
    danger = Color(0xFFDC2626),
    atelierCanvas = Color(0xFFF8FAFC),
    atelierContainer = Color(0xFFFFFFFF),
    ivory = Color(0xFF111827),
    ivoryMuted = Color(0xFF4B5563),
    saffronDeep = Color(0xFF4F46E5),
    silkMist = Color(0xFFE0E7FF),
    modalityImage = Color(0xFF7C3AED),
    modalityVideo = Color(0xFFEC4899),
    modalityCode = Color(0xFF0891B2),
    modalityAudio = Color(0xFFF97316),
    isDark = false,
)

// Obsidian night (#0B0F19) · slate surface (#131C2E) · crisp white ink (#FFFFFF) · radiant gold (#F59E0B)
private val DarkPalette = VestraPalette(
    canvas = Color(0xFF080B14),
    surface = Color(0xFF111827),
    surfaceRaised = Color(0xFF1A2235),
    surfaceFloating = Color(0xFF0D1324),
    ink = Color(0xFFF8FAFC),
    inkMuted = Color(0xFFA1A1AA),
    accent = Color(0xFFA78BFA),
    accentSoft = Color(0xFFC4B5FD),
    accentGlow = Color(0x337C3AED),
    glassFill = Color(0xF211182B),
    glassFillStrong = Color(0xF91A2235),
    glassBorder = Color(0x3DA1A1AA),
    glassHighlight = Color(0x26FFFFFF),
    glassShadow = Color(0x80000000),
    danger = Color(0xFFF87171),
    atelierCanvas = Color(0xFF080B14),
    atelierContainer = Color(0xFF111827),
    ivory = Color(0xFFF8FAFC),
    ivoryMuted = Color(0xFFD4D4D8),
    saffronDeep = Color(0xFF60A5FA),
    silkMist = Color(0xFF1E293B),
    modalityImage = Color(0xFFA78BFA),
    modalityVideo = Color(0xFFF472B6),
    modalityCode = Color(0xFF22D3EE),
    modalityAudio = Color(0xFFFB923C),
    isDark = true,
)

/**
 * Bridge for existing call sites. [install] is called from [VestraTheme] on the
 * main thread before content composes — safe for Compose UI usage.
 */
object VestraColors {
    @Volatile
    private var active: VestraPalette = LightPalette

    fun install(palette: VestraPalette) {
        active = palette
    }

    val Canvas get() = active.canvas
    val Surface get() = active.surface
    val SurfaceRaised get() = active.surfaceRaised
    val SurfaceFloating get() = active.surfaceFloating
    val Ink get() = active.ink
    val InkMuted get() = active.inkMuted
    val Accent get() = active.accent
    val AccentSoft get() = active.accentSoft
    val AccentGlow get() = active.accentGlow
    val GlassFill get() = active.glassFill
    val GlassFillStrong get() = active.glassFillStrong
    val GlassBorder get() = active.glassBorder
    val GlassHighlight get() = active.glassHighlight
    val GlassShadow get() = active.glassShadow
    val Danger get() = active.danger
    val AtelierCanvas get() = active.atelierCanvas
    val AtelierContainer get() = active.atelierContainer
    val Ivory get() = active.ivory
    val IvoryMuted get() = active.ivoryMuted
    val SaffronDeep get() = active.saffronDeep
    val SilkMist get() = active.silkMist

    /** Per-modality accents (Create/Video/Code/Audio Studio) — see [VestraPalette] docs. */
    val ModalityImage get() = active.modalityImage
    val ModalityVideo get() = active.modalityVideo
    val ModalityCode get() = active.modalityCode
    val ModalityAudio get() = active.modalityAudio
}

private fun VestraPalette.toScheme() = if (isDark) {
    darkColorScheme(
        primary = accent,
        onPrimary = Color(0xFF1E1B4B),
        primaryContainer = accentSoft.copy(alpha = 0.25f),
        onPrimaryContainer = Color(0xFFF5F3FF),
        secondary = Color(0xFFF472B6),
        onSecondary = Color(0xFF1F1027),
        secondaryContainer = secondary.copy(alpha = 0.22f),
        onSecondaryContainer = Color(0xFFFCE7F3),
        tertiary = saffronDeep,
        onTertiary = Color(0xFF0C1A2A),
        tertiaryContainer = saffronDeep.copy(alpha = 0.3f),
        onTertiaryContainer = Color(0xFFE0F2FE),
        background = canvas,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = surfaceFloating,
        onSurfaceVariant = inkMuted,
        surfaceContainerLowest = canvas,
        surfaceContainerLow = surfaceFloating,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceRaised,
        surfaceContainerHighest = Color(0xFF243042),
        outline = glassBorder,
        outlineVariant = Color(0xFF334155),
        error = danger,
        onError = Color.White,
        errorContainer = Color(0x33F87171),
        onErrorContainer = Color(0xFFFECACA),
    )
} else {
    lightColorScheme(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEDE9FE),
        onPrimaryContainer = Color(0xFF2E1065),
        secondary = Color(0xFFDB2777),
        onSecondary = Color.White,
        // AI-generation accents use a violet/pink spectrum instead of default M3 purple.
        secondaryContainer = Color(0xFFFCE7F3),
        onSecondaryContainer = Color(0xFF831843),
        tertiary = saffronDeep,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE0F2FE),
        onTertiaryContainer = Color(0xFF0C4A6E),
        background = canvas,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = surfaceFloating,
        onSurfaceVariant = inkMuted,
        surfaceContainerLowest = Color.White,
        surfaceContainer = Color.White,
        surfaceContainerHigh = surfaceRaised,
        surfaceContainerHighest = Color.White,
        outline = glassBorder,
        error = danger,
    )
}

@Composable
fun VestraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    VestraColors.install(palette)
    CompositionLocalProvider(
        LocalVestraPalette provides palette,
        LocalContentColor provides palette.ink,
    ) {
        MaterialTheme(
            colorScheme = palette.toScheme(),
            typography = VestraTypography,
            content = content,
        )
    }
}
