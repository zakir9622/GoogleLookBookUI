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
    surfaceRaised = Color(0xFFF1F5F9),
    surfaceFloating = Color(0xFFE2E8F0),
    ink = Color(0xFF0F172A),
    inkMuted = Color(0xFF475569),
    accent = Color(0xFFD97706),
    accentSoft = Color(0xFFF59E0B),
    accentGlow = Color(0x33D97706),
    glassFill = Color(0xF2FFFFFF),
    glassFillStrong = Color(0xFAFFFFFF),
    glassBorder = Color(0x260F172A),
    glassHighlight = Color(0x80FFFFFF),
    glassShadow = Color(0x0F0F172A),
    danger = Color(0xFFDC2626),
    atelierCanvas = Color(0xFFF8FAFC),
    atelierContainer = Color(0xFFFFFFFF),
    ivory = Color(0xFF0F172A),
    ivoryMuted = Color(0xFF475569),
    saffronDeep = Color(0xFF0284C7),
    silkMist = Color(0xFFE2E8F0),
    modalityImage = Color(0xFFD97706),
    modalityVideo = Color(0xFFEA580C),
    modalityCode = Color(0xFF0284C7),
    modalityAudio = Color(0xFFDB2777),
    isDark = false,
)

// Obsidian night (#0B0F19) · slate surface (#131C2E) · crisp white ink (#FFFFFF) · radiant gold (#F59E0B)
private val DarkPalette = VestraPalette(
    canvas = Color(0xFF0B0F19),
    surface = Color(0xFF131C2E),
    surfaceRaised = Color(0xFF1E293B),
    surfaceFloating = Color(0xFF0F172A),
    ink = Color(0xFFFFFFFF),
    inkMuted = Color(0xFF94A3B8),
    accent = Color(0xFFF59E0B),
    accentSoft = Color(0xFFFCD34D),
    accentGlow = Color(0x33F59E0B),
    glassFill = Color(0xEE131C2E),
    glassFillStrong = Color(0xF81E293B),
    glassBorder = Color(0x3394A3B8),
    glassHighlight = Color(0x26FFFFFF),
    glassShadow = Color(0x80000000),
    danger = Color(0xFFF87171),
    atelierCanvas = Color(0xFF0B0F19),
    atelierContainer = Color(0xFF131C2E),
    ivory = Color(0xFFFFFFFF),
    ivoryMuted = Color(0xFFCBD5E1),
    saffronDeep = Color(0xFF38BDF8),
    silkMist = Color(0xFF1E293B),
    modalityImage = Color(0xFFF59E0B),
    modalityVideo = Color(0xFFFB923C),
    modalityCode = Color(0xFF38BDF8),
    modalityAudio = Color(0xFFF472B6),
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
        onPrimary = Color(0xFF0F172A),
        primaryContainer = accentSoft.copy(alpha = 0.25f),
        onPrimaryContainer = Color(0xFFFEF3C7),
        secondary = Color(0xFF94A3B8),
        onSecondary = Color(0xFF0F172A),
        secondaryContainer = accentSoft.copy(alpha = 0.28f),
        onSecondaryContainer = Color(0xFFFEF3C7),
        tertiary = saffronDeep,
        onTertiary = Color(0xFF0F172A),
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
        primaryContainer = Color(0xFFE8DCC8),
        onPrimaryContainer = Color(0xFF1A1208),
        secondary = Color(0xFF3D5A64),
        onSecondary = Color.White,
        // FilterChip selected fill — brass mist, not M3 purple defaults.
        secondaryContainer = Color(0xFFE4D4B8),
        onSecondaryContainer = Color(0xFF3A2A14),
        tertiary = saffronDeep,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFD0E0E4),
        onTertiaryContainer = Color(0xFF0E1419),
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
