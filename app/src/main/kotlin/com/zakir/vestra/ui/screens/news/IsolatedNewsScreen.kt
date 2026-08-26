package com.zakir.vestra.ui.screens.news

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
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zakir.vestra.shared.cloud.FreeCloudDiscovery
import com.zakir.vestra.shared.news.NewsRepository
import com.zakir.vestra.shared.packs.ModelPackManager
import com.zakir.vestra.shared.settings.AppSettings
import com.zakir.vestra.ui.ChatViewModel
import com.zakir.vestra.ui.components.SpatialBackground
import com.zakir.vestra.ui.theme.RadiusTokens
import com.zakir.vestra.ui.theme.VestraColors

/**
 * Isolated full-screen News & Chat Intel Screen.
 */
@Composable
fun IsolatedNewsScreen(
    newsRepository: NewsRepository?,
    chatViewModel: ChatViewModel?,
    appSettings: AppSettings,
    freeCloudDiscovery: FreeCloudDiscovery?,
    packManager: ModelPackManager,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPacks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SpatialBackground {
        Column(
            modifier = modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            // Dedicated Top Bar
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
                            .background(Color(0xFF6366F1).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFF6366F1).copy(alpha = 0.4f), RoundedCornerShape(RadiusTokens.sm)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Newspaper,
                            contentDescription = "News & Intel",
                            tint = Color(0xFF6366F1),
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "Fashion Intel & Chat",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                            ),
                            color = VestraColors.Ink,
                        )
                        Text(
                            text = "Live couture trends & on-device reasoning",
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

            // Embedded NewsChatScreen Content
            Box(modifier = Modifier.fillMaxSize()) {
                NewsChatScreen(
                    newsRepository = newsRepository,
                    chatViewModel = chatViewModel,
                    appSettings = appSettings,
                    freeCloudDiscovery = freeCloudDiscovery,
                    packManager = packManager,
                )
            }
        }
    }
}
