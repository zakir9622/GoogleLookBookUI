package com.zakir.vestra.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
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
 * Loom Ink atelier — cool mist silk + brass thread on deep ink.
 * Avoids purple gradients, cream+terracotta, and broadsheet density.
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

// Cool mist (#E4ECF1) · brass (#9A7340) · deep ink (#0E1419) · teal loom (#1A3A42)
private val LightPalette = VestraPalette(
    canvas = Color(0xFFE4ECF1),
    surface = Color(0xFFF2F6F8),
    surfaceRaised = Color(0xFFFFFFFF),
    surfaceFloating = Color(0xFFD2DEE6),
    ink = Color(0xFF0E1419),
    inkMuted = Color(0xFF556671),
    accent = Color(0xFF9A7340),
    accentSoft = Color(0xFFC49A5C),
    accentGlow = Color(0x339A7340),
    glassFill = Color(0xF2F4F9FB),
    glassFillStrong = Color(0xFAFFFFFF),
    glassBorder = Color(0x669A7340),
    glassHighlight = Color(0xCCFFFFFF),
    glassShadow = Color(0x1A0E1419),
    danger = Color(0xFFB42318),
    atelierCanvas = Color(0xFF071015),
    atelierContainer = Color(0xFF122028),
    ivory = Color(0xFFE8F0F4),
    ivoryMuted = Color(0xFF9AADB8),
    saffronDeep = Color(0xFF1A3A42),
    silkMist = Color(0xFFC5D4DC),
    modalityImage = Color(0xFF9A7340),
    modalityVideo = Color(0xFFB0693F),
    modalityCode = Color(0xFF1A3A42),
    modalityAudio = Color(0xFFA8677A),
    isDark = false,
)

private val DarkPalette = VestraPalette(
    canvas = Color(0xFF060A0C),
    surface = Color(0xFF0E1518),
    surfaceRaised = Color(0xFF162024),
    surfaceFloating = Color(0xFF0A1013),
    ink = Color(0xFFE8F0F4),
    inkMuted = Color(0xFF8FA3AE),
    accent = Color(0xFFD4A85C),
    accentSoft = Color(0xFFE4C07A),
    accentGlow = Color(0x40D4A85C),
    glassFill = Color(0xF2162024),
    glassFillStrong = Color(0xF8222C32),
    glassBorder = Color(0x66D4A85C),
    glassHighlight = Color(0x33FFFFFF),
    glassShadow = Color(0x66000000),
    danger = Color(0xFFF97066),
    atelierCanvas = Color(0xFF04080A),
    atelierContainer = Color(0xFF101A20),
    ivory = Color(0xFFE8F0F4),
    ivoryMuted = Color(0xFF8FA3AE),
    saffronDeep = Color(0xFF2A5A64),
    silkMist = Color(0xFF243038),
    modalityImage = Color(0xFFD4A85C),
    modalityVideo = Color(0xFFD98B5F),
    modalityCode = Color(0xFF2A5A64),
    modalityAudio = Color(0xFFC98BA0),
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
        onPrimary = Color(0xFF1A1208),
        primaryContainer = accentSoft.copy(alpha = 0.22f),
        onPrimaryContainer = ivory,
        secondary = Color(0xFF8FA3AE),
        onSecondary = Color(0xFF0E1419),
        secondaryContainer = accentSoft.copy(alpha = 0.28f),
        onSecondaryContainer = ivory,
        tertiary = saffronDeep,
        onTertiary = ivory,
        tertiaryContainer = saffronDeep.copy(alpha = 0.35f),
        onTertiaryContainer = ivory,
        background = canvas,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = surfaceFloating,
        onSurfaceVariant = inkMuted,
        surfaceContainerLowest = surface,
        surfaceContainer = surfaceRaised,
        surfaceContainerHigh = surfaceRaised,
        surfaceContainerHighest = Color(0xFF1E2A30),
        outline = glassBorder,
        error = danger,
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
    CompositionLocalProvider(LocalVestraPalette provides palette) {
        MaterialTheme(
            colorScheme = palette.toScheme(),
            typography = VestraTypography,
            content = content,
        )
    }
}
