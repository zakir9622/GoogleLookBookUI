package com.zakir.vestra.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zakir.vestra.ui.theme.VestraColors

/**
 * Floating status pill for the cyber-atelier header: "Pro Tier (NPU)" with a
 * cyan dot when the on-device Pro engine is ready (pack installed + capable
 * device), dimmed otherwise. Frosted container with a thin glass border.
 */
@Composable
fun ProTierPill(active: Boolean, modifier: Modifier = Modifier) {
    val cyan = Color(0xFF35E0E0)
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(VestraColors.AtelierContainer)
            .border(1.dp, VestraColors.GlassBorder, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (active) cyan else VestraColors.IvoryMuted.copy(alpha = 0.5f)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Pro Tier (NPU)",
            style = MaterialTheme.typography.labelMedium,
            color = if (active) VestraColors.Ivory else VestraColors.IvoryMuted,
        )
    }
}
