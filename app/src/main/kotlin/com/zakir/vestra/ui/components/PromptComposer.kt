package com.zakir.vestra.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.zakir.vestra.ui.TestTags
import com.zakir.vestra.ui.theme.VestraColors

/**
 * Isolated floating spatial generation dock — combines prompt input, model selector pill,
 * input file attachment, assist controls, and an integrated live telemetry box with countdown.
 */
@Composable
fun PromptComposer(
    prompt: String,
    onPromptChange: (String) -> Unit,
    modelLabel: String,
    onModelClick: (() -> Unit)? = null,
    assistCount: Int = 0,
    onAssistsClick: (() -> Unit)? = null,
    busy: Boolean,
    enabled: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Describe the look…",
    referenceUri: String? = null,
    onAddReference: (() -> Unit)? = null,
    onClearReference: (() -> Unit)? = null,
    assistToggles: (@Composable () -> Unit)? = null,
    liveLog: List<String> = emptyList(),
    generationStartedAtMs: Long? = null,
    deadlineEpochMs: Long? = null,
    showLiveDock: Boolean = true,
    quickPrompts: List<QuickPromptItem> = emptyList(),
    onSelectQuickPrompt: ((String) -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        // Quick Prompts Horizontal Chip Carousel above persistent dock
        if (quickPrompts.isNotEmpty() && onSelectQuickPrompt != null && !busy) {
            QuickPromptCarousel(
                prompts = quickPrompts,
                onSelectPrompt = onSelectQuickPrompt,
                enabled = enabled && !busy,
            )
        }

        // Attached Live Telemetry & Countdown Box (Above input controls in the dock)
        if (showLiveDock && (busy || liveLog.isNotEmpty())) {
            LiveGenConsole(
                lines = liveLog,
                generationStartedAtMs = generationStartedAtMs,
                deadlineEpochMs = deadlineEpochMs,
                collapsible = true,
                defaultExpanded = true,
            )
            Spacer(Modifier.height(8.dp))
        }

        val shape = RoundedCornerShape(26.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(VestraColors.SurfaceRaised)
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(VestraColors.GlassHighlight, VestraColors.Accent.copy(alpha = 0.35f)),
                    ),
                    shape,
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            // Attached media preview or upload slot
            if (onAddReference != null || referenceUri != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    referenceUri?.let { uri ->
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, VestraColors.Accent.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                        ) {
                            AsyncImage(
                                model = uri,
                                contentDescription = "Reference",
                                modifier = Modifier
                                    .size(54.dp)
                                    .testTag(TestTags.REFERENCE_IMAGE_THUMB),
                                contentScale = ContentScale.Crop,
                            )
                            if (onClearReference != null) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .align(Alignment.TopEnd)
                                        .clip(CircleShape)
                                        .background(VestraColors.Canvas.copy(alpha = 0.85f))
                                        .clickable { onClearReference() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "Clear reference",
                                        modifier = Modifier.size(12.dp),
                                        tint = VestraColors.Ink,
                                    )
                                }
                            }
                        }
                    }
                    if (onAddReference != null && referenceUri == null) {
                        Box(
                            Modifier
                                .size(50.dp)
                                .testTag(TestTags.ADD_REFERENCE_BUTTON)
                                .clip(RoundedCornerShape(12.dp))
                                .background(VestraColors.GlassFill)
                                .border(1.dp, VestraColors.GlassBorder, RoundedCornerShape(12.dp))
                                .clickable(enabled = !busy, onClick = onAddReference),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.AddPhotoAlternate,
                                contentDescription = "Add reference image",
                                tint = VestraColors.Accent,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Text input
            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.PROMPT_INPUT),
                enabled = !busy,
                minLines = 2,
                maxLines = 5,
                placeholder = {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = VestraColors.InkMuted.copy(alpha = 0.7f),
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VestraColors.Accent.copy(alpha = 0.55f),
                    unfocusedBorderColor = VestraColors.GlassBorder,
                    focusedContainerColor = VestraColors.GlassFill,
                    unfocusedContainerColor = VestraColors.GlassFill,
                    focusedTextColor = VestraColors.Ink,
                    unfocusedTextColor = VestraColors.Ink,
                ),
                shape = RoundedCornerShape(16.dp),
            )

            // Assist Toggles Row
            if (assistToggles != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    assistToggles()
                }
            }

            // Bottom action row: Model Pill + Assists Counter + Send Orb
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ModelChip(
                    label = modelLabel,
                    onClick = onModelClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(TestTags.MODEL_CHIP),
                )
                if (onAssistsClick != null || assistCount > 0) {
                    AssistChip(
                        count = assistCount,
                        onClick = onAssistsClick,
                        modifier = Modifier.testTag(TestTags.ASSIST_CHIP),
                    )
                }
                SendOrb(
                    busy = busy,
                    enabled = enabled && (busy || prompt.isNotBlank()),
                    onSend = onSend,
                    onStop = onStop,
                    modifier = Modifier.testTag(TestTags.SEND_BUTTON),
                )
            }
        }
    }
}

@Composable
private fun ModelChip(
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    val a11y = if (onClick != null) {
        "Selected model $label. Opens model picker."
    } else {
        "Selected model $label"
    }
    Row(
        modifier
            .clip(shape)
            .background(VestraColors.GlassFill)
            .border(1.dp, VestraColors.Accent.copy(alpha = 0.4f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = a11y }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(VestraColors.Accent),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = VestraColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AssistChip(count: Int, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    val a11y = when {
        count <= 0 -> "No assists active"
        count == 1 -> "1 assist active"
        else -> "$count assists active"
    }
    Row(
        modifier
            .clip(shape)
            .background(VestraColors.GlassFill)
            .border(1.dp, VestraColors.GlassBorder, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = a11y }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Layers,
            contentDescription = null,
            tint = VestraColors.InkMuted,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = VestraColors.Ink,
        )
    }
}

@Composable
private fun SendOrb(
    busy: Boolean,
    enabled: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                if (busy) {
                    Brush.radialGradient(listOf(VestraColors.Danger, VestraColors.SaffronDeep))
                } else if (enabled) {
                    Brush.radialGradient(listOf(VestraColors.AccentSoft, VestraColors.SaffronDeep))
                } else {
                    Brush.radialGradient(
                        listOf(
                            VestraColors.InkMuted.copy(alpha = 0.35f),
                            VestraColors.InkMuted.copy(alpha = 0.2f),
                        ),
                    )
                },
            )
            .clickable(enabled = enabled) { if (busy) onStop() else onSend() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (busy) Icons.Outlined.Stop else Icons.AutoMirrored.Filled.Send,
            contentDescription = if (busy) "Cancel generation" else "Generate",
            tint = VestraColors.Ivory,
            modifier = Modifier.size(20.dp),
        )
    }
}
