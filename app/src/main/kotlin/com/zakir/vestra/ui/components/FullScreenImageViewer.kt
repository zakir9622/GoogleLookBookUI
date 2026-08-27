package com.zakir.vestra.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.zakir.vestra.media.MediaExport
import com.zakir.vestra.shared.cloud.ImageEditIntent
import com.zakir.vestra.shared.cloud.ImageEditIntentCatalog
import com.zakir.vestra.ui.theme.VestraColors
import java.io.File

/**
 * Gemini-style immersive full-screen image expansion viewer.
 * Provides smooth pinch-to-zoom / double-tap zoom, clean floating controls,
 * and quick-action buttons for Save, Remix, and Share.
 */
@Composable
fun FullScreenImageViewer(
    imagePath: String,
    prompt: String? = null,
    onDismiss: () -> Unit,
    onRemix: (() -> Unit)? = null,
    onReport: (() -> Unit)? = null,
    onEditIntent: ((ImageEditIntent) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageFile = remember(imagePath) { File(imagePath) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showControls by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                                offset = Offset.Zero
                            }
                        },
                        onTap = {
                            showControls = !showControls
                        },
                    )
                },
        ) {
            // Full Screen Zoomable & Pannable Image Canvas
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                val maxX = (size.width * (scale - 1)) / 2f
                                val maxY = (size.height * (scale - 1)) / 2f
                                offset = Offset(
                                    x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    y = (offset.y + pan.y).coerceIn(-maxY, maxY),
                                )
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = imageFile,
                    contentDescription = prompt ?: "Full screen preview",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                        )
                        .testTag("full_screen_image"),
                    contentScale = ContentScale.Fit,
                )
            }

            // Top Header Bar Overlay
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent),
                            ),
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                            .testTag("close_full_screen_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close full screen",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    if (!prompt.isNullOrBlank()) {
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.2.sp,
                            ),
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    // Quick double-tap zoom reset hint
                    if (scale > 1f) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = VestraColors.Accent.copy(alpha = 0.25f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, VestraColors.Accent.copy(alpha = 0.6f)),
                            modifier = Modifier.clickable {
                                scale = 1f
                                offset = Offset.Zero
                            },
                        ) {
                            Text(
                                text = "1x Reset",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                ),
                                color = VestraColors.Accent,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            // Bottom Gemini-Style Floating Action Capsule (Save, Remix, Share)
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                            ),
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (onEditIntent != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ImageEditIntentCatalog.all.forEach { intent ->
                                    ViewerActionButton(
                                        icon = Icons.Outlined.AutoAwesome,
                                        label = intent.label,
                                        isAccent = true,
                                        onClick = {
                                            onDismiss()
                                            onEditIntent(intent)
                                        },
                                        testTag = "viewer_edit_${intent.id}",
                                    )
                                }
                            }
                        }
                        Surface(
                        shape = RoundedCornerShape(32.dp),
                        color = Color(0xFF1E1F24).copy(alpha = 0.92f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    VestraColors.Accent.copy(alpha = 0.5f),
                                    Color.White.copy(alpha = 0.2f),
                                    VestraColors.Accent.copy(alpha = 0.5f),
                                ),
                            ),
                        ),
                        shadowElevation = 8.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 1. SAVE Button
                            ViewerActionButton(
                                icon = Icons.Outlined.SaveAlt,
                                label = "Save",
                                onClick = {
                                    MediaExport.saveImageToGallery(context, imageFile)
                                    Toast.makeText(context, "Saved to Photos", Toast.LENGTH_SHORT).show()
                                },
                                testTag = "viewer_save_button",
                            )

                            // 2. REMIX Button (Gemini-style)
                            if (onRemix != null) {
                                ViewerActionButton(
                                    icon = Icons.Outlined.AutoAwesome,
                                    label = "Remix",
                                    isAccent = true,
                                    onClick = {
                                        onDismiss()
                                        onRemix()
                                    },
                                    testTag = "viewer_remix_button",
                                )
                            }

                            // 3. SHARE Button
                            ViewerActionButton(
                                icon = Icons.Outlined.Share,
                                label = "Share",
                                onClick = {
                                    MediaExport.share(context, imageFile, "Share look")
                                },
                                testTag = "viewer_share_button",
                            )

                            // 4. REPORT Button
                            if (onReport != null) {
                                ViewerActionButton(
                                    icon = Icons.Outlined.Report,
                                    label = "Report",
                                    onClick = onReport,
                                    testTag = "viewer_report_button",
                                )
                            }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isAccent: Boolean = false,
    testTag: String = "",
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (isAccent) VestraColors.Accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isAccent) VestraColors.Accent.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.15f),
        ),
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(17.dp),
                tint = if (isAccent) VestraColors.Accent else Color.White,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp,
                ),
                color = if (isAccent) VestraColors.Accent else Color.White,
            )
        }
    }
}
