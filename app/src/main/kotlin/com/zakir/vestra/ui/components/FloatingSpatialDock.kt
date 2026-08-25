package com.zakir.vestra.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zakir.vestra.ui.theme.RadiusTokens
import com.zakir.vestra.ui.theme.VestraColors
import com.zakir.vestra.ui.util.rememberReduceMotion

enum class DockDestination {
    HOME,
    STUDIO,
    WARDROBE,
    SETTINGS,
}

/**
 * Floating glassmorphism dock navigation bar matching the Lovable web design.
 * Features 4 navigational slots and a raised, pulsating center "+" Action button.
 */
@Composable
fun FloatingSpatialDock(
    currentDestination: DockDestination,
    onNavigate: (DockDestination) -> Unit,
    onQuickCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Floating pill container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(RadiusTokens.xl))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            VestraColors.AtelierContainer.copy(alpha = 0.92f),
                            VestraColors.AtelierCanvas.copy(alpha = 0.98f),
                        ),
                    ),
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            VestraColors.AccentSoft.copy(alpha = 0.45f),
                            VestraColors.GlassBorder.copy(alpha = 0.2f),
                        ),
                    ),
                    shape = RoundedCornerShape(RadiusTokens.xl),
                )
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left slot: Home / Atelier
                DockItem(
                    icon = Icons.Outlined.AutoAwesome,
                    label = "Atelier",
                    selected = currentDestination == DockDestination.HOME,
                    onClick = { onNavigate(DockDestination.HOME) },
                    testTag = "dock_tab_home",
                )

                // Left-Center slot: Studio
                DockItem(
                    icon = Icons.Outlined.GridView,
                    label = "Studios",
                    selected = currentDestination == DockDestination.STUDIO,
                    onClick = { onNavigate(DockDestination.STUDIO) },
                    testTag = "dock_tab_studio",
                )

                // Center placeholder for the elevated FAB
                Spacer(modifier = Modifier.width(56.dp))

                // Right-Center slot: Wardrobe
                DockItem(
                    icon = Icons.Outlined.Checkroom,
                    label = "Wardrobe",
                    selected = currentDestination == DockDestination.WARDROBE,
                    onClick = { onNavigate(DockDestination.WARDROBE) },
                    testTag = "dock_tab_wardrobe",
                )

                // Right slot: Settings
                DockItem(
                    icon = Icons.Outlined.Settings,
                    label = "Settings",
                    selected = currentDestination == DockDestination.SETTINGS,
                    onClick = { onNavigate(DockDestination.SETTINGS) },
                    testTag = "dock_tab_settings",
                )
            }
        }

        // Center Raised Floating Action Button "+"
        CenterCreateFab(
            onClick = onQuickCreate,
            reduceMotion = reduceMotion,
            modifier = Modifier.offset(y = (-14).dp),
        )
    }
}

@Composable
private fun DockItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else if (selected) 1.05f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "dock_item_scale",
    )

    val iconColor by animateColorAsState(
        targetValue = if (selected) VestraColors.Accent else VestraColors.IvoryMuted.copy(alpha = 0.7f),
        label = "dock_icon_color",
    )

    Column(
        modifier = modifier
            .testTag(testTag)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            ),
            color = iconColor,
        )

        // Indicator dot below active item
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(VestraColors.Accent),
            )
        }
    }
}

@Composable
private fun CenterCreateFab(
    onClick: () -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "center_fab_scale",
    )

    val gradient = Brush.radialGradient(
        colors = listOf(
            VestraColors.AccentSoft,
            VestraColors.Accent,
            VestraColors.SaffronDeep,
        ),
    )

    Box(
        modifier = modifier
            .testTag("dock_center_create_fab")
            .size(56.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(gradient)
            .border(2.dp, Color(0xFFFFF2D6).copy(alpha = 0.65f), CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = "Quick Create Studio",
            tint = Color(0xFF071015),
            modifier = Modifier.size(28.dp),
        )
    }
}
