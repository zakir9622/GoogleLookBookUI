package com.zakir.vestra.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zakir.vestra.ui.TestTags
import com.zakir.vestra.ui.theme.RadiusTokens
import com.zakir.vestra.ui.theme.VestraColors

/**
 * Data item representing a quick contextual prompt starter chip.
 */
data class QuickPromptItem(
    val prompt: String,
    val tag: String? = null,
    val iconDesc: String? = null,
)

/**
 * Quick Prompt horizontal chip carousel rendered above the persistent input dock,
 * providing one-tap contextual suggestions based on the active chat/studio module.
 */
@Composable
fun QuickPromptCarousel(
    prompts: List<QuickPromptItem>,
    onSelectPrompt: (String) -> Unit,
    enabled: Boolean = true,
    title: String? = "QUICK PROMPTS",
    modifier: Modifier = Modifier,
) {
    if (prompts.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .testTag(TestTags.QUICK_PROMPT_CAROUSEL),
    ) {
        if (title != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = VestraColors.Accent,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                        fontSize = 10.sp,
                    ),
                    color = VestraColors.InkMuted,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            prompts.forEachIndexed { index, item ->
                Surface(
                    onClick = { if (enabled) onSelectPrompt(item.prompt) },
                    enabled = enabled,
                    shape = RoundedCornerShape(RadiusTokens.md),
                    color = VestraColors.GlassFillStrong,
                    border = BorderStroke(
                        1.dp,
                        VestraColors.GlassBorder.copy(alpha = 0.6f),
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(RadiusTokens.md))
                        .testTag(TestTags.quickPromptChip(index)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (item.tag != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .padding(end = 6.dp),
                            ) {
                                Text(
                                    text = item.tag,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                    ),
                                    color = VestraColors.Accent,
                                )
                            }
                        }
                        Text(
                            text = item.prompt,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                            ),
                            color = VestraColors.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
