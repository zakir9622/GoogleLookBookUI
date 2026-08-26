package com.zakir.vestra.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.zakir.vestra.ui.theme.VestraColors
import java.io.File
import java.io.FileOutputStream

data class AttachmentItem(
    val uri: String,
    val name: String,
    val isImage: Boolean = true,
    val mimeType: String? = null,
)

/**
 * Modern attachment bottom sheet allowing users to add from Camera, Gallery, or Files.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentOptionsSheet(
    onDismiss: () -> Unit,
    onAttachmentAdded: (AttachmentItem) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Camera Capture Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            runCatching {
                val file = File(context.cacheDir, "camera_capture_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                val uri = Uri.fromFile(file).toString()
                onAttachmentAdded(
                    AttachmentItem(
                        uri = uri,
                        name = "Photo Capture",
                        isImage = true,
                        mimeType = "image/jpeg",
                    ),
                )
                onDismiss()
            }.onFailure {
                Toast.makeText(context, "Failed to process camera photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Gallery Picker Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            val name = queryFileName(context, uri) ?: "Gallery Image"
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            onAttachmentAdded(
                AttachmentItem(
                    uri = uri.toString(),
                    name = name,
                    isImage = true,
                    mimeType = mime,
                ),
            )
            onDismiss()
        }
    }

    // Files Picker Launcher
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            val name = queryFileName(context, uri) ?: "Selected File"
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val isImg = mime.startsWith("image/")
            onAttachmentAdded(
                AttachmentItem(
                    uri = uri.toString(),
                    name = name,
                    isImage = isImg,
                    mimeType = mime,
                ),
            )
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = VestraColors.Canvas,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                "Add Attachment",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = VestraColors.Ink,
            )
            Text(
                "Attach images, garments, or documents to your prompt",
                style = MaterialTheme.typography.bodySmall,
                color = VestraColors.InkMuted,
            )

            Spacer(Modifier.height(16.dp))

            // Option: Camera
            AttachmentOptionRow(
                icon = Icons.Outlined.CameraAlt,
                title = "Take from Camera",
                subtitle = "Capture a real-time photo",
                onClick = { cameraLauncher.launch() },
            )

            Spacer(Modifier.height(10.dp))

            // Option: Gallery
            AttachmentOptionRow(
                icon = Icons.Outlined.Image,
                title = "Add from Gallery",
                subtitle = "Pick existing photos from your library",
                onClick = { galleryLauncher.launch("image/*") },
            )

            Spacer(Modifier.height(10.dp))

            // Option: Files / Documents
            AttachmentOptionRow(
                icon = Icons.Outlined.Description,
                title = "Add from Files",
                subtitle = "Browse documents, models, and assets",
                onClick = { fileLauncher.launch(arrayOf("*/*")) },
            )

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AttachmentOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(VestraColors.SurfaceRaised)
            .border(1.dp, VestraColors.GlassBorder.copy(alpha = 0.5f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(VestraColors.Accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = VestraColors.Accent,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                color = VestraColors.Ink,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Horizontal preview carousel showing match thumbnails of attached items.
 */
@Composable
fun AttachmentThumbnailBar(
    attachments: List<AttachmentItem>,
    onRemoveAttachment: (AttachmentItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        attachments.forEach { item ->
            AttachmentThumbnailItem(
                item = item,
                onRemove = { onRemoveAttachment(item) },
            )
        }
    }
}

@Composable
fun AttachmentThumbnailItem(
    item: AttachmentItem,
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(shape)
            .background(VestraColors.SurfaceRaised)
            .border(1.dp, VestraColors.Accent.copy(alpha = 0.5f), shape),
    ) {
        if (item.isImage) {
            AsyncImage(
                model = item.uri,
                contentDescription = item.name,
                modifier = Modifier.size(56.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.InsertDriveFile,
                    contentDescription = null,
                    tint = VestraColors.Accent,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = VestraColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Close / Detach button
        Box(
            modifier = Modifier
                .size(18.dp)
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .clip(CircleShape)
                .background(VestraColors.Canvas.copy(alpha = 0.85f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Remove attachment",
                modifier = Modifier.size(10.dp),
                tint = VestraColors.Ink,
            )
        }
    }
}

fun queryFileName(context: Context, uri: Uri): String? {
    if (uri.scheme == "content") {
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return cursor.getString(index)
                }
            }
        }
    }
    return uri.path?.substringAfterLast('/')
}
