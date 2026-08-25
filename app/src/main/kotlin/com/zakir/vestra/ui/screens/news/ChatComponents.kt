package com.zakir.vestra.ui.screens.news

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zakir.vestra.shared.chat.ChatMessage
import com.zakir.vestra.shared.news.NewsItem
import com.zakir.vestra.ui.TestTags
import com.zakir.vestra.ui.theme.RadiusTokens
import com.zakir.vestra.ui.theme.VestraColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * High-craft chat bubble distinguishing User vs Assistant responses
 * with modern glassmorphism, accent highlights, copy capability, and telemetry tags.
 */
@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    index: Int,
    modifier: Modifier = Modifier,
    modelDisplayName: String? = null,
) {
    val isUser = message.role.equals("user", ignoreCase = true)
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    val formattedTime = remember(message.timestampMs) {
        if (message.timestampMs > 0) {
            try {
                SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestampMs))
            } catch (_: Exception) {
                ""
            }
        } else {
            ""
        }
    }

    val bubbleShape = if (isUser) {
        RoundedCornerShape(
            topStart = RadiusTokens.lg,
            topEnd = RadiusTokens.lg,
            bottomStart = RadiusTokens.lg,
            bottomEnd = 4.dp,
        )
    } else {
        RoundedCornerShape(
            topStart = RadiusTokens.lg,
            topEnd = RadiusTokens.lg,
            bottomStart = 4.dp,
            bottomEnd = RadiusTokens.lg,
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp, bottom = 4.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(VestraColors.Accent.copy(alpha = 0.25f), VestraColors.SaffronDeep.copy(alpha = 0.4f)),
                        ),
                    )
                    .border(1.dp, VestraColors.Accent.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = "AI Assistant",
                    modifier = Modifier.size(16.dp),
                    tint = VestraColors.Accent,
                )
            }
        }

        Surface(
            shape = bubbleShape,
            color = if (isUser) {
                VestraColors.GlassFillStrong
            } else {
                VestraColors.GlassFill
            },
            modifier = Modifier
                .widthIn(max = 320.dp)
                .border(
                    width = 1.dp,
                    brush = if (isUser) {
                        Brush.horizontalGradient(
                            listOf(
                                VestraColors.Accent.copy(alpha = 0.6f),
                                VestraColors.AccentSoft.copy(alpha = 0.3f),
                            ),
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(
                                VestraColors.GlassHighlight,
                                VestraColors.GlassBorder.copy(alpha = 0.4f),
                            ),
                        )
                    },
                    shape = bubbleShape,
                )
                .testTag(TestTags.chatMessageBubble(index, message.role)),
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                // Header row: Role tag + time + copy action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isUser) {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = VestraColors.InkMuted,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "YOU",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                ),
                                color = VestraColors.InkMuted,
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(VestraColors.Accent),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = modelDisplayName ?: (message.providerId ?: "LOOKBOOK AI"),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.5.sp,
                                ),
                                color = VestraColors.Accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (formattedTime.isNotBlank()) {
                            Text(
                                text = formattedTime,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = VestraColors.InkMuted.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(message.text))
                                copied = true
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(20.dp),
                        ) {
                            Icon(
                                imageVector = if (copied) Icons.Outlined.Done else Icons.Outlined.ContentCopy,
                                contentDescription = "Copy message",
                                modifier = Modifier.size(12.dp),
                                tint = if (copied) VestraColors.Accent else VestraColors.InkMuted,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Message Text Content
                Text(
                    text = message.text.ifBlank { "…" },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 22.sp,
                        letterSpacing = 0.2.sp,
                    ),
                    color = VestraColors.Ink,
                )
            }
        }

        if (isUser) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp, bottom = 4.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(VestraColors.GlassFill)
                    .border(1.dp, VestraColors.GlassBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = "You",
                    modifier = Modifier.size(16.dp),
                    tint = VestraColors.Ink,
                )
            }
        }
    }
}

/**
 * Animated pulsating typing indicator for incoming model responses.
 */
