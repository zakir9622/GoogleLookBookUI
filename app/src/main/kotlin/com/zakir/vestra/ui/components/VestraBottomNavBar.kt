package com.zakir.vestra.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Collections
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.zakir.vestra.ui.Routes
import com.zakir.vestra.ui.theme.VestraColors
import com.zakir.vestra.ui.theme.VestraMotion
import com.zakir.vestra.ui.theme.VestraShapes
import com.zakir.vestra.ui.theme.VestraSpacing

/**
 * Top-level Bottom Navigation destinations
 */
enum class BottomNavDestination(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
    val aliases: Set<String> = emptySet(),
) {
    HOME(
        route = Routes.HOME,
        title = "Home",
        selectedIcon = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome,
        testTag = "dock_tab_home",
        aliases = setOf("home", "studio", "studio/home", Routes.STUDIO),
    ),
    LIBRARY(
        route = Routes.LIBRARY,
        title = "Library",
        selectedIcon = Icons.Filled.Collections,
        unselectedIcon = Icons.Outlined.Collections,
        testTag = "dock_tab_library",
        aliases = setOf("library", "wardrobe", Routes.WARDROBE),
    ),
    SETTINGS(
        route = Routes.SETTINGS,
        title = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "dock_tab_settings",
        aliases = setOf("settings", Routes.SETTINGS),
    );

    fun isSelected(destination: NavDestination?): Boolean {
        if (destination == null) return false
        val currentRoute = destination.route ?: return false
        return destination.hierarchy.any { it.route == route || it.route in aliases } ||
            currentRoute == route ||
            currentRoute in aliases
    }
}

/**
 * Spatial Glass Bottom Navigation Bar using Jetpack Compose Navigation
 */
@Composable
fun VestraBottomNavBar(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = VestraSpacing.md, vertical = VestraSpacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(VestraShapes.sheet))
                .background(VestraColors.Surface.copy(alpha = 0.98f))
                .border(
                    width = 1.dp,
                    color = VestraColors.GlassBorder,
                    shape = RoundedCornerShape(VestraShapes.sheet),
                )
                .padding(horizontal = VestraSpacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomNavDestination.entries.forEach { destination ->
                    val selected = destination.isSelected(currentDestination)
                    BottomNavItem(
                        destination = destination,
                        isSelected = selected,
                        onClick = {
                            if (!selected) {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    destination: BottomNavDestination,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.72f else 1f,
        animationSpec = tween(VestraMotion.pressMillis),
        label = "bottom_nav_press_alpha",
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) VestraColors.Accent else VestraColors.IvoryMuted.copy(alpha = 0.65f),
        label = "bottom_nav_content_color",
    )

    Column(
        modifier = modifier
            .testTag(destination.testTag)
            .graphicsLayer { alpha = pressAlpha }
            .clip(RoundedCornerShape(VestraShapes.control))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
            contentDescription = destination.title,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )

        Spacer(Modifier.height(2.dp))

        Text(
            text = destination.title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            ),
            color = contentColor,
        )

        // Animated Active Dot
        AnimatedVisibility(
            visible = isSelected,
            enter = fadeIn(),
            exit = fadeOut(),
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
