package com.zakir.vestra.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zakir.vestra.shared.cloud.AiCapability
import com.zakir.vestra.shared.cloud.FreeCloudDiscovery
import com.zakir.vestra.shared.packs.ModelPackManager
import com.zakir.vestra.shared.settings.AppSettings
import com.zakir.vestra.ui.GenerativeViewModel
import com.zakir.vestra.ui.components.SpatialBackground
import com.zakir.vestra.ui.theme.RadiusTokens
import com.zakir.vestra.ui.theme.VestraColors

/**
 * Isolated full-screen Studio generator screen for each modality (Image, Video, Code, Audio).
 * Contains dedicated top header with back button, model badge, and the unified studio workspace.
 */
@Composable
fun IsolatedStudioScreen(
    capability: AiCapability,
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    viewModel: GenerativeViewModel,
    appSettings: AppSettings,
    packManager: ModelPackManager,
    freeCloudDiscovery: FreeCloudDiscovery?,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPacks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cloudModelsEnabled by appSettings.cloudModelsEnabled.collectAsState()
    val packStates by packManager.states.collectAsState()

    SpatialBackground {
        Column(
            modifier = modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            // Dedicated Clean Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(VestraColors.GlassFill)
                            .border(1.dp, VestraColors.GlassBorder, CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back to Home",
                            tint = VestraColors.Ink,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(RadiusTokens.sm))
                            .background(accentColor.copy(alpha = 0.15f))
                            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(RadiusTokens.sm)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                            ),
                            color = VestraColors.Ink,
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = VestraColors.InkMuted,
                            maxLines = 1,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenPacks,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(VestraColors.GlassFill)
                            .border(1.dp, VestraColors.GlassBorder, CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = "Packs",
                            tint = VestraColors.Ink,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(VestraColors.GlassFill)
                            .border(1.dp, VestraColors.GlassBorder, CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = VestraColors.Accent,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // Dedicated Canvas / Studio Pane
            Box(modifier = Modifier.fillMaxSize()) {
                if (capability == AiCapability.AUDIO) {
                    AudioStudioPane(
                        viewModel = viewModel,
                        onOpenSettings = onOpenSettings,
                        freeCloudDiscovery = freeCloudDiscovery,
                        packManager = packManager,
                    )
                } else {
                    UnifiedStudioPane(
                        capability = capability,
                        viewModel = viewModel,
                        onOpenSettings = onOpenSettings,
                        freeCloudDiscovery = freeCloudDiscovery,
                        packManager = packManager,
                    )
                }
            }
        }
    }
}