@Composable
fun ChatTypingIndicator(
    modifier: Modifier = Modifier,
    modelLabel: String = "Lookbook Assistant",
) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val dot1Scale by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot1",
    )
    val dot2Scale by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot2",
    )
    val dot3Scale by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot3",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(
            modifier = Modifier
                .padding(end = 8.dp, bottom = 4.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(VestraColors.Accent.copy(alpha = 0.25f), VestraColors.SaffronDeep.copy(alpha = 0.4f)),
                    ),
                )
                .border(1.dp, VestraColors.Accent.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.SmartToy,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = VestraColors.Accent,
            )
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = RadiusTokens.lg,
                topEnd = RadiusTokens.lg,
                bottomStart = 4.dp,
                bottomEnd = RadiusTokens.lg,
            ),
            color = VestraColors.GlassFill,
            modifier = Modifier.border(
                1.dp,
                VestraColors.GlassBorder.copy(alpha = 0.5f),
                RoundedCornerShape(
                    topStart = RadiusTokens.lg,
                    topEnd = RadiusTokens.lg,
                    bottomStart = 4.dp,
                    bottomEnd = RadiusTokens.lg,
                ),
            ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "$modelLabel is thinking…",
                    style = MaterialTheme.typography.labelSmall,
                    color = VestraColors.Accent,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .scale(dot1Scale)
                            .clip(CircleShape)
                            .background(VestraColors.Accent),
                    )
                    Box(
                        Modifier
                            .size(8.dp)
                            .scale(dot2Scale)
                            .clip(CircleShape)
                            .background(VestraColors.AccentSoft),
                    )
                    Box(
                        Modifier
                            .size(8.dp)
                            .scale(dot3Scale)
                            .clip(CircleShape)
                            .background(VestraColors.AccentGlow),
                    )
                }
            }
        }
    }
}

/**
 * Editorial Empty State with starter queries for instant conversation kickoff.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatEmptyState(
    onPromptSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val starterPrompts = listOf(
        "Discuss modest winter layering & fabric textures" to "🧥 Styling",
        "Compare on-device LiteRT Gemma vs Cloud Llama 70B" to "⚡ Performance",
        "Suggest tailored silhouettes for contemporary fashion" to "📐 Silhouettes",
        "Synthesize runway trends for modest apparel" to "✨ Editorial",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(VestraColors.Accent.copy(alpha = 0.25f), Color.Transparent),
                    ),
                )
                .border(1.dp, VestraColors.Accent.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = VestraColors.Accent,
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "Atelier Intelligence",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            ),
            color = VestraColors.Ink,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Ask questions about modest fashion, runway aesthetics, on-device reasoning, or tap a headline above.",
            style = MaterialTheme.typography.bodySmall,
            color = VestraColors.InkMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = "CONVERSATION STARTERS",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = VestraColors.InkMuted,
        )

        Spacer(Modifier.height(10.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            starterPrompts.forEach { (prompt, tag) ->
                Surface(
                    shape = RoundedCornerShape(RadiusTokens.md),
                    color = VestraColors.GlassFill,
                    modifier = Modifier
                        .clip(RoundedCornerShape(RadiusTokens.md))
                        .border(1.dp, VestraColors.GlassBorder.copy(alpha = 0.5f), RoundedCornerShape(RadiusTokens.md))
                        .clickable { onPromptSelected(prompt) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = VestraColors.Accent,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodySmall,
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

/**
 * Top News Header & Carousel Strip for quick headlines browsing and discussion insertion.
 */
