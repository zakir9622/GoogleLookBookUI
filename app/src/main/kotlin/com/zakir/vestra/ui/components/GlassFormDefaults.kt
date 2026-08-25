package com.zakir.vestra.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zakir.vestra.ui.theme.VestraColors

/**
 * Opaque form tokens so OutlinedTextField / ExposedDropdownMenu never sit on
 * translucent glass (text must not ghost through menu overlays — WCAG 1.4.3).
 */
object GlassFormDefaults {

    val MenuShadow: Dp = 10.dp

    @Composable
    fun menuContainerColor(): Color {
        // Force fully opaque — never inherit translucent glass fills.
        val base = VestraColors.SurfaceRaised
        return base.copy(alpha = 1f)
    }

    @Composable
    fun outlinedFieldColors(): TextFieldColors {
        val fill = VestraColors.SurfaceRaised
        val ink = VestraColors.Ink
        val muted = VestraColors.InkMuted
        val accent = VestraColors.Accent
        val border = VestraColors.GlassBorder
        return OutlinedTextFieldDefaults.colors(
            focusedContainerColor = fill,
            unfocusedContainerColor = fill,
            disabledContainerColor = fill.copy(alpha = 0.92f),
            errorContainerColor = fill,
            focusedTextColor = ink,
            unfocusedTextColor = ink,
            disabledTextColor = muted,
            focusedLabelColor = accent,
            unfocusedLabelColor = muted,
            disabledLabelColor = muted.copy(alpha = 0.7f),
            focusedSupportingTextColor = muted,
            unfocusedSupportingTextColor = muted,
            focusedBorderColor = accent,
            unfocusedBorderColor = border,
            disabledBorderColor = border.copy(alpha = 0.5f),
            cursorColor = accent,
            focusedTrailingIconColor = accent,
            unfocusedTrailingIconColor = muted,
            disabledTrailingIconColor = muted.copy(alpha = 0.5f),
        )
    }

    @Composable
    fun menuItemColors() = MenuDefaults.itemColors(
        textColor = VestraColors.Ink,
        leadingIconColor = VestraColors.Ink,
        trailingIconColor = VestraColors.InkMuted,
        disabledTextColor = VestraColors.InkMuted.copy(alpha = 0.55f),
        disabledLeadingIconColor = VestraColors.InkMuted.copy(alpha = 0.4f),
        disabledTrailingIconColor = VestraColors.InkMuted.copy(alpha = 0.4f),
    )

    fun fieldModifier(): Modifier = Modifier.fillMaxWidth()
}
