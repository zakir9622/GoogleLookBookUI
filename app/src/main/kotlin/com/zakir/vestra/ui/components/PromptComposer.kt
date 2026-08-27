package com.zakir.vestra.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.zakir.vestra.ui.TestTags
import com.zakir.vestra.ui.theme.RadiusTokens
import com.zakir.vestra.ui.theme.VestraColors

/**
 * Clean, modern, clutter-free Gemini-style input capsule.
 * Keeps the screen spacious and unobstructed, cleanly docking right above the keyboard.
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
    placeholder: String = "Ask Lookbook or describe look…",
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
    modelLoading: Boolean = false,
    attachments: List<AttachmentItem> = emptyList(),
    onAddAttachment: ((AttachmentItem) -> Unit)? = null,
    onRemoveAttachment: ((AttachmentItem) -> Unit)? = null,
) {
    var showOptionsSheet by remember { mutableStateOf(false) }
    var localAttachments by remember { mutableStateOf<List<AttachmentItem>>(emptyList()) }
    val effectiveAttachments = if (attachments.isNotEmpty()) attachments else localAttachments

    // Plus (+) Menu Sheet: Attachments, Model Switcher, and Generation Assists
    if (showOptionsSheet) {
        ComposerPlusSheet(
            modelLabel = modelLabel,
            onModelClick = onModelClick,
            assistCount = assistCount,
            onAssistsClick = onAssistsClick,
            modelLoading = modelLoading,
            assistToggles = assistToggles,
            onDismiss = { showOptionsSheet = false },
            onAttachmentAdded = { item ->
                if (onAddAttachment != null) {
                    onAddAttachment(item)
                } else {
                    localAttachments = localAttachments + item
                }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        // Compact Attached Media Strip (takes minimal space when active)
        if (effectiveAttachments.isNotEmpty()) {
            AttachmentThumbnailBar(
                attachments = effectiveAttachments,
                onRemoveAttachment = { item ->
                    if (onRemoveAttachment != null) {
                        onRemoveAttachment(item)
                    } else {
                        localAttachments = localAttachments - item
                    }
                },
            )
            Spacer(Modifier.height(6.dp))
        } else if (referenceUri != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, VestraColors.Accent.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                ) {
                    AsyncImage(
                        model = referenceUri,
                        contentDescription = "Reference",
                        modifier = Modifier
                            .size(46.dp)
                            .testTag(TestTags.REFERENCE_IMAGE_THUMB),
                        contentScale = ContentScale.Crop,
                    )
                    if (onClearReference != null) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .align(Alignment.TopEnd)
                                .clip(CircleShape)
                                .background(VestraColors.Canvas.copy(alpha = 0.85f))
                                .clickable { onClearReference() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Clear reference",
                                modifier = Modifier.size(10.dp),
                                tint = VestraColors.Ink,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Reference image attached",
                    style = MaterialTheme.typography.labelSmall,
                    color = VestraColors.InkMuted,
                )
            }
        }

        // Sleek Gemini Floating Capsule Bar
        val capsuleShape = RoundedCornerShape(30.dp)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = capsuleShape,
            color = VestraColors.GlassFillStrong,
            shadowElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                VestraColors.GlassHighlight,
                                VestraColors.Accent.copy(alpha = 0.35f),
                            ),
                        ),
                        shape = capsuleShape,
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: Plus Action Button (+ Menu for attachments, models & assists)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (effectiveAttachments.isNotEmpty() || referenceUri != null || assistCount > 0) {
                                VestraColors.Accent.copy(alpha = 0.18f)
                            } else {
                                VestraColors.GlassFill
                            },
                        )
                        .clickable(enabled = !busy) {
                            if (onAddReference != null && assistToggles == null && onModelClick == null) {
                                onAddReference()
                            } else {
                                showOptionsSheet = true
                            }
                        }
                        .testTag(TestTags.ADD_REFERENCE_BUTTON),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add attachments and options",
                        tint = if (effectiveAttachments.isNotEmpty() || referenceUri != null) VestraColors.Accent else VestraColors.Ink,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Center: Clean, borderless prompt input
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = prompt,
                        onValueChange = onPromptChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TestTags.PROMPT_INPUT),
                        enabled = !busy,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = VestraColors.Ink,
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                        ),
                        cursorBrush = SolidColor(VestraColors.Accent),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (prompt.isNotBlank() && enabled && !busy) {
                                    onSend()
                                }
                            },
                        ),
                        decorationBox = { innerTextField ->
                            if (prompt.isEmpty()) {
                                Text(
                                    text = if (modelLoading) "Initializing model…" else placeholder,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = VestraColors.InkMuted.copy(alpha = 0.65f),
                                        fontSize = 15.sp,
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            innerTextField()
                        },
                    )
                }

                Spacer(Modifier.width(6.dp))

                // Right: Voice dictation mic
                VoiceDictationButton(
                    onTranscription = { spoken ->
                        val updated = if (prompt.isBlank()) spoken else "$prompt $spoken"
                        onPromptChange(updated)
                    },
                    enabled = !busy,
                    modifier = Modifier.size(38.dp),
                )

                Spacer(Modifier.width(4.dp))

                // Right: Send or Stop Orb
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

/**
 * Modern modal sheet opened via the (+) plus capsule action.
 * Aggregates model switching, attachments (Camera, Gallery, Files), and assist toggles in one clean place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposerPlusSheet(
    modelLabel: String,
    onModelClick: (() -> Unit)?,
    assistCount: Int,
    onAssistsClick: (() -> Unit)?,
    modelLoading: Boolean,
    assistToggles: (@Composable () -> Unit)?,
    onDismiss: () -> Unit,
    onAttachmentAdded: (AttachmentItem) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAttachmentSheet by remember { mutableStateOf(false) }

    if (showAttachmentSheet) {
        AttachmentOptionsSheet(
            onDismiss = {
                showAttachmentSheet = false
                onDismiss()
            },
            onAttachmentAdded = { item ->
                onAttachmentAdded(item)
                showAttachmentSheet = false
                onDismiss()
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = VestraColors.SurfaceRaised,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "OPTIONS & ATTACHMENTS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                ),
                color = VestraColors.InkMuted,
            )

            Spacer(Modifier.height(14.dp))

            // Model Switcher Tile
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, VestraColors.GlassBorder, RoundedCornerShape(16.dp))
                    .clickable(enabled = onModelClick != null) {
                        onDismiss()
                        onModelClick?.invoke()
                    }
                    .testTag(TestTags.MODEL_CHIP),
                color = VestraColors.GlassFill,
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (modelLoading) VestraColors.AccentSoft else VestraColors.Accent),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Active Engine",
                                style = MaterialTheme.typography.labelSmall,
                                color = VestraColors.InkMuted,
                            )
                            Text(
                                text = modelLabel,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = VestraColors.Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (onModelClick != null) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = VestraColors.Accent.copy(alpha = 0.12f),
                        ) {
                            Text(
                                text = "Change",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = VestraColors.Accent,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Attachment Actions Tile
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, VestraColors.GlassBorder, RoundedCornerShape(16.dp))
                    .clickable { showAttachmentSheet = true },
                color = VestraColors.GlassFill,
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(VestraColors.Accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AttachFile,
                            contentDescription = null,
                            tint = VestraColors.Accent,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Add Media or File",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = VestraColors.Ink,
                        )
                        Text(
                            text = "Take a photo, choose from gallery, or attach files",
                            style = MaterialTheme.typography.bodySmall,
                            color = VestraColors.InkMuted,
                        )
                    }
                }
            }

            // Generation Assist Toggles (if provided)
            if (assistToggles != null) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "GENERATION PARAMETERS & ASSISTS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                        ),
                        color = VestraColors.InkMuted,
                    )
                    if (onAssistsClick != null || assistCount > 0) {
                        Text(
                            text = "$assistCount active",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = VestraColors.Accent,
                            ),
                            modifier = Modifier.testTag(TestTags.ASSIST_CHIP),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    assistToggles()
                }
            }
        }
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
            .size(38.dp)
            .clip(CircleShape)
            .background(
                if (busy) {
                    Brush.radialGradient(listOf(VestraColors.Danger, VestraColors.SaffronDeep))
                } else if (enabled) {
                    Brush.radialGradient(listOf(VestraColors.AccentSoft, VestraColors.SaffronDeep))
                } else {
                    Brush.radialGradient(
                        listOf(
                            VestraColors.InkMuted.copy(alpha = 0.25f),
                            VestraColors.InkMuted.copy(alpha = 0.15f),
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
            modifier = Modifier.size(18.dp),
        )
    }
}