@Composable
fun NewsHeadlinesBar(
    newsItems: List<NewsItem>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onHeadlineClick: (NewsItem, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { expanded = !expanded },
            ) {
                Icon(
                    imageVector = Icons.Outlined.Newspaper,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = VestraColors.Accent,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "LIVE HEADLINES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                    ),
                    color = VestraColors.Ink,
                )
                if (newsItems.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(VestraColors.Accent.copy(alpha = 0.18f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "${newsItems.size}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = VestraColors.Accent,
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse headlines" else "Expand headlines",
                    modifier = Modifier.size(18.dp),
                    tint = VestraColors.InkMuted,
                )
            }

            IconButton(
                onClick = onRefresh,
                enabled = !refreshing,
                modifier = Modifier
                    .size(28.dp)
                    .testTag(TestTags.CHAT_REFRESH_BUTTON),
            ) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = VestraColors.Accent,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Refresh headlines",
                        modifier = Modifier.size(16.dp),
                        tint = VestraColors.Accent,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            if (newsItems.isEmpty()) {
                if (refreshing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "Loading latest fashion & AI dispatches…",
                            style = MaterialTheme.typography.bodySmall,
                            color = VestraColors.InkMuted,
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    newsItems.take(8).forEachIndexed { index, item ->
                        Surface(
                            shape = RoundedCornerShape(RadiusTokens.md),
                            color = VestraColors.GlassFill,
                            modifier = Modifier
                                .widthIn(min = 180.dp, max = 260.dp)
                                .clip(RoundedCornerShape(RadiusTokens.md))
                                .border(
                                    1.dp,
                                    VestraColors.GlassBorder.copy(alpha = 0.4f),
                                    RoundedCornerShape(RadiusTokens.md),
                                )
                                .clickable { onHeadlineClick(item, index) }
                                .testTag(TestTags.chatHeadlineCard(index)),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = item.source.uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        color = VestraColors.Accent,
                                    )
                                    Text(
                                        text = "Tap to discuss",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = VestraColors.InkMuted,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 16.sp,
                                    ),
                                    color = VestraColors.Ink,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Persistent bottom input bar with floating glass aesthetic, model trigger pill,
 * multi-line prompt support, and reactive Send/Stop control.
 */
@Composable
fun ChatPersistentInputBar(
    prompt: String,
    onPromptChange: (String) -> Unit,
    modelLabel: String,
    busy: Boolean,
    enabled: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onModelClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    placeholder: String = "Message Lookbook or tap a headline…",
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        shape = RoundedCornerShape(RadiusTokens.lg),
        color = VestraColors.GlassFillStrong,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            VestraColors.GlassHighlight,
                            VestraColors.Accent.copy(alpha = 0.3f),
                        ),
                    ),
                    shape = RoundedCornerShape(RadiusTokens.lg),
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            // Top control row: Model Pill + Prompt length / Clear
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = VestraColors.GlassFill,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, VestraColors.GlassBorder.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .clickable(enabled = onModelClick != null) { onModelClick?.invoke() }
                        .testTag(TestTags.MODEL_CHIP),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (busy) VestraColors.Danger else VestraColors.Accent),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = modelLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = VestraColors.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (onModelClick != null) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = "Change model",
                                modifier = Modifier.size(12.dp),
                                tint = VestraColors.InkMuted,
                            )
                        }
                    }
                }

                if (prompt.isNotBlank()) {
                    IconButton(
                        onClick = { onPromptChange("") },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Clear,
                            contentDescription = "Clear input",
                            modifier = Modifier.size(14.dp),
                            tint = VestraColors.InkMuted,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Main input row: TextField + Send/Stop button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(TestTags.PROMPT_INPUT),
                    placeholder = {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = VestraColors.InkMuted.copy(alpha = 0.7f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    maxLines = 4,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = VestraColors.Ink),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = VestraColors.Accent,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (prompt.isNotBlank() && !busy && enabled) {
                                onSend()
                            }
                        },
                    ),
                )

                Spacer(Modifier.width(8.dp))

                if (busy) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(VestraColors.Danger.copy(alpha = 0.15f))
                            .border(1.dp, VestraColors.Danger, CircleShape)
                            .clickable(onClick = onStop)
                            .testTag(TestTags.SEND_BUTTON),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Stop,
                            contentDescription = "Stop generation",
                            modifier = Modifier.size(20.dp),
                            tint = VestraColors.Danger,
                        )
                    }
                } else {
                    val canSend = prompt.isNotBlank() && enabled
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (canSend) {
                                    Brush.linearGradient(
                                        listOf(VestraColors.Accent, VestraColors.AccentSoft),
                                    )
                                } else {
                                    Brush.linearGradient(
                                        listOf(VestraColors.GlassFill, VestraColors.GlassFill),
                                    )
                                },
                            )
                            .border(
                                1.dp,
                                if (canSend) VestraColors.Accent else VestraColors.GlassBorder.copy(alpha = 0.3f),
                                CircleShape,
                            )
                            .clickable(enabled = canSend, onClick = onSend)
                            .testTag(TestTags.SEND_BUTTON),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send message",
                            modifier = Modifier.size(18.dp),
                            tint = if (canSend) Color.White else VestraColors.InkMuted.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}
