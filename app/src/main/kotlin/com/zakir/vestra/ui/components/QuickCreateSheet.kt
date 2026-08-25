package com.zakir.vestra.ui.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zakir.vestra.shared.cloud.AiCapability
import com.zakir.vestra.ui.screens.home.HomeTab
import com.zakir.vestra.ui.theme.RadiusTokens
import com.zakir.vestra.ui.theme.VestraColors

internal data class QuickToolItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val accentColor: Color,
    val badge: String,
    val tab: HomeTab?,
    val isAction: Boolean = false,
)

/**
 * Lovable-style Quick Create Tool Launcher Modal.
 * Offers direct access to all AI studios and modesty pipelines.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickCreateSheet(
    onSelectTab: (HomeTab) -> Unit,
    onStartTryOn: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val tools = listOf(
        QuickToolItem(
            id = "image_gen",
            title = "Image Studio",
            description = "Modest silhouettes, textiles & couture lookbook renders",
            icon = Icons.Outlined.Image,
            accentColor = Color(0xFF38BDF8),
            badge = "Local + Free Cloud",
            tab = HomeTab.IMAGE,
        ),
        QuickToolItem(
            id = "video_gen",
            title = "Video Studio",
            description = "Cinematic fabric drape, camera sweeps & motion loops",
            icon = Icons.Outlined.Videocam,
            accentColor = Color(0xFFF59E0B),
            badge = "Local Canvas + AI",
            tab = HomeTab.VIDEO,
        ),
        QuickToolItem(
            id = "code_studio",
            title = "Code & LiteRT",
            description = "LiteRT Gemma 4 fashion reasoning & styling logic",
            icon = Icons.Outlined.Code,
            accentColor = Color(0xFF10B981),
            badge = "100% On-Device",
            tab = HomeTab.CODE,
        ),
        QuickToolItem(
            id = "audio_lab",
            title = "Audio Lab",
            description = "Fashion narration, DSP voice shaping & ambiance",
            icon = Icons.Outlined.GraphicEq,
            accentColor = Color(0xFFEC4899),
            badge = "Offline Native TTS",
            tab = HomeTab.AUDIO,
        ),
        QuickToolItem(
            id = "try_on",
            title = "Virtual Try-On",
            description = "Drape abayas, hijabs & garments onto casting models",
            icon = Icons.Outlined.Checkroom,
            accentColor = VestraColors.Accent,
            badge = "Fast / Pro ONNX",
            tab = null,
            isAction = true,
        ),
        QuickToolItem(
            id = "news_trends",
            title = "Modesty Intel",
            description = "Curated fashion intelligence & global trends digest",
            icon = Icons.Outlined.Newspaper,
            accentColor = Color(0xFF6366F1),
            badge = "Live Intel",
            tab = HomeTab.NEWS,
        ),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = VestraColors.AtelierCanvas,
        contentColor = VestraColors.Ivory,
        shape = RoundedCornerShape(topStart = RadiusTokens.xl, topEnd = RadiusTokens.xl),
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(24.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Creative Atelier",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = VestraColors.Ivory,
                    )
                    Text(
                        text = "Select a studio modality or generation workflow",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VestraColors.IvoryMuted,
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(VestraColors.AtelierContainer),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = VestraColors.IvoryMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Tool Cards Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(tools, key = { it.id }) { tool ->
                    ToolCard(
                        tool = tool,
                        onClick = {
                            onDismiss()
                            if (tool.isAction) {
                                onStartTryOn()
                            } else if (tool.tab != null) {
                                onSelectTab(tool.tab)
                            }
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ToolCard(
    tool: QuickToolItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cardBackground = Brush.verticalGradient(
        colors = listOf(
            VestraColors.AtelierContainer.copy(alpha = 0.95f),
            tool.accentColor.copy(alpha = 0.08f),
        ),
    )

    Column(
        modifier = modifier
            .testTag("tool_card_${tool.id}")
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusTokens.lg))
            .background(cardBackground)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        tool.accentColor.copy(alpha = 0.35f),
                        Color.Transparent,
                    ),
                ),
                shape = RoundedCornerShape(RadiusTokens.lg),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon Bubble
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tool.accentColor.copy(alpha = 0.15f))
                    .border(1.dp, tool.accentColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = tool.title,
                    tint = tool.accentColor,
                    modifier = Modifier.size(22.dp),
                )
            }

            // Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(tool.accentColor.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = tool.badge,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = tool.accentColor,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = tool.title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = VestraColors.Ivory,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = tool.description,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
            color = VestraColors.IvoryMuted,
            maxLines = 2,
        )
    }
}
